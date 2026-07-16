package com.srelab.sandbox.model;

/**
 * A single event emitted during a run, pushed live to SSE subscribers and
 * also printed by the CLI. Keeping this as one shared event type lets the
 * CLI and the GUI both observe the exact same orchestration (RunService)
 * instead of the GUI re-implementing run logic separately.
 */
public record RunEvent(String type, String message, long timestamp) {

    public static RunEvent of(String type, String message) {
        return new RunEvent(type, message, System.currentTimeMillis());
    }

    // Common event types emitted by RunService. Kept as plain strings (not an
    // enum) so new event types don't require a shared model change on every
    // addition -- consumers should treat unknown types as generic "info" logs.
    public static final String STAGE = "stage";
    public static final String LOG = "log";
    public static final String AGENT_ACTION = "agent-action";
    public static final String ERROR = "error";
    public static final String REPORT = "report";
    public static final String DONE = "done";
}
