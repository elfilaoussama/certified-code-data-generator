package com.pipeline.orchestrator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

final class PipelineCommands {

    private PipelineCommands() {
    }

    static final class CommonOptions {
        @Option(names = {"--metamodel-dir"}, description = "Path to java-metamodel module (default: ../java-metamodel)")
        Path metamodelDir;

        @Option(names = {"--verifier-dir"}, description = "Path to VerificationEnvironment module (default: ../alloy-in-ecore-java-verification/VerificationEnvironment)")
        Path verifierDir;

        @Option(names = {"--platform"}, defaultValue = "AUTO", description = "Execution platform: AUTO (default), WINDOWS, UNIX, or LINUX")
        Platform platform = Platform.AUTO;

        Platform effectivePlatform() {
            return PlatformDetector.effective(platform);
        }

        boolean convertWindowsPaths() {
            // Convert drive-letter paths only when THIS Java process runs inside a Linux environment
            // hosted by Windows (so /mnt/<drive>/... paths exist).
            // If we're on Windows but choose Platform.LINUX for child processes, path translation
            // is handled by ProcessRunner, while local file IO stays Windows-native.
            return PlatformDetector.isLinuxEnvOnWindowsHost();
        }

        Path resolveMetamodelDir(Path baseDir) {
            return metamodelDir != null ? metamodelDir : baseDir.resolve("../java-metamodel").normalize();
        }

        Path resolveVerifierDir(Path baseDir) {
            return verifierDir != null ? verifierDir : baseDir.resolve("../alloy-in-ecore-java-verification/VerificationEnvironment").normalize();
        }
    }

    private static boolean isMultiRepoMode(String mode) {
        return mode != null && mode.equalsIgnoreCase("multi-repo");
    }

    private static String repoNameFromPath(Path repoDir) {
        if (repoDir == null) {
            return "repo";
        }
        Path name = repoDir.getFileName();
        if (name == null) {
            return "repo";
        }
        String s = name.toString().trim();
        return s.isEmpty() ? "repo" : s;
    }

    private static Path ensureRepoOutDir(Path baseOutDir, String repoName) {
        if (baseOutDir == null) {
            throw new IllegalArgumentException("baseOutDir is required");
        }
        if (repoName == null || repoName.isBlank()) {
            return baseOutDir;
        }
        Path last = baseOutDir.getFileName();
        if (last != null && repoName.equals(last.toString())) {
            return baseOutDir;
        }
        return baseOutDir.resolve(repoName);
    }

    @Command(name = "extract", mixinStandardHelpOptions = true,
            description = "Run java-metamodel (SPOON) to extract types into JSON.")
    static class Extract implements Callable<Integer> {

        @Option(names = {"--source"}, required = true, description = "Source folder to analyze (single repo) or container folder (multi-repo).")
        String source;

        @Option(names = {"--mode"}, defaultValue = "single", description = "single (default) or multi-repo")
        String mode;

        @Option(names = {"--out"}, required = true, description = "Output directory where spoon.json will be written.")
        String out;

        @Option(names = {"--json-name"}, defaultValue = "spoon.json", description = "Filename for the SPOON JSON output.")
        String jsonName;

        @Option(names = {"--summary"}, defaultValue = "false", description = "Print human-readable summary from java-metamodel.")
        boolean summary;

        @Option(names = {"--maven-quiet"}, defaultValue = "true", description = "Run mvn with -q inside submodules.")
        boolean mavenQuiet;

        @picocli.CommandLine.Mixin
        CommonOptions common;

