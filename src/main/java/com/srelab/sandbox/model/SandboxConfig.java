package com.srelab.sandbox.model;

public class SandboxConfig {
    private String image = "ubuntu:22.04";
    private long memoryLimitMB = 512;
    private long cpuQuota = 50000;
    private boolean networkIsolated = true;
    private int timeoutSeconds = 300;

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public long getMemoryLimitMB() { return memoryLimitMB; }
    public void setMemoryLimitMB(long memoryLimitMB) { this.memoryLimitMB = memoryLimitMB; }
    public long getCpuQuota() { return cpuQuota; }
    public void setCpuQuota(long cpuQuota) { this.cpuQuota = cpuQuota; }
    public boolean isNetworkIsolated() { return networkIsolated; }
    public void setNetworkIsolated(boolean networkIsolated) { this.networkIsolated = networkIsolated; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
