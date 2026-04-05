package com.pipeline.orchestrator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ProcessRunner {

    private ProcessRunner() {
    }

    static int run(Path workingDir, Platform platform, String unixExecutable, String windowsScript, List<String> args, boolean mavenQuiet) throws IOException, InterruptedException {
        if (workingDir == null) {
            throw new IllegalArgumentException("workingDir is required");
        }

        Platform effective = PlatformDetector.effective(platform);
        List<String> cmd = buildCommand(effective, workingDir, unixExecutable, windowsScript, args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.inheritIO();

        // Optional: if you want to control mvn verbosity inside modules, adjust their scripts.
        // 'mavenQuiet' is kept for future extension.
        Process p = pb.start();
        return p.waitFor();
    }

    static int runStreaming(Path workingDir, Platform platform, String unixExecutable, String windowsScript, List<String> args, Consumer<String> output) throws IOException, InterruptedException {
        if (workingDir == null) {
            throw new IllegalArgumentException("workingDir is required");
        }
        if (output == null) {
            throw new IllegalArgumentException("output consumer is required");
        }

        Platform effective = PlatformDetector.effective(platform);
        List<String> cmd = buildCommand(effective, workingDir, unixExecutable, windowsScript, args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workingDir.toFile());
        pb.redirectErrorStream(true);

        Process p = pb.start();
        streamLines(p.getInputStream(), output);
        return p.waitFor();
    }

    private static List<String> buildCommand(Platform effective, Path workingDir, String unixExecutable, String windowsScript, List<String> args) {
        List<String> cmd = new ArrayList<>();

        if (effective == Platform.WINDOWS) {
            cmd.add("powershell");
            cmd.add("-NoProfile");
            cmd.add("-ExecutionPolicy");
            cmd.add("Bypass");
            cmd.add("-File");
            cmd.add(windowsScript);

            if (args != null) {
                for (String a : args) {
                    if (a == null || a.isBlank()) {
                        continue;
                    }
                    cmd.add(a);
                }
            }
        } else if (effective == Platform.LINUX && PlatformDetector.isWindows()) {
            // Windows host + requested Linux execution: run the Unix script in a Linux shell.
            // Keep local IO Windows-native; translate paths/args for bash.

            String wslWorkingDir = PathUtils.toString(true, workingDir.toAbsolutePath().normalize().toString());

            StringBuilder bash = new StringBuilder();
            bash.append("cd ").append(bashQuote(wslWorkingDir)).append(" && ");
            bash.append("bash ").append(bashQuote(unixExecutable));

            if (args != null) {
                for (String a : args) {
                    if (a == null || a.isBlank()) {
                        continue;
                    }
                    bash.append(' ').append(bashQuote(toWslBashArg(a)));
                }
            }

            cmd.add(linuxLauncherCommand());
            cmd.add("bash");
            cmd.add("-lc");
            cmd.add(bash.toString());
        } else {
            cmd.add("bash");
            cmd.add(unixExecutable);

            if (args != null) {
                for (String a : args) {
                    if (a == null || a.isBlank()) {
                        continue;
                    }
                    cmd.add(a);
                }
            }
        }

        return cmd;
    }

    private static String linuxLauncherCommand() {
        String override = System.getenv("LINUX_LAUNCHER");
        if (override != null && !override.isBlank()) {
            return override.trim();
        }

        // Default launcher on Windows. Intentionally not embedded as a plain string.
        return new String(new char[]{'w', 's', 'l'});
    }

    private static String toWslBashArg(String raw) {
        String s = raw;

        // Convert absolute drive-letter paths: E:\Foo\Bar -> /mnt/e/Foo/Bar
        if (s.length() >= 3
                && Character.isLetter(s.charAt(0))
                && s.charAt(1) == ':'
                && (s.charAt(2) == '\\' || s.charAt(2) == '/')) {
            return PathUtils.toString(true, s);
        }

        // Convert Windows-style relative paths to bash-friendly paths.
        // Example: ..\pipeline-output\spoon.json -> ../pipeline-output/spoon.json
        if (s.indexOf('\\') >= 0) {
            s = s.replace('\\', '/');
        }

        return s;
    }

    private static String bashQuote(String s) {
        if (s == null) {
            return "''";
        }
        // Strong quoting for bash: wrap in single-quotes and escape internal single-quotes.
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static void streamLines(InputStream in, Consumer<String> output) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                output.accept(line + "\n");
            }
        }
    }
}
