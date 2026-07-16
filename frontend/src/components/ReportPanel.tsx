import type { RunReport } from "../types/run";

interface ReportPanelProps {
  report: RunReport | null;
}

export function ReportPanel({ report }: ReportPanelProps) {
  if (!report) {
    return (
      <section className="rounded bg-panel border border-border p-4">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-navy mb-3">Report</h2>
        <p className="text-muted m-0">No report yet. Complete a run to see the benchmark score.</p>
      </section>
    );
  }

  return (
    <section className="rounded bg-panel border border-border p-4">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-navy mb-3">Report</h2>

      <div className="flex items-baseline gap-2 mb-4">
        <div className="text-4xl font-bold text-navy leading-none">{report.score}</div>
        <div className="text-sm text-muted">/ 100</div>
      </div>

      <dl className="grid grid-cols-2 gap-x-3 gap-y-2 m-0">
        <dt className="text-[11px] uppercase tracking-wide text-muted">Resolved</dt>
        <dd className={`text-[13px] m-0 mb-2 font-semibold ${report.resolved ? "text-navy" : "text-danger"}`}>
          {report.resolved ? "Yes" : "No"}
        </dd>

        <dt className="text-[11px] uppercase tracking-wide text-muted">Time to Resolve</dt>
        <dd className="text-[13px] m-0 mb-2">{(report.timeToResolveMs / 1000).toFixed(1)}s</dd>

        <dt className="text-[11px] uppercase tracking-wide text-muted">Commands Executed</dt>
        <dd className="text-[13px] m-0 mb-2">{report.commandsExecuted}</dd>

        <dt className="text-[11px] uppercase tracking-wide text-muted">Agent Ran</dt>
        <dd className="text-[13px] m-0 mb-2">{report.agentRan ? "Yes" : "No"}</dd>

        {report.agentSelfReportedResolved !== null && (
          <>
            <dt className="text-[11px] uppercase tracking-wide text-muted">Agent Self-Report</dt>
            <dd className="text-[13px] m-0 mb-2">{report.agentSelfReportedResolved ? "Resolved" : "Unresolved"}</dd>
          </>
        )}
      </dl>

      {report.agentFinalMessage && (
        <div className="mt-4 pt-3 border-t border-border">
          <label className="text-xs uppercase tracking-wide text-muted">Agent Final Message</label>
          <p className="text-[13px] whitespace-pre-wrap mt-1 mb-0">{report.agentFinalMessage}</p>
        </div>
      )}

      {report.transcript.length > 0 && (
        <div className="mt-4 pt-3 border-t border-border">
          <label className="text-xs uppercase tracking-wide text-muted">
            Transcript ({report.transcript.length} commands)
          </label>
          <div className="max-h-[220px] overflow-y-auto mt-2">
            {report.transcript.map((entry, i) => (
              <div
                key={i}
                className="flex justify-between gap-2 py-1 border-b border-border text-xs last:border-b-0"
              >
                <code className="font-mono text-navy overflow-hidden text-ellipsis whitespace-nowrap">
                  {entry.command}
                </code>
                {entry.error ? (
                  <span className="text-danger shrink-0">{entry.error}</span>
                ) : (
                  <span className="text-muted shrink-0">exit={entry.exitCode}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}
