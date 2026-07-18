package com.srelab.sandbox.service;

import com.srelab.sandbox.agent.AIAgent;
import com.srelab.sandbox.agent.AgentTranscript;
import com.srelab.sandbox.data.BYOCManager;
import com.srelab.sandbox.data.DatabaseManager;
import com.srelab.sandbox.evaluator.Evaluator;
import com.srelab.sandbox.injector.FaultInjector;
import com.srelab.sandbox.core.SandboxManager;
import com.srelab.sandbox.model.GitImportRequest;
import com.srelab.sandbox.model.RunEvent;
import com.srelab.sandbox.model.RunReport;
import com.srelab.sandbox.model.RunStatus;
import com.srelab.sandbox.model.SandboxConfig;
import com.srelab.sandbox.model.StartRunRequest;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.Network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared run orchestration, extracted from RunCommand so both the CLI and
 * the REST/SSE layer (for the GUI) drive the exact same sandbox -> db ->
 * deploy -> fault -> agent -> verify -> score pipeline, rather than the GUI
 * re-implementing this logic separately.
 *
 * Each active run's mutable state (managers, status, transcript) is tracked
 * in-memory by runId so a REST client can start a run, poll/stream its
 * progress, and separately trigger the agent once ready.
 */
@Service
public class RunService {

    private final Map<String, RunContext> activeRuns = new ConcurrentHashMap<>();

    /**
     * Per-run state, not shared across runs. Each run gets its own manager
     * instances (matching RunCommand's previous per-invocation "new X()"
     * pattern) so concurrent runs from the GUI don't interfere with each
     * other's sandboxes.
     */
    private static class RunContext {
        final SandboxManager sandboxManager = new SandboxManager();
        final DatabaseManager dbManager = new DatabaseManager();
        final BYOCManager byocManager = new BYOCManager();
        final FaultInjector faultInjector = new FaultInjector();
        final Evaluator evaluator = new Evaluator();
        final AIAgent agent;
        final RunStatus status;
        final StartRunRequest request;
        String sandboxId;
        String jdbcUrl;

        RunContext(String runId, StartRunRequest request) {
            this.status = new RunStatus(runId);
            this.request = request;
            this.agent = new AIAgent(sandboxManager);
        }
    }

    /**
     * Runs the full pipeline synchronously up to (and including) fault
     * injection, then either runs the agent automatically (if
     * request.isAutoStartAgent()) or leaves the run parked awaiting an
     * explicit triggerAgent(runId) call, then always finishes with
     * independent health verification and scoring.
     *
     * emit is called for every event along the way; the CLI passes a
     * consumer that prints to stdout, the REST layer passes one that pushes
     * to an SSE queue.
     */
    public String startRun(String runId, StartRunRequest request, Consumer<RunEvent> emit) {
        Consumer<RunEvent> safeEmit = safe(emit);
        RunContext ctx = new RunContext(runId, request);
        activeRuns.put(runId, ctx);

        try {
            runSetupAndFault(ctx, safeEmit);

            if (request.isAutoStartAgent()) {
                runAgentAndFinish(ctx, safeEmit);
            } else {
                ctx.status.setPhase(RunStatus.Phase.AWAITING_AGENT_TRIGGER);
                safeEmit.accept(RunEvent.of(RunEvent.STAGE, "Awaiting explicit agent trigger"));
            }
        } catch (Exception e) {
            ctx.status.setPhase(RunStatus.Phase.FAILED);
            ctx.status.setErrorMessage(e.getMessage());
            safeEmit.accept(RunEvent.of(RunEvent.ERROR, "Run failed: " + e.getMessage()));
            cleanup(ctx, safeEmit);
        }

        return runId;
    }

