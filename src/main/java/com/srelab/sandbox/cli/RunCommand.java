package com.srelab.sandbox.cli;

import com.srelab.sandbox.core.SandboxManager;
import com.srelab.sandbox.data.DatabaseManager;
import com.srelab.sandbox.data.BYOCManager;
import com.srelab.sandbox.model.SandboxConfig;
import com.srelab.sandbox.model.GitImportRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.testcontainers.containers.Network;

import java.util.Map;

@Command(
    name = "run",
    description = "Run a fault injection scenario"
)
public class RunCommand implements Runnable {
    
    @Option(names = {"-t", "--target"}, required = true, description = "Target application (e.g., byoc-app:test)")
    private String target;
    
    @Option(names = {"-f", "--fault"}, required = true, description = "Fault type (db-timeout, memory-starvation, config-corruption)")
    private String fault;
    
    @Option(names = {"-d", "--duration"}, defaultValue = "300", description = "Timeout in seconds (default: 300)")
    private int duration;
    
    @Option(names = {"-r", "--repo"}, description = "Git repository URL to import code from")
    private String repoUrl;
    
    @Option(names = {"-b", "--branch"}, description = "Git branch (default: main)")
    private String branch = "main";

    @Override
    public void run() {
        System.out.println("Starting SRElab AI incident simulation...");
        System.out.println("Target: " + target);
        System.out.println("Fault: " + fault);
        System.out.println("Duration: " + duration + "s\n");
        
        SandboxManager sandboxManager = new SandboxManager();
        DatabaseManager dbManager = new DatabaseManager();
        BYOCManager byocManager = new BYOCManager();
        
        String sandboxId = null;
        
        try {
            // Step 1: Create sandbox
            System.out.println("[Step 1/4] Creating sandbox environment...");
            SandboxConfig config = new SandboxConfig();
            config.setImage("ubuntu:22.04");
            config.setMemoryLimitMB(512);
            config.setCpuQuota(50000L);
            config.setNetworkIsolated(false);
            
            sandboxId = sandboxManager.createSandbox(config);
            System.out.println("Sandbox created: " + sandboxId.substring(0, 8) + "...\n");
            
            // Step 2: Start database
            System.out.println("[Step 2/4] Starting PostgreSQL database...");
            Network network = sandboxManager.getNetwork(sandboxId);
            String jdbcUrl = dbManager.createDatabase(sandboxId, network);
            System.out.println("Database started: " + jdbcUrl + "\n");
            
            // Step 3: Import code (if repo provided)
            if (repoUrl != null && !repoUrl.isEmpty()) {
                System.out.println("[Step 3/4] Importing code from Git...");
                GitImportRequest gitRequest = new GitImportRequest();
                gitRequest.setRepoUrl(repoUrl);
                gitRequest.setBranch(branch);
                gitRequest.setTargetPath("/app");
                
                sandboxManager.importGitRepo(sandboxId, gitRequest);
                System.out.println("Code imported to /app\n");
            } else {
                System.out.println("[Step 3/4] Skipped (no repo provided)\n");
            }
            
            // Step 4: Deploy application
            System.out.println("[Step 4/4] Deploying application...");
            Map<String, String> envVars = Map.of(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/testdb",
                "SPRING_DATASOURCE_USERNAME", "testuser",
                "SPRING_DATASOURCE_PASSWORD", "testpass"
            );
            
            String appUrl = byocManager.deployApp(sandboxId, target, envVars, network);
            System.out.println("Application deployed: " + appUrl + "\n");
            
            System.out.println("Sandbox ready! ID: " + sandboxId.substring(0, 8));
            System.out.println("\nChaos injection coming in Phase 2...");
            
            // Keep alive for duration
            System.out.println("\nRunning for " + duration + " seconds...");
            Thread.sleep(duration * 1000L);
            
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (sandboxId != null) {
                System.out.println("\nCleaning up...");
                byocManager.destroyApp(sandboxId);
                dbManager.destroyDatabase(sandboxId);
                sandboxManager.destroySandbox(sandboxId);
                System.out.println("Cleanup complete");
            }
        }
    }
}
