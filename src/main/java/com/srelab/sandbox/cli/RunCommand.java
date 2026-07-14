package com.srelab.sandbox.cli;

import com.srelab.sandbox.agent.AIAgent;
import com.srelab.sandbox.data.BYOCManager;
import com.srelab.sandbox.data.DatabaseManager;
import com.srelab.sandbox.evaluator.Evaluator;
import com.srelab.sandbox.injector.FaultInjector;
import com.srelab.sandbox.core.SandboxManager;
import com.srelab.sandbox.model.GitImportRequest;
import com.srelab.sandbox.model.SandboxConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.testcontainers.containers.Network;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;

@Command(
    name = "run",
    description = "Run a fault injection scenario"
)
public class RunCommand implements Runnable {

    @Option(names = {"-t", "--target"}, required = true, description = "Target application (e.g., byoc-app:test)")
    private String target;

    @Option(names = {"-f", "--fault"}, required = true,
        description = "Fault type (db-timeout, memory-starvation, config-corruption, " +
                       "connection-pool-exhaustion, silent-data-corruption, n1-query, cascading-timeout)")
    private String fault;

    @Option(names = {"-d", "--duration"}, defaultValue = "300", description = "Timeout in seconds (default: 300)")
    private int duration;

    @Option(names = {"-r", "--repo"}, description = "Git repository URL to import code from")
    private String repoUrl;

    @Option(names = {"-b", "--branch"}, description = "Git branch (default: main)")
    private String branch = "main";

    @Override
    public void run() {
        System.out.println("Starting SRElab AI incident simulation...");
        System.out.println("Target: " + target);
        System.out.println("Fault: " + fault);
        System.out.println("Duration: " + duration + "s\n");

        SandboxManager sandboxManager = new SandboxManager();
        DatabaseManager dbManager = new DatabaseManager();
        BYOCManager byocManager = new BYOCManager();
        FaultInjector faultInjector = new FaultInjector();
        Evaluator evaluator = new Evaluator();
        AIAgent agent = new AIAgent(sandboxManager);

        String sandboxId = null;
        String appContainerId = null;
        String healthUrl = null;

        try {
            // Step 1: Create sandbox
            System.out.println("[Step 1/7] Creating sandbox environment...");
            SandboxConfig config = new SandboxConfig();
            config.setImage("ubuntu:22.04");
            config.setMemoryLimitMB(512);
            config.setCpuQuota(50000L);
            config.setNetworkIsolated(false);

            sandboxId = sandboxManager.createSandbox(config);
            System.out.println("Sandbox created: " + sandboxId.substring(0, 8) + "...\n");

            // Step 2: Start database
            System.out.println("[Step 2/7] Starting PostgreSQL database...");
            Network network = sandboxManager.getNetwork(sandboxId);
            String jdbcUrl = dbManager.createDatabase(sandboxId, network);
            System.out.println("Database started: " + jdbcUrl + "\n");

            // Step 3: Import code (if repo provided)
            if (repoUrl != null && !repoUrl.isEmpty()) {
                System.out.println("[Step 3/7] Importing code from Git...");
                GitImportRequest gitRequest = new GitImportRequest();
                gitRequest.setRepoUrl(repoUrl);
                gitRequest.setBranch(branch);
                gitRequest.setTargetPath("/app");

                sandboxManager.importGitRepo(sandboxId, gitRequest);
                System.out.println("Code imported to /app\n");
            } else {
                System.out.println("[Step 3/7] Skipped (no repo provided)\n");
            }

            // Step 4: Deploy application
            System.out.println("[Step 4/7] Deploying application...");
            Map<String, String> envVars = buildEnvVars();

            String appUrl = byocManager.deployApp(sandboxId, target, envVars, network);
            healthUrl = byocManager.getHealthUrl(sandboxId);
            appContainerId = byocManager.getAppContainerId(sandboxId);
            System.out.println("Application deployed: " + appUrl + "\n");

            // Step 5: Inject fault
            System.out.println("[Step 5/7] Injecting fault: " + fault + "...");
            injectFault(faultInjector, appContainerId, appUrl, jdbcUrl);
            System.out.println("Fault injected.\n");

            // Step 6: Agent works the issue
            System.out.println("[Step 6/7] AI agent activated. Working the issue...");
            evaluator.startEvaluation();
            boolean healthVerified;
            try {
                AIAgent.AgentRunResult agentResult = agent.run(appContainerId, healthUrl);
                for (var entry : agentResult.transcript().getEntries()) {
                    evaluator.recordCommand();
                }
                System.out.println("Agent finished. Self-reported resolved=" + agentResult.resolved());
                System.out.println("Final message: " + agentResult.finalMessage() + "\n");
            } catch (IllegalStateException e) {
                System.out.println("Agent could not run: " + e.getMessage());
                System.out.println("(Set GEMINI_API_KEY to enable the AI agent.)\n");
            } catch (Exception e) {
                // Any other agent failure (LLM rate limit, network error, malformed
                // response, etc.) should not abort the whole benchmark run -- fall
                // through to an independent health check and still produce a score,
                // treating the agent's failure to act as part of what's being measured.
                System.out.println("Agent run failed: " + e.getMessage());
                System.out.println("(Continuing to independent health verification and scoring.)\n");
            }

            // Step 7: Verify health independently rather than trusting the agent's claim
            System.out.println("[Step 7/7] Verifying health endpoint independently...");
            healthVerified = checkHealth(healthUrl);
            System.out.println("Health check result: " + (healthVerified ? "HEALTHY" : "STILL UNHEALTHY") + "\n");

            boolean resolved = healthVerified;
            Evaluator.EvaluationResult result = evaluator.finish(resolved);

            System.out.println("=== Benchmark Report ===");
            System.out.println("Resolved:          " + result.resolved());
            System.out.println("Time to resolve:   " + result.timeToResolveMs() + "ms");
            System.out.println("Commands executed:  " + result.commandsExecuted());
            System.out.println("Score:              " + result.score() + "/100");
            System.out.println("========================\n");

        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (sandboxId != null) {
                System.out.println("\nCleaning up...");
                byocManager.destroyApp(sandboxId);
                dbManager.destroyDatabase(sandboxId);
                sandboxManager.destroySandbox(sandboxId);
                System.out.println("Cleanup complete");
            }
        }
    }

