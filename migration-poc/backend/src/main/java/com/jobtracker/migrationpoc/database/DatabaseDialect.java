package com.jobtracker.migrationpoc.database;

/** Minimal SQL compatibility layer while PostgreSQL remains available as a migration source. */
public final class DatabaseDialect {
    private static final String SQLITE_NOW = "(CAST(strftime('%s','now') AS INTEGER) * 1000)";

    private DatabaseDialect() {}

    public static String rewrite(LegacyDatabaseUrl database, String sql) {
        if (!database.isSqlite() || sql == null || sql.isBlank()) return sql;
        String value = sql;
        value = value.replace("NOW()+(? || ' seconds')::interval", SQLITE_NOW + "+(CAST(? AS INTEGER)*1000)");
        value = value.replace("to_jsonb(CAST(? AS boolean))", "CASE WHEN ? THEN 'true' ELSE 'false' END");
        value = value.replace("to_jsonb(CAST(? AS text))", "json_quote(?)");
        value = value.replace("value #>> '{}'", "json_extract(value,'$')");
        value = value.replace("jsonb_typeof", "json_type");
        value = value.replace("jsonb_array_length", "json_array_length");
        value = value.replace("octet_length", "length");
        value = value.replace("ORDER BY created_at DESC,id DESC OFFSET 29", "ORDER BY created_at DESC,id DESC LIMIT -1 OFFSET 29");
        value = value.replaceAll("(?i)\\s+FOR\\s+UPDATE(?:\\s+OF\\s+\\w+)?", "");
        value = value.replaceAll("(?i)::jsonb", "");
        value = value.replaceAll("(?i)::text", "");
        value = value.replaceAll("(?i)\\bNOW\\(\\)", SQLITE_NOW);
        return value;
    }
}
