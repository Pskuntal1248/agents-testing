package com.srelab.sandbox.agent;

import com.srelab.sandbox.core.SandboxManager;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Real AI agent backed by LangChain4j. Runs a ReAct-style loop: the LLM is
 * given the initial observation (logs + health status), decides which tool
 * to call (SandboxTools -> SandboxManager.executeCommand), observes the
 * result via the tool's return value, and continues until it reports
 * RESOLVED/UNRESOLVED or the turn budget is exhausted.
 *
 * Provider is swappable and auto-detected from which API key is present:
 *   - GROQ_API_KEY   -> Groq (OpenAI-compatible endpoint; generous free-tier
 *                        rate limits, which is why it's preferred when set)
 *   - GEMINI_API_KEY -> Google Gemini
 * An explicit SRELAB_AGENT_PROVIDER (groq|gemini) overrides auto-detection.
 *
 * Mode is controlled by SRELAB_AGENT_MODE (guarded|unguarded, default
 * guarded) -- see SreAgentService for what each mode actually changes.
 *
 * NOTE: this class is instantiated directly (new AIAgent(...)) by the CLI
 * entry point (RunCommand), which does not boot a Spring ApplicationContext.
 * Configuration is therefore read directly from environment variables /
 * system properties rather than via @Value, which would silently no-op
 * outside of a Spring-managed bean.
 */
public class AIAgent {

    private static final String GROQ_BASE_URL = "https://api.groq.com/openai/v1";
    private static final String DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile";
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.5-flash";

    private final String provider;
    private final String apiKey;
    private final String modelName;
    private final int maxTurns;
    private final boolean defaultUnguarded;
    private final SandboxManager sandboxManager;

    public AIAgent(SandboxManager sandboxManager) {
        this.sandboxManager = sandboxManager;

        String groqKey = firstNonBlank(System.getenv("GROQ_API_KEY"), System.getProperty("srelab.agent.groq-api-key"));
        String geminiKey = firstNonBlank(System.getenv("GEMINI_API_KEY"), System.getProperty("srelab.agent.gemini-api-key"));

        String explicitProvider = firstNonBlank(
            System.getenv("SRELAB_AGENT_PROVIDER"),
            System.getProperty("srelab.agent.provider"));

        // Resolve provider: explicit override wins; otherwise prefer Groq if
        // its key is present (better free-tier limits), then fall back to Gemini.
        if ("groq".equalsIgnoreCase(explicitProvider)) {
            this.provider = "groq";
            this.apiKey = groqKey;
        } else if ("gemini".equalsIgnoreCase(explicitProvider)) {
            this.provider = "gemini";
            this.apiKey = geminiKey;
        } else if (groqKey != null) {
            this.provider = "groq";
            this.apiKey = groqKey;
        } else {
            this.provider = "gemini";
            this.apiKey = geminiKey;
        }

        String defaultModel = "groq".equals(this.provider) ? DEFAULT_GROQ_MODEL : DEFAULT_GEMINI_MODEL;
        this.modelName = firstNonBlank(
            System.getenv("SRELAB_AGENT_MODEL"),
            System.getProperty("srelab.agent.model-name"),
            defaultModel);

        String maxTurnsRaw = firstNonBlank(
            System.getenv("SRELAB_AGENT_MAX_TURNS"),
            System.getProperty("srelab.agent.max-turns"),
            "15");
        this.maxTurns = Integer.parseInt(maxTurnsRaw);

        String modeRaw = firstNonBlank(
            System.getenv("SRELAB_AGENT_MODE"),
            System.getProperty("srelab.agent.mode"),
            "guarded");
        this.defaultUnguarded = "unguarded".equalsIgnoreCase(modeRaw);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private ChatModel buildChatModel() {
        if ("groq".equals(provider)) {
            return OpenAiChatModel.builder()
                .baseUrl(GROQ_BASE_URL)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .build();
        }
        return GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.2)
            .build();
    }

    /**
     * Runs the agent against a given target container until it self-reports
     * resolution, gives up, or exhausts the turn budget. targetContainerId
     * must be the real Docker container id of the deployed BYOC app (not an
     * internal sandboxId), since that's the container the agent needs to
     * inspect/act on. Returns the final transcript plus the agent's last
     * message. Uses the default mode (SRELAB_AGENT_MODE env var, or
     * "guarded" if unset).
     */
    public AgentRunResult run(String targetContainerId, String healthUrl) {
        return run(targetContainerId, healthUrl, defaultUnguarded);
    }

    /**
     * Same as run(String, String), but with an explicit per-call mode
     * override -- lets a caller (e.g. RunService, driven by a per-request
     * StartRunRequest.agentMode) choose guarded/unguarded independently of
     * the server-wide default, so both modes can be run and compared
     * against the same fault.
     */
    public AgentRunResult run(String targetContainerId, String healthUrl, boolean unguarded) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "No LLM API key configured. Set GROQ_API_KEY (preferred) or GEMINI_API_KEY env var.");
        }

        AgentTranscript transcript = new AgentTranscript();
        SandboxTools tools = new SandboxTools(sandboxManager, targetContainerId, transcript);

        ChatModel chatModel = buildChatModel();

        SreAgentService agentService = AiServices.builder(SreAgentService.class)
            .chatModel(chatModel)
            .tools(tools)
            .build();

        String observation = "Health check URL: " + healthUrl
            + "\nThe application appears unhealthy. Investigate and fix the issue.";

        String lastMessage = "";
        boolean resolved = false;

        for (int turn = 0; turn < maxTurns; turn++) {
            lastMessage = unguarded ? agentService.actUnguarded(observation) : agentService.actGuarded(observation);

            // Check UNRESOLVED first: "UNRESOLVED:" contains "RESOLVED:" as a
            // substring, so checking for "RESOLVED:" first would misclassify
            // every UNRESOLVED message as resolved=true. This previously
            // caused the agent's self-report to show "Resolved" even when
            // its own message text said "UNRESOLVED: ...".
            if (lastMessage.contains("UNRESOLVED:")) {
                resolved = false;
                break;
            }
            if (lastMessage.contains("RESOLVED:")) {
                // Don't just trust the claim: the system prompt tells the
                // agent to verify with a real health check before declaring
                // victory, but LLMs have been observed doing this anyway --
                // e.g. every command in the transcript returning a failure/
                // unknown exit code while the agent still asserts "RESOLVED".
                // Downgrade an unsupported claim to unresolved rather than
                // letting an assertion with no evidence drive the report.
                if (isClaimSupportedByEvidence(transcript)) {
                    resolved = true;
                } else {
                    resolved = false;
                    lastMessage = "UNRESOLVED: Agent claimed resolution (\"" + lastMessage
                        + "\") but its most recent tool call did not provide evidence of a "
                        + "healthy endpoint (no successful health check as the last action). "
                        + "Downgraded automatically -- an unsupported success claim is treated "
                        + "as a failure to resolve.";
                }
                break;
            }

            // Feed the agent's own last message back as the next observation,
            // continuing the ReAct loop until it self-terminates or the budget runs out.
            observation = "Continue investigating. Your previous step: " + lastMessage;
        }

        return new AgentRunResult(resolved, lastMessage, transcript, unguarded ? "unguarded" : "guarded");
    }

    /**
     * Checks whether the agent's most recent tool call actually demonstrates
     * a healthy endpoint, rather than trusting a bare "RESOLVED:" claim.
     * Looks at the last transcript entry: it must have succeeded (exit code
     * 0, no transport error) and its output must contain positive evidence
     * of health (HTTP 200 and/or an "UP" status string) -- matching what
     * SandboxTools.checkHealthEndpoint's curl command would produce on
     * success. A command that timed out, errored, or returned a non-zero/
     * unknown exit code is never sufficient evidence, regardless of what
     * the agent's text claims.
     */
    // Package-private (not private) so RunnerEvidenceTest can exercise this
    // logic directly without needing a live LLM call.
    boolean isClaimSupportedByEvidence(AgentTranscript transcript) {
        var entries = transcript.getEntries();
        if (entries.isEmpty()) {
            return false;
        }
        AgentTranscript.Entry last = entries.get(entries.size() - 1);

        if (last.error() != null) {
            return false;
        }
        if (last.exitCode() == null || last.exitCode() != 0) {
            return false;
        }
        String stdout = last.stdout() != null ? last.stdout() : "";
        boolean looksLikeHealthCheck = last.command().contains("curl") || last.command().contains("health");
        boolean showsHealthy = stdout.contains("http_status=200") || stdout.contains("\"UP\"") || stdout.contains("UP\"");

        return looksLikeHealthCheck && showsHealthy;
    }

    public record AgentRunResult(boolean resolved, String finalMessage, AgentTranscript transcript, String mode) {}
}