        @Override
        public Integer call() throws Exception {
            Path baseDir = Path.of("").toAbsolutePath();
            Path metamodelDir = common.resolveMetamodelDir(baseDir);

            boolean convertPaths = common.convertWindowsPaths();

            Path outBaseDirAbs = PathUtils.resolveAgainst(baseDir, convertPaths, out);
            Path sourceAbs = PathUtils.resolveAgainst(baseDir, convertPaths, source);

            if (!isMultiRepoMode(mode)) {
                String repoName = repoNameFromPath(sourceAbs);
                Path outDirAbs = ensureRepoOutDir(outBaseDirAbs, repoName);
                Files.createDirectories(outDirAbs);
                Path jsonOutAbs = outDirAbs.resolve(jsonName);

                String srcPath = PathUtils.toModuleArg(metamodelDir, convertPaths, sourceAbs.toString());
                String jsonOutArg = PathUtils.toModuleArg(metamodelDir, convertPaths, jsonOutAbs.toString());

                List<String> args = new ArrayList<>();
                args.add("-i");
                args.add(srcPath);
                args.add("-m");
                args.add("single");
                args.add("--json");
                args.add("-o");
                args.add(jsonOutArg);
                if (!summary) {
                    args.add("--no-summary");
                }

                int exit = ProcessRunner.run(
                        metamodelDir,
                        common.effectivePlatform(),
                        "./run.sh",
                        "run.ps1",
                        args,
                        mavenQuiet
                );

                if (exit != 0) {
                    return exit;
                }

                System.out.println("\n[SUCCESS] SPOON JSON written: " + jsonOutAbs);
                return 0;
            }

            // Multi-repo mode: run each immediate subdirectory as a separate repo, with per-repo output.
            if (!Files.isDirectory(sourceAbs)) {
                System.err.println("[ERROR] In multi-repo mode, --source must be a directory containing repos: " + sourceAbs);
                return 2;
            }
            Files.createDirectories(outBaseDirAbs);

            List<Path> repos;
            try (var s = Files.list(sourceAbs)) {
                repos = s.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(p -> p.getFileName() != null ? p.getFileName().toString() : ""))
                        .toList();
            }

            int failures = 0;
            for (Path repoDir : repos) {
                String repoName = repoNameFromPath(repoDir);
                Path repoOutDir = ensureRepoOutDir(outBaseDirAbs, repoName);
                Files.createDirectories(repoOutDir);

                Path jsonOutAbs = repoOutDir.resolve(jsonName);

                String srcPath = PathUtils.toModuleArg(metamodelDir, convertPaths, repoDir.toString());
                String jsonOutArg = PathUtils.toModuleArg(metamodelDir, convertPaths, jsonOutAbs.toString());

                System.out.println("\n[REPO] " + repoName);
                List<String> args = new ArrayList<>();
                args.add("-i");
                args.add(srcPath);
                args.add("-m");
                args.add("single");
                args.add("--json");
                args.add("-o");
                args.add(jsonOutArg);
                if (!summary) {
                    args.add("--no-summary");
                }

                int exit = ProcessRunner.run(
                        metamodelDir,
                        common.effectivePlatform(),
                        "./run.sh",
                        "run.ps1",
                        args,
                        mavenQuiet
                );
                if (exit != 0) {
                    failures++;
                    System.err.println("[ERROR] Extract failed for " + repoName + " (exit=" + exit + ")");
                } else {
                    System.out.println("[SUCCESS] SPOON JSON written: " + jsonOutAbs);
                }
            }

