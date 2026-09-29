package com.jobtracker.careerflow.database;

import java.sql.ResultSet;
import java.sql.Timestamp;

public final class DatabaseTime {
    private DatabaseTime() {}

    public static String instant(ResultSet result, String column) throws Exception {
        Timestamp value = result.getTimestamp(column);
        return value == null ? "" : value.toInstant().toString();
    }
}
