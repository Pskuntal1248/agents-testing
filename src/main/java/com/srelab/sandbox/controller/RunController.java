package com.srelab.sandbox.controller;

import com.srelab.sandbox.model.InvalidRunRequestException;
import com.srelab.sandbox.model.RunStatus;
import com.srelab.sandbox.model.StartRunRequest;
import com.srelab.sandbox.service.RunEventBus;
import com.srelab.sandbox.service.RunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST/SSE entry point for the GUI. Every run this controller starts goes
 * through RunService (the same orchestration the CLI uses), with
 * autoStartAgent defaulted to false here -- the agent only starts when the
 * GUI explicitly calls POST /api/runs/{id}/agent/start, per the requirement
 * that the agent must be triggered by the user, not run automatically.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService runService;
    private final RunEventBus eventBus;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public RunController(RunService runService, RunEventBus eventBus) {
        this.runService = runService;
        this.eventBus = eventBus;
    }

    /**
     * Starts a run from a JSON body: either a Git repo URL or a single
     * pasted file. Returns immediately with the generated runId; use the
     * SSE endpoint to observe progress.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> startRun(@RequestBody StartRunRequest request) {
        request.setAutoStartAgent(false);
        try {
            request.validate();
        } catch (InvalidRunRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        String runId = UUID.randomUUID().toString();
        executor.submit(() -> runService.startRun(runId, request, event -> eventBus.publish(runId, event)));
        return ResponseEntity.ok(Map.of("runId", runId));
    }

    /**
     * Starts a run from an uploaded zip archive (multipart), plus the same
     * target/fault/duration fields as the JSON variant. Kept as a separate
     * endpoint since multipart file upload and a JSON body don't mix in one
     * request.
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> startRunFromZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam String target,
            @RequestParam String fault,
            @RequestParam(defaultValue = "300") int durationSeconds,
            @RequestParam(defaultValue = "guarded") String agentMode) throws IOException {

        StartRunRequest request = new StartRunRequest();
        request.setTarget(target);
        request.setFault(fault);
        request.setDurationSeconds(durationSeconds);
        request.setZipBytes(file.getBytes());
        request.setAutoStartAgent(false);
        request.setAgentMode(agentMode);

        try {
            request.validate();
        } catch (InvalidRunRequestException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        String runId = UUID.randomUUID().toString();
        executor.submit(() -> runService.startRun(runId, request, event -> eventBus.publish(runId, event)));
        return ResponseEntity.ok(Map.of("runId", runId));
    }

    /**
     * Explicitly starts the AI agent for a run that is currently parked at
     * AWAITING_AGENT_TRIGGER. This is the GUI's "Start Agent" button.
     */
    @PostMapping("/{runId}/agent/start")
    public ResponseEntity<Map<String, String>> triggerAgent(@PathVariable String runId) {
        executor.submit(() -> runService.triggerAgent(runId, event -> eventBus.publish(runId, event)));
        return ResponseEntity.ok(Map.of("status", "agent-triggered", "runId", runId));
    }

    /**
     * Live event stream (Server-Sent Events) for a run: stage changes, log
     * lines, agent tool calls, and the final report. Late subscribers still
     * receive everything emitted so far via RunEventBus's replay buffer.
     */
    @GetMapping(value = "/{runId}/events", produces = "text/event-stream")
    public SseEmitter streamEvents(@PathVariable String runId) {
        return eventBus.subscribe(runId);
    }

    @GetMapping("/{runId}")
    public ResponseEntity<RunStatus> getStatus(@PathVariable String runId) {
        RunStatus status = runService.getStatus(runId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping
    public ResponseEntity<List<String>> listRuns() {
        return ResponseEntity.ok(runService.listRunIds());
    }
}
