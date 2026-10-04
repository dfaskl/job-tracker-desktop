package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.database.AiService.AiResponseException;
import com.jobtracker.careerflow.database.AiService.AiRateLimitException;
import com.jobtracker.careerflow.database.AiService.AiValidationException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Component
public class InterviewWorkbenchService {
    private static final String SUMMARY_VERSION = "importance-prioritized-review-v10-batched";
    private final Environment environment;
    private final ApplicationService applications;
    private final ObjectMapper mapper;
    private final AiService ai;
    private final ConcurrentMap<String, ReentrantLock> summaryLocks = new ConcurrentHashMap<>();

    public InterviewWorkbenchService(Environment environment, ApplicationService applications, ObjectMapper mapper, AiService ai) {
        this.environment = environment;
        this.applications = applications;
        this.mapper = mapper;
        this.ai = ai;
    }

    public ObjectNode state(String email) throws Exception {
        try (Connection connection = open()) {
            Document document = read(connection, email, false);
            ObjectNode result = workbench(document.root()).deepCopy();
            result.remove("classification");
            result.remove("summaries");
            ArrayNode allReviews = reviews(document.root());
            String currentKey = sourceKey(allReviews);
            result.put("sourceKey", currentKey);
            if (result.path("overallSummary") instanceof ObjectNode summary) {
                String fingerprint = summaryFingerprint(summaryInput(allReviews), result.path("resume"));
                summary.put("stale", !fingerprint.equals(summary.path("sourceKey").asText("")));
            }
            return result;
        }
    }

    public ObjectNode saveResume(String email, JsonNode input) throws Exception {
        ObjectNode resume = cleanResume(input);
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Document document = read(connection, email, true);
                ObjectNode workbench = workbench(document.root());
                workbench.remove("classification");
                workbench.remove("summaries");
                workbench.set("resume", resume);
                write(connection, document);
                connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
        return state(email);
    }

    public ObjectNode summarize(String email) throws Exception {
        return summarizeStreaming(email, ignored -> { });
    }

    public ObjectNode summarizeStreaming(String email, Consumer<ObjectNode> onQuestion) throws Exception {
        return summarizeStreaming(email, onQuestion, false);
    }

    public ObjectNode summarizeStreaming(String email, Consumer<ObjectNode> onQuestion, boolean force) throws Exception {
        String lockKey = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        ReentrantLock lock = summaryLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        lock.lock();
        try { return summarizeStreamingLocked(email, onQuestion, force); }
        finally { lock.unlock(); }
    }

