package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 不可变的数据库调度工单版本；资源分配事实由Java库存规则产生。 */
public record DispatchPlan(
        String planId,
        EmergencyEvent event,
        List<ResourceRequirement> resourceRequirements,
        List<AllocatedResource> allocatedResources,
        List<ResourceShortage> resourceShortages,
        String rescuePlan,
        DispatchStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String rejectionReason,
        String errorMessage,
        EmergencyResponsePlanSnapshot responsePlan
) {
    public DispatchPlan {
        planId = requireText(planId, "planId不能为空");
        event = Objects.requireNonNull(event, "event不能为空");
        resourceRequirements = copy(resourceRequirements);
        allocatedResources = copy(allocatedResources);
        resourceShortages = copy(resourceShortages);
        rescuePlan = rescuePlan == null ? "" : rescuePlan.trim();
        status = Objects.requireNonNull(status, "status不能为空");
        if (version < 1) throw new IllegalArgumentException("工单版本必须大于0");
        createdAt = Objects.requireNonNull(createdAt, "createdAt不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt不能为空");
        rejectionReason = normalize(rejectionReason);
        errorMessage = normalize(errorMessage);
    }

    /** 兼容尚未引入预案快照的旧代码和历史数据。 */
    public DispatchPlan(
            String planId, EmergencyEvent event,
            List<ResourceRequirement> resourceRequirements,
            List<AllocatedResource> allocatedResources,
            List<ResourceShortage> resourceShortages,
            String rescuePlan, DispatchStatus status, long version,
            Instant createdAt, Instant updatedAt, String rejectionReason,
            String errorMessage
    ) {
        this(planId, event, resourceRequirements, allocatedResources, resourceShortages,
                rescuePlan, status, version, createdAt, updatedAt, rejectionReason,
                errorMessage, null);
    }

    /** 兼容旧测试和旧适配器构造方式。 */
    public DispatchPlan(
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
        this(planId, event, legacyRequirements(suggestedResources),
                legacyAllocations(event, suggestedResources), List.of(), rescuePlan,
                status, version, createdAt, updatedAt, rejectionReason, errorMessage);
    }

    public static DispatchPlan generating(
            String planId, EmergencyEvent event, long version, Instant now
    ) {
        return new DispatchPlan(
                planId, event, List.of(), List.of(), List.of(), "",
                DispatchStatus.GENERATING, version, now, now, null, null
        );
    }

    public static DispatchPlan generating(
            String planId, EmergencyEvent event, long version, Instant now,
            EmergencyResponsePlanSnapshot responsePlan
    ) {
        return new DispatchPlan(
                planId, event, List.of(), List.of(), List.of(), "",
                DispatchStatus.GENERATING, version, now, now, null, null, responsePlan
        );
    }

    public DispatchPlan generated(
            List<ResourceRequirement> requirements,
            List<AllocatedResource> allocations,
            List<ResourceShortage> shortages,
            String generatedRescuePlan,
            Instant now
    ) {
        requireStatus(DispatchStatus.GENERATING);
        if (requirements == null || requirements.isEmpty()) {
            throw new IllegalArgumentException("资源需求清单不能为空");
        }
        if ((allocations == null || allocations.isEmpty())
                && (shortages == null || shortages.isEmpty())) {
            throw new IllegalArgumentException("资源匹配结果和缺口不能同时为空");
        }
        if (generatedRescuePlan == null || generatedRescuePlan.isBlank()) {
            throw new IllegalArgumentException("模型生成的救援方案不能为空");
        }
        return new DispatchPlan(
                planId, event, requirements, allocations, shortages, generatedRescuePlan,
                DispatchStatus.WAITING_APPROVAL, version, createdAt, now, null, null,
                responsePlan
        );
    }

    /** 兼容旧测试；正式流程使用带需求、分配和缺口的重载。 */
    public DispatchPlan generated(
            List<SuggestedResource> resources,
            String generatedRescuePlan,
            Instant now
    ) {
        return generated(
                legacyRequirements(resources), legacyAllocations(event, resources),
                List.of(), generatedRescuePlan, now
        );
    }

    /** 对旧接口保留的兼容投影，内容来自已匹配数据库资源。 */
    public List<SuggestedResource> suggestedResources() {
        return allocatedResources.stream()
                .map(AllocatedResource::compatibilityProjection)
                .toList();
    }

    public boolean hasResourceShortage() {
        return !resourceShortages.isEmpty();
    }

    public DispatchPlan withResponsePlan(EmergencyResponsePlanSnapshot snapshot) {
        return new DispatchPlan(
                planId, event, resourceRequirements, allocatedResources, resourceShortages,
                rescuePlan, status, version, createdAt, updatedAt, rejectionReason,
                errorMessage, Objects.requireNonNull(snapshot, "预案快照不能为空")
        );
    }

    public DispatchPlan approve(Instant now) {
        requireStatus(DispatchStatus.WAITING_APPROVAL);
        return withStatus(DispatchStatus.APPROVED, now, null, null);
    }

    public DispatchPlan reject(String reason, Instant now) {
        requireStatus(DispatchStatus.WAITING_APPROVAL);
        return withStatus(
                DispatchStatus.REJECTED, now,
                requireText(reason, "驳回意见不能为空"), null
        );
    }

    public DispatchPlan failed(String message, Instant now) {
        requireStatus(DispatchStatus.GENERATING);
        return withStatus(
                DispatchStatus.FAILED, now, rejectionReason,
                message == null || message.isBlank() ? "模型生成失败" : message.trim()
        );
    }

    public DispatchPlan retry(Instant now) {
        if (status != DispatchStatus.FAILED && status != DispatchStatus.GENERATING) {
            throw new IllegalStateException("只有失败或超时的生成任务可以重试");
        }
        return new DispatchPlan(
                planId, event, List.of(), List.of(), List.of(), "",
                DispatchStatus.GENERATING, version, createdAt, now, rejectionReason, null,
                responsePlan
        );
    }

    public DispatchPlan retry(Instant now, EmergencyResponsePlanSnapshot nextResponsePlan) {
        DispatchPlan retried = retry(now);
        return new DispatchPlan(
                retried.planId, retried.event, retried.resourceRequirements,
                retried.allocatedResources, retried.resourceShortages, retried.rescuePlan,
                retried.status, retried.version, retried.createdAt, retried.updatedAt,
                retried.rejectionReason, retried.errorMessage,
                nextResponsePlan == null ? responsePlan : nextResponsePlan
        );
    }

    public DispatchPlan nextRevision(Instant now) {
        return nextRevision(event, now);
    }

    public DispatchPlan nextRevision(EmergencyEvent nextEvent, Instant now) {
        requireStatus(DispatchStatus.REJECTED);
        return new DispatchPlan(
                planId, Objects.requireNonNull(nextEvent, "新事件快照不能为空"),
                List.of(), List.of(), List.of(), "",
                DispatchStatus.GENERATING, version + 1, now, now, null, null,
                responsePlan
        );
    }

    public DispatchPlan nextRevision(
            EmergencyEvent nextEvent, Instant now,
            EmergencyResponsePlanSnapshot nextResponsePlan
    ) {
        DispatchPlan revision = nextRevision(nextEvent, now);
        return new DispatchPlan(
                revision.planId, revision.event, revision.resourceRequirements,
                revision.allocatedResources, revision.resourceShortages, revision.rescuePlan,
                revision.status, revision.version, revision.createdAt, revision.updatedAt,
                revision.rejectionReason, revision.errorMessage, nextResponsePlan
        );
    }

    private DispatchPlan withStatus(
            DispatchStatus next, Instant now, String nextRejectionReason, String nextError
    ) {
        return new DispatchPlan(
                planId, event, resourceRequirements, allocatedResources, resourceShortages,
                rescuePlan, next, version, createdAt, now, nextRejectionReason, nextError,
                responsePlan
        );
    }

    private void requireStatus(DispatchStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("状态" + status + "不能执行该操作，要求状态为" + expected);
        }
    }

    private static List<ResourceRequirement> legacyRequirements(List<SuggestedResource> resources) {
        if (resources == null) return List.of();
        List<ResourceRequirement> result = new ArrayList<>();
        for (int index = 0; index < resources.size(); index++) {
            SuggestedResource item = resources.get(index);
            result.add(new ResourceRequirement(
                    "LEGACY_" + (index + 1), item.resourceType(), item.quantity(),
                    item.unit(), item.purpose()
            ));
        }
        return result;
    }

    private static List<AllocatedResource> legacyAllocations(
            EmergencyEvent event, List<SuggestedResource> resources
    ) {
        if (resources == null) return List.of();
        List<AllocatedResource> result = new ArrayList<>();
        for (int index = 0; index < resources.size(); index++) {
            SuggestedResource item = resources.get(index);
            result.add(new AllocatedResource(
                    "LEGACY-" + (index + 1), "LEGACY_" + (index + 1),
                    item.resourceType(), item.resourceName(),
                    event.cityCode() == null ? "000000" : event.cityCode(),
                    event.cityName() == null ? "历史数据" : event.cityName(),
                    item.quantity(), item.unit(), item.purpose(), 0,
                    DispatchScope.LOCAL
            ));
        }
        return result;
    }

    private static <T> List<T> copy(List<T> items) {
        return items == null ? List.of() : List.copyOf(items);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
