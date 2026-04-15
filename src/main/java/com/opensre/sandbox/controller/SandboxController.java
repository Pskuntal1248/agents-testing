package com.opensre.sandbox.controller;

import com.opensre.sandbox.core.SandboxManager;
import com.opensre.sandbox.model.CommandRequest;
import com.opensre.sandbox.model.CommandResponse;
import com.opensre.sandbox.model.GitImportRequest;
import com.opensre.sandbox.model.SandboxConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {

    private final SandboxManager sandboxManager;

    public SandboxController(SandboxManager sandboxManager) {
        this.sandboxManager = sandboxManager;
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createSandbox(@RequestBody(required = false) SandboxConfig config) {
        if (config == null) {
            config = new SandboxConfig();
        }
        String sandboxId = sandboxManager.createSandbox(config);
        return ResponseEntity.ok(Map.of("sandboxId", sandboxId));
    }

    @PostMapping("/execute")
    public ResponseEntity<CommandResponse> executeCommand(@RequestBody CommandRequest request) {
        CommandResponse response = sandboxManager.executeCommand(
            request.getSandboxId(),
            request.getCommand(),
            request.getTimeoutSeconds()
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{sandboxId}")
    public ResponseEntity<Void> destroySandbox(@PathVariable String sandboxId) {
        sandboxManager.destroySandbox(sandboxId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sandboxId}/exists")
    public ResponseEntity<Map<String, Boolean>> checkExists(@PathVariable String sandboxId) {
        return ResponseEntity.ok(Map.of("exists", sandboxManager.exists(sandboxId)));
    }

    @PostMapping("/{sandboxId}/import-git")
    public ResponseEntity<Map<String, String>> importGit(
            @PathVariable String sandboxId,
            @RequestBody GitImportRequest request) {
        sandboxManager.importGitRepo(sandboxId, request);
        return ResponseEntity.ok(Map.of("status", "imported", "path", request.getTargetPath()));
    }

    @PostMapping("/{sandboxId}/upload-code")
    public ResponseEntity<Map<String, String>> uploadCode(
            @PathVariable String sandboxId,
            @RequestParam MultipartFile tarball,
            @RequestParam(defaultValue = "/workspace") String targetPath) throws Exception {
        sandboxManager.uploadTarball(sandboxId, tarball.getBytes(), targetPath);
        return ResponseEntity.ok(Map.of("status", "uploaded", "path", targetPath));
    }
}
