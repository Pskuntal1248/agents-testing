package com.srelab.sandbox.model;

public class CommandResponse {
    private int exitCode;
    private String stdout;
    private String stderr;
    private long executionTimeMs;

    public CommandResponse(int exitCode, String stdout, String stderr, long executionTimeMs) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.executionTimeMs = executionTimeMs;
    }

    public int getExitCode() { return exitCode; }
    public String getStdout() { return stdout; }
    public String getStderr() { return stderr; }
    public long getExecutionTimeMs() { return executionTimeMs; }
}
