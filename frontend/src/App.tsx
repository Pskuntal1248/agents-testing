import { useEffect, useState } from "react";
import { SourcePanel } from "./components/SourcePanel";
import type { SourceValue } from "./components/SourcePanel";
import { ConfigPanel } from "./components/ConfigPanel";
import type { ConfigValue } from "./components/ConfigPanel";
import { RunControls } from "./components/RunControls";
import { EventLogPanel } from "./components/EventLogPanel";
import { ReportPanel } from "./components/ReportPanel";
import { useRunEvents } from "./hooks/useRunEvents";
import { useRunStatus } from "./hooks/useRunStatus";
import { startRunFromJson, startRunFromZip, triggerAgent, ApiError } from "./api/runApi";
import "./App.css";

const DEFAULT_SOURCE: SourceValue = {
  mode: "git",
  repoUrl: "",
  branch: "main",
  zipFile: null,
  pastedFileName: "",
  pastedFileContent: "",
};

const DEFAULT_CONFIG: ConfigValue = {
  target: "byoc-app:test",
  fault: "db-timeout",
  durationSeconds: 60,
};

function App() {
  const [source, setSource] = useState<SourceValue>(DEFAULT_SOURCE);
  const [config, setConfig] = useState<ConfigValue>(DEFAULT_CONFIG);
  const [runId, setRunId] = useState<string | null>(null);
  const [startingRun, setStartingRun] = useState(false);
  const [triggeringAgent, setTriggeringAgent] = useState(false);
  const [errorBanner, setErrorBanner] = useState<string | null>(null);

  const { events, connected } = useRunEvents(runId);
  const { status, refresh } = useRunStatus(runId);

  // RunStatus (including the final report) is only available via the REST
  // endpoint, not embedded in the SSE payload itself -- refresh it whenever
  // a state-relevant event arrives (stage change, error, report, done).
  const lastEvent = events[events.length - 1];
  useEffect(() => {
    if (!lastEvent) return;
    if (["stage", "error", "report", "done"].includes(lastEvent.type)) {
      void refresh();
    }
  }, [lastEvent, refresh]);

  async function handleStartRun() {
    setErrorBanner(null);
    setStartingRun(true);
    try {
      let result: { runId: string };
      if (source.mode === "zip") {
        if (!source.zipFile) {
          setErrorBanner("Select a zip file before starting the run.");
          return;
        }
        result = await startRunFromZip(source.zipFile, config.target, config.fault, config.durationSeconds);
      } else {
        result = await startRunFromJson({
          target: config.target,
          fault: config.fault,
          durationSeconds: config.durationSeconds,
          repoUrl: source.mode === "git" ? source.repoUrl : undefined,
          branch: source.mode === "git" ? source.branch : undefined,
          pastedFileName: source.mode === "paste" ? source.pastedFileName : undefined,
          pastedFileContent: source.mode === "paste" ? source.pastedFileContent : undefined,
        });
      }
      setRunId(result.runId);
    } catch (err) {
      setErrorBanner(err instanceof ApiError ? err.message : "Failed to start run. Is the backend running?");
    } finally {
      setStartingRun(false);
    }
  }

  async function handleStartAgent() {
    if (!runId) return;
    setErrorBanner(null);
    setTriggeringAgent(true);
    try {
      await triggerAgent(runId);
    } catch (err) {
      setErrorBanner(err instanceof ApiError ? err.message : "Failed to trigger agent.");
    } finally {
      setTriggeringAgent(false);
    }
  }

  const runInFlight = status !== null && status.phase !== "COMPLETED" && status.phase !== "FAILED";

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>SRElab AI</h1>
        <p className="app-subtitle">Sandboxed fault injection and AI agent benchmarking</p>
      </header>

      {errorBanner && (
        <div className="error-banner" role="alert">
          {errorBanner}
        </div>
      )}

      <div className="app-grid">
        <div className="app-column">
          <SourcePanel value={source} onChange={setSource} disabled={runInFlight} />
          <ConfigPanel value={config} onChange={setConfig} disabled={runInFlight} />
          <RunControls
            phase={status?.phase ?? null}
            onStartRun={handleStartRun}
            onStartAgent={handleStartAgent}
            startRunDisabled={startingRun}
            startingRun={startingRun}
            triggeringAgent={triggeringAgent}
          />
        </div>

        <div className="app-column">
          <EventLogPanel events={events} connected={connected} />
        </div>

        <div className="app-column">
          <ReportPanel report={status?.report ?? null} />
        </div>
      </div>
    </div>
  );
}

export default App;
