package cn.fj.roadagent.core.dispatch;

import java.util.List;

/** 模型输出格式；Java随后会检查资源ID和任务完整性。 */
public record DispatchPlanProposal(
        String summary,
        List<ProposedTask> tasks,
        List<String> selectedResourceIds,
        List<String> warnings
) {
    public DispatchPlanProposal {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        selectedResourceIds = selectedResourceIds == null ? List.of() : List.copyOf(selectedResourceIds);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public record ProposedTask(String action, String responsibleUnit, String resourceId) {
    }
}