    private ObjectNode summarizeStreamingLocked(String email, Consumer<ObjectNode> onQuestion, boolean force) throws Exception {
        Document snapshot;
        try (Connection connection = open()) { snapshot = read(connection, email, false); }
        ObjectNode workbench = workbench(snapshot.root());
        ArrayNode selected = summaryInput(reviews(snapshot.root()));
        if (selected.isEmpty()) throw new AiValidationException("请先在已完成的日程中记录面试回顾");
        JsonNode resume = workbench.path("resume");
        String summaryKey = summaryFingerprint(selected, resume);
        boolean resumableJob = !force && workbench.path("summaryJob") instanceof ObjectNode savedJob
            && summaryKey.equals(savedJob.path("sourceKey").asText());
        ObjectNode job = resumableJob && workbench.path("summaryJob") instanceof ObjectNode saved
            && summaryKey.equals(saved.path("sourceKey").asText()) ? saved.deepCopy() : newSummaryJob(summaryKey);
        ArrayNode classificationBatches = (ArrayNode) job.path("classificationBatches");
        ArrayNode reviewBatches = reviewBatches(selected, 12_000);
        if (classificationBatches.size() != reviewBatches.size()) {
            classificationBatches.removeAll();
            for (int i = 0; i < reviewBatches.size(); i++) classificationBatches.addObject().put("status", "pending");
        }
        persistJob(email, summaryKey, job, null);
        for (int index = 0; index < reviewBatches.size(); index++) {
            ObjectNode batchState = (ObjectNode) classificationBatches.get(index);
            if ("completed".equals(batchState.path("status").asText())) continue;
            onQuestion.accept(progress("正在识别面试回顾（第 " + (index + 1) + "/" + reviewBatches.size() + " 批）…"));
            batchState.put("status", "running"); persistJob(email, summaryKey, job, null);
            JsonNode candidate = classifyWithRateLimitRetry(email, reviewBatches.get(index), resume);
            if (!candidate.path("topics").isArray()) throw new AiResponseException("AI 未返回第 " + (index + 1) + " 批问题分类");
            batchState.set("topics", candidate.path("topics").deepCopy()); batchState.put("status", "completed");
            persistJob(email, summaryKey, job, null);
        }

        ObjectNode summary;
        JsonNode previousSummary = workbench.path("overallSummary");
        if (resumableJob && previousSummary instanceof ObjectNode savedSummary && summaryKey.equals(savedSummary.path("sourceKey").asText())
            && savedSummary.path("topics").isArray()) summary = savedSummary.deepCopy();
        else summary = mergeClassifications(classificationBatches, resume);
        summary.put("sourceKey", summaryKey).put("stale", false);
        for (JsonNode batch : classificationBatches) if (batch instanceof ObjectNode batchObject) batchObject.remove("topics");
        job.put("stage", "answers");
        persistJob(email, summaryKey, job, summary);
        ObjectNode classifiedEvent = mapper.createObjectNode().put("type", "classified");
        classifiedEvent.set("summary", summary.deepCopy()); onQuestion.accept(classifiedEvent);
        ArrayNode topics = (ArrayNode) summary.path("topics");
        int totalBatches = countAnswerBatches(topics);
        int batchNumber = 0;
        for (JsonNode topicNode : topics) {
            ObjectNode topic = (ObjectNode) topicNode;
            ArrayNode answers = (ArrayNode) topic.path("questionAnswers");
            ArrayNode pending = mapper.createArrayNode();
            for (JsonNode item : answers) if (item.path("answer").asText("").isBlank()) pending.add(item.deepCopy());
            for (int from = 0; from < pending.size(); from += 3) {
                ArrayNode batch = mapper.createArrayNode();
                for (int i = from; i < Math.min(from + 3, pending.size()); i++) batch.add(pending.get(i).deepCopy());
                batchNumber++;
                onQuestion.accept(progress("正在生成「" + topic.path("name").asText() + "」的学习讲解（第 " + batchNumber + "/" + totalBatches + " 批）…"));
                for (JsonNode item : batch) setAnswerStatus(answers, item.path("question").asText(), "running");
                persistJob(email, summaryKey, job, summary);
                String resumeRef = topic.path("resumeRef").asText("");
                JsonNode relevantResume = resumeForReference(resume, resumeRef);
                IncrementalInterviewJsonParser parser = new IncrementalInterviewJsonParser(mapper, node -> {
                    ObjectNode event = mapper.createObjectNode().put("type", "question")
                        .put("question", node.path("question").asText()).put("answer", node.path("answer").asText())
                        .put("topic", topic.path("name").asText());
                    onQuestion.accept(event);
                });
                JsonNode result = streamAnswersWithRateLimitRetry(email, topic, batch, relevantResume, parser::accept);
                JsonNode generated = result.path("questionAnswers");
                if (!generated.isArray()) throw new AiResponseException("AI 未返回完整的问题回答");
                for (JsonNode item : batch) {
                    JsonNode answer = findAnswer(generated, item.path("question").asText());
                    String answerText = answer == null ? "" : answer.path("answer").asText("").trim();
                    if (answerText.isBlank()) throw new AiResponseException("AI 未完成问题「" + item.path("question").asText() + "」的回答");
                    updateAnswer(answers, item.path("question").asText(), answerText);
                }
                persistJob(email, summaryKey, job, summary);
            }
        }
        job.put("stage", "completed"); job.put("completedAt", java.time.Instant.now().toString());
        persistJob(email, summaryKey, job, summary);
        return state(email);
    }