    /**
     * Wraps a caller-provided event consumer so that a failure in the
     * transport layer (e.g. an SSE client disconnecting mid-run) can never
     * be mistaken for a failure of the actual sandbox/agent pipeline. Event
     * delivery is best-effort; orchestration must continue regardless of
     * whether anyone is listening.
     */
    private Consumer<RunEvent> safe(Consumer<RunEvent> emit) {
        return event -> {
            try {
                emit.accept(event);
            } catch (Exception | Error ignored) {
                // Intentionally swallowed: event delivery is best-effort and
                // must never abort or corrupt the run's actual status.
            }
        };
    }

    /**
     * Explicitly triggers the agent for a run that was started with
     * autoStartAgent=false and is currently parked at AWAITING_AGENT_TRIGGER.
     * This is what the GUI's "Start Agent" button calls, matching the
     * requirement that the agent only runs when a human asks it to.
     */
    public void triggerAgent(String runId, Consumer<RunEvent> emit) {
        Consumer<RunEvent> safeEmit = safe(emit);
        RunContext ctx = activeRuns.get(runId);
        if (ctx == null) {
            throw new IllegalArgumentException("Unknown run: " + runId);
        }
        if (ctx.status.getPhase() != RunStatus.Phase.AWAITING_AGENT_TRIGGER) {
            throw new IllegalStateException(
                "Run " + runId + " is not awaiting an agent trigger (current phase: " + ctx.status.getPhase() + ")");
        }
        try {
            runAgentAndFinish(ctx, safeEmit);
        } catch (Exception e) {
            ctx.status.setPhase(RunStatus.Phase.FAILED);
            ctx.status.setErrorMessage(e.getMessage());
            safeEmit.accept(RunEvent.of(RunEvent.ERROR, "Run failed: " + e.getMessage()));
            cleanup(ctx, safeEmit);
        }
    }

    public RunStatus getStatus(String runId) {
        RunContext ctx = activeRuns.get(runId);
        return ctx != null ? ctx.status : null;
    }

    public List<String> listRunIds() {
        return List.copyOf(activeRuns.keySet());
    }

    // ---- Pipeline stages ----

    private void runSetupAndFault(RunContext ctx, Consumer<RunEvent> emit) {
        StartRunRequest request = ctx.request;

        emit.accept(RunEvent.of(RunEvent.STAGE, "Creating sandbox environment..."));
        SandboxConfig config = new SandboxConfig();
        config.setImage("ubuntu:22.04");
        config.setMemoryLimitMB(512);
        config.setCpuQuota(50000L);
        config.setNetworkIsolated(false);

        ctx.sandboxId = ctx.sandboxManager.createSandbox(config);
        emit.accept(RunEvent.of(RunEvent.LOG, "Sandbox created: " + ctx.sandboxId));

        ctx.status.setPhase(RunStatus.Phase.STARTING_DATABASE);
        emit.accept(RunEvent.of(RunEvent.STAGE, "Starting PostgreSQL database..."));
        Network network = ctx.sandboxManager.getNetwork(ctx.sandboxId);
        ctx.jdbcUrl = ctx.dbManager.createDatabase(ctx.sandboxId, network);
        emit.accept(RunEvent.of(RunEvent.LOG, "Database started: " + ctx.jdbcUrl));

        ctx.status.setPhase(RunStatus.Phase.IMPORTING_CODE);
        importCode(ctx, emit);

        ctx.status.setPhase(RunStatus.Phase.DEPLOYING_APP);
        emit.accept(RunEvent.of(RunEvent.STAGE, "Deploying application..."));
        Map<String, String> envVars = buildEnvVars(request);
        String appUrl = ctx.byocManager.deployApp(ctx.sandboxId, request.getTarget(), envVars, network);
        ctx.status.setAppUrl(appUrl);
        ctx.status.setHealthUrl(ctx.byocManager.getHealthUrl(ctx.sandboxId));
        ctx.status.setAppContainerId(ctx.byocManager.getAppContainerId(ctx.sandboxId));
        emit.accept(RunEvent.of(RunEvent.LOG, "Application deployed: " + appUrl));

        ctx.status.setPhase(RunStatus.Phase.FAULT_INJECTED);
        emit.accept(RunEvent.of(RunEvent.STAGE, "Injecting fault: " + request.getFault() + "..."));
        injectFault(ctx, request.getFault(), appUrl);
        emit.accept(RunEvent.of(RunEvent.LOG, "Fault injected: " + request.getFault()));
    }

