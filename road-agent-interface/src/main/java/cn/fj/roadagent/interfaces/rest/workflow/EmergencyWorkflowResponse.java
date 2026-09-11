package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.EmergencyWorkflowView;
import cn.fj.roadagent.domain.dispatch.CommandDecision;
import cn.fj.roadagent.domain.dispatch.ProfessionalReview;
import cn.fj.roadagent.domain.dispatch.WorkflowAction;
import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import cn.fj.roadagent.domain.dispatch.WorkflowStatus;
import cn.fj.roadagent.interfaces.rest.dispatch.DispatchResponse;
import cn.fj.roadagent.interfaces.rest.emergency.EmergencyEventResponse;

import java.util.List;

public record EmergencyWorkflowResponse(
        String workflowId,
        WorkflowStage currentStage,
        WorkflowStatus workflowStatus,
        long workflowVersion,
        String terminalReason,
        EmergencyEventResponse event,
        DispatchResponse currentPlan,
        ProfessionalReview professionalReview,
        CommandDecision commandDecision,
        List<WorkflowAction> timeline,
        boolean resourcesReleased,
        boolean canReleaseResources,
        String completionStatus,
        String resourceReleaseUnavailableReason
) {
    public static EmergencyWorkflowResponse from(EmergencyWorkflowView view) {
        return new EmergencyWorkflowResponse(
                view.workflow() == null ? null : view.workflow().workflowId(),
                view.workflow() == null ? WorkflowStage.LEVEL_1 : view.workflow().currentStage(),
                view.workflow() == null
                        ? WorkflowStatus.WAITING_GENERATION : view.workflow().status(),
                view.workflow() == null ? 0 : view.workflow().lockVersion(),
                view.workflow() == null ? null : view.workflow().terminalReason(),
                EmergencyEventResponse.from(view.event()),
                view.currentPlan() == null ? null : DispatchResponse.from(view.currentPlan()),
                view.professionalReview(), view.commandDecision(), view.timeline(),
                view.resourcesReleased(), view.canReleaseResources(),
                view.workflow() == null || (view.workflow().status() != WorkflowStatus.PUBLISHED
                        && view.workflow().status() != WorkflowStatus.NO_DISPATCH) ? null
                        : view.canReleaseResources() ? "PENDING" : "COMPLETED",
                view.canReleaseResources() ? null : (view.resourcesReleased() ? "资源已全部归还" : "没有需要归还的已调度资源")
        );
    }
}
