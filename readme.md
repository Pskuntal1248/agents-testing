# SRElab AI

An AI-powered Site Reliability Engineering sandbox. It spins up an isolated Docker environment around your application, deliberately injects a realistic production failure, then turns an AI agent loose to diagnose and fix it — while an independent health check verifies whether the fix actually worked. The whole thing is driven from either a CLI or a desktop GUI.

This README covers what the system does, how data flows through it end to end, and exactly how to start everything from a completely cold machine.

---

## 1. What this actually is

Three problems this project is trying to solve:

1. **You don't want an AI agent's first exposure to your production code and database to be production.** So you hand it a sandboxed clone instead — same code, fake data, fully disposable.
2. **You want to know how an agent behaves under a *specific*, *repeatable* failure**, not a vague "does it seem smart" impression. So faults are injected deliberately and consistently (DB timeouts, memory starvation, connection pool exhaustion, N+1 queries, silent data corruption, cascading timeouts, config corruption).
3. **You want evidence, not vibes.** The agent's own claim that it fixed something is never trusted — an independent health check re-verifies the target application after the agent is done, and a score is computed from real signals (time to resolve, commands executed, whether it actually worked).

## 2. The pieces

```
ai/
├── src/                        Java backend ("sandbox-service") — Spring Boot
│   ├── main/java/com/srelab/sandbox/
│   │   ├── core/SandboxManager.java       Creates/destroys isolated Docker sandboxes (Testcontainers)
│   │   ├── data/DatabaseManager.java      Spins up a throwaway Postgres per run, seeded with test data
│   │   ├── data/BYOCManager.java          Deploys your target app image into the sandbox network
│   │   ├── injector/FaultInjector.java    Injects one of 7 fault types into the running app/DB
│   │   ├── agent/AIAgent.java             Wraps Gemini (via LangChain4j) in a ReAct tool-calling loop
│   │   ├── agent/SandboxTools.java        The only tools the agent can call (shell exec, read logs, check health)
│   │   ├── evaluator/Evaluator.java       Turns (resolved?, time, commands) into a 0–100 score
│   │   ├── service/RunService.java        Orchestrates all of the above into one pipeline; shared by CLI + REST
│   │   ├── service/RunEventBus.java       Fans out live progress events to SSE subscribers (for the GUI)
│   │   ├── controller/RunController.java REST + SSE API the GUI talks to
│   │   └── cli/RunCommand.java            The `srelab run ...` CLI command
│   └── test/                              EvaluatorTest (pure logic) + SandboxIntegrationTest (real Docker)
│
├── byoc-app/                    Example "bring your own code" target app (Spring Boot + Postgres)
│                                 Simple Users/Facilities CRUD app used as the default fault-injection target.
│0
├── test-apps/inventory-app/     A second, richer target app (Products/Orders/OrderItems)
│                                 Has real JPA relationships, so N+1 and connection-pool faults have
│                                 actual substance to bite into instead of a simulated toggle.
│                                 NOT wired into Docker by this project — you run/connect it yourself.
│
├── frontend/                    React + TypeScript + Tailwind GUI
│   ├── src/                     Source/Config/RunControls/EventLog/Report panels, talking to the REST/SSE API
│   └── src-tauri/                Tauri shell — packages the React app as a native desktop window
│
├── srelab                       Shell wrapper: `java -jar target/sandbox-service-1.0.0.jar "$@"`
├── pom.xml                      Root Maven project (the backend)
└── .env                         Your GEMINI_API_KEY (gitignored, never commit this)
```

Two ways to drive this:
- **CLI** — one blocking command, prints progress to stdout, agent runs automatically. Good for scripting/CI.
- **GUI (Tauri desktop app)** — start a run, watch it live, and only trigger the AI agent when you click the button. Good for actually watching what's happening.

Both paths go through the exact same `RunService` — nothing is duplicated between them.

## 3. The full data flow, start to finish

This is what happens on one `srelab run` (CLI) or one "Start Sandbox" click (GUI):