    private void importCode(RunContext ctx, Consumer<RunEvent> emit) {
        StartRunRequest request = ctx.request;

        if (request.getRepoUrl() != null && !request.getRepoUrl().isEmpty()) {
            emit.accept(RunEvent.of(RunEvent.STAGE, "Importing code from Git..."));
            GitImportRequest gitRequest = new GitImportRequest();
            gitRequest.setRepoUrl(request.getRepoUrl());
            gitRequest.setBranch(request.getBranch());
            gitRequest.setTargetPath("/app");
            ctx.sandboxManager.importGitRepo(ctx.sandboxId, gitRequest);
            emit.accept(RunEvent.of(RunEvent.LOG, "Code imported to /app"));
        } else if (request.getZipBytes() != null && request.getZipBytes().length > 0) {
            emit.accept(RunEvent.of(RunEvent.STAGE, "Extracting uploaded zip archive..."));
            try {
                byte[] tarGz = convertZipToTarGz(request.getZipBytes());
                ctx.sandboxManager.uploadTarball(ctx.sandboxId, tarGz, "/app");
                emit.accept(RunEvent.of(RunEvent.LOG, "Zip archive extracted to /app"));
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract uploaded zip: " + e.getMessage(), e);
            }
        } else if (request.getPastedFileContent() != null && !request.getPastedFileContent().isEmpty()) {
            String fileName = request.getPastedFileName() != null && !request.getPastedFileName().isBlank()
                ? request.getPastedFileName() : "Pasted.txt";
            emit.accept(RunEvent.of(RunEvent.STAGE, "Writing pasted file " + fileName + "..."));
            writePastedFile(ctx, fileName, request.getPastedFileContent());
            emit.accept(RunEvent.of(RunEvent.LOG, "Pasted file written to /app/" + fileName));
        } else {
            emit.accept(RunEvent.of(RunEvent.STAGE, "No code import requested (using target image as-is)"));
        }
    }

    /**
     * Writes a single pasted file into the sandbox by packing it into a
     * minimal tar.gz via commons-compress and reusing the existing tarball
     * upload/extraction mechanism (SandboxManager.uploadTarball), so no new
     * exec/extraction path is needed inside the sandbox container.
     */
    private void writePastedFile(RunContext ctx, String fileName, String content) {
        try {
            byte[] tarball = buildTarGz(Map.of(fileName, content.getBytes(StandardCharsets.UTF_8)));
            ctx.sandboxManager.uploadTarball(ctx.sandboxId, tarball, "/app");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write pasted file: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts an uploaded zip file's entries and repacks them as a tar.gz,
     * since SandboxManager's extraction path expects `tar -xzf`. This lets
     * the GUI's zip-upload endpoint reuse the existing tarball-extraction
     * plumbing rather than needing a second extraction code path inside the
     * sandbox container.
     */
    public static byte[] convertZipToTarGz(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (isMacMetadataEntry(entry.getName())) continue;
                ByteArrayOutputStream entryContent = new ByteArrayOutputStream();
                zis.transferTo(entryContent);
                entries.put(entry.getName(), entryContent.toByteArray());
            }
        }
        return buildTarGzFromBytes(entries);
    }

