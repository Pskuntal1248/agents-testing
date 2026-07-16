package com.srelab.sandbox.cli;

import com.srelab.sandbox.model.RunEvent;
import com.srelab.sandbox.model.StartRunRequest;
import com.srelab.sandbox.service.RunService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.UUID;

@Command(
    name = "run",
    description = "Run a fault injection scenario"
)
public class RunCommand implements Runnable {

    @Option(names = {"-t", "--target"}, required = true, description = "Target application (e.g., byoc-app:test)")
    private String target;

    @Option(names = {"-f", "--fault"}, required = true,
        description = "Fault type (db-timeout, memory-starvation, config-corruption, " +
                       "connection-pool-exhaustion, silent-data-corruption, n1-query, cascading-timeout)")
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

        StartRunRequest request = new StartRunRequest();
        request.setTarget(target);
        request.setFault(fault);
        request.setDurationSeconds(duration);
        request.setRepoUrl(repoUrl);
        request.setBranch(branch);
        // CLI preserves the previous behavior: the agent runs automatically
        // right after fault injection, with no separate trigger step.
        request.setAutoStartAgent(true);

        RunService runService = new RunService();
        String runId = UUID.randomUUID().toString();

        runService.startRun(runId, request, this::printEvent);
    }

    private void printEvent(RunEvent event) {
        switch (event.type()) {
            case RunEvent.ERROR -> System.err.println(event.message());
            default -> System.out.println(event.message());
        }
    }
}
