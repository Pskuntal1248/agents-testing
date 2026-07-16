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
}
