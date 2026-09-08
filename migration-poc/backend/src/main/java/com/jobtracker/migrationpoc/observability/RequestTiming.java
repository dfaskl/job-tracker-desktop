package com.jobtracker.migrationpoc.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class RequestTiming {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private RequestTiming() {}

    public static void begin() { CURRENT.set(new State(System.nanoTime())); }
    public static void clear() { CURRENT.remove(); }

    public static void record(String name, long elapsedNanos) {
        State state = CURRENT.get();
        if (state == null || elapsedNanos < 0) return;
        state.durations.merge(name, elapsedNanos, Long::sum);
        state.counts.merge(name, 1, Integer::sum);
    }

    public static String serverTiming() {
        State state = CURRENT.get();
        if (state == null) return "";
        long total = Math.max(0, System.nanoTime() - state.startedAt);
        long measured = state.durations.values().stream().mapToLong(Long::longValue).sum();
        long app = Math.max(0, total - measured);
        StringBuilder value = new StringBuilder();
        append(value, "db", state.durations.getOrDefault("db", 0L), state.counts.getOrDefault("db", 0));
        append(value, "sql", state.durations.getOrDefault("sql", 0L), state.counts.getOrDefault("sql", 0));
        append(value, "ai", state.durations.getOrDefault("ai", 0L), state.counts.getOrDefault("ai", 0));
        append(value, "app", app, 0);
        append(value, "total", total, 0);
        return value.toString();
    }

    private static void append(StringBuilder target, String name, long nanos, int count) {
        if (!target.isEmpty()) target.append(", ");
        target.append(name).append(";dur=").append(String.format(Locale.ROOT, "%.2f", nanos / 1_000_000d));
        if (count > 0) target.append(";desc=\"").append(count).append(count == 1 ? " call" : " calls").append("\"");
    }

    private static final class State {
        private final long startedAt;
        private final Map<String, Long> durations = new LinkedHashMap<>();
        private final Map<String, Integer> counts = new LinkedHashMap<>();
        private State(long startedAt) { this.startedAt = startedAt; }
    }
}