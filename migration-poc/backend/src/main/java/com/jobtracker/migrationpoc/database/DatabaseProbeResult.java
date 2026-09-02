package com.jobtracker.migrationpoc.database;

import java.util.List;

public record DatabaseProbeResult(
    boolean configured,
    boolean connected,
    boolean schemaCompatible,
    List<String> missingTables,
    boolean samplePresent,
    boolean businessJsonCompatible,
    String message
) {
    public static DatabaseProbeResult notConfigured() {
        return new DatabaseProbeResult(false, false, false, List.of(), false, false, "DATABASE_URL 未配置");
    }

    public static DatabaseProbeResult failed(String message) {
        return new DatabaseProbeResult(true, false, false, List.of(), false, false, message);
    }
}