```
 1. CREATE SANDBOX
    SandboxManager spins up an isolated Docker network + a bare ubuntu
    "control" container. Every run gets its own network — nothing is shared
    between runs.

 2. START DATABASE
    DatabaseManager starts a throwaway Postgres container on that network,
    seeded from mock-schema-seed.sql (byoc-app) or your own seed script.
    This Postgres is destroyed at the end of the run. Nothing persists.

 3. IMPORT CODE (one of three ways, GUI/REST only — the CLI can skip this
    entirely and just point at a pre-built image)
      - Git URL      -> cloned into the sandbox
      - Zip upload   -> extracted into the sandbox
      - Pasted file  -> written into the sandbox
    (The REST API rejects a run with none of these, or with more than one,
    with a 400 and a clear error message -- e.g. "No code source provided.
    Provide exactly one of: repoUrl, a zip upload, or pastedFileContent."
    This validation is deliberately NOT applied to the CLI, since running
    the CLI against a pre-built image with no import is documented,
    intended behavior there.)

 4. DEPLOY APPLICATION
    BYOCManager starts your target app's container (e.g. byoc-app:test) on
    the same network, hardened: all Linux capabilities dropped except
    NET_ADMIN (needed for the network-latency fault), no-new-privileges,
    tmpfs-only /tmp, memory/CPU limits.

 5. INJECT FAULT
    FaultInjector applies exactly one of:
      db-timeout                 tc qdisc network delay on the app container
      memory-starvation          shrinks the container's memory limit live
      config-corruption          corrupts a config file inside the container
      connection-pool-exhaustion holds N idle DB connections open via pg_sleep
      silent-data-corruption     UPDATE ... SET x = NULL directly in the DB
                                  (no crash, no error -- just wrong data)
      n1-query                   flips an env var that makes the target app
                                  do one query per row instead of a batch query
      cascading-timeout          combines db-timeout with a burst of
                                  concurrent requests to saturate the app's
                                  thread pool

 6. AGENT ACTIVATED (CLI: automatic / GUI: only after you click "Start Agent")
    AIAgent runs a ReAct loop against Gemini. It can only do three things:
      - executeShellCommand  (inside the target container, nothing else)
      - readLogs
      - checkHealthEndpoint
    Every single call is logged to a transcript. The agent keeps going
    until it says "RESOLVED: ..." / "UNRESOLVED: ..." or hits a turn limit.

 7. VERIFY HEALTH INDEPENDENTLY
    RunService re-checks the app's /health endpoint ITSELF -- it does not
    trust the agent's self-report. If the agent says "fixed" but health is
    still down, the run is scored as unresolved. This is the whole point:
    the agent's opinion of itself is not the source of truth.

 8. SCORE + REPORT
    Evaluator computes: resolved (bool), time-to-resolve, commands executed,
    and a 0-100 score. RunReport bundles this with the agent's full
    transcript (every command it ran, every result) so you can audit
    *why* it got that score, not just the number.

 9. CLEANUP
    Every container from this run (sandbox, DB, app) is destroyed.
    The run's final report stays queryable via GET /api/runs/{id} for the
    life of the backend process, even after cleanup.
```

If the GUI is driving this, every one of these steps pushes a live event over Server-Sent Events to `EventLogPanel`, and step 8's result populates `ReportPanel`.

## 4. Why Docker matters here (and the heat problem)

