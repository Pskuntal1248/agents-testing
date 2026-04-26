package com.srelab.sandbox.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
@Command(
    name = "srelab",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "SRElab AI - AI-Powered Site Reliability Engineering Sandbox",
    subcommands = {RunCommand.class}
)
public class SRElabCli implements Runnable {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new SRElabCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Use 'srelab --help' for usage information");
    }
}
