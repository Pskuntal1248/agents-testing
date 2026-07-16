package com.srelab.sandbox.model;

/**
 * Snapshot of a run's current state, returned by GET /api/runs/{id}.
 * report is null until the run reaches a terminal status.
 */
public class RunStatus {

    public enum Phase {
        CREATING_SANDBOX,
        STARTING_DATABASE,
        IMPORTING_CODE,
        DEPLOYING_APP,
        FAULT_INJECTED,
        AWAITING_AGENT_TRIGGER,
        AGENT_RUNNING,
        VERIFYING_HEALTH,
        COMPLETED,
        FAILED
    }

    private final String runId;
    private volatile Phase phase;
    private volatile String appContainerId;
    private volatile String healthUrl;
    private volatile String appUrl;
    private volatile String errorMessage;
    private volatile RunReport report;

    public RunStatus(String runId) {
        this.runId = runId;
        this.phase = Phase.CREATING_SANDBOX;
    }

    public String getRunId() { return runId; }
    public Phase getPhase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }
    public String getAppContainerId() { return appContainerId; }
    public void setAppContainerId(String appContainerId) { this.appContainerId = appContainerId; }
    public String getHealthUrl() { return healthUrl; }
    public void setHealthUrl(String healthUrl) { this.healthUrl = healthUrl; }
    public String getAppUrl() { return appUrl; }
    public void setAppUrl(String appUrl) { this.appUrl = appUrl; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public RunReport getReport() { return report; }
    public void setReport(RunReport report) { this.report = report; }
}
