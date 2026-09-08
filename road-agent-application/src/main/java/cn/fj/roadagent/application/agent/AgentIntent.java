package cn.fj.roadagent.application.agent;

public enum AgentIntent {
    // 以下三个枚举常量的数据类型都是AgentIntent，类似于标签
    TRAFFIC_QUERY,  // 交通查询
    EMERGENCY_DISPATCH, // 应急调度
    DIRECT_ANSWER, // 由Java受控规则直接回答项目口径或能力边界
    UNSUPPORTED // 不支持
}
