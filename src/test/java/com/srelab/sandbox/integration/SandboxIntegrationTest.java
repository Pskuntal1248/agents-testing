package com.srelab.sandbox.integration;

import com.srelab.sandbox.data.BYOCManager;
import com.srelab.sandbox.data.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.Network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SandboxIntegrationTest {

    @Autowired
    private DatabaseManager databaseManager;

    @Autowired
    private BYOCManager byocManager;

    @Test
    public void testFullSandboxDeployment() throws Exception {
        String sandboxId = "test-sandbox-" + System.currentTimeMillis();
        Network network = Network.newNetwork();
        
        try {
            // Step 1: Create database with seed data
            System.out.println("Creating database...");
            String jdbcUrl = databaseManager.createDatabase(sandboxId, network);
            assertNotNull(jdbcUrl, "Database JDBC URL should not be null");
            System.out.println("Database created: " + jdbcUrl);

            // Step 2: Build BYOC app image
            System.out.println("Building BYOC app Docker image...");
            ProcessBuilder pb = new ProcessBuilder("docker", "build", "-t", "byoc-app:test", "./byoc-app");
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            assertEquals(0, exitCode, "Docker build should succeed");
            System.out.println("BYOC app image built successfully");

            // Step 3: Deploy BYOC app with database connection
            System.out.println("Deploying BYOC app...");
            Map<String, String> envVars = Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/testdb",
                "SPRING_DATASOURCE_USERNAME", "testuser",
                "SPRING_DATASOURCE_PASSWORD", "testpass"
            );
            String appUrl = byocManager.deployApp(sandboxId, "byoc-app:test", envVars, network);
            assertNotNull(appUrl, "App URL should not be null");
            System.out.println("BYOC app deployed: " + appUrl);

            // Step 4: Wait for app to be ready
            System.out.println("Waiting for app to be ready...");
            Thread.sleep(10000);

            // Step 5: Test health endpoint
            System.out.println("Testing health endpoint...");
            HttpClient client = HttpClient.newHttpClient();
            String healthUrl = byocManager.getHealthUrl(sandboxId);
            HttpRequest healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(healthUrl))
                .GET()
                .build();
            HttpResponse<String> healthResponse = client.send(healthRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, healthResponse.statusCode(), "Health check should return 200");
            assertTrue(healthResponse.body().contains("UP"), "Health status should be UP");
            System.out.println("Health check passed: " + healthResponse.body());

            // Step 6: Test GET /api/users (should return seeded data)
            System.out.println("Testing GET /api/users...");
            HttpRequest usersRequest = HttpRequest.newBuilder()
                .uri(URI.create(appUrl + "/api/users"))
                .GET()
                .build();
            HttpResponse<String> usersResponse = client.send(usersRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, usersResponse.statusCode(), "GET /api/users should return 200");
            assertTrue(usersResponse.body().contains("admin"), "Should contain seeded user 'admin'");
            System.out.println("GET /api/users passed: " + usersResponse.body());

            // Step 7: Test GET /api/facilities (should return seeded data)
            System.out.println("Testing GET /api/facilities...");
            HttpRequest facilitiesRequest = HttpRequest.newBuilder()
                .uri(URI.create(appUrl + "/api/facilities"))
                .GET()
                .build();
            HttpResponse<String> facilitiesResponse = client.send(facilitiesRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, facilitiesResponse.statusCode(), "GET /api/facilities should return 200");
            assertTrue(facilitiesResponse.body().contains("Data Center Alpha"), "Should contain seeded facility");
            System.out.println("GET /api/facilities passed: " + facilitiesResponse.body());

            // Step 8: Test POST /api/users (create new user)
            System.out.println("Testing POST /api/users...");
            String newUserJson = "{\"username\":\"testuser\",\"email\":\"test@example.com\"}";
            HttpRequest createUserRequest = HttpRequest.newBuilder()
                .uri(URI.create(appUrl + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(newUserJson))
                .build();
            HttpResponse<String> createUserResponse = client.send(createUserRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, createUserResponse.statusCode(), "POST /api/users should return 200");
            assertTrue(createUserResponse.body().contains("testuser"), "Response should contain new user");
            System.out.println("POST /api/users passed: " + createUserResponse.body());

            System.out.println("\n✅ All tests passed! Sandbox is fully operational.");

        } finally {
            // Cleanup
            System.out.println("Cleaning up...");
            byocManager.destroyApp(sandboxId);
            databaseManager.destroyDatabase(sandboxId);
            network.close();
            System.out.println("Cleanup complete");
        }
    }
}
