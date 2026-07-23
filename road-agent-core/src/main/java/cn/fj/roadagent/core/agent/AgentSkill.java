package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;

/** Skill是一段受Java约束的业务流程，不是一次原子API调用。 */
public interface AgentSkill {
    AgentIntent intent();   // SkillRegistry 用这个返回值来建立映射，

    // execute() 被 AgentRuntime 调用，根据大模型识别的范围分派到不同流程
    AgentSkillResult execute(AgentExecutionContext context, AgentEventSink sink);
}