    /**
     * Filters out macOS Finder/Archive Utility metadata that ends up inside
     * a zip created via "Compress" on a Mac: a __MACOSX/ sibling tree full
     * of AppleDouble resource-fork files (._Foo.java alongside every real
     * Foo.java). These are never legitimate source files, and their
     * combined path length (e.g. "__MACOSX/deeply/nested/project/._File.java")
     * routinely exceeds the classic tar format's 100-byte filename limit,
     * which previously surfaced as a confusing
     * "file name '...' is too long ( > 100 bytes)" error that had nothing
     * to do with the user's actual code.
     */
    private static boolean isMacMetadataEntry(String entryName) {
        if (entryName.startsWith("__MACOSX/")) return true;
        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
        return fileName.startsWith("._") || fileName.equals(".DS_Store");
    }

    private static byte[] buildTarGz(Map<String, byte[]> filesByPath) throws IOException {
        return buildTarGzFromBytes(filesByPath);
    }

    private static byte[] buildTarGzFromBytes(Map<String, byte[]> filesByPath) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var gzip = new java.util.zip.GZIPOutputStream(out);
             var tar = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(gzip)) {
            // Defense in depth: even after filtering macOS metadata, a
            // legitimately long path from a real project could still exceed
            // the classic tar format's 100-byte filename limit. GNU longfile
            // mode transparently splits long names across an extra header
            // entry instead of throwing.
            tar.setLongFileMode(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_GNU);
            for (var entry : filesByPath.entrySet()) {
                var tarEntry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(entry.getKey());
                tarEntry.setSize(entry.getValue().length);
                tar.putArchiveEntry(tarEntry);
                tar.write(entry.getValue());
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
        return out.toByteArray();
    }

    private Map<String, String> buildEnvVars(StartRunRequest request) {
        Map<String, String> envVars = new HashMap<>(Map.of(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/testdb",
            "SPRING_DATASOURCE_USERNAME", "testuser",
            "SPRING_DATASOURCE_PASSWORD", "testpass"
        ));
        if ("n1-query".equals(request.getFault())) {
            envVars.put("SRELAB_FAULT_N1_QUERY_MODE", "true");
        }
        return envVars;
    }

    private void injectFault(RunContext ctx, String fault, String appUrl) {
        String appContainerId = ctx.status.getAppContainerId();
        switch (fault) {
            case "db-timeout" -> ctx.faultInjector.injectDatabaseTimeout(appContainerId, 5000);
            case "memory-starvation" -> ctx.faultInjector.injectMemoryStarvation(appContainerId, 10L * 1024 * 1024);
            case "config-corruption" -> ctx.faultInjector.injectConfigCorruption(appContainerId, "/app/application.properties");
            case "connection-pool-exhaustion" -> ctx.faultInjector.injectConnectionPoolExhaustion(
                ctx.jdbcUrl, "testuser", "testpass", 20);
            case "silent-data-corruption" -> ctx.faultInjector.injectSilentDataCorruption(
                ctx.jdbcUrl, "testuser", "testpass", "facilities", "capacity");
            case "n1-query" -> { /* applied via env var at deploy time */ }
            case "cascading-timeout" -> ctx.faultInjector.injectCascadingTimeout(appContainerId, appUrl, 5000, 50);
            default -> throw new IllegalArgumentException("Unknown fault: " + fault);
        }
    }

    private void runAgentAndFinish(RunContext ctx, Consumer<RunEvent> emit) {
        ctx.status.setPhase(RunStatus.Phase.AGENT_RUNNING);
        emit.accept(RunEvent.of(RunEvent.STAGE, "AI agent activated. Working the issue..."));
        ctx.evaluator.startEvaluation();

        boolean agentRan = false;
        Boolean agentSelfReportedResolved = null;
        String agentFinalMessage = null;
        String agentMode = null;
        List<AgentTranscript.Entry> transcriptEntries = List.of();

        try {
            boolean unguarded = "unguarded".equalsIgnoreCase(ctx.request.getAgentMode());
            AIAgent.AgentRunResult agentResult = ctx.agent.run(
                ctx.status.getAppContainerId(), ctx.status.getHealthUrl(), unguarded);
            agentRan = true;
            agentSelfReportedResolved = agentResult.resolved();
            agentFinalMessage = agentResult.finalMessage();
            agentMode = agentResult.mode();
            transcriptEntries = agentResult.transcript().getEntries();
            for (var entry : transcriptEntries) {
                ctx.evaluator.recordCommand();
                emit.accept(RunEvent.of(RunEvent.AGENT_ACTION, describeEntry(entry)));
            }
            emit.accept(RunEvent.of(RunEvent.LOG,
                "Agent finished (" + agentMode + " mode). Self-reported resolved=" + agentResult.resolved()));
        } catch (IllegalStateException e) {
            emit.accept(RunEvent.of(RunEvent.LOG, "Agent could not run: " + e.getMessage()));
        } catch (Exception e) {
            emit.accept(RunEvent.of(RunEvent.LOG, "Agent run failed: " + e.getMessage()));
        }

        ctx.status.setPhase(RunStatus.Phase.VERIFYING_HEALTH);
        emit.accept(RunEvent.of(RunEvent.STAGE, "Verifying health endpoint independently..."));
        boolean healthVerified = checkHealth(ctx.status.getHealthUrl());
        emit.accept(RunEvent.of(RunEvent.LOG, "Health check result: " + (healthVerified ? "HEALTHY" : "STILL UNHEALTHY")));

        Evaluator.EvaluationResult result = ctx.evaluator.finish(healthVerified);

        List<RunReport.TranscriptEntry> reportTranscript = transcriptEntries.stream()
            .map(e -> new RunReport.TranscriptEntry(e.command(), e.stdout(), e.stderr(), e.exitCode(), e.error()))
            .toList();

        RunReport report = new RunReport(
            result.resolved(),
            result.timeToResolveMs(),
            result.commandsExecuted(),
            result.score(),
            agentRan,
            agentSelfReportedResolved,
            agentFinalMessage,
            agentMode,
            reportTranscript
        );
        ctx.status.setReport(report);
        ctx.status.setPhase(RunStatus.Phase.COMPLETED);
        emit.accept(RunEvent.of(RunEvent.REPORT, "Score: " + result.score() + "/100, resolved=" + result.resolved()));
        emit.accept(RunEvent.of(RunEvent.DONE, "Run complete"));

        cleanup(ctx, emit);
    }

    private String describeEntry(AgentTranscript.Entry entry) {
        if (entry.error() != null) {
            return entry.command() + " -> ERROR: " + entry.error();
        }
        return entry.command() + " -> exit=" + entry.exitCode();
    }

    private boolean checkHealth(String healthUrl) {
        if (healthUrl == null) return false;
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(healthUrl).toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            if (status != 200) return false;
            String body = new String(conn.getInputStream().readAllBytes());
            return body.contains("\"UP\"") || body.contains("UP");
        } catch (Exception e) {
            return false;
        }
    }

    private void cleanup(RunContext ctx, Consumer<RunEvent> emit) {
        if (ctx.sandboxId != null) {
            emit.accept(RunEvent.of(RunEvent.LOG, "Cleaning up..."));
            try {
                ctx.byocManager.destroyApp(ctx.sandboxId);
                ctx.dbManager.destroyDatabase(ctx.sandboxId);
                ctx.sandboxManager.destroySandbox(ctx.sandboxId);
                emit.accept(RunEvent.of(RunEvent.LOG, "Cleanup complete"));
            } catch (Exception e) {
                emit.accept(RunEvent.of(RunEvent.LOG, "Cleanup encountered an error: " + e.getMessage()));
            }
        }
        // Intentionally NOT removing the run from activeRuns here: the GUI
        // needs GET /api/runs/{id} to keep returning the final report after
        // the run finishes (e.g. user re-opens the app after the SSE stream
        // closed), not 404 just because Docker resources were torn down.
        // Completed run metadata is cheap (no live containers behind it) so
        // it's left in memory for the life of the server process.
    }
}
