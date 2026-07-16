import { useEffect, useRef } from "react";
import type { RunEvent } from "../types/run";

interface EventLogPanelProps {
  events: RunEvent[];
  connected: boolean;
}

function formatTimestamp(ts: number): string {
  const date = new Date(ts);
  return date.toLocaleTimeString(undefined, { hour12: false });
}

const EVENT_TYPE_CLASS: Record<string, string> = {
  error: "text-[#d98c8c]",
  report: "text-mist",
  done: "text-mist",
};

const EVENT_MESSAGE_CLASS: Record<string, string> = {
  error: "text-[#e8b4b4]",
};

export function EventLogPanel({ events, connected }: EventLogPanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) {
      el.scrollTop = el.scrollHeight;
    }
  }, [events]);

  return (
    <section className="rounded bg-panel border border-border p-4 flex flex-col min-h-0">
      <div className="flex items-center justify-between">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-navy mb-3">Live Log</h2>
        <span
          className={`text-[11px] uppercase tracking-wide border rounded px-2 py-0.5 mb-3 ${
            connected ? "text-navy border-slate" : "text-muted border-border"
          }`}
        >
          {connected ? "connected" : "idle"}
        </span>
      </div>
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto bg-black rounded p-3 font-mono text-xs min-h-[280px] max-h-[480px]"
      >
        {events.length === 0 && <p className="text-[#6f7d88] m-0">No events yet. Start a run to see live output.</p>}
        {events.map((event, i) => (
          <div key={i} className="flex gap-2 text-[#d7dee4] py-0.5 whitespace-pre-wrap break-words">
            <span className="text-[#6f7d88] shrink-0">{formatTimestamp(event.timestamp)}</span>
            <span className={`shrink-0 uppercase text-[11px] min-w-[84px] ${EVENT_TYPE_CLASS[event.type] ?? "text-sky"}`}>
              {event.type}
            </span>
            <span className={`flex-1 ${EVENT_MESSAGE_CLASS[event.type] ?? ""}`}>{event.message}</span>
          </div>
        ))}
      </div>
    </section>
  );
}
