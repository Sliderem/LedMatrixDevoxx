package com.devoxx.led.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal {@code .env} loader. Reads simple {@code KEY=VALUE} lines from a
 * {@code .env} file in the working directory, ignoring blank lines and
 * {@code #} comments. Real OS environment variables take precedence over
 * values from the file, so a key can always be overridden at runtime.
 *
 * <p>No external dependencies; values are not unquoted beyond trimming
 * surrounding whitespace and an optional pair of wrapping quotes.</p>
 */
public final class DotEnv {

    private final Map<String, String> values = new HashMap<>();

    private DotEnv(Map<String, String> values) {
        this.values.putAll(values);
    }

    /** Load from {@code ./.env} if present; missing file yields an empty env. */
    public static DotEnv load() {
        return load(Path.of(".env"));
    }

    public static DotEnv load(Path file) {
        Map<String, String> map = new HashMap<>();
        if (Files.isRegularFile(file)) {
            try {
                for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    value = stripQuotes(value);
                    map.put(key, value);
                }
            } catch (IOException ignored) {
                // Treat an unreadable .env as absent.
            }
        }
        return new DotEnv(map);
    }

    /**
     * Resolve a key. OS environment variable wins; otherwise the {@code .env}
     * value; otherwise {@code null}.
     */
    public String get(String key) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String v = values.get(key);
        return (v != null && !v.isBlank()) ? v : null;
    }

    public String getOrDefault(String key, String fallback) {
        String v = get(key);
        return v != null ? v : fallback;
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
