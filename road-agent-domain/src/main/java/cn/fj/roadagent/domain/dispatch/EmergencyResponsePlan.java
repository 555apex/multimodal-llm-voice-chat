package cn.fj.roadagent.domain.dispatch;

import java.util.List;

/** 数据库中已发布的应急预案版本。 */
public record EmergencyResponsePlan(
        String planId,
        String eventType,
        String eventTypeName,
        long version,
        List<String> requiredFacts,
        String rescuePlanTemplate,
        List<ResponsePlanResourceBaseline> resourceBaseline,
        String contentHash
) {
    public EmergencyResponsePlan {
        planId = requireText(planId, "预案ID不能为空");
        eventType = EmergencyEventType.require(requireText(eventType, "事件类型不能为空")).name();
        eventTypeName = requireText(eventTypeName, "事件类型名称不能为空");
        if (version < 1) throw new IllegalArgumentException("预案版本必须大于0");
        requiredFacts = requiredFacts == null ? List.of() : List.copyOf(requiredFacts);
        rescuePlanTemplate = requireText(rescuePlanTemplate, "预案模板不能为空");
        resourceBaseline = resourceBaseline == null ? List.of() : List.copyOf(resourceBaseline);
        if (resourceBaseline.isEmpty()) throw new IllegalArgumentException("预案资源基线不能为空");
        contentHash = requireText(contentHash, "预案内容摘要不能为空");
        if (!contentHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("预案内容摘要必须是64位SHA-256");
        }
        long distinctCodes = resourceBaseline.stream()
                .map(ResponsePlanResourceBaseline::resourceTypeCode).distinct().count();
        if (distinctCodes != resourceBaseline.size()) {
            throw new IllegalArgumentException("预案资源基线不能包含重复类型");
        }
    }

    public EmergencyResponsePlanSnapshot snapshot() {
        return new EmergencyResponsePlanSnapshot(
                planId, eventType, eventTypeName, version, requiredFacts,
                rescuePlanTemplate, resourceBaseline, contentHash
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
