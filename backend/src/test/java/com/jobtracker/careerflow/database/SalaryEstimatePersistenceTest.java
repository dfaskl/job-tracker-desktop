package com.jobtracker.careerflow.database;

import com.jobtracker.careerflow.ai.AiEndpointPolicy;
import com.jobtracker.careerflow.application.ApplicationDocumentMutator;
import com.jobtracker.careerflow.backup.BackupDocumentValidator;
import com.jobtracker.careerflow.compat.LegacyPasswordVerifier;
import com.jobtracker.careerflow.compat.LegacySecretCrypto;
import com.jobtracker.careerflow.compat.LegacySecretCryptoWriter;
import com.jobtracker.careerflow.config.DatabaseSchemaInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryEstimatePersistenceTest {
    private static final String EMAIL = "salary-owner@example.com";

    @Test
    void persistsParsedRangesAndKeepsOtherBatchesPending() throws Exception {
        Fixture fixture = fixture();
        var positions = List.of(
            new AiService.CompanyPosition("招商银行", "Java 开发", "金融机构"),
            new AiService.CompanyPosition("小影科技", "全栈工程师", "民营科技/软件企业"),
            new AiService.CompanyPosition("下一批科技", "后端工程师", "民营科技/软件企业")
        );
        fixture.backup.ensureCompanyEntries(EMAIL, positions.stream().map(AiService.CompanyPosition::company).toList());
        fixture.backup.saveIndustryResults(EMAIL, Map.of(
            "招商银行", "金融机构", "小影科技", "民营科技/软件企业", "下一批科技", "民营科技/软件企业"
        ), "");
        fixture.backup.ensureSalaryEntries(EMAIL, positions);
        var batch = positions.subList(0, 2);
        var envelope = fixture.mapper.createObjectNode();
        envelope.putArray("choices").addObject().put("finish_reason", "stop").putObject("message").put("content",
            "{\"items\":[{\"company\":\"招商银行有限公司\",\"position\":\"  JAVA   开发 \",\"minK\":20,\"maxK\":25},"
                + "{\"company\":\"小影科技\",\"position\":\"全栈工程师\",\"range\":\"25k-30k/月\"}]}");

        var ranges = fixture.ai.parseSalaryEstimates(envelope, batch);
        fixture.backup.saveSalaryResults(EMAIL, batch, ranges, "");
        JsonNode saved = fixture.backup.companyLinks(EMAIL).items();

        assertThat(estimate(saved, "招商银行").path("status").asText()).isEqualTo("classified");
        assertThat(estimate(saved, "招商银行").path("range").asText()).isEqualTo("20-25k/月");
        assertThat(estimate(saved, "小影科技").path("range").asText()).isEqualTo("25-30k/月");
        assertThat(estimate(saved, "下一批科技").path("status").asText()).isEqualTo("pending");

        fixture.backup.saveSalaryResults(EMAIL, positions.subList(2, 3), Map.of(), "AI 接口请求超时");
        saved = fixture.backup.companyLinks(EMAIL).items();
        assertThat(estimate(saved, "招商银行").path("range").asText()).isEqualTo("20-25k/月");
        assertThat(estimate(saved, "小影科技").path("status").asText()).isEqualTo("classified");
        assertThat(estimate(saved, "下一批科技").path("error").asText()).isEqualTo("AI 接口请求超时");
    }

    @Test
    void requeuesOldFalseFailuresWithoutReestimatingSuccessfulRanges() throws Exception {
        Fixture fixture = fixture();
        var links = fixture.mapper.createArrayNode();
        for (String company : List.of("已成功科技", "误失败科技")) {
            var entry = links.addObject().put("company", company).put("url", "")
                .put("industry", "民营科技/软件企业").put("industryStatus", "classified")
                .put("industryVersion", CompanyIndustryCatalog.VERSION);
            var salary = entry.putArray("salaryEstimates").addObject().put("position", "后端工程师")
                .put("version", "salary-v5");
            if (company.equals("已成功科技")) salary.put("status", "classified").put("range", "20-25k/月");
            else salary.put("status", "failed").put("error", "模型未能为该岗位返回有效薪资范围")
                .put("retryAfter", "2099-01-01T00:00:00Z");
        }
        fixture.backup.saveCompanyLinks(EMAIL, links);

        JsonNode saved = fixture.backup.ensureSalaryEntries(EMAIL, List.of(
            new AiService.CompanyPosition("已成功科技", "后端工程师"),
            new AiService.CompanyPosition("误失败科技", "后端工程师")
        )).items();

        assertThat(estimate(saved, "已成功科技").path("status").asText()).isEqualTo("classified");
        assertThat(estimate(saved, "已成功科技").path("range").asText()).isEqualTo("20-25k/月");
        assertThat(estimate(saved, "误失败科技").path("status").asText()).isEqualTo("pending");
        assertThat(estimate(saved, "误失败科技").has("error")).isFalse();
        assertThat(estimate(saved, "误失败科技").has("retryAfter")).isFalse();
    }

    private JsonNode estimate(JsonNode companies, String company) {
        for (JsonNode entry : companies) if (company.equals(entry.path("company").asText()))
            return entry.path("salaryEstimates").get(0);
        throw new AssertionError("Company missing: " + company);
    }

    private Fixture fixture() throws Exception {
        Path directory = Path.of("target", "sqlite-tests").toAbsolutePath();
        Files.createDirectories(directory);
        MockEnvironment environment = new MockEnvironment()
            .withProperty("APP_DATABASE_URL", "jdbc:sqlite:" + directory.resolve(UUID.randomUUID() + ".db"))
            .withProperty("ALLOW_REGISTRATION", "true");
        new DatabaseSchemaInitializer(environment).run(null);
        ObjectMapper mapper = new ObjectMapper();
        ApplicationService applications = new ApplicationService(environment, mapper, new ApplicationDocumentMutator(mapper));
        new AccountService(environment, applications, new LegacyPasswordVerifier()).register(EMAIL, "salary-test-password", "");
        BackupService backup = new BackupService(environment, applications, new BackupDocumentValidator(mapper), mapper);
        LegacySecretCrypto crypto = new LegacySecretCrypto();
        AiService ai = new AiService(environment, applications, crypto, new LegacySecretCryptoWriter(crypto),
            new AiEndpointPolicy(environment), mapper);
        return new Fixture(mapper, backup, ai);
    }

    private record Fixture(ObjectMapper mapper, BackupService backup, AiService ai) { }
}
