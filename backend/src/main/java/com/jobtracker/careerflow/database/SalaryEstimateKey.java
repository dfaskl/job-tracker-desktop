package com.jobtracker.careerflow.database;

import java.util.Locale;

final class SalaryEstimateKey {
    private SalaryEstimateKey() { }

    static String of(String company, String position) {
        String companyName = company == null ? "" : company.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s·•・()（）\\[\\]【】._-]+", "")
            .replaceAll("(股份)?有限公司$|有限责任公司$|集团$|公司$", "");
        String title = position == null ? "" : position.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return companyName + "\n" + title;
    }
}
