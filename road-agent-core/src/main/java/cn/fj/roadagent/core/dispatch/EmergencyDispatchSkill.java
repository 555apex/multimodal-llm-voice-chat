package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import cn.fj.roadagent.core.agent.AgentSkill;
import cn.fj.roadagent.core.agent.AgentSkillResult;

import java.util.Map;

/** 正式应急调度必须从数据库告警卡发起，聊天入口只负责引导。 */
public final class EmergencyDispatchSkill implements AgentSkill {
    private static final String GUIDANCE =
            "正式应急调度必须关联数据库异常事件，请通过聊天区顶部的红色告警卡片生成和审批工单。";

    @Override
    public AgentIntent intent() {
        return AgentIntent.EMERGENCY_DISPATCH;
    }

    @Override
    public AgentSkillResult execute(AgentExecutionContext context, AgentEventSink sink) {
        sink.emit(new AgentEvent("answer.delta", Map.of("content", GUIDANCE)));
        return new AgentSkillResult(GUIDANCE);
    }
}
