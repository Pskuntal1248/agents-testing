package com.opensre.sandbox.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.Network;

import com.opensre.sandbox.core.SandboxManager;
import com.opensre.sandbox.data.BYOCManager;
import com.opensre.sandbox.data.DatabaseManager;
import com.opensre.sandbox.model.SandboxConfig;

@RestController
@RequestMapping("/api/environment")
public class EnvironmentController {

    private final SandboxManager sandboxManager;
    private final DatabaseManager databaseManager;
    private final BYOCManager byocManager;

    public EnvironmentController(SandboxManager sandboxManager, 
                                DatabaseManager databaseManager,
                                BYOCManager byocManager) {
        this.sandboxManager = sandboxManager;
        this.databaseManager = databaseManager;
        this.byocManager = byocManager;
    }

    @PostMapping("/deploy")
    public ResponseEntity<Map<String, String>> deployFullEnvironment(
            @RequestParam(defaultValue = "spring-petclinic/spring-petclinic") String appImage) {
        
        SandboxConfig config = new SandboxConfig();
        config.setNetworkIsolated(false);
        String sandboxId = sandboxManager.createSandbox(config);
        
        Network network = sandboxManager.getNetwork(sandboxId);
        
        String jdbcUrl = databaseManager.createDatabase(sandboxId, network);
        
        Map<String, String> envVars = new HashMap<>();
        envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/testdb");
        envVars.put("SPRING_DATASOURCE_USERNAME", "testuser");
        envVars.put("SPRING_DATASOURCE_PASSWORD", "testpass");
        
        String appUrl = byocManager.deployApp(sandboxId, appImage, envVars, network);
        String healthUrl = byocManager.getHealthUrl(sandboxId);
        
        Map<String, String> response = new HashMap<>();
        response.put("sandboxId", sandboxId);
        response.put("appUrl", appUrl);
        response.put("healthUrl", healthUrl);
        response.put("jdbcUrl", jdbcUrl);
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{sandboxId}")
    public ResponseEntity<Void> teardownEnvironment(@PathVariable String sandboxId) {
        byocManager.destroyApp(sandboxId);
        databaseManager.destroyDatabase(sandboxId);
        sandboxManager.destroySandbox(sandboxId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sandboxId}/health")
    public ResponseEntity<Map<String, String>> checkHealth(@PathVariable String sandboxId) {
        String healthUrl = byocManager.getHealthUrl(sandboxId);
        return ResponseEntity.ok(Map.of("healthUrl", healthUrl != null ? healthUrl : "not found"));
    }
}


