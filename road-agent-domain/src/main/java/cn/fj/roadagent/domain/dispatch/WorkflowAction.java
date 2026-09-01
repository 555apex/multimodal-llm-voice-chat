package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.Objects;

/** 跨三级统一展示的只增不改动作流水。 */
public record WorkflowAction(
        String actionId,
        String workflowId,
        WorkflowActionType actionType,
        WorkflowStage fromStage,
        WorkflowStage toStage,
        WorkflowStatus fromStatus,
        WorkflowStatus toStatus,
        String planId,
        long planVersion,
        String comment,
        String detailJson,
        String idempotencyKey,
        Instant createdAt
) {
    public WorkflowAction {
        actionId = requireText(actionId, "actionId不能为空");
        workflowId = requireText(workflowId, "workflowId不能为空");
        actionType = Objects.requireNonNull(actionType, "动作类型不能为空");
        toStatus = Objects.requireNonNull(toStatus, "目标状态不能为空");
        planId = normalize(planId);
        if (planVersion < 0) {
            throw new IllegalArgumentException("方案版本不能为负数");
        }
        comment = normalize(comment);
        detailJson = normalize(detailJson);
        idempotencyKey = requireText(idempotencyKey, "幂等键不能为空");
        createdAt = Objects.requireNonNull(createdAt, "动作时间不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
