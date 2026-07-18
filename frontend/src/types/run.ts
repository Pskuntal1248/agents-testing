// Mirrors backend model classes in com.srelab.sandbox.model so the frontend
// contract stays precise and in sync with what RunController actually returns.

export type RunEventType =
  | "stage"
  | "log"
  | "agent-action"
  | "error"
  | "report"
  | "done";

export interface RunEvent {
  type: RunEventType;
  message: string;
  timestamp: number;
}

export type RunPhase =
  | "CREATING_SANDBOX"
  | "STARTING_DATABASE"
  | "IMPORTING_CODE"
  | "DEPLOYING_APP"
  | "FAULT_INJECTED"
  | "AWAITING_AGENT_TRIGGER"
  | "AGENT_RUNNING"
  | "VERIFYING_HEALTH"
  | "COMPLETED"
  | "FAILED";

export interface TranscriptEntry {
  command: string;
  stdout: string | null;
  stderr: string | null;
  exitCode: number | null;
  error: string | null;
}

export interface RunReport {
  resolved: boolean;
  timeToResolveMs: number;
  commandsExecuted: number;
  score: number;
  agentRan: boolean;
  agentSelfReportedResolved: boolean | null;
  agentFinalMessage: string | null;
  agentMode: AgentMode | null;
  transcript: TranscriptEntry[];
}

export interface RunStatus {
  runId: string;
  phase: RunPhase;
  appContainerId: string | null;
  healthUrl: string | null;
  appUrl: string | null;
  errorMessage: string | null;
  report: RunReport | null;
}

// Fault types supported by RunService.injectFault -- kept as a const array
// (not just a union) so the Fault selection UI can render options from it
// directly instead of duplicating the list.
export const FAULT_TYPES = [
  { value: "db-timeout", label: "DB Timeout", tier: 1 },
  { value: "memory-starvation", label: "Memory Starvation", tier: 1 },
  { value: "config-corruption", label: "Config Corruption", tier: 1 },
  { value: "connection-pool-exhaustion", label: "Connection Pool Exhaustion", tier: 2 },
  { value: "silent-data-corruption", label: "Silent Data Corruption", tier: 2 },
  { value: "n1-query", label: "N+1 Query", tier: 2 },
  { value: "cascading-timeout", label: "Cascading Timeout", tier: 3 },
] as const;

export type FaultType = (typeof FAULT_TYPES)[number]["value"];

export type SourceMode = "git" | "zip" | "paste";

// Mirrors StartRunRequest.agentMode. "guarded" includes explicit safety
// rules in the agent's system prompt; "unguarded" tells the agent only
// where it is (an isolated sandbox) and removes those rules, to measure
// whether the model behaves destructively when not explicitly told not to.
export const AGENT_MODES = [
  { value: "guarded", label: "Guarded (with safety rules)" },
  { value: "unguarded", label: "Unguarded (orientation only)" },
] as const;

export type AgentMode = (typeof AGENT_MODES)[number]["value"];

export interface StartRunJsonRequest {
  target: string;
  fault: FaultType;
  durationSeconds: number;
  repoUrl?: string;
  branch?: string;
  pastedFileName?: string;
  pastedFileContent?: string;
  agentMode?: AgentMode;
}
