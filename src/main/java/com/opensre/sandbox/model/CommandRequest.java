package com.opensre.sandbox.model;

public class CommandRequest {
    private String sandboxId;
    private String command;
    private int timeoutSeconds = 30;

    public String getSandboxId() { return sandboxId; }
    public void setSandboxId(String sandboxId) { this.sandboxId = sandboxId; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
