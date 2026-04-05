package com.pipeline.orchestrator;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "pipeline",
        mixinStandardHelpOptions = true,
        version = "1.0",
    description = "Orchestrates java-metamodel -> verifier pipeline (Linux-friendly)",
        subcommands = {
                PipelineCommands.Extract.class,
                PipelineCommands.Verify.class,
        PipelineCommands.Run.class,
        PipelineCommands.Gui.class
        }
)
public class PipelineCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PipelineCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }
}
