package com.pipeline.orchestrator;

import java.nio.file.Files;
import java.nio.file.Path;

final class PlatformDetector {
    private PlatformDetector() {
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    static boolean isLinuxEnvOnWindowsHost() {
        // Used only for drive-letter path convenience conversions.
        // On Linux hosted by Windows, the kernel release typically contains 'microsoft'.
        if (isWindows()) {
            return false;
        }
        try {
            String osrelease = Files.readString(Path.of("/proc/sys/kernel/osrelease")).toLowerCase();
            return osrelease.contains("microsoft");
        } catch (Exception ignored) {
            return false;
        }
    }

    static Platform effective(Platform requested) {
        if (requested == null || requested == Platform.AUTO) {
            if (isWindows()) {
                return Platform.WINDOWS;
            }
            return Platform.UNIX;
        }
        return requested;
    }
}
