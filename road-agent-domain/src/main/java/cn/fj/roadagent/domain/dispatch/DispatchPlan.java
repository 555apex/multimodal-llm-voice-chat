package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 不可变的数据库调度工单版本；模型只能生成内容，状态由Java规则推进。 */
public record DispatchPlan(
        String planId,
        EmergencyEvent event,
        List<SuggestedResource> suggestedResources,
        String rescuePlan,
        DispatchStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String rejectionReason,
        String errorMessage
) {
    public DispatchPlan {
        planId = requireText(planId, "planId不能为空");
        event = Objects.requireNonNull(event, "event不能为空");
        suggestedResources = suggestedResources == null ? List.of() : List.copyOf(suggestedResources);
        rescuePlan = rescuePlan == null ? "" : rescuePlan.trim();
        status = Objects.requireNonNull(status, "status不能为空");
        if (version < 1) {
            throw new IllegalArgumentException("工单版本必须大于0");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt不能为空");
        rejectionReason = normalize(rejectionReason);
        errorMessage = normalize(errorMessage);
    }

    public static DispatchPlan generating(
            String planId,
            EmergencyEvent event,
            long version,
            Instant now
    ) {
        return new DispatchPlan(
                planId, event, List.of(), "", DispatchStatus.GENERATING,
                version, now, now, null, null
        );
    }

    public DispatchPlan generated(
            List<SuggestedResource> resources,
            String generatedRescuePlan,
            Instant now
    ) {
        requireStatus(DispatchStatus.GENERATING);
        if (resources == null || resources.isEmpty()) {
            throw new IllegalArgumentException("模型生成的建议资源清单不能为空");
        }
        if (generatedRescuePlan == null || generatedRescuePlan.isBlank()) {
            throw new IllegalArgumentException("模型生成的救援方案不能为空");
        }
        return new DispatchPlan(
                planId, event, resources, generatedRescuePlan,
                DispatchStatus.WAITING_APPROVAL, version, createdAt, now, null, null
        );
    }

    public DispatchPlan approve(Instant now) {
        requireStatus(DispatchStatus.WAITING_APPROVAL);
        return withStatus(DispatchStatus.APPROVED, now, null, null);
    }

    public DispatchPlan reject(String reason, Instant now) {
        requireStatus(DispatchStatus.WAITING_APPROVAL);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("驳回意见不能为空");
        }
        return withStatus(DispatchStatus.REJECTED, now, reason.trim(), null);
    }

    public DispatchPlan failed(String message, Instant now) {
        requireStatus(DispatchStatus.GENERATING);
        return withStatus(
                DispatchStatus.FAILED,
                now,
                rejectionReason,
                message == null || message.isBlank() ? "模型生成失败" : message.trim()
        );
    }

    public DispatchPlan retry(Instant now) {
        if (status != DispatchStatus.FAILED && status != DispatchStatus.GENERATING) {
            throw new IllegalStateException("只有失败或超时的生成任务可以重试");
        }
        return new DispatchPlan(
                planId, event, List.of(), "", DispatchStatus.GENERATING,
                version, createdAt, now, rejectionReason, null
        );
    }

    public DispatchPlan nextRevision(Instant now) {
        requireStatus(DispatchStatus.REJECTED);
        return new DispatchPlan(
                planId, event, List.of(), "", DispatchStatus.GENERATING,
                version + 1, now, now, null, null
        );
    }

    private DispatchPlan withStatus(
            DispatchStatus next,
            Instant now,
            String nextRejectionReason,
            String nextError
    ) {
        return new DispatchPlan(
                planId, event, suggestedResources, rescuePlan, next, version,
                createdAt, now, nextRejectionReason, nextError
        );
    }

    private void requireStatus(DispatchStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("状态" + status + "不能执行该操作，要求状态为" + expected);
        }
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
