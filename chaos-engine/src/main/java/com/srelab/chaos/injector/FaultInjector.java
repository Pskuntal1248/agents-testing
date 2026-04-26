package com.srelab.chaos.injector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.stereotype.Service;

@Service
public class FaultInjector {
    
    private final DockerClient docker;
    
    public FaultInjector() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .build();
        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }
    
    public void injectDatabaseTimeout(String containerId, int delayMs) {
        execInContainer(containerId, "tc", "qdisc", "add", "dev", "eth0", "root", "netem", "delay", delayMs + "ms");
    }
    
    public void injectMemoryStarvation(String containerId, long limitBytes) {
        docker.updateContainerCmd(containerId)
            .withMemory(limitBytes)
            .exec();
    }
    
    public void injectConfigCorruption(String containerId, String configPath) {
        execInContainer(containerId, "sh", "-c", "echo 'corrupted=true' >> " + configPath);
    }
    
    private void execInContainer(String containerId, String... command) {
        try {
            var exec = docker.execCreateCmd(containerId)
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();
            
            docker.execStartCmd(exec.getId()).exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>()).awaitCompletion();
        } catch (Exception e) {
            throw new RuntimeException("Fault injection failed", e);
        }
    }
}
