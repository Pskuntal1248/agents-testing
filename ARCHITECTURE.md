# SRElab AI — Revised Architecture Flow

This document captures the revised end-to-end flow for the sandbox → fault injection → AI agent → evaluation → report loop, based on the original whiteboard sketch plus proposed changes (evaluation engine, verification gate, and reproducibility via snapshots).

## Flow Diagram

```mermaid
flowchart TD
    A[Client via CLI] -->|sandbox configuration| B[Sandbox Created]
    B --> C[BYOC + DB configured with fake/seed data]
    C --> D[(Snapshot clean state)]
    D --> E[Fault Taxonomy Module<br/>selects + injects fault<br/>seeded/deterministic]
    E --> F[Sandbox now in broken state]
    F --> G((AI Agent enters sandbox))
    G --> H[Agent works the issue<br/>ReAct loop, fully logged]
    H --> I{Verification:<br/>health check / retest}
    I -->|fixed| J[Evaluation Engine<br/>TTR, blast radius,<br/>command efficiency,<br/>destructiveness]
    I -->|not fixed / timeout| J
    J --> K[Generate REPORT.md<br/>benchmark score + transcript]
    J --> L[Generate PLAYBOOK.md<br/>what worked]
    J --> M[Generate GUARDRAILS.md<br/>what to block]
    K --> N[Result to Client]
    L --> N
    M --> N
    N --> A

    D -.->|restore for next agent run| E

    style A fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style B fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style C fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style D fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style E fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style F fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style G fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style H fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style I fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style J fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style K fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style L fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style M fill:#1a1a1a,stroke:#4ade80,color:#4ade80
    style N fill:#1a1a1a,stroke:#4ade80,color:#4ade80
```

## Notes on changes vs. original sketch

- **Snapshot node**: captures clean sandbox state right after BYOC+DB setup, before any fault is injected. Enables restoring identical conditions to replay the same fault against multiple agents for fair benchmarking.
- **Verification gate**: after the agent acts, the sandbox is re-tested (health check / retest of the original failing request) before scoring — prevents crediting an agent that merely *claims* to have fixed the issue.
- **Evaluation Engine**: sits between the agent's work and doc generation. Produces a multi-dimensional score (TTR, blast radius, command efficiency, destructiveness) regardless of whether the fix succeeded or timed out, so failures are still logged and comparable.
- **Three output artifacts** instead of two: `REPORT.md` (score + transcript, for auditability) alongside `PLAYBOOK.md` (what worked) and `GUARDRAILS.md` (what to block, derived from failed/destructive attempts).
- **Restore loop (dotted arrow)**: snapshot feeds back into fault injection, supporting repeatable runs against different agents/models for comparison.

## Open items / future extension

- Multi-agent comparison mode: same fault + snapshot restored, run in parallel against agent A/B/C, feeding into one comparison report.
- Fault severity tiers (tier 1 config typo → tier 3 cascading timeout + data corruption) to show competence curves rather than pass/fail.
- Dynamic fault taxonomy updates: human-reviewed postmortem patterns feeding back into the taxonomy module over time, similar to the Rulebook/RAG loop described in `readme.md`.
