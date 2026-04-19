package com.opensre.sandbox.data;

import org.springframework.stereotype.Service;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BYOCManager {
    
    private final Map<String, GenericContainer<?>> apps = new ConcurrentHashMap<>();

    public String deployApp(String sandboxId, String image, Map<String, String> envVars, Network network) {
        GenericContainer<?> app = new GenericContainer<>(DockerImageName.parse(image))
            .withNetwork(network)
            .withNetworkAliases("app")
            .withExposedPorts(8080)
            .withEnv(envVars);
        
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

    public void destroyApp(String sandboxId) {
        GenericContainer<?> app = apps.remove(sandboxId);
        if (app != null) {
            app.stop();
        }
    }
}
