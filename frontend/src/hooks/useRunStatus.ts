import { useCallback, useEffect, useState } from "react";
import type { RunStatus } from "../types/run";
import { getRunStatus } from "../api/runApi";

interface UseRunStatusResult {
  status: RunStatus | null;
  refresh: () => Promise<void>;
}

/**
 * Fetches and holds the current RunStatus for a run. Callers trigger
 * refresh() when an SSE "done"/"report" event arrives, rather than polling
 * continuously -- the SSE stream is already the source of truth for when
 * something changed.
 */
export function useRunStatus(runId: string | null): UseRunStatusResult {
  const [status, setStatus] = useState<RunStatus | null>(null);

  const refresh = useCallback(async () => {
    if (!runId) {
      setStatus(null);
      return;
    }
    try {
      const latest = await getRunStatus(runId);
      setStatus(latest);
    } catch {
      // Run may not exist yet (e.g. requested immediately after start) or
      // the server may be briefly unreachable -- leave the previous status
      // in place rather than clearing it on a transient failure.
    }
  }, [runId]);

  useEffect(() => {
    setStatus(null);
    if (runId) {
      void refresh();
    }
  }, [runId, refresh]);

  return { status, refresh };
}
