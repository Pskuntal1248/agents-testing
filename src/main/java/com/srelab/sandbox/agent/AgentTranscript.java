package com.srelab.sandbox.agent;

import com.srelab.sandbox.model.CommandResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records every tool call the agent makes during a run, so the Evaluator
 * and REPORT.md generation can show the full transcript alongside the
 * benchmark score -- an auditable trail, not just a number.
 */
public class AgentTranscript {

    public record Entry(String command, String stdout, String stderr, Integer exitCode, String error) {}

    private final List<Entry> entries = new ArrayList<>();

    public void recordCommand(String command) {
        // placeholder entry updated by recordResult/recordFailure; kept simple to avoid races
    }

    public synchronized void recordResult(String command, CommandResponse response) {
        entries.add(new Entry(command, response.getStdout(), response.getStderr(), response.getExitCode(), null));
    }

    public synchronized void recordFailure(String command, String error) {
        entries.add(new Entry(command, null, null, null, error));
    }

    public synchronized List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public synchronized int commandCount() {
        return entries.size();
    }
}
