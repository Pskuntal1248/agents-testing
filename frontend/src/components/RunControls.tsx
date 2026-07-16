import type { RunPhase } from "../types/run";

interface RunControlsProps {
  phase: RunPhase | null;
  onStartRun: () => void;
  onStartAgent: () => void;
  startRunDisabled: boolean;
  startingRun: boolean;
  triggeringAgent: boolean;
}

const PHASE_LABELS: Record<RunPhase, string> = {
  CREATING_SANDBOX: "Creating sandbox...",
  STARTING_DATABASE: "Starting database...",
  IMPORTING_CODE: "Importing code...",
  DEPLOYING_APP: "Deploying application...",
  FAULT_INJECTED: "Fault injected",
  AWAITING_AGENT_TRIGGER: "Awaiting agent trigger",
  AGENT_RUNNING: "Agent running",
  VERIFYING_HEALTH: "Verifying health...",
  COMPLETED: "Completed",
  FAILED: "Failed",
};

const PHASE_DOT_CLASS: Record<RunPhase, string> = {
  CREATING_SANDBOX: "bg-slate",
  STARTING_DATABASE: "bg-slate",
  IMPORTING_CODE: "bg-slate",
  DEPLOYING_APP: "bg-slate",
  FAULT_INJECTED: "bg-slate",
  AWAITING_AGENT_TRIGGER: "bg-sky",
  AGENT_RUNNING: "bg-sky",
  VERIFYING_HEALTH: "bg-slate",
  COMPLETED: "bg-navy",
  FAILED: "bg-danger",
};

export function RunControls({
  phase,
  onStartRun,
  onStartAgent,
  startRunDisabled,
  startingRun,
  triggeringAgent,
}: RunControlsProps) {
  const canStartAgent = phase === "AWAITING_AGENT_TRIGGER";
  const isRunInProgress = phase !== null && phase !== "COMPLETED" && phase !== "FAILED";

  return (
    <section className="rounded bg-panel border border-border p-4">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-navy mb-3">Run</h2>

      <div className="flex gap-3">
        <button
          type="button"
          onClick={onStartRun}
          disabled={startRunDisabled || (isRunInProgress && phase !== null)}
          className="flex-1 rounded border border-navy bg-navy text-white text-[13px] px-4 py-2 transition-colors hover:bg-slate hover:border-slate disabled:opacity-40 disabled:cursor-not-allowed disabled:bg-white disabled:text-muted disabled:border-border"
        >
          {startingRun ? "Starting..." : "Start Sandbox"}
        </button>

        <button
          type="button"
          onClick={onStartAgent}
          disabled={!canStartAgent || triggeringAgent}
          className="flex-1 rounded border border-navy bg-navy text-white text-[13px] px-4 py-2 transition-colors hover:bg-slate hover:border-slate disabled:opacity-40 disabled:cursor-not-allowed disabled:bg-white disabled:text-muted disabled:border-border"
        >
          {triggeringAgent ? "Starting Agent..." : "Start Agent"}
        </button>
      </div>

      {phase && (
        <div className="flex items-center gap-2 mt-4 pt-3 border-t border-border">
          <span className={`w-2 h-2 rounded-full shrink-0 ${PHASE_DOT_CLASS[phase]}`} />
          <span className="text-[13px]">{PHASE_LABELS[phase]}</span>
        </div>
      )}
    </section>
  );
}
