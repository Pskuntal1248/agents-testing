package com.srelab.inventory.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;

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
