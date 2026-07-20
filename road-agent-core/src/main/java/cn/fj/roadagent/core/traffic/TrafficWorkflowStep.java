package cn.fj.roadagent.core.traffic;

/**
 * 一期交通查询Skill采用固定步骤，不允许大模型自由改写流程。
 */
public enum TrafficWorkflowStep {   // 枚举，五个有名字的常量
    VALIDATE_QUERY, // 校验查询
    QUERY_TRAFFIC,  // 查询交通数据
    VALIDATE_FRESHNESS, // 校验数据更新度
    SUMMARIZE,      // 生成的摘要
    PUBLISH_RESULT  // 发布结果
}
