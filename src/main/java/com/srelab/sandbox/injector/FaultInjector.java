package com.srelab.sandbox.injector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Injects faults into sandboxed containers, ranging from shallow infra-level
 * chaos (tier 1) to deep postmortem-style production bugs (tier 2/3).
 *
 * Tiering follows real incident postmortem categories rather than generic
 * chaos-engineering primitives:
 *  - Tier 1: infra-level (latency, memory pressure, config drift)
 *  - Tier 2: resource-exhaustion / app-level bugs (pool exhaustion, N+1, silent corruption)
 *  - Tier 3: compound failures (cascading timeout under load)
 */
@Service
public class FaultInjector {

    private final DockerClient docker;

    public FaultInjector() {
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ZerodepDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .build();
        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }

    // ---- Tier 1: infra-level faults ----

    public void injectDatabaseTimeout(String containerId, int delayMs) {
        // The target image bakes in iproute2 (tc) at build time (see byoc-app/Dockerfile);
        // the running container is granted only the NET_ADMIN capability at deploy time
        // (see BYOCManager), not the broader capability set apt/dpkg would need to
        // install packages inside an already-hardened container. Verify tc is present
        // rather than assuming so -- silently doing nothing when tc is missing was a
        // real bug: the exec would "succeed" (no exception) while injecting no fault.
        requireTcAvailable(containerId);
        execInContainer(containerId, "tc", "qdisc", "add", "dev", "eth0", "root", "netem", "delay", delayMs + "ms");
    }

    private void requireTcAvailable(String containerId) {
        int exitCode = execInContainerAllowFailure(containerId, "sh", "-c", "command -v tc");
        if (exitCode != 0) {
            throw new RuntimeException(
                "tc (iproute2) not found in container " + containerId + ". "
                    + "The db-timeout/cascading-timeout faults require the target image to "
                    + "bake in iproute2 at build time (see byoc-app/Dockerfile) and the "
                    + "container to be granted the NET_ADMIN capability at deploy time.");
        }
    }

    public void injectMemoryStarvation(String containerId, long limitBytes) {
        docker.updateContainerCmd(containerId)
            .withMemory(limitBytes)
            .exec();
    }

    public void injectConfigCorruption(String containerId, String configPath) {
        execInContainer(containerId, "sh", "-c", "echo 'corrupted=true' >> " + configPath);
    }

    // ---- Tier 2: resource-exhaustion / app-level faults ----

    /**
     * Exhausts the target Postgres container's connection pool by opening and
     * holding a batch of idle connections that never release, mirroring
     * HikariCP pool-timeout style incidents ("all connections busy").
     *
     * Returns the list of open Connections; caller must close them to end
     * the fault (or let sandbox teardown do it).
     */
    public List<Connection> injectConnectionPoolExhaustion(String jdbcUrl, String username, String password, int connectionsToHold) {
        List<Connection> heldConnections = new ArrayList<>();
        try {
            for (int i = 0; i < connectionsToHold; i++) {
                Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                try (Statement stmt = conn.createStatement()) {
                    // pg_sleep holds the backend busy so the connection can't be reused by the pool
                    stmt.execute("SELECT pg_sleep(9999)");
                } catch (SQLException ignored) {
                    // expected: the sleep will be interrupted on cleanup/teardown, not on injection
                }
                heldConnections.add(conn);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Connection pool exhaustion injection failed", e);
        }
        return heldConnections;
    }

    public void releaseConnectionPoolExhaustion(List<Connection> heldConnections) {
        for (Connection conn : heldConnections) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Silently corrupts data in a table without crashing the app or failing
     * any health check — mirrors real incidents where a bad migration or
     * batch job overwrites/nulls production data and nothing alerts on it
     * until a customer notices wrong values.
     */
    public void injectSilentDataCorruption(String jdbcUrl, String username, String password, String tableName, String columnName) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE " + tableName + " SET " + columnName + " = NULL");
        } catch (SQLException e) {
            throw new RuntimeException("Silent data corruption injection failed", e);
        }
    }

    /**
     * Toggles the BYOC app's N+1 query mode via an environment-driven Spring
     * profile flag (see byoc-app FacilityController/UserController), rather
     * than shipping a separate "buggy" build. Requires the container to
     * support runtime env mutation via restart-with-env, so this variant
     * writes a marker file the app reads at request time.
     */
    public void injectN1QueryToggle(String containerId, String markerPath) {
        execInContainer(containerId, "sh", "-c", "echo 'n1-query-mode=true' > " + markerPath);
    }

    public void clearN1QueryToggle(String containerId, String markerPath) {
        execInContainer(containerId, "sh", "-c", "rm -f " + markerPath);
    }

    // ---- Tier 3: compound / cascading faults ----

    /**
     * Combines DB latency injection with a burst of concurrent requests
     * against the app, so the app's request-handling thread pool saturates
     * waiting on the slow DB and the whole app appears to hang -- a classic
     * "one slow dependency cascades into a full outage" postmortem pattern.
     */
    public void injectCascadingTimeout(String containerId, String appBaseUrl, int dbDelayMs, int concurrentRequests) {
        injectDatabaseTimeout(containerId, dbDelayMs);

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, concurrentRequests));
        try {
            for (int i = 0; i < concurrentRequests; i++) {
                pool.submit(() -> {
                    try {
                        var url = new java.net.URI(appBaseUrl + "/api/facilities").toURL();
                        var conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(2000);
                        conn.setReadTimeout(2000);
                        conn.getResponseCode();
                    } catch (Exception ignored) {
                        // expected: requests are meant to pile up / time out under the induced latency
                    }
                });
            }
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Execs a command in the container and throws if it fails (non-zero exit
     * or transport error). Use this for fault steps that must succeed for the
     * fault to be considered injected.
     */
    private void execInContainer(String containerId, String... command) {
        int exitCode = execInContainerAllowFailure(containerId, command);
        if (exitCode != 0) {
            throw new RuntimeException(
                "Fault injection command exited with code " + exitCode
                    + " in container " + containerId + ": " + String.join(" ", command));
        }
    }

    /**
     * Execs a command in the container and returns its exit code without
     * throwing, so callers can implement fallback logic (e.g. try apt, then
     * apk) instead of failing on the first attempt.
     */
    private int execInContainerAllowFailure(String containerId, String... command) {
        try {
            var exec = docker.execCreateCmd(containerId)
                .withCmd(command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

            docker.execStartCmd(exec.getId())
                .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<>())
                .awaitCompletion();

            Long exitCodeLong = docker.inspectExecCmd(exec.getId()).exec().getExitCodeLong();
            return exitCodeLong != null ? exitCodeLong.intValue() : -1;
        } catch (Exception e) {
            throw new RuntimeException("Fault injection exec failed for command: " + String.join(" ", command), e);
        }
    }
}