    private ArrayNode reviewBatches(ArrayNode reviews, int maximumChars) {
        ArrayNode batches = mapper.createArrayNode(); ArrayNode batch = mapper.createArrayNode(); int size = 0;
        for (JsonNode review : reviews) {
            String[] lines = review.path("questions").asText("").split("\\R");
            StringBuilder chunkText = new StringBuilder(); int questionCount = 0;
            for (String rawLine : lines) {
                String line = rawLine.trim(); if (line.isEmpty()) continue;
                if (questionCount >= 8 || chunkText.length() + line.length() > 6_000) {
                    if (!chunkText.isEmpty()) {
                        ObjectNode piece = reviewPiece(review, chunkText.toString());
                        int itemSize = piece.toString().length();
                        if (!batch.isEmpty() && size + itemSize > maximumChars) { batches.add(batch); batch = mapper.createArrayNode(); size = 0; }
                        batch.add(piece); size += itemSize;
                    }
                    chunkText.setLength(0); questionCount = 0;
                }
                if (!chunkText.isEmpty()) chunkText.append('\n');
                chunkText.append(line); questionCount++;
            }
            if (!chunkText.isEmpty()) {
                ObjectNode piece = reviewPiece(review, chunkText.toString());
                int itemSize = piece.toString().length();
                if (!batch.isEmpty() && size + itemSize > maximumChars) { batches.add(batch); batch = mapper.createArrayNode(); size = 0; }
                batch.add(piece); size += itemSize;
            }
        }
        if (!batch.isEmpty()) batches.add(batch);
        return batches;
    }

    private ObjectNode reviewPiece(JsonNode review, String questions) {
        ObjectNode piece = mapper.createObjectNode();
        for (String field : new String[]{"eventId", "applicationId", "company", "position", "title"})
            piece.put(field, review.path(field).asText(""));
        piece.put("questions", questions); return piece;
    }

    private ObjectNode newSummaryJob(String key) {
        ObjectNode job = mapper.createObjectNode().put("sourceKey", key).put("stage", "classification");
        job.putArray("classificationBatches"); return job;
    }

    private ObjectNode progress(String message) { return mapper.createObjectNode().put("type", "progress").put("message", message); }

