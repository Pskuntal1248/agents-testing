package com.srelab.sandbox.data;

import com.github.dockerjava.api.model.Capability;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BYOCManager {
    
    private final Map<String, GenericContainer<?>> apps = new ConcurrentHashMap<>();

    public String deployApp(String sandboxId, String image, Map<String, String> envVars, Network network) {
        GenericContainer<?> app = new GenericContainer<>(DockerImageName.parse(image))
            .withNetwork(network)
            .withNetworkAliases("app-" + sandboxId)
            .withExposedPorts(8080)
            .withEnv(envVars)
            .withCreateContainerCmdModifier(cmd -> cmd
                .withHostConfig(cmd.getHostConfig()
                    .withMemory(512L * 1024 * 1024)
                    .withCpuQuota(50000L)
                    .withCapDrop(Capability.ALL)
                    // NET_ADMIN is required for the db-timeout/cascading-timeout fault
                    // injectors to run `tc qdisc` against the container's network
                    // interface. This is the only capability re-added; the container
                    // otherwise remains fully hardened (no-new-privileges, all other
                    // caps dropped).
                    .withCapAdd(Capability.NET_ADMIN)
                    .withSecurityOpts(java.util.List.of("no-new-privileges"))
                    .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,size=100m"))
                )
            )
            .waitingFor(Wait.forHttp("/health").withStartupTimeout(Duration.ofSeconds(60)));
        
        app.start();
        apps.put(sandboxId, app);
        
        return "http://" + app.getHost() + ":" + app.getMappedPort(8080);
    }

    public String getHealthUrl(String sandboxId) {
        GenericContainer<?> app = apps.get(sandboxId);
        if (app != null) {
            return "http://" + app.getHost() + ":" + app.getMappedPort(8080) + "/health";
        }
        return null;
    }

    /**
     * Returns the real Docker container id for the deployed app (as opposed
     * to the internal sandboxId key), needed by FaultInjector to exec/inspect
     * against the actual running container.
     */
    public String getAppContainerId(String sandboxId) {
        GenericContainer<?> app = apps.get(sandboxId);
        return app != null ? app.getContainerId() : null;
    }

    public void destroyApp(String sandboxId) {
        GenericContainer<?> app = apps.remove(sandboxId);
        if (app != null) {
            app.stop();
        }
    }
}
