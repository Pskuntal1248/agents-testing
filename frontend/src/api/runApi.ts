import type { RunEvent, RunStatus, StartRunJsonRequest } from "../types/run";

// Backend base URL. Kept as a plain constant (not env-driven yet) since the
// Spring Boot service always runs on localhost:8080 in this project's setup;
// revisit if/when this is packaged in Tauri and needs to be configurable.
const API_BASE = "http://localhost:8080/api/runs";

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function handleJsonResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new ApiError(text || `Request failed with status ${res.status}`, res.status);
  }
  return res.json() as Promise<T>;
}

/**
 * Starts a run from a Git repo URL or a single pasted file (JSON body path).
 * Mirrors RunController.startRun -- always starts with autoStartAgent forced
 * false server-side, so the caller must separately call triggerAgent.
 */
export async function startRunFromJson(request: StartRunJsonRequest): Promise<{ runId: string }> {
  const res = await fetch(API_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  return handleJsonResponse(res);
}

/**
 * Starts a run from an uploaded zip archive (multipart path). Mirrors
 * RunController.startRunFromZip.
 */
export async function startRunFromZip(
  file: File,
  target: string,
  fault: string,
  durationSeconds: number
): Promise<{ runId: string }> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("target", target);
  formData.append("fault", fault);
  formData.append("durationSeconds", String(durationSeconds));

  const res = await fetch(`${API_BASE}/upload`, {
    method: "POST",
    body: formData,
  });
  return handleJsonResponse(res);
}

/**
 * Explicitly triggers the AI agent for a run parked at
 * AWAITING_AGENT_TRIGGER. This is the GUI's "Start Agent" button.
 */
export async function triggerAgent(runId: string): Promise<{ status: string; runId: string }> {
  const res = await fetch(`${API_BASE}/${runId}/agent/start`, { method: "POST" });
  return handleJsonResponse(res);
}

export async function getRunStatus(runId: string): Promise<RunStatus> {
  const res = await fetch(`${API_BASE}/${runId}`);
  return handleJsonResponse(res);
}

export async function listRuns(): Promise<string[]> {
  const res = await fetch(API_BASE);
  return handleJsonResponse(res);
}

/**
 * Subscribes to the live SSE event stream for a run. Returns an unsubscribe
 * function. onEvent fires for every RunEvent (stage/log/agent-action/error/
 * report/done); onError fires if the stream itself breaks (not the same as
 * a "error"-typed RunEvent, which is a normal application-level event).
 */
export function subscribeToRunEvents(
  runId: string,
  onEvent: (event: RunEvent) => void,
  onError?: (err: Event) => void
): () => void {
  const source = new EventSource(`${API_BASE}/${runId}/events`);

  source.onmessage = (message) => {
    try {
      const parsed = JSON.parse(message.data) as RunEvent;
      onEvent(parsed);
    } catch {
      // Malformed event payload -- ignore rather than crash the stream handler.
    }
  };

  source.onerror = (err) => {
    onError?.(err);
  };

  return () => source.close();
}
