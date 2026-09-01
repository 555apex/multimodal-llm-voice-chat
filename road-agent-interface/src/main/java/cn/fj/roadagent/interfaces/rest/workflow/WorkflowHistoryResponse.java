package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.WorkflowHistoryPage;

import java.util.List;

public record WorkflowHistoryResponse(
        List<EmergencyWorkflowResponse> items,
        int page,
        int size,
        long total
) {
    public static WorkflowHistoryResponse from(WorkflowHistoryPage history) {
        return new WorkflowHistoryResponse(
                history.items().stream().map(EmergencyWorkflowResponse::from).toList(),
                history.page(), history.size(), history.total()
        );
    }
}
