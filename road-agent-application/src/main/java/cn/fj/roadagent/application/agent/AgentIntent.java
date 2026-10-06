package cn.fj.roadagent.application.agent;

public enum AgentIntent {
    // 以下三个枚举常量的数据类型都是AgentIntent，类似于标签
    TRAFFIC_QUERY,  // 交通查询
    EMERGENCY_DISPATCH, // 应急调度
    MAINTENANCE_PROJECT_LIST, // 养护工程项目库
    MAINTENANCE_PREPLAN, // 养护预安排计划
    MAINTENANCE_SCHEME_COMPARISON, // 单项目养护方案比选
    MAINTENANCE_REPORT, // 养护统计分析与考核报告
    KNOWLEDGE_QA, // 基于知识库检索结果回答
    DIRECT_ANSWER, // 由Java受控规则直接回答项目口径或能力边界
    UNSUPPORTED // 不支持
}
