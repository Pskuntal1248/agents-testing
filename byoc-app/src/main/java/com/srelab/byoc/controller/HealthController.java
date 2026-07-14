package com.srelab.byoc.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * Real health check: verifies actual DB connectivity instead of returning a
 * hardcoded "UP". This matters for fault scenarios where the DB is down or
 * unreachable but the app process itself is still running -- a classic
 * "health check lies" postmortem pattern that a static health endpoint can
 * never surface.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    @Value("${srelab.fault.n1-query-mode:false}")
    private boolean n1QueryModeEnabled;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            if (!valid) {
                return Map.of("status", "DOWN", "reason", "database connection invalid");
            }
            return Map.of("status", "UP", "database", "reachable");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "reason", "database unreachable: " + e.getMessage());
        }
    }
}