    private Map<String, String> buildEnvVars() {
        Map<String, String> envVars = new java.util.HashMap<>(Map.of(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/testdb",
            "SPRING_DATASOURCE_USERNAME", "testuser",
            "SPRING_DATASOURCE_PASSWORD", "testpass"
        ));
        if ("n1-query".equals(fault)) {
            envVars.put("SRELAB_FAULT_N1_QUERY_MODE", "true");
        }
        return envVars;
    }

    /**
     * Dispatches to the appropriate FaultInjector method based on the -f flag.
     * appContainerId must be the real Docker container id of the deployed
     * BYOC app (not the internal sandboxId), since that's the container
     * whose network/process/filesystem the fault actually needs to touch.
     */
    private void injectFault(FaultInjector injector, String appContainerId, String appUrl, String jdbcUrl) {
        switch (fault) {
            case "db-timeout" -> injector.injectDatabaseTimeout(appContainerId, 5000);
            case "memory-starvation" -> injector.injectMemoryStarvation(appContainerId, 10L * 1024 * 1024);
            case "config-corruption" -> injector.injectConfigCorruption(appContainerId, "/app/application.properties");
            case "connection-pool-exhaustion" -> injector.injectConnectionPoolExhaustion(
                jdbcUrl, "testuser", "testpass", 20);
            case "silent-data-corruption" -> injector.injectSilentDataCorruption(
                jdbcUrl, "testuser", "testpass", "facilities", "capacity");
            case "n1-query" -> System.out.println("(n1-query fault applied via env var at deploy time)");
            case "cascading-timeout" -> injector.injectCascadingTimeout(appContainerId, appUrl, 5000, 50);
            default -> throw new IllegalArgumentException("Unknown fault: " + fault);
        }
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
}
