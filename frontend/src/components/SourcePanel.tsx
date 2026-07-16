import { useState } from "react";
import type { SourceMode } from "../types/run";

export interface SourceValue {
  mode: SourceMode;
  repoUrl: string;
  branch: string;
  zipFile: File | null;
  pastedFileName: string;
  pastedFileContent: string;
}

interface SourcePanelProps {
  value: SourceValue;
  onChange: (value: SourceValue) => void;
  disabled?: boolean;
}

const TABS: { mode: SourceMode; label: string }[] = [
  { mode: "git", label: "Git URL" },
  { mode: "zip", label: "Zip Upload" },
  { mode: "paste", label: "Paste Code" },
];

export function SourcePanel({ value, onChange, disabled }: SourcePanelProps) {
  const [fileName, setFileName] = useState<string>(value.zipFile?.name ?? "");

  function setMode(mode: SourceMode) {
    onChange({ ...value, mode });
  }

  function handleZipSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0] ?? null;
    setFileName(file?.name ?? "");
    onChange({ ...value, zipFile: file });
  }

  return (
    <section className="rounded bg-panel border border-border p-4">
      <h2 className="text-xs font-semibold uppercase tracking-wider text-navy mb-3">Source</h2>

      <div role="tablist" className="flex gap-1 mb-4 border-b border-border">
        {TABS.map((tab) => (
          <button
            key={tab.mode}
            role="tab"
            aria-selected={value.mode === tab.mode}
            type="button"
            disabled={disabled}
            onClick={() => setMode(tab.mode)}
            className={`px-3 py-2 text-[13px] border-0 border-b-2 rounded-none bg-transparent transition-colors
              ${
                value.mode === tab.mode
                  ? "border-b-slate text-navy font-semibold"
                  : "border-b-transparent text-muted hover:text-navy hover:bg-transparent"
              }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {value.mode === "git" && (
        <div className="flex flex-col gap-1">
          <label htmlFor="repo-url" className="text-xs uppercase tracking-wide text-muted">
            Repository URL
          </label>
          <input
            id="repo-url"
            type="text"
            placeholder="https://github.com/org/repo.git"
            value={value.repoUrl}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, repoUrl: e.target.value })}
            className="text-[13px] border border-border rounded px-2.5 py-1.5 focus:outline-none focus:border-sky"
          />
          <label htmlFor="branch" className="text-xs uppercase tracking-wide text-muted mt-3">
            Branch
          </label>
          <input
            id="branch"
            type="text"
            placeholder="main"
            value={value.branch}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, branch: e.target.value })}
            className="text-[13px] border border-border rounded px-2.5 py-1.5 focus:outline-none focus:border-sky"
          />
        </div>
      )}

      {value.mode === "zip" && (
        <div className="flex flex-col gap-1">
          <label htmlFor="zip-file" className="text-xs uppercase tracking-wide text-muted">
            Zip Archive
          </label>
          <input id="zip-file" type="file" accept=".zip" disabled={disabled} onChange={handleZipSelect} className="text-[13px]" />
          {fileName && <p className="text-xs text-muted mt-2">Selected: {fileName}</p>}
        </div>
      )}

      {value.mode === "paste" && (
        <div className="flex flex-col gap-1">
          <label htmlFor="pasted-file-name" className="text-xs uppercase tracking-wide text-muted">
            File Name
          </label>
          <input
            id="pasted-file-name"
            type="text"
            placeholder="Example.java"
            value={value.pastedFileName}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, pastedFileName: e.target.value })}
            className="text-[13px] border border-border rounded px-2.5 py-1.5 focus:outline-none focus:border-sky"
          />
          <label htmlFor="pasted-file-content" className="text-xs uppercase tracking-wide text-muted mt-3">
            File Content
          </label>
          <textarea
            id="pasted-file-content"
            rows={10}
            placeholder="Paste a single file's contents here"
            value={value.pastedFileContent}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, pastedFileContent: e.target.value })}
            className="font-mono text-[13px] border border-border rounded px-2.5 py-1.5 resize-y focus:outline-none focus:border-sky"
          />
        </div>
      )}
    </section>
  );
}