Every run creates 2–3 real Docker containers (sandbox shell, Postgres, your app) via [Testcontainers](https://testcontainers.com/). This is intentional — the whole point is that faults are injected into *real* running processes, not mocks. That's also exactly why it's CPU/fan-intensive: container startup, Postgres init, and (if the fault is `db-timeout`) `iproute2` package installs all cost real CPU cycles.

Things that help:
- **Run one thing at a time.** Don't leave old sandbox containers running between tests — `docker ps` and clean up (`docker rm -f <name>`) anything that isn't `testcontainers-ryuk-*` (that one's supposed to be there, it's the auto-cleanup reaper).
- **Close Docker Desktop when you're not actively running a sandbox.** There's no "always-on" component in this system — nothing needs Docker running except during an actual run.
- **Keep Docker Desktop's resource allocation modest** if your laptop is thermal-limited (Docker Desktop → Settings → Resources). Fewer allocated cores/RAM means less max heat, at the cost of slower container startup.
- The `test-apps/inventory-app` was deliberately built **without any Docker/Testcontainers wiring** — it's a plain Spring Boot app you run and connect to a database however you choose (a lightweight local Postgres, a managed cloud instance, whatever generates the least heat for you). It only becomes part of the sandbox flow if you choose to point a run's target at it.

## 5. Prerequisites

| Tool | Version used in this project | Check with |
|---|---|---|
| Java (JDK) | 24 | `java -version` |
| Maven | uses the bundled `./mvnw` wrapper, no separate install needed | — |
| Docker Desktop | any recent version supporting the Docker API | `docker info` |
| Node.js | 22.x | `node --version` |
| npm | 10.x | `npm --version` |
| Rust + Cargo | 1.96+ (only needed for the Tauri desktop app) | `rustc --version` |
| Xcode Command Line Tools (macOS only, for Tauri's native build) | any | `xcode-select -p` |
| A Google Gemini API key | free tier works, but is rate-limited (20 req/day on `gemini-2.5-flash` at time of writing) | https://ai.google.dev/gemini-api/docs/api-key |

## 6. First-time setup (do this once)

```bash
# 1. Clone/open the repo, then create your API key file at the project root
echo "GEMINI_API_KEY=your-real-key-here" > .env
```

`.env` is gitignored — never commit it. The backend reads `GEMINI_API_KEY` directly from the process environment (not from Spring's config), because the CLI never boots a Spring context, so this has to work as a plain env var regardless of entry point.

```bash
# 2. Build the backend
./mvnw clean package -DskipTests

# 3. Build the example target app's Docker image (this is what gets deployed
#    into the sandbox and has faults injected into it)
cd byoc-app
docker build -t byoc-app:test .
cd ..

# 4. Install frontend dependencies
cd frontend
npm install
cd ..
```

## 7. Running it — CLI path (simplest, no GUI)

```bash
# Make sure Docker Desktop is running first
docker info   # should succeed, not error

# Load your API key into the shell, then run
set -a && source .env && set +a
./srelab run --target=byoc-app:test --fault=db-timeout --duration=60
```

You'll see all 9 pipeline stages print live to stdout, ending in a benchmark report:

```
=== Benchmark Report ===
Resolved:          true
Time to resolve:   12450ms
Commands executed:  4
Score:              78/100
========================
```

Other CLI flags:
```
-t, --target   Docker image to deploy and test (required)
-f, --fault    One of: db-timeout, memory-starvation, config-corruption,
               connection-pool-exhaustion, silent-data-corruption,
               n1-query, cascading-timeout (required)
-d, --duration Sandbox keep-alive timeout in seconds (default 300)
-r, --repo     Git repo URL to clone into the sandbox before deploying (optional)
-b, --branch   Git branch to use with --repo (default: main)
```

## 8. Running it — GUI path (backend + frontend + Tauri)

The GUI needs three things running: Docker (for the sandbox), the Spring Boot backend (REST + SSE API), and the frontend (either as a browser dev server or the packaged Tauri window).

**Terminal 1 — backend:**
```bash
docker info                       # confirm Docker is up
set -a && source .env && set +a

# The packaged jar's default entrypoint is the CLI (SRElabCli), not the web
# server, so start the web server class directly:
./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp "target/classes:$(cat /tmp/cp.txt)" com.srelab.sandbox.SandboxServiceApplication
```
Confirm it's up: `curl http://localhost:8080/api/runs` should return `[]`.

**Terminal 2 — frontend, either mode:**

*Browser dev mode (fastest to iterate on):*
```bash
cd frontend
npm run dev
# open http://localhost:5173
```

*Native desktop app (Tauri):*
```bash
cd frontend
npx tauri dev
```
This opens an actual native window (not a browser tab) titled "SRElab AI", running the same React app, talking to the same backend on `localhost:8080`.

**In the GUI:**
1. Pick a source (Git URL / Zip upload / Paste code) — at least one is required, the backend will reject a run with none.
2. Pick a target image and fault type.
3. Click **Start Sandbox** — watch the live log stream (sandbox → DB → deploy → fault injection).
4. Once it reaches "Awaiting agent trigger", click **Start Agent** — this is the only way the agent runs in the GUI, it never starts on its own.
5. Watch the agent's tool calls stream in live, then see the final score/report.

## 9. Running the standalone CRUD test app (optional, your own DB)

`test-apps/inventory-app` is a second target app with real database relationships (Products, Orders, OrderItems), built so N+1 query bugs and connection-pool exhaustion have genuine substance instead of a fake toggle. This project does **not** wire it into Docker for you — connect it to whatever Postgres you're already running:

```bash
cd test-apps/inventory-app

# Point it at your own database via env vars (defaults shown)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/inventory
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres

# Run the schema/seed script yourself against that database first, e.g.:
psql "$SPRING_DATASOURCE_URL" -f src/main/resources/inventory-schema-seed.sql

../mvnw spring-boot:run
```

It listens on port 8081 by default (`SERVER_PORT` env var to change). Endpoints: `GET/POST/PUT/DELETE /api/products`, `GET/POST/PUT/DELETE /api/orders`, `GET /api/orders/with-items` (the N+1-fixed comparison endpoint), `GET /health`.

## 10. Running the tests

```bash
./mvnw test
```
- `EvaluatorTest` — pure scoring logic, no Docker needed, always fast.
- `SandboxIntegrationTest` — builds the real `byoc-app` image, deploys it against a real Postgres, exercises the full CRUD surface. **Requires Docker running.** This is the test most likely to be flaky on a resource-constrained machine — if it times out waiting for a container's health check, it's almost always host CPU contention, not a code bug. Close other heavy processes and retry.

## 11. Common problems

| Symptom | Cause | Fix |
|---|---|---|
| `Could not find a valid Docker environment` | Docker Desktop isn't running | Start Docker Desktop, wait for `docker info` to succeed, retry |
| CLI/backend can't find the API key | `.env` not sourced into the shell before running | `set -a && source .env && set +a` before every run in a new terminal |
| Frontend requests fail with a CORS error | Backend not running, or running with a stale build | Restart the backend; `WebConfig` already allows `localhost:*` and `tauri://localhost` |
| A run silently deploys the target image with no changes | You started a run with no code source | Fixed — the backend now rejects this with a 400 error requiring exactly one of Git URL / zip / pasted file |
| `db-timeout` fault seems to do nothing | Target image is missing `iproute2` (`tc`) | `byoc-app`'s Dockerfile already bakes this in; if you're using your own image, install `iproute2` at build time and grant the container `NET_ADMIN` |
| Agent runs but never gets a response, seems stuck | Gemini free-tier rate limit hit (5/min or 20/day) | Wait for the quota to reset, or use a paid-tier key |
| `SandboxIntegrationTest` times out | Host under CPU load (another Docker workload, thermal throttling) | Free up CPU, retry — this is environmental, not a code defect |
| Docker overheating your machine | Container-heavy by design | See section 4 — run one thing at a time, lower Docker Desktop's resource cap, close Docker when idle |

## 12. What's deliberately out of scope right now

- No multi-agent comparison mode (same fault, multiple models side by side) yet.
- No persisted run history across backend restarts — reports live in memory for the life of the process.
- No authentication on the REST API — it's a local developer tool, not designed to be exposed to a network.
- `inventory-app` is not part of the automated fault-injection pipeline (`RunService`) — it's a standalone target you can point a run at manually once you've built its image, but the N+1/pool-exhaustion faults on it would need to be triggered the same way as any other target.
