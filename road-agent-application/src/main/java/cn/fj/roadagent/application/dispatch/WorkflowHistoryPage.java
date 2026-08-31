package cn.fj.roadagent.application.dispatch;

import java.util.List;

public record WorkflowHistoryPage(
        List<EmergencyWorkflowView> items,
        int page,
        int size,
        long total
) {
    public WorkflowHistoryPage {
        items = List.copyOf(items);
    }
}
