package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.Objects;

/** 方案版本与实际资源库存之间的可追溯占用记录。 */
public record ResourceAllocation(
        String allocationId,
        String workflowId,
        String eventId,
        String planId,
        long planVersion,
        AllocatedResource resource,
        ResourceAllocationStatus status,
        Instant reservedAt,
        Instant dispatchedAt,
        Instant releasedAt,
        String releaseReason
) {
    public ResourceAllocation {
        allocationId = requireText(allocationId, "allocationId不能为空");
        workflowId = requireText(workflowId, "workflowId不能为空");
        eventId = requireText(eventId, "eventId不能为空");
        planId = requireText(planId, "planId不能为空");
        if (planVersion < 1) throw new IllegalArgumentException("方案版本必须大于0");
        resource = Objects.requireNonNull(resource, "资源快照不能为空");
        status = Objects.requireNonNull(status, "资源占用状态不能为空");
        reservedAt = Objects.requireNonNull(reservedAt, "资源预留时间不能为空");
        releaseReason = releaseReason == null || releaseReason.isBlank() ? null : releaseReason.trim();
    }

    public static ResourceAllocation reserved(
            String allocationId, String workflowId, String eventId,
            String planId, long planVersion, AllocatedResource resource, Instant now
    ) {
        return new ResourceAllocation(
                allocationId, workflowId, eventId, planId, planVersion, resource,
                ResourceAllocationStatus.RESERVED, now, null, null, null
        );
    }

    public ResourceAllocation dispatched(Instant now) {
        requireStatus(ResourceAllocationStatus.RESERVED);
        return new ResourceAllocation(
                allocationId, workflowId, eventId, planId, planVersion, resource,
                ResourceAllocationStatus.DISPATCHED, reservedAt, now, null, null
        );
    }

    public ResourceAllocation released(String reason, Instant now) {
        if (status != ResourceAllocationStatus.RESERVED
                && status != ResourceAllocationStatus.DISPATCHED) {
            throw new IllegalStateException("资源占用记录已经释放");
        }
        return new ResourceAllocation(
                allocationId, workflowId, eventId, planId, planVersion, resource,
                ResourceAllocationStatus.RELEASED, reservedAt, dispatchedAt, now,
                requireText(reason, "资源释放原因不能为空")
        );
    }

    private void requireStatus(ResourceAllocationStatus expected) {
        if (status != expected) throw new IllegalStateException("资源占用状态不允许执行该操作");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }
}
