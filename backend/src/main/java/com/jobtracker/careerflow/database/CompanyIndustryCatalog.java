package com.jobtracker.careerflow.database;

import java.util.List;
import java.util.Set;

public final class CompanyIndustryCatalog {
    public static final String VERSION = "employment-type-v3";
    public static final String UNKNOWN = "未分类";
    public static final List<String> INDUSTRIES = List.of(
        "互联网/平台型企业", "央企/国企", "外资/合资企业", "金融机构",
        "民营科技/软件企业", "民营传统行业企业", "创业公司/初创企业",
        "事业单位/政府/科研院所", "教育/医疗等专业机构", "其他", UNKNOWN
    );
    public static final Set<String> ALLOWED = Set.copyOf(INDUSTRIES);

    private CompanyIndustryCatalog() {}
}
