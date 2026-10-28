package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.database.AiService.CompanyIdentity;
import com.jobtracker.careerflow.database.AiService.CompanyPosition;
import com.jobtracker.careerflow.database.BackupService.CompanyLinks;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator.ApplicationView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class CompanyIndustryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanyIndustryService.class);
    private static final int CLASSIFICATION_BATCH_SIZE = 60;
    private static final int SALARY_ESTIMATE_BATCH_SIZE = 10;
    private final ApplicationService applications;
    private final BackupService backup;
    private final AiService ai;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();

    public CompanyIndustryService(ApplicationService applications, BackupService backup, AiService ai) {
        this.applications = applications;
        this.backup = backup;
        this.ai = ai;
    }

    public CompanyLinks prepareAndQueue(String email) throws Exception {
        List<String> companies;
        List<CompanyPosition> positions;
        try {
            List<ApplicationView> current = applications.findApplications(email).applications();
            companies = current.stream().map(ApplicationView::company).filter(value -> value != null && !value.isBlank()).distinct().toList();
            positions = current.stream().map(item -> new CompanyPosition(item.company(), item.position()))
                .filter(item -> item.company() != null && !item.company().isBlank() && item.position() != null && !item.position().isBlank())
                .distinct().toList();
        } catch (ApplicationService.SandboxDataNotFoundException missingCopy) {
            companies = List.of();
            positions = List.of();
        }
        CompanyLinks links = backup.ensureCompanyEntries(email, companies);
        links = backup.ensureSalaryEntries(email, positions);
        queue(email, links.items());
        return links;
    }

    public void enqueueAfterApplicationWrite(String email, String company, String position) {
        if (company == null || company.isBlank()) return;
        try {
            CompanyLinks links = backup.ensureCompanyEntries(email, List.of(company));
            queue(email, links.items());
        } catch (Exception exception) {
            LOGGER.warn("company industry task enqueue failed for user {}", safeEmail(email), exception);
        }
    }

    private void queue(String email, JsonNode items) {
        if (email == null || email.isBlank() || !activeUsers.add(key(email))) return;
        try {
            executor.execute(() -> {
                try {
                    JsonNode fresh = backup.companyLinks(email).items();
                    classifyPending(email, fresh);
                    processSalaryEstimates(email);
                }
                catch (Exception exception) {
                    try { backup.saveIndustryResults(email, Map.of(), exception.getMessage()); }
                    catch (Exception persistenceFailure) { exception.addSuppressed(persistenceFailure); }
                    LOGGER.warn("company industry classification failed for user {}", safeEmail(email), exception);
                } finally { activeUsers.remove(key(email)); }
            });
        } catch (RuntimeException rejected) {
            activeUsers.remove(key(email));
            LOGGER.warn("company industry task could not be queued", rejected);
        }
    }

    private void classifyPending(String email, JsonNode source) throws Exception {
        if (!ai.companyIndustryClassificationAvailable(email)) return;
        JsonNode current = source;
        while (true) {
            List<CompanyIdentity> pending = pending(current);
            if (pending.isEmpty()) return;
            List<CompanyIdentity> batch = pending.subList(0, Math.min(CLASSIFICATION_BATCH_SIZE, pending.size()));
            Map<String, String> industries = ai.classifyCompanyIndustries(email, batch);
            current = backup.saveIndustryResults(email, industries, "").items();
            if (pending.size() > CLASSIFICATION_BATCH_SIZE) Thread.sleep(3_100);
        }
    }

    private void processSalaryEstimates(String email) throws Exception {
        if (!ai.companyIndustryClassificationAvailable(email)) return;
        List<CompanyPosition> applicationsNow;
        try {
            applicationsNow = applications.findApplications(email).applications().stream()
                .map(item -> new CompanyPosition(item.company(), item.position()))
                .filter(item -> item.company() != null && !item.company().isBlank() && item.position() != null && !item.position().isBlank())
                .distinct().toList();
        } catch (ApplicationService.SandboxDataNotFoundException missingCopy) { return; }
        backup.ensureCompanyEntries(email, applicationsNow.stream().map(CompanyPosition::company).distinct().toList());
        JsonNode current = backup.ensureSalaryEntries(email, applicationsNow).items();
        List<CompanyPosition> pending = pendingSalaries(current);
        if (!pending.isEmpty()) Thread.sleep(3_100);
        for (int start = 0; start < pending.size(); start += SALARY_ESTIMATE_BATCH_SIZE) {
            if (start > 0) Thread.sleep(3_100);
            List<CompanyPosition> batch = pending.subList(start, Math.min(start + SALARY_ESTIMATE_BATCH_SIZE, pending.size()));
            try {
                Map<String, String> ranges = ai.estimateSalaryRanges(email, batch);
                current = backup.saveSalaryResults(email, ranges, "").items();
            } catch (Exception exception) {
                String reason = salaryFailureMessage(exception);
                backup.saveSalaryResults(email, Map.of(), reason);
                LOGGER.warn("salary estimate failed for user {}: {}", safeEmail(email), reason, exception);
            }
        }
    }

    private String salaryFailureMessage(Exception exception) {
        String message = exception.getMessage();
        if (message != null && !message.isBlank()) return message.substring(0, Math.min(300, message.length()));
        if (exception instanceof java.net.http.HttpTimeoutException) return "AI 接口请求超时（60 秒）";
        return "AI 估算请求异常（" + exception.getClass().getSimpleName() + "）";
    }

    private List<CompanyPosition> pendingSalaries(JsonNode items) {
        List<CompanyPosition> result = new ArrayList<>();
        if (items == null || !items.isArray()) return result;
        for (JsonNode company : items) {
            if (!"classified".equals(company.path("industryStatus").asText("")) || !company.path("salaryEstimates").isArray()) continue;
            for (JsonNode estimate : company.path("salaryEstimates")) if ("pending".equals(estimate.path("status").asText("")))
                result.add(new CompanyPosition(company.path("company").asText(""), estimate.path("position").asText(""), company.path("industry").asText("")));
        }
        return result;
    }

    private List<CompanyIdentity> pending(JsonNode items) {
        Map<String, CompanyIdentity> distinct = new LinkedHashMap<>();
        if (items == null || !items.isArray()) return List.of();
        for (JsonNode item : items) {
            String company = item.path("company").asText("").trim();
            if (company.isEmpty() || !item.path("industry").asText("").isBlank()
                || !"pending".equals(item.path("industryStatus").asText(""))) continue;
            distinct.putIfAbsent(key(company), new CompanyIdentity(company, item.path("url").asText("")));
        }
        return new ArrayList<>(distinct.values());
    }

    private String key(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s·•・()（）\\[\\]【】._-]+", "")
            .replaceAll("(股份)?有限公司$|有限责任公司$|集团$|公司$", "");
    }

    private String safeEmail(String email) {
        if (email == null) return "unknown";
        int separator = email.indexOf('@');
        return separator < 1 ? "user" : email.substring(0, 1) + "***" + email.substring(separator);
    }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }
}
