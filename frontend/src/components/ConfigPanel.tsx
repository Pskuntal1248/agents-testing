import { FAULT_TYPES, AGENT_MODES } from "../types/run";
import type { FaultType, AgentMode } from "../types/run";

export interface ConfigValue {
  target: string;
  fault: FaultType;
  durationSeconds: number;
  agentMode: AgentMode;
}

interface ConfigPanelProps {
  value: ConfigValue;
  onChange: (value: ConfigValue) => void;
  disabled?: boolean;
}

export function ConfigPanel({ value, onChange, disabled }: ConfigPanelProps) {
  return (
    <section className="rounded bg-panel border border-border p-4">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-navy mb-3">Target &amp; Fault</h2>

      <div className="flex flex-col gap-1">
        <label htmlFor="target-image" className="text-xs uppercase tracking-wide text-muted">
          Target Image
        </label>
        <input
          id="target-image"
          type="text"
          placeholder="byoc-app:test"
          value={value.target}
          disabled={disabled}
          onChange={(e) => onChange({ ...value, target: e.target.value })}
          className="text-[13px] border border-border rounded px-2.5 py-1.5 focus:outline-none focus:border-sky"
        />

        <label htmlFor="fault-type" className="text-xs uppercase tracking-wide text-muted mt-3">
          Fault Type
        </label>
        <select
          id="fault-type"
          value={value.fault}
          disabled={disabled}
          onChange={(e) => onChange({ ...value, fault: e.target.value as FaultType })}
          className="text-[13px] border border-border rounded px-2.5 py-1.5 bg-white focus:outline-none focus:border-sky"
        >
          {FAULT_TYPES.map((f) => (
            <option key={f.value} value={f.value}>
              {f.label} (tier {f.tier})
            </option>
          ))}
        </select>

        <label htmlFor="duration" className="text-xs uppercase tracking-wide text-muted mt-3">
          Duration (seconds)
        </label>
        <input
          id="duration"
          type="number"
          min={10}
          value={value.durationSeconds}
          disabled={disabled}
          onChange={(e) => onChange({ ...value, durationSeconds: Number(e.target.value) })}
          className="text-[13px] border border-border rounded px-2.5 py-1.5 focus:outline-none focus:border-sky"
        />

        <label htmlFor="agent-mode" className="text-xs uppercase tracking-wide text-muted mt-3">
          Agent Mode
        </label>
        <select
          id="agent-mode"
          value={value.agentMode}
          disabled={disabled}
          onChange={(e) => onChange({ ...value, agentMode: e.target.value as AgentMode })}
          className="text-[13px] border border-border rounded px-2.5 py-1.5 bg-white focus:outline-none focus:border-sky"
        >
          {AGENT_MODES.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
      </div>
    </section>
  );
}
