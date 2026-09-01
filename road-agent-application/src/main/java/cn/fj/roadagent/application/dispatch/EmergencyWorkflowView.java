package cn.fj.roadagent.application.dispatch;

import cn.fj.roadagent.domain.dispatch.CommandDecision;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyWorkflow;
import cn.fj.roadagent.domain.dispatch.ProfessionalReview;
import cn.fj.roadagent.domain.dispatch.WorkflowAction;

import java.util.List;

/** 前端三级待办和历史详情共用的完整只读视图。 */
public record EmergencyWorkflowView(
        EmergencyWorkflow workflow,
        EmergencyEvent event,
        DispatchPlan currentPlan,
        ProfessionalReview professionalReview,
        CommandDecision commandDecision,
        List<WorkflowAction> timeline,
        boolean resourcesReleased
) {
    public EmergencyWorkflowView {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }

    public EmergencyWorkflowView(
            EmergencyWorkflow workflow,
            EmergencyEvent event,
            DispatchPlan currentPlan,
            ProfessionalReview professionalReview,
            CommandDecision commandDecision,
            List<WorkflowAction> timeline
    ) {
        this(workflow, event, currentPlan, professionalReview, commandDecision, timeline, false);
    }
}
