package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.WorkflowInbox;
import java.util.List;

public record WorkflowInboxResponse(
        EmergencyWorkflowResponse item,
        List<EmergencyWorkflowResponse> items,
        WorkflowCountsResponse counts
) {
    public static WorkflowInboxResponse from(WorkflowInbox inbox) {
        return new WorkflowInboxResponse(
                inbox.item() == null ? null : EmergencyWorkflowResponse.from(inbox.item()),
                inbox.items().stream().map(EmergencyWorkflowResponse::from).toList(),
                WorkflowCountsResponse.from(inbox.counts())
        );
    }
}