            return failures == 0 ? 0 : 1;
        }
    }

    @Command(name = "verify", mixinStandardHelpOptions = true,
            description = "Run AlloyInEcore verifier on an instance (.json/.aie/.xmi).")
    static class Verify implements Callable<Integer> {

        @Option(names = {"--instance"}, required = true, description = "Instance path (.json, .aie, .xmi). If .json, verifier maps JSON->AIE automatically.")
        String instance;

        @Option(names = {"--recore"}, description = "Path to .recore file (default: verifier's ClassHierarchies.recore).")
        String recore;

        @Option(names = {"--out"}, required = true, description = "Output directory for verifier artifacts (ecore/kodkod/etc).")
        String out;

        @Option(names = {"--details"}, defaultValue = "false", description = "Print broken rules when UNSAT.")
        boolean details;

        @Option(names = {"--report"}, description = "Write report JSON to this path (default: <out>/report.json when --details is set).")
        String report;

        @Option(names = {"--maven-quiet"}, defaultValue = "true", description = "Run mvn with -q inside submodules.")
        boolean mavenQuiet;

        @picocli.CommandLine.Mixin
        CommonOptions common;

        @Override
        public Integer call() throws Exception {
            Path baseDir = Path.of("").toAbsolutePath();
            Path verifierDir = common.resolveVerifierDir(baseDir);

            boolean convertPaths = common.convertWindowsPaths();

            Path outDirAbs = PathUtils.resolveAgainst(baseDir, convertPaths, out);
            Files.createDirectories(outDirAbs);

            Path instanceAbs = PathUtils.resolveAgainst(baseDir, convertPaths, instance);
            String instancePath = PathUtils.toModuleArg(verifierDir, convertPaths, instanceAbs.toString());
            String outDirArg = PathUtils.toModuleArg(verifierDir, convertPaths, outDirAbs.toString());

            List<String> args = new ArrayList<>();
            args.add("-i");
            args.add(instancePath);
            args.add("-o");
            args.add(outDirArg);

            if (recore != null && !recore.isBlank()) {
                args.add("-r");
                Path recoreAbs = PathUtils.resolveAgainst(baseDir, convertPaths, recore);
                args.add(PathUtils.toModuleArg(verifierDir, convertPaths, recoreAbs.toString()));
            }

            boolean wantsReport = (report != null && !report.isBlank()) || details;
            if (details) {
                args.add("--details");
            }
            if (wantsReport) {
                Path reportAbs;
                if (report != null && !report.isBlank()) {
                    reportAbs = PathUtils.resolveAgainst(baseDir, convertPaths, report);
                } else {
                    reportAbs = outDirAbs.resolve("report.json");
                }
                String reportPath = PathUtils.toModuleArg(verifierDir, convertPaths, reportAbs.toString());
                args.add("--report");
                args.add(reportPath);
            }

            int exit = ProcessRunner.run(
                    verifierDir,
                    common.effectivePlatform(),
                    "./run.sh",
                    "run.ps1",
                    args,
                    mavenQuiet
            );

            return exit;
        }
    }

    @Command(name = "run", mixinStandardHelpOptions = true,
            description = "Run extract + verify end-to-end, writing all artifacts under one output folder.")
    static class Run implements Callable<Integer> {

        @Option(names = {"--source"}, required = true, description = "Source folder to analyze.")
        String source;

        @Option(names = {"--mode"}, defaultValue = "single", description = "single (default) or multi-repo")
        String mode;

        @Option(names = {"--out"}, required = true, description = "Output directory for the whole pipeline.")
        String out;

        @Option(names = {"--details"}, defaultValue = "false", description = "Print broken rules when UNSAT.")
        boolean details;

        @Option(names = {"--recore"}, description = "Path to .recore file (optional).")
        String recore;

        @Option(names = {"--summary"}, defaultValue = "false", description = "Print java-metamodel summary.")
        boolean summary;

        @Option(names = {"--maven-quiet"}, defaultValue = "true", description = "Run mvn with -q inside submodules.")
        boolean mavenQuiet;

        @picocli.CommandLine.Mixin
        CommonOptions common;

        @Override
        public Integer call() throws Exception {
            boolean convertPaths = common.convertWindowsPaths();
            Path baseDir = Path.of("").toAbsolutePath();

            Path outBaseDirAbs = PathUtils.resolveAgainst(baseDir, convertPaths, out);
            Path sourceAbs = PathUtils.resolveAgainst(baseDir, convertPaths, source);

            if (!isMultiRepoMode(mode)) {
                String repoName = repoNameFromPath(sourceAbs);
                Path outDirAbs = ensureRepoOutDir(outBaseDirAbs, repoName);
                Files.createDirectories(outDirAbs);

                Path spoonJson = outDirAbs.resolve("spoon.json");
                Path verifierOut = outDirAbs.resolve("verifier");
                Path verifierReport = verifierOut.resolve("report.json");

                // 1) extract
                Extract extract = new Extract();
                extract.source = sourceAbs.toString();
                extract.mode = "single";
                extract.out = outBaseDirAbs.toString();
                extract.jsonName = "spoon.json";
                extract.summary = summary;
                extract.mavenQuiet = mavenQuiet;
                extract.common = common;

                int e = extract.call();
                if (e != 0) {
                    return e;
                }

                // 2) verify
                Verify verify = new Verify();
                verify.instance = spoonJson.toString();
                verify.out = verifierOut.toString();
                verify.details = details;
                verify.report = verifierReport.toString();
                verify.recore = recore;
                verify.mavenQuiet = mavenQuiet;
                verify.common = common;

                int v = verify.call();
                if (v != 0) {
                    return v;
                }

                System.out.println("\n[SUCCESS] Pipeline complete.");
                System.out.println("  Repo: " + repoName);
                System.out.println("  SPOON JSON: " + spoonJson);
                System.out.println("  Verifier out: " + verifierOut);
                System.out.println("  Report: " + verifierReport);
                return 0;
            }

            // Multi-repo mode: run the whole pipeline per repo.
            if (!Files.isDirectory(sourceAbs)) {
                System.err.println("[ERROR] In multi-repo mode, --source must be a directory containing repos: " + sourceAbs);
                return 2;
            }
            Files.createDirectories(outBaseDirAbs);

            List<Path> repos;
            try (var s = Files.list(sourceAbs)) {
                repos = s.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(p -> p.getFileName() != null ? p.getFileName().toString() : ""))
                        .toList();
            }

            int failures = 0;
            for (Path repoDir : repos) {
                String repoName = repoNameFromPath(repoDir);
                Path outDirAbs = ensureRepoOutDir(outBaseDirAbs, repoName);
                Files.createDirectories(outDirAbs);

                Path spoonJson = outDirAbs.resolve("spoon.json");
                Path verifierOut = outDirAbs.resolve("verifier");
                Path verifierReport = verifierOut.resolve("report.json");

                System.out.println("\n==============================");
                System.out.println("[REPO] " + repoName);
                System.out.println("  Source: " + repoDir);
                System.out.println("  Out:    " + outDirAbs);
                System.out.println("==============================");

                Extract extract = new Extract();
                extract.source = repoDir.toString();
                extract.mode = "single";
                extract.out = outBaseDirAbs.toString();
                extract.jsonName = "spoon.json";
                extract.summary = summary;
                extract.mavenQuiet = mavenQuiet;
                extract.common = common;

                int e = extract.call();
                if (e != 0) {
                    failures++;
                    continue;
                }

                Verify verify = new Verify();
                verify.instance = spoonJson.toString();
                verify.out = verifierOut.toString();
                verify.details = details;
                verify.report = verifierReport.toString();
                verify.recore = recore;
                verify.mavenQuiet = mavenQuiet;
                verify.common = common;

                int v = verify.call();
                if (v != 0) {
                    failures++;
                }
            }

            return failures == 0 ? 0 : 1;
        }
    }

    @Command(name = "gui", mixinStandardHelpOptions = true,
            description = "Launch a simple GUI for running the pipeline.")
    static class Gui implements Callable<Integer> {

        @Option(names = {"--default-out"}, description = "Default output directory shown in the GUI.")
        String defaultOut;

        @Option(names = {"--default-source"}, description = "Default source directory shown in the GUI.")
        String defaultSource;

        @picocli.CommandLine.Mixin
        CommonOptions common;

        @Override
        public Integer call() throws Exception {
            PipelineGui.launchAndWait(common, defaultSource, defaultOut);
            return 0;
        }
    }
}
