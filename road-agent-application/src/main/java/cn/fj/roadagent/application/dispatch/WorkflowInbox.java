package cn.fj.roadagent.application.dispatch;

import java.util.List;
import java.util.Map;
import cn.fj.roadagent.domain.dispatch.EventSeverity;

public record WorkflowInbox(EmergencyWorkflowView item, List<EmergencyWorkflowView> items,
        WorkflowCounts counts, Map<String, EventSeverity> severityAssessments) {
    public WorkflowInbox {
        items = items == null ? List.of() : List.copyOf(items);
        severityAssessments = severityAssessments == null ? Map.of() : Map.copyOf(severityAssessments);
    }

    public WorkflowInbox(EmergencyWorkflowView item, WorkflowCounts counts) {
        this(item, item == null ? List.of() : List.of(item), counts, Map.of());
    }
}
