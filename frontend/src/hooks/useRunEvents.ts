import { useEffect, useRef, useState } from "react";
import type { RunEvent } from "../types/run";
import { subscribeToRunEvents } from "../api/runApi";

interface UseRunEventsResult {
  events: RunEvent[];
  connected: boolean;
}

/**
 * Subscribes to a run's SSE event stream for as long as runId is set,
 * accumulating every event received (RunEventBus already replays history
 * on subscribe, so reconnecting mid-run doesn't lose earlier events).
 */
export function useRunEvents(runId: string | null): UseRunEventsResult {
  const [events, setEvents] = useState<RunEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const unsubscribeRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    setEvents([]);
    setConnected(false);

    if (!runId) {
      return;
    }

    setConnected(true);
    const unsubscribe = subscribeToRunEvents(
      runId,
      (event) => {
        setEvents((prev) => [...prev, event]);
      },
      () => {
        setConnected(false);
      }
    );
    unsubscribeRef.current = unsubscribe;
    return () => {
      unsubscribe();
      unsubscribeRef.current = null;
    };
  }, [runId]);

  return { events, connected };
}
