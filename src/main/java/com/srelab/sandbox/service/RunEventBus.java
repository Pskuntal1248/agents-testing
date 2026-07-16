package com.srelab.sandbox.service;

import com.srelab.sandbox.model.RunEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans out RunEvents to any number of SSE subscribers per run, and buffers
 * events so a client that connects to /api/runs/{id}/events slightly after
 * the run started still receives everything emitted so far (rather than
 * only events emitted after it happened to subscribe).
 */
@Component
public class RunEventBus {

    private final Map<String, List<RunEvent>> history = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public void publish(String runId, RunEvent event) {
        history.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(event);

        List<SseEmitter> emitters = subscribers.get(runId);
        if (emitters == null) return;

        // Iterate a snapshot and remove dead emitters immediately on first
        // failure, rather than relying solely on onError/onCompletion
        // callbacks -- those fire asynchronously and left a race window
        // where a background thread (e.g. the agent's virtual thread
        // publishing an event) could call send() on an emitter the servlet
        // container had already torn down after a client disconnect,
        // triggering "AsyncContext used after onError() had returned".
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(event);
                if (RunEvent.DONE.equals(event.type())) {
                    emitter.complete();
                    emitters.remove(emitter);
                }
            } catch (Exception e) {
                emitters.remove(emitter);
                safeCompleteWithError(emitter, e);
            } catch (Error e) {
                // SseEmitter/AsyncContext can throw AssertionError-style
                // Errors (not Exceptions) when used after the container has
                // already torn it down -- must still be caught here so one
                // dead subscriber can't abort the whole publish/run.
                emitters.remove(emitter);
            }
        }
    }

    private void safeCompleteWithError(SseEmitter emitter, Exception e) {
        try {
            emitter.completeWithError(e);
        } catch (Exception ignored) {
            // already completed/torn down by the container; nothing more to do
        }
    }

    public SseEmitter subscribe(String runId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; run completion closes it
        subscribers.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Replay everything emitted so far so a late subscriber isn't missing
        // the start of the run.
        List<RunEvent> pastEvents = history.get(runId);
        if (pastEvents != null) {
            for (RunEvent event : pastEvents) {
                try {
                    emitter.send(event);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    return emitter;
                }
            }
        }

        emitter.onCompletion(() -> removeSubscriber(runId, emitter));
        emitter.onTimeout(() -> removeSubscriber(runId, emitter));
        emitter.onError(e -> removeSubscriber(runId, emitter));

        return emitter;
    }

    private void removeSubscriber(String runId, SseEmitter emitter) {
        List<SseEmitter> emitters = subscribers.get(runId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    public void clearHistory(String runId) {
        history.remove(runId);
        subscribers.remove(runId);
    }
}
