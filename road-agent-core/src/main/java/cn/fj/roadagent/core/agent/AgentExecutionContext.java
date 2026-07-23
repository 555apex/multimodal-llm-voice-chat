package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentMessageCommand;
import cn.fj.roadagent.application.agent.ConversationMessage;

import java.util.List;

public record AgentExecutionContext(
        AgentMessageCommand command,
        AgentDecision decision,
        List<ConversationMessage> history
) {
}
