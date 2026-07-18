package com.srelab.sandbox.model;

/**
 * Request body for starting a run via the REST API. Mirrors RunCommand's CLI
 * options (-t/-f/-d/-r/-b) plus the additional code-import methods the GUI
 * supports (zip upload is a separate multipart endpoint; this covers the
 * JSON-body cases: git URL and single-file paste).
 */
public class StartRunRequest {

    private String target;
    private String fault;
    private int durationSeconds = 300;

    private String repoUrl;
    private String branch = "main";

    // Single-file paste import: written to targetPath inside the sandbox
    // before deployment, for quick single-class/snippet testing rather than
    // a full project checkout.
    private String pastedFileName;
    private String pastedFileContent;

    // Zip upload import: base64-encoded zip bytes, extracted into /app before
    // deployment. Populated by the multipart upload endpoint before the
    // request is handed to RunService; kept as a field here (rather than a
    // separate method signature) so RunService has a single import path to
    // branch on (git / pasted file / zip), matching the git/paste pattern.
    private byte[] zipBytes;

    // If true, the agent is NOT started automatically after fault injection;
    // the GUI must call the explicit agent-trigger endpoint. CLI usage keeps
    // the previous automatic behavior for backward compatibility.
    private boolean autoStartAgent = true;

    // "guarded" (default) includes explicit safety rules in the agent's
    // system prompt (don't kill PID 1, don't drop tables, verify before
    // claiming success). "unguarded" tells the agent only where it is (an
    // isolated sandbox) and removes those behavioral rules, to measure
    // whether the model behaves destructively when not explicitly told not
    // to. See SreAgentService for the full rationale. Per-run rather than
    // server-wide so both modes can be compared against the same fault.
    private String agentMode = "guarded";

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getFault() { return fault; }
    public void setFault(String fault) { this.fault = fault; }
    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getPastedFileName() { return pastedFileName; }
    public void setPastedFileName(String pastedFileName) { this.pastedFileName = pastedFileName; }
    public String getPastedFileContent() { return pastedFileContent; }
    public void setPastedFileContent(String pastedFileContent) { this.pastedFileContent = pastedFileContent; }
    public byte[] getZipBytes() { return zipBytes; }
    public void setZipBytes(byte[] zipBytes) { this.zipBytes = zipBytes; }
    public boolean isAutoStartAgent() { return autoStartAgent; }
    public void setAutoStartAgent(boolean autoStartAgent) { this.autoStartAgent = autoStartAgent; }
    public String getAgentMode() { return agentMode; }
    public void setAgentMode(String agentMode) { this.agentMode = agentMode; }

    private static final java.util.Set<String> VALID_FAULTS = java.util.Set.of(
        "db-timeout", "memory-starvation", "config-corruption",
        "connection-pool-exhaustion", "silent-data-corruption", "n1-query", "cascading-timeout"
    );

    /**
     * Rejects the request before any Docker/sandbox work starts. In
     * particular, this closes a real gap where a run with no target image
     * and no code-import source (no repoUrl, no zipBytes, no pastedFileContent)
     * would previously be accepted and silently deploy the target image
     * completely unmodified -- defeating the entire "bring your own code"
     * premise of a run without any error or warning.
     */
    public void validate() {
        if (target == null || target.isBlank()) {
            throw new InvalidRunRequestException("target is required (e.g. \"byoc-app:test\")");
        }
        if (fault == null || fault.isBlank()) {
            throw new InvalidRunRequestException("fault is required");
        }
        if (!VALID_FAULTS.contains(fault)) {
            throw new InvalidRunRequestException(
                "Unknown fault: \"" + fault + "\". Valid values: " + VALID_FAULTS);
        }
        if (durationSeconds <= 0) {
            throw new InvalidRunRequestException("durationSeconds must be positive");
        }

        boolean hasRepo = repoUrl != null && !repoUrl.isBlank();
        boolean hasZip = zipBytes != null && zipBytes.length > 0;
        boolean hasPastedFile = pastedFileContent != null && !pastedFileContent.isBlank();

        if (!hasRepo && !hasZip && !hasPastedFile) {
            throw new InvalidRunRequestException(
                "No code source provided. Provide exactly one of: repoUrl, a zip upload, or pastedFileContent. "
                    + "A run with no code import would silently deploy the target image unmodified, "
                    + "which is never what you want when testing your own code.");
        }

        int sourceCount = (hasRepo ? 1 : 0) + (hasZip ? 1 : 0) + (hasPastedFile ? 1 : 0);
        if (sourceCount > 1) {
            throw new InvalidRunRequestException(
                "Multiple code sources provided (repoUrl / zip / pastedFileContent). Provide exactly one.");
        }

        if (agentMode != null && !agentMode.isBlank()
                && !"guarded".equalsIgnoreCase(agentMode) && !"unguarded".equalsIgnoreCase(agentMode)) {
            throw new InvalidRunRequestException(
                "Unknown agentMode: \"" + agentMode + "\". Valid values: guarded, unguarded");
        }
    }
}
