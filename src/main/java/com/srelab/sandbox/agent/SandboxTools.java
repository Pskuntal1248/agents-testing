package com.srelab.sandbox.agent;

import com.srelab.sandbox.model.CommandResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.srelab.sandbox.core.SandboxManager;

/**
 * Tool surface exposed to the LLM agent. Every tool call is routed through
 * SandboxManager, which only ever operates inside the isolated sandbox
 * container -- the agent has no path to touch anything outside the sandbox.
 *
 * Each invocation is logged to the transcript so the Evaluator can score
 * command count / blast radius after the run.
 *
 * NOT a Spring bean: it is scoped to a single agent run and is instantiated
 * directly by AIAgent.run() for each invocation, since its constructor
 * arguments (target container id, transcript) are per-run state rather
 * than singleton dependencies.
 */
public class SandboxTools {

    private final SandboxManager sandboxManager;
    private final String targetContainerId;
    private final AgentTranscript transcript;

    public SandboxTools(SandboxManager sandboxManager, String targetContainerId, AgentTranscript transcript) {
        this.sandboxManager = sandboxManager;
        this.targetContainerId = targetContainerId;
        this.transcript = transcript;
    }

    @Tool("Execute a shell command inside the sandboxed target container. " +
          "Use this to inspect processes, restart services, edit config files, or run diagnostic commands. " +
          "Returns stdout, stderr, and exit code.")
    public String executeShellCommand(@P("The shell command to run, e.g. 'cat /app/application.properties'") String command) {
        transcript.recordCommand(command);
        try {
            CommandResponse response = sandboxManager.executeCommandByContainerId(targetContainerId, command, 30);
            transcript.recordResult(command, response);
            return "exitCode=" + response.getExitCode()
                + "\nstdout:\n" + truncate(response.getStdout())
                + "\nstderr:\n" + truncate(response.getStderr());
        } catch (Exception e) {
            String error = "Command failed: " + e.getMessage();
            transcript.recordFailure(command, error);
            return error;
        }
    }

    @Tool("Read the last N lines of application logs from the target container. " +
          "Use this first to understand what is failing before taking action.")
    public String readLogs(@P("Number of log lines to read from the tail") int lines) {
        String command = "tail -n " + Math.max(1, lines) + " /app/app.log 2>/dev/null || " +
                          "tail -n " + Math.max(1, lines) + " /proc/1/fd/1 2>/dev/null || " +
                          "echo 'no log file found at known locations'";
        return executeShellCommand(command);
    }

    @Tool("Check the health/status endpoint of the deployed application to verify whether it is currently healthy.")
    public String checkHealthEndpoint(@P("Full health check URL, e.g. http://localhost:8080/health") String healthUrl) {
        String command = "curl -s -o /dev/null -w 'http_status=%{http_code}' " + healthUrl
            + " ; echo; curl -s " + healthUrl;
        return executeShellCommand(command);
    }

    private String truncate(String text) {
        if (text == null) return "";
        int max = 4000;
        return text.length() > max ? text.substring(0, max) + "\n...(truncated)" : text;
    }
}
