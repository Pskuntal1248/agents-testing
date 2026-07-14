package com.srelab.sandbox.agent;

import com.srelab.sandbox.core.SandboxManager;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Real AI agent backed by LangChain4j + Google Gemini. Replaces the previous
 * stub that returned hardcoded strings. Runs a ReAct-style loop: the LLM is
 * given the initial observation (logs + health status), decides which tool
 * to call (SandboxTools -> SandboxManager.executeCommand), observes the
 * result via the tool's return value, and continues until it reports
 * RESOLVED/UNRESOLVED or the turn budget is exhausted.
 *
 * NOTE: this class is instantiated directly (new AIAgent(...)) by the CLI
 * entry point (RunCommand), which does not boot a Spring ApplicationContext.
 * Configuration is therefore read directly from environment variables /
 * system properties rather than via @Value, which would silently no-op
 * outside of a Spring-managed bean.
 */
public class AIAgent {

    private final String apiKey;
    private final String modelName;
    private final int maxTurns;
    private final SandboxManager sandboxManager;

    public AIAgent(SandboxManager sandboxManager) {
        this.sandboxManager = sandboxManager;
        this.apiKey = firstNonBlank(
            System.getenv("GEMINI_API_KEY"),
            System.getProperty("srelab.agent.gemini-api-key"));
        this.modelName = firstNonBlank(
            System.getenv("SRELAB_AGENT_MODEL"),
            System.getProperty("srelab.agent.model-name"),
            "gemini-2.5-flash");
        String maxTurnsRaw = firstNonBlank(
            System.getenv("SRELAB_AGENT_MAX_TURNS"),
            System.getProperty("srelab.agent.max-turns"),
            "15");
        this.maxTurns = Integer.parseInt(maxTurnsRaw);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Runs the agent against a given target container until it self-reports
     * resolution, gives up, or exhausts the turn budget. targetContainerId
     * must be the real Docker container id of the deployed BYOC app (not an
     * internal sandboxId), since that's the container the agent needs to
     * inspect/act on. Returns the final transcript plus the agent's last
     * message.
     */
    public AgentRunResult run(String targetContainerId, String healthUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "No LLM API key configured. Set GEMINI_API_KEY env var or srelab.agent.gemini-api-key property.");
        }

        AgentTranscript transcript = new AgentTranscript();
        SandboxTools tools = new SandboxTools(sandboxManager, targetContainerId, transcript);

        ChatModel chatModel = GoogleAiGeminiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(0.2)
            .build();

        SreAgentService agentService = AiServices.builder(SreAgentService.class)
            .chatModel(chatModel)
            .tools(tools)
            .build();

        String observation = "Health check URL: " + healthUrl
            + "\nThe application appears unhealthy. Investigate and fix the issue.";

        String lastMessage = "";
        boolean resolved = false;

        for (int turn = 0; turn < maxTurns; turn++) {
            lastMessage = agentService.act(observation);

            if (lastMessage.contains("RESOLVED:")) {
                resolved = true;
                break;
            }
            if (lastMessage.contains("UNRESOLVED:")) {
                break;
            }

            // Feed the agent's own last message back as the next observation,
            // continuing the ReAct loop until it self-terminates or the budget runs out.
            observation = "Continue investigating. Your previous step: " + lastMessage;
        }

        return new AgentRunResult(resolved, lastMessage, transcript);
    }

    public record AgentRunResult(boolean resolved, String finalMessage, AgentTranscript transcript) {}
}
