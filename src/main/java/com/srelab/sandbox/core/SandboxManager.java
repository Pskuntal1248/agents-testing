package com.srelab.sandbox.core;

import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Capability;
import com.srelab.sandbox.model.CommandResponse;
import com.srelab.sandbox.model.GitImportRequest;
import com.srelab.sandbox.model.SandboxConfig;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class SandboxManager {
    
    private final Map<String, GenericContainer<?>> activeSandboxes = new ConcurrentHashMap<>();
    private final Map<String, Network> sandboxNetworks = new ConcurrentHashMap<>();

    public String createSandbox(SandboxConfig config) {
        String sandboxId = UUID.randomUUID().toString();
        
        try {
            Network network = Network.newNetwork();
            sandboxNetworks.put(sandboxId, network);
            
            GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(config.getImage()))
                .withNetwork(network)
                .withCreateContainerCmdModifier(cmd -> cmd
                    .withHostConfig(cmd.getHostConfig()
                        .withMemory(config.getMemoryLimitMB() * 1024 * 1024)
                        .withCpuQuota(config.getCpuQuota())
                        .withCapDrop(Capability.ALL)
                        .withReadonlyRootfs(false)
                        .withSecurityOpts(java.util.List.of("no-new-privileges"))
                    )
                )
                .withCommand("tail", "-f", "/dev/null");
            
            container.start();
            
            activeSandboxes.put(sandboxId, container);
            
            System.out.println("Sandbox created: " + sandboxId);
            return sandboxId;
            
        } catch (Exception e) {
            System.err.println("Failed to create sandbox: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Sandbox creation failed: " + e.getMessage(), e);
        }
    }

    public CommandResponse executeCommand(String sandboxId, String command, int timeoutSeconds) {
        GenericContainer<?> container = activeSandboxes.get(sandboxId);
        if (container == null) {
            throw new IllegalArgumentException("Sandbox not found: " + sandboxId);
        }

        long startTime = System.currentTimeMillis();
        
        try {
            ExecCreateCmdResponse execCreateCmdResponse = container.getDockerClient()
                .execCreateCmd(container.getContainerId())
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("/bin/sh", "-c", command)
                .exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            container.getDockerClient()
                .execStartCmd(execCreateCmdResponse.getId())
                .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                    @Override
                    public void onNext(com.github.dockerjava.api.model.Frame frame) {
                        try {
                            switch (frame.getStreamType()) {
                                case STDOUT -> stdout.write(frame.getPayload());
                                case STDERR -> stderr.write(frame.getPayload());
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing frame: " + e.getMessage());
                        }
                    }
                })
                .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);

            Integer exitCode = container.getDockerClient()
                .inspectExecCmd(execCreateCmdResponse.getId())
                .exec()
                .getExitCodeLong()
                .intValue();

            long executionTime = System.currentTimeMillis() - startTime;

            return new CommandResponse(
                exitCode,
                stdout.toString(),
                stderr.toString(),
                executionTime
            );

        } catch (Exception e) {
            System.err.println("Command execution failed: " + e.getMessage());
            throw new RuntimeException("Command execution failed", e);
        }
    }

    public void destroySandbox(String sandboxId) {
        GenericContainer<?> container = activeSandboxes.remove(sandboxId);
        Network network = sandboxNetworks.remove(sandboxId);
        
        if (container != null) {
            try {
                container.stop();
                System.out.println("Sandbox destroyed: " + sandboxId);
            } catch (Exception e) {
                System.err.println("Error stopping sandbox: " + e.getMessage());
            }
        }
        
        if (network != null) {
            try {
                network.close();
            } catch (Exception e) {
                System.err.println("Error closing network: " + e.getMessage());
            }
        }
    }

    public boolean exists(String sandboxId) {
        return activeSandboxes.containsKey(sandboxId);
    }

    public Network getNetwork(String sandboxId) {
        return sandboxNetworks.get(sandboxId);
    }

    public void importGitRepo(String sandboxId, GitImportRequest request) {
        GenericContainer<?> container = activeSandboxes.get(sandboxId);
        if (container == null) {
            throw new IllegalArgumentException("Sandbox not found: " + sandboxId);
        }

        try {
            String containerId = container.getContainerId();
            
           // for git leaving sometime for the network open it works like semi permeable membrane
            container.getDockerClient()
                .connectToNetworkCmd()
                .withContainerId(containerId)
                .withNetworkId("bridge")
                .exec();
            
            System.out.println("Network open for Git clone: " + sandboxId);
            executeCommand(sandboxId, "command -v git || (apt-get update && apt-get install -y git)", 60);
            String cloneCmd = "git clone " + request.getRepoUrl();
            if (request.getBranch() != null) {
                cloneCmd += " -b " + request.getBranch();
            }
            cloneCmd += " " + request.getTargetPath();
            CommandResponse response = executeCommand(sandboxId, cloneCmd, 300);
            
            if (response.getExitCode() != 0) {
                throw new RuntimeException("Git clone failed: " + response.getStderr());
            }

            System.out.println("Git clone complete: " + sandboxId);
        } catch (Exception e) {
            throw new RuntimeException("Git import failed: " + e.getMessage(), e);
        } finally {
            try {
                container.getDockerClient()
                    .disconnectFromNetworkCmd()
                    .withContainerId(container.getContainerId())
                    .withNetworkId("bridge")
                    .exec();
                    
                System.out.println("Network disabled (air-gap restored): " + sandboxId);
            } catch (Exception e) {
                System.err.println("Failed to disconnect network: " + e.getMessage());
            }
        }
    }

    public void uploadTarball(String sandboxId, byte[] tarballData, String targetPath) {
        GenericContainer<?> container = activeSandboxes.get(sandboxId);
        if (container == null) {
            throw new IllegalArgumentException("Sandbox not found: " + sandboxId);
        }

        File tempFile = null;
        try {
            tempFile = File.createTempFile("sandbox-code-", ".tar.gz");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(tarballData);
            }

            container.copyFileToContainer(
                org.testcontainers.utility.MountableFile.forHostPath(tempFile.getAbsolutePath()),
                "/tmp/code.tar.gz"
            );

            executeCommand(sandboxId, "mkdir -p " + targetPath, 10);
            CommandResponse response = executeCommand(
                sandboxId, 
                "tar -xzf /tmp/code.tar.gz -C " + targetPath, 
                60
            );

            if (response.getExitCode() != 0) {
                throw new RuntimeException("Tarball extraction failed: " + response.getStderr());
            }

            executeCommand(sandboxId, "rm /tmp/code.tar.gz", 10);
            
            System.out.println("Tarball uploaded and extracted: " + sandboxId);

        } catch (Exception e) {
            throw new RuntimeException("Tarball upload failed: " + e.getMessage(), e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
}