    private JsonNode classifyWithRateLimitRetry(String email, JsonNode reviews, JsonNode resume) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try { return ai.classifyInterviewReviewBatch(email, reviews, resume); }
            catch (AiRateLimitException exception) {
                if (attempt >= 3) throw exception;
                Thread.sleep(3_100L);
            }
        }
    }

    private JsonNode streamAnswersWithRateLimitRetry(String email, JsonNode topic, JsonNode questions,
                                                       JsonNode resume, Consumer<String> onChunk) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try { return ai.streamInterviewAnswerBatch(email, topic, questions, resume, onChunk); }
            catch (AiRateLimitException exception) {
                if (attempt >= 3) throw exception;
                Thread.sleep(3_100L);
            }
        }
    }

    private void persistJob(String email, String key, ObjectNode job, ObjectNode summary) throws Exception {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Document current = read(connection, email, true); ObjectNode wb = workbench(current.root());
                if (!key.equals(summaryFingerprint(summaryInput(reviews(current.root())), wb.path("resume"))))
                    throw new WorkbenchConflictException("面试回顾或简历已更新，请重新汇总");
                wb.set("summaryJob", job.deepCopy());
                if (summary != null) wb.set("overallSummary", summary.deepCopy());
                write(connection, current); connection.commit();
            } catch (Exception exception) { connection.rollback(); throw exception; }
        }
    }

    private ObjectNode mergeClassifications(ArrayNode batches, JsonNode resume) {
        ObjectNode merged = mapper.createObjectNode(); ArrayNode topics = merged.putArray("topics");
        Map<String, ObjectNode> byKey = new LinkedHashMap<>();
        for (int b = 0; b < batches.size(); b++) for (JsonNode candidate : batches.get(b).path("topics")) {
            String kind = candidate.path("kind").asText("other");
            if (!Set.of("project", "knowledge", "other").contains(kind)) kind = "other";
            String ref = candidate.path("resumeRef").asText("");
            if (kind.equals("project") && !validResumeReference(resume, ref)) continue;
            String name = kind.equals("project") ? resumeName(resume, ref) : limit(candidate.path("name").asText("其他问题").trim(), 100);
            String key = kind + ":" + (kind.equals("project") ? ref : normalizeTopic(name));
            final String topicKind = kind, topicRef = ref, topicName = name;
            ObjectNode topic = byKey.computeIfAbsent(key, ignored -> {
                ObjectNode created = mapper.createObjectNode().put("name", topicName).put("count", 0).put("kind", topicKind)
                    .put("resumeRef", topicKind.equals("project") ? topicRef : "").put("summary", "");
                created.putArray("questionAnswers"); topics.add(created); return created;
            });
            ArrayNode questionAnswers = (ArrayNode) topic.path("questionAnswers");
            for (JsonNode question : candidate.path("questions")) {
                String text = limit(question.path("question").asText("").trim(), 500); if (text.isEmpty()) continue;
                JsonNode existing = findAnswer(questionAnswers, text);
                String eventId = question.path("eventId").asText("");
                if (existing instanceof ObjectNode existingQuestion) {
                    existingQuestion.put("frequency", existing.path("frequency").asInt(1) + 1);
                    if (!eventId.isBlank() && existingQuestion.path("sourceEventIds") instanceof ArrayNode sources) {
                        boolean seen = false; for (JsonNode sourceId : sources) if (sourceId.asText().equals(eventId)) { seen = true; break; }
                        if (!seen) sources.add(eventId);
                    }
                    topic.put("count", topic.path("count").asInt() + 1);
                    continue;
                }
                ObjectNode item = questionAnswers.addObject().put("question", text).put("answer", "")
                    .put("answerStatus", "pending").put("frequency", 1).put("eventId", eventId);
                ArrayNode sourceIds = item.putArray("sourceEventIds"); if (!eventId.isBlank()) sourceIds.add(eventId);
                topic.put("count", topic.path("count").asInt() + 1);
            }
            String description = candidate.path("summary").asText("").trim();
            if (!description.isEmpty()) topic.put("summary", limit(description, 500));
        }
        // Project directories always mirror the user's resume, including experiences with no matching questions.
        Map<String, ObjectNode> resumeTopics = new LinkedHashMap<>();
        addResumeTopics(resume.path("internships"), "internship-", "internship", resumeTopics);
        addResumeTopics(resume.path("projects"), "project-", "project", resumeTopics);
        for (Map.Entry<String, ObjectNode> entry : resumeTopics.entrySet()) {
            String key = "project:" + entry.getKey();
            if (!byKey.containsKey(key)) topics.add(entry.getValue());
        }
        if (topics.isEmpty()) throw new AiResponseException("没有识别出可整理的问题");
        var sortedTopics = new java.util.ArrayList<JsonNode>();
        for (JsonNode topic : topics) sortedTopics.add(topic);
        sortedTopics.sort((left, right) -> Integer.compare(right.path("count").asInt(), left.path("count").asInt()));
        topics.removeAll(); for (JsonNode topic : sortedTopics) topics.add(topic);
        return merged;
    }

    private boolean validResumeReference(JsonNode resume, String reference) {
        return reference.startsWith("internship-") && indexExists(resume.path("internships"), reference, "internship-")
            || reference.startsWith("project-") && indexExists(resume.path("projects"), reference, "project-");
    }
    private boolean indexExists(JsonNode entries, String reference, String prefix) {
        try { int index = Integer.parseInt(reference.substring(prefix.length())) - 1; return entries.isArray() && index >= 0 && index < entries.size(); }
        catch (NumberFormatException exception) { return false; }
    }
    private String resumeName(JsonNode resume, String reference) {
        boolean internship = reference.startsWith("internship-"); JsonNode entries = resume.path(internship ? "internships" : "projects");
        int index = Integer.parseInt(reference.substring(internship ? 11 : 8)) - 1; JsonNode item = entries.path(index);
        if (!internship) return limit(item.path("name").asText("项目经历 " + (index + 1)), 100);
        String company = item.path("company").asText(""); String role = item.path("role").asText("");
        String value = String.join(" · ", java.util.stream.Stream.of(company, role).filter(v -> !v.isBlank()).toList());
        return limit(value.isBlank() ? "实习经历 " + (index + 1) : value + "（实习）", 100);
    }
    private String normalizeTopic(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[\\s，。、“”‘’：:;；、/_-]+", ""); }
    private JsonNode resumeForReference(JsonNode resume, String reference) {
        if (!validResumeReference(resume, reference)) return mapper.createObjectNode();
        boolean internship = reference.startsWith("internship-"); int index = Integer.parseInt(reference.substring(internship ? 11 : 8)) - 1;
        ObjectNode result = mapper.createObjectNode(); result.set(internship ? "internship" : "project", resume.path(internship ? "internships" : "projects").get(index).deepCopy());
        return result;
    }
    private int countAnswerBatches(ArrayNode topics) {
        int count = 0; for (JsonNode topic : topics) { int pending = 0; for (JsonNode q : topic.path("questionAnswers")) if (q.path("answer").asText("").isBlank()) pending++; count += (pending + 2) / 3; }
        return Math.max(1, count);
    }
    private JsonNode findAnswer(JsonNode answers, String question) {
        if (!answers.isArray()) return null;
        for (JsonNode item : answers) if (item.path("question").asText("").equalsIgnoreCase(question)) return item;
        return null;
    }
    private void updateAnswer(ArrayNode answers, String question, String answer) {
        for (JsonNode item : answers) if (item instanceof ObjectNode object && item.path("question").asText("").equalsIgnoreCase(question)) { object.put("answer", limit(answer.trim(), 5_000)); object.put("answerStatus", "completed"); return; }
    }
    private void setAnswerStatus(ArrayNode answers, String question, String status) {
        for (JsonNode item : answers) if (item instanceof ObjectNode object && item.path("question").asText("").equalsIgnoreCase(question)) { object.put("answerStatus", status); return; }
    }

    private ObjectNode cleanResume(JsonNode source) {
        if (source == null || !source.isObject()) throw new AiValidationException("简历内容格式无效");
        ObjectNode clean = mapper.createObjectNode();
        ArrayNode internships = clean.putArray("internships");
        ArrayNode projects = clean.putArray("projects");
        copyResumeItems(source.path("internships"), internships, new String[]{"company", "role", "description", "coreWork"});
        copyResumeItems(source.path("projects"), projects, new String[]{"name", "description", "coreWork"});
        if (clean.toString().length() > 20_000) throw new AiValidationException("简历内容不能超过 20000 个字符");
        return clean;
    }

    private void copyResumeItems(JsonNode source, ArrayNode target, String[] fields) {
        if (!source.isArray() || source.size() > 10) throw new AiValidationException("每类简历经历最多填写 10 项");
        for (JsonNode item : source) {
            if (!item.isObject()) throw new AiValidationException("简历条目格式无效");
            ObjectNode clean = target.addObject();
            for (String field : fields) {
                String value = item.path(field).asText("").trim();
                int maximum = field.equals("coreWork") ? 2_000 : field.equals("description") ? 1_000 : 120;
                if (value.length() > maximum) throw new AiValidationException("简历字段内容过长");
                clean.put(field, value);
            }
        }
    }

    private ObjectNode cleanSummary(JsonNode result, JsonNode resume) {
        if (!result.path("topics").isArray()) throw new AiResponseException("AI 未返回考点总结");
        ObjectNode clean = mapper.createObjectNode();
        ArrayNode topics = clean.putArray("topics");
        Map<String, ObjectNode> projectTopics = new LinkedHashMap<>();
        addResumeTopics(resume.path("internships"), "internship-", "internship", projectTopics);
        addResumeTopics(resume.path("projects"), "project-", "project", projectTopics);
        for (JsonNode topic : result.path("topics")) {
            String name = topic.path("name").asText("").trim();
            if (name.isEmpty()) continue;
            String kind = topic.path("kind").asText("knowledge");
            if (!Set.of("project", "knowledge", "other").contains(kind)) kind = "other";
            ObjectNode item;
            if (kind.equals("project")) {
                item = projectTopics.get(topic.path("resumeRef").asText(""));
                if (item == null) continue;
                item.put("count", Math.min(1000, item.path("count").asInt() + Math.max(0, topic.path("count").asInt())));
                String summary = topic.path("summary").asText("").trim();
                if (!summary.isEmpty() && !item.path("summary").asText("").contains(summary)) {
                    String previous = item.path("summary").asText("");
                    item.put("summary", limit(previous.isEmpty() ? summary : previous + " " + summary, 500));
                }
            } else {
                item = topics.addObject().put("name", limit(name, 100))
                    .put("count", Math.max(1, Math.min(1000, topic.path("count").asInt(1))))
                    .put("kind", kind).put("summary", limit(topic.path("summary").asText(""), 500));
                item.putArray("questionAnswers");
            }
            ArrayNode questionAnswers = (ArrayNode) item.path("questionAnswers");
            JsonNode sourceQuestions = topic.path("questionAnswers").isArray()
                ? topic.path("questionAnswers") : topic.path("questions");
            if (sourceQuestions.isArray()) for (JsonNode sourceQuestion : sourceQuestions) {
                String question = limit((sourceQuestion.isObject()
                    ? sourceQuestion.path("question").asText("") : sourceQuestion.asText("")).trim(), 500);
                String answer = limit(sourceQuestion.path("answer").asText("").trim(), 5_000);
                if (question.isEmpty()) continue;
                if (answer.isEmpty()) throw new AiResponseException("AI 未返回完整的问题回答，请重新汇总");
                boolean duplicate = false;
                for (JsonNode existing : questionAnswers) {
                    if (existing.path("question").asText("").equalsIgnoreCase(question)) { duplicate = true; break; }
                }
                if (duplicate) continue;
                questionAnswers.addObject().put("question", question).put("answer", answer);
            }
        }
        for (ObjectNode projectTopic : projectTopics.values()) topics.add(projectTopic);
        if (topics.isEmpty()) throw new AiResponseException("AI 未返回有效考点");
        var sorted = new java.util.ArrayList<JsonNode>();
        for (JsonNode item : topics) sorted.add(item);
        sorted.sort((a, b) -> Integer.compare(b.path("count").asInt(), a.path("count").asInt()));
        topics.removeAll();
        for (JsonNode item : sorted) topics.add(item);
        return clean;
    }

    private void addResumeTopics(JsonNode entries, String prefix, String type, Map<String, ObjectNode> topics) {
        if (!entries.isArray()) return;
        for (int index = 0; index < entries.size(); index++) {
            JsonNode entry = entries.get(index);
            String reference = prefix + (index + 1);
            String name;
            if (type.equals("internship")) {
                String company = entry.path("company").asText("").trim();
                String role = entry.path("role").asText("").trim();
                name = String.join(" · ", java.util.stream.Stream.of(company, role)
                    .filter(value -> !value.isBlank()).toList());
                if (name.isBlank()) name = "实习经历 " + (index + 1);
                else name += "（实习）";
            } else {
                name = entry.path("name").asText("").trim();
                if (name.isBlank()) name = "项目经历 " + (index + 1);
            }
            ObjectNode topic = mapper.createObjectNode().put("name", limit(name, 100))
                .put("count", 0).put("kind", "project").put("summary", "");
            topic.putArray("questionAnswers");
            topics.put(reference, topic);
        }
    }

    private ArrayNode reviews(ObjectNode root) {
        Map<String, JsonNode> applications = new HashMap<>();
        for (JsonNode app : root.path("applications")) applications.put(app.path("id").asText(""), app);
        ArrayNode reviews = mapper.createArrayNode();
        for (JsonNode event : root.path("events")) {
            String questions = event.path("interviewQuestions").asText("").trim();
            if (questions.isEmpty() || !event.path("completed").asBoolean(false)
                || event.path("missed").asBoolean(false) || event.path("abandoned").asBoolean(false)) continue;
            String appId = event.path("applicationId").asText("");
            JsonNode app = applications.get(appId);
            if (app == null) continue;
            reviews.addObject().put("eventId", event.path("id").asText(""))
                .put("applicationId", appId).put("company", limit(app.path("company").asText(""), 120))
                .put("position", limit(app.path("position").asText(""), 120))
                .put("title", limit(event.path("title").asText(event.path("type").asText("面试")), 120))
                .put("questions", limit(questions, 8_000));
        }
        return reviews;
    }

    private ArrayNode summaryInput(ArrayNode allReviews) {
        ArrayNode selected = mapper.createArrayNode();
        for (JsonNode review : allReviews) {
            ObjectNode item = selected.addObject();
            for (String field : new String[]{"eventId", "company", "position", "title", "questions"}) item.put(field, review.path(field).asText(""));
        }
        return selected;
    }

    private String sourceKey(ArrayNode reviews) throws Exception {
        var signatures = new java.util.ArrayList<String>();
        for (JsonNode review : reviews) signatures.add(review.toString());
        signatures.sort(String::compareTo);
        return signatures.isEmpty() ? "" : digest(String.join("\n", signatures));
    }

    private String summaryFingerprint(ArrayNode selected, JsonNode resume) throws Exception {
        return digest(SUMMARY_VERSION + "\n" + selected.toString() + resume.toString());
    }

    private String digest(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private ObjectNode workbench(ObjectNode root) {
        ObjectNode settings = root.path("settings") instanceof ObjectNode value ? value : root.putObject("settings");
        return settings.path("interviewWorkbench") instanceof ObjectNode value ? value : settings.putObject("interviewWorkbench");
    }

    private Document read(Connection connection, String email, boolean lock) throws Exception {
        String sql = "SELECT u.id,d.data::text FROM users u JOIN user_data d ON d.user_id=u.id WHERE lower(u.email)=? AND u.disabled_at IS NULL"
            + (lock ? " FOR UPDATE OF d" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new AiValidationException("找不到当前账号的业务数据");
                return new Document(result.getLong(1), (ObjectNode) mapper.readTree(result.getString(2)));
            }
        }
    }

    private void write(Connection connection, Document document) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE user_data SET data=?::jsonb,updated_at=NOW() WHERE user_id=?")) {
            statement.setString(1, mapper.writeValueAsString(document.root()));
            statement.setLong(2, document.userId());
            statement.executeUpdate();
        }
    }

    private Connection open() throws Exception {
        if (!applications.status().enabled()) throw new AiService.AiDisabledException("业务数据写入未开启");
        LegacyDatabaseUrl config = LegacyDatabaseUrl.parse(com.jobtracker.careerflow.config.AppEnvironment.databaseUrl(environment));
        Properties properties = new Properties();
        if (config.username() != null) properties.setProperty("user", config.username());
        if (config.password() != null) properties.setProperty("password", config.password());
        properties.setProperty("ApplicationName", "careerflow-interview-workbench");
        return PooledConnections.open(config, properties);
    }

    private String limit(String text, int maximum) { return text.length() <= maximum ? text : text.substring(0, maximum); }
    private record Document(long userId, ObjectNode root) {}
    public static class WorkbenchConflictException extends RuntimeException { public WorkbenchConflictException(String message) { super(message); } }
}
