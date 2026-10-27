package com.jobtracker.careerflow.database;

import java.util.List;
import java.util.Set;

public final class CompanyIndustryCatalog {
    public static final String UNKNOWN = "未分类";
    public static final List<String> INDUSTRIES = List.of(
        "互联网/软件/云服务", "人工智能/大模型", "通信/电子/半导体", "金融/银行/证券/保险",
        "制造/工业/装备", "汽车/新能源/智能驾驶", "能源/电力/环保", "医疗/医药/生物科技",
        "消费品/零售/电商", "物流/交通/航空", "教育/培训", "传媒/游戏/文娱",
        "建筑/地产/工程", "咨询/法律/会计/专业服务", "农业/食品", "其他", UNKNOWN
    );
    public static final Set<String> ALLOWED = Set.copyOf(INDUSTRIES);

    private CompanyIndustryCatalog() {}
}
