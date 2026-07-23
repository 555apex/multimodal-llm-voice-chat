package cn.fj.roadagent.application.agent;

@FunctionalInterface
public interface AgentEventSink {
    void emit(AgentEvent event);
}
