package com.srelab.sandbox.agent;

import com.srelab.sandbox.core.SandboxManager;
import com.srelab.sandbox.model.CommandResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a real observed failure: an agent (via Groq's
 * llama-3.3-70b-versatile) ran several commands that all failed or timed
 * out (exit=-1), never once observed a successful health check, and still
 * declared "RESOLVED: The application is now healthy...". AIAgent.run()
 * must not trust that claim -- it should require the transcript's last
 * entry to be genuine evidence of a healthy endpoint.
 */
class AIAgentEvidenceTest {

    private final AIAgent agent = new AIAgent(new SandboxManager());

    @Test
    void unsupportedResolvedClaimIsRejected_lastCommandFailed() {
        AgentTranscript transcript = new AgentTranscript();
        // Reproduces the exact observed sequence: repeated failed restart
        // attempts, with no successful health check ever recorded.
        transcript.recordResult("curl -s http://localhost:8080/health",
            new CommandResponse(7, "", "", 5)); // curl exit 7 = connection refused
        transcript.recordFailure("java -jar /app/app.jar", "Command failed: exec timed out");

        assertFalse(agent.isClaimSupportedByEvidence(transcript),
            "a failed/errored last command must never support a RESOLVED claim");
    }

    @Test
    void unsupportedResolvedClaimIsRejected_lastCommandUnrelatedToHealth() {
        AgentTranscript transcript = new AgentTranscript();
        // Last command succeeded, but it's not a health check at all (e.g.
        // just listing processes) -- still not evidence of resolution.
        transcript.recordResult("ps aux",
            new CommandResponse(0, "PID USER ...", "", 10));

        assertFalse(agent.isClaimSupportedByEvidence(transcript),
            "a successful but unrelated last command must not support a RESOLVED claim");
    }

    @Test
    void supportedResolvedClaimIsAccepted() {
        AgentTranscript transcript = new AgentTranscript();
        transcript.recordResult(
            "curl -s -o /dev/null -w 'http_status=%{http_code}' http://localhost:8080/health ; echo; curl -s http://localhost:8080/health",
            new CommandResponse(0, "http_status=200\n{\"status\":\"UP\",\"database\":\"reachable\"}", "", 20));

        assertTrue(agent.isClaimSupportedByEvidence(transcript),
            "a successful health check showing http_status=200 and UP should support a RESOLVED claim");
    }

    @Test
    void emptyTranscriptNeverSupportsAResolvedClaim() {
        AgentTranscript transcript = new AgentTranscript();
        assertFalse(agent.isClaimSupportedByEvidence(transcript),
            "no evidence at all must never support a RESOLVED claim");
    }
}
