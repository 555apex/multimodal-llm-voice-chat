package cn.fj.roadagent.interfaces.rest.workflow;

import cn.fj.roadagent.application.dispatch.WorkflowInbox;
import java.util.List;
import java.util.Map;

public record WorkflowInboxResponse(
        EmergencyWorkflowResponse item,
        List<EmergencyWorkflowResponse> items,
        WorkflowCountsResponse counts,
        Map<String, String> severityAssessments
) {
    public static WorkflowInboxResponse from(WorkflowInbox inbox) {
        return new WorkflowInboxResponse(
                inbox.item() == null ? null : EmergencyWorkflowResponse.from(inbox.item()),
                inbox.items().stream().map(EmergencyWorkflowResponse::from).toList(),
                WorkflowCountsResponse.from(inbox.counts()),
                inbox.severityAssessments().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().name()))
        );
    }
}
