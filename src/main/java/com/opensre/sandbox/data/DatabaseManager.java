package com.opensre.sandbox.data;

import org.springframework.stereotype.Service;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.Network;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DatabaseManager {
    
    private final Map<String, PostgreSQLContainer<?>> databases = new ConcurrentHashMap<>();

    public String createDatabase(String sandboxId, Network network) {
        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withInitScript("mock-schema-seed.sql");
        
        postgres.start();
        databases.put(sandboxId, postgres);
        
        return postgres.getJdbcUrl();
    }

    public String getJdbcUrl(String sandboxId) {
        PostgreSQLContainer<?> db = databases.get(sandboxId);
        return db != null ? db.getJdbcUrl() : null;
    }

    public void destroyDatabase(String sandboxId) {
        PostgreSQLContainer<?> db = databases.remove(sandboxId);
        if (db != null) {
            db.stop();
        }
    }
}
