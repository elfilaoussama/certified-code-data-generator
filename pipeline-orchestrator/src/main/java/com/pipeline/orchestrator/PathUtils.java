package com.pipeline.orchestrator;

import java.nio.file.Path;

final class PathUtils {
    private PathUtils() {
    }

    static Path toPath(boolean linux, String raw) {
        if (raw == null) {
            return null;
        }
        return Path.of(toString(linux, raw));
    }

    static Path resolveAgainst(Path cwd, boolean linux, String raw) {
        if (cwd == null) {
            throw new IllegalArgumentException("cwd is required");
        }
        if (raw == null) {
            return null;
        }

        String normalized = toString(linux, raw);
        Path p = Path.of(normalized);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        return cwd.resolve(p).normalize();
    }

    static String toString(boolean linux, String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (!linux) {
            return trimmed;
        }

        // Best-effort Windows path conversion for convenience:
        //   E:\Foo\Bar -> /mnt/e/Foo/Bar
        //   E:/Foo/Bar  -> /mnt/e/Foo/Bar
        if (trimmed.length() >= 3 && Character.isLetter(trimmed.charAt(0)) && trimmed.charAt(1) == ':'
                && (trimmed.charAt(2) == '\\' || trimmed.charAt(2) == '/')) {
            char drive = Character.toLowerCase(trimmed.charAt(0));
            String rest = trimmed.substring(2);
            rest = rest.replace('\\', '/');
            if (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            return "/mnt/" + drive + "/" + rest;
        }

        // If it already looks like a Linux path, keep it.
        return trimmed;
    }

    static String toModuleArg(Path moduleDir, boolean linux, String rawPath) {
        if (rawPath == null) {
            return null;
        }

        String normalized = toString(linux, rawPath);
        Path p = Path.of(normalized);
        if (!p.isAbsolute()) {
            return normalized;
        }

        try {
            Path base = moduleDir.toAbsolutePath().normalize();
            Path abs = p.toAbsolutePath().normalize();
            return base.relativize(abs).toString();
        } catch (IllegalArgumentException ex) {
            // Different roots/filesystems; keep absolute.
            return normalized;
        }
    }
}
