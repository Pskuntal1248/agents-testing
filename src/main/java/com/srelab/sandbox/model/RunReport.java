package com.srelab.sandbox.model;

import java.util.List;

/**
 * Final benchmark report for a completed run, combining the Evaluator's
 * score with the agent's self-report and full transcript so the GUI can
 * show not just a number but the evidence behind it.
 */
public record RunReport(
    boolean resolved,
    long timeToResolveMs,
    int commandsExecuted,
    int score,
    boolean agentRan,
    Boolean agentSelfReportedResolved,
    String agentFinalMessage,
    String agentMode,
    List<TranscriptEntry> transcript
) {
    public record TranscriptEntry(String command, String stdout, String stderr, Integer exitCode, String error) {}
}
