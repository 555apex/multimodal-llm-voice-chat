package cn.fj.roadagent.application.agent;

public interface ConverseWithAgentUseCase {
    void handle(AgentMessageCommand command, AgentEventSink sink);
}
