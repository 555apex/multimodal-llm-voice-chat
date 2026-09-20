package cn.fj.roadagent.application.dispatch;

import java.util.List;

public record WorkflowInbox(EmergencyWorkflowView item, List<EmergencyWorkflowView> items, WorkflowCounts counts) {
    public WorkflowInbox {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public WorkflowInbox(EmergencyWorkflowView item, WorkflowCounts counts) {
        this(item, item == null ? List.of() : List.of(item), counts);
    }
}
