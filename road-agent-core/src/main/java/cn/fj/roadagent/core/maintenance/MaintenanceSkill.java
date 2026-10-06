package cn.fj.roadagent.core.maintenance;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import cn.fj.roadagent.core.agent.AgentSkill;
import cn.fj.roadagent.core.agent.AgentSkillResult;

import java.util.Map;

public final class MaintenanceSkill implements AgentSkill {
    private final AgentIntent intent;
    private final MaintenanceService service;

    public MaintenanceSkill(AgentIntent intent, MaintenanceService service) {
        this.intent = intent;
        this.service = service;
    }

    @Override public AgentIntent intent() { return intent; }

    @Override
    public AgentSkillResult execute(AgentExecutionContext context, AgentEventSink sink) {
        String message = context.command().message();
        sink.emit(new AgentEvent("stage.changed", Map.of("stage", "TOOL_CALLING", "label", "正在整理养护业务数据")));
        if (intent == AgentIntent.MAINTENANCE_PROJECT_LIST) {
            var result = service.projects(message);
            sink.emit(new AgentEvent("answer.delta", Map.of("content", result.summary())));
            sink.emit(new AgentEvent("result.maintenance-projects", result));
            return new AgentSkillResult(result.summary());
        }
        if (intent == AgentIntent.MAINTENANCE_SCHEME_COMPARISON && service.selectedProject(message).isEmpty()) {
            var candidates = service.projects("");
            String answer = "请从下方养护项目清单中指定项目编号、路线编号或具体路段，再生成该项目的方案比选。";
            sink.emit(new AgentEvent("answer.delta", Map.of("content", answer)));
            sink.emit(new AgentEvent("result.maintenance-projects", candidates));
            return new AgentSkillResult(answer);
        }
        sink.emit(new AgentEvent("stage.changed", Map.of("stage", "ANSWERING", "label", "正在生成养护文档")));
        var document = service.document(intent, message);
        String answer = document.summary() + " " + document.conclusion();
        sink.emit(new AgentEvent("answer.delta", Map.of("content", answer)));
        sink.emit(new AgentEvent("result.maintenance-document", document));
        return new AgentSkillResult(answer);
    }
}
