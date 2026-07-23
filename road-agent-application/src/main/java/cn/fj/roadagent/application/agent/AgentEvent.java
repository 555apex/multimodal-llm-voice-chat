package cn.fj.roadagent.application.agent;

/** name对应SSE的event字段，data会由接口层转成JSON。 */
public record AgentEvent(String name, Object data) {
}
