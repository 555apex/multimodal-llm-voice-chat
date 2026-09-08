package cn.fj.roadagent.domain.dispatch;

import java.util.List;

/** 工单生成时冻结的预案全量快照，不受后续预案修改影响。 */
public record EmergencyResponsePlanSnapshot(
        String planId,
        String eventType,
        String eventTypeName,
        long version,
        List<String> requiredFacts,
        String rescuePlanTemplate,
        List<ResponsePlanResourceBaseline> resourceBaseline,
        String contentHash
) {
    public EmergencyResponsePlanSnapshot {
        if (planId == null || planId.isBlank()) throw new IllegalArgumentException("预案ID不能为空");
        planId = planId.trim();
        eventType = EmergencyEventType.require(eventType).name();
        if (eventTypeName == null || eventTypeName.isBlank()) throw new IllegalArgumentException("预案名称不能为空");
        eventTypeName = eventTypeName.trim();
        if (version < 1) throw new IllegalArgumentException("预案版本必须大于0");
        requiredFacts = requiredFacts == null ? List.of() : List.copyOf(requiredFacts);
        if (rescuePlanTemplate == null || rescuePlanTemplate.isBlank()) throw new IllegalArgumentException("预案模板不能为空");
        rescuePlanTemplate = rescuePlanTemplate.trim();
        resourceBaseline = resourceBaseline == null ? List.of() : List.copyOf(resourceBaseline);
        if (resourceBaseline.isEmpty()) throw new IllegalArgumentException("预案资源基线不能为空");
        if (contentHash == null || !contentHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("预案内容摘要必须是64位SHA-256");
        }
    }
}
