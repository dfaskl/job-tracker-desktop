package com.jobtracker.migrationpoc.backup;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BackupDocumentValidator {
    private final ObjectMapper objectMapper;

    public BackupDocumentValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BackupSummary validate(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
        if (!root.isObject()) throw new InvalidBackupException("备份内容不是 JSON 对象");
        JsonNode applications = root.path("applications");
        JsonNode events = root.path("events");
        if (!applications.isArray() || !events.isArray()) {
            throw new InvalidBackupException("备份缺少 applications 或 events 数组");
        }
        return new BackupSummary(applications.size(), events.size());
    }

    public record BackupSummary(int applicationCount, int eventCount) {}

    public static class InvalidBackupException extends RuntimeException {
        public InvalidBackupException(String message) { super(message); }
    }
}
