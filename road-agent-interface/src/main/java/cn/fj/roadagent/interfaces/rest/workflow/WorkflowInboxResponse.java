package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.WorkflowInbox;

public record WorkflowInboxResponse(
        EmergencyWorkflowResponse item,
        WorkflowCountsResponse counts
) {
    public static WorkflowInboxResponse from(WorkflowInbox inbox) {
        return new WorkflowInboxResponse(
                inbox.item() == null ? null : EmergencyWorkflowResponse.from(inbox.item()),
                WorkflowCountsResponse.from(inbox.counts())
        );
    }
}
