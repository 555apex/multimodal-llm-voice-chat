package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 三级批准时冻结的本地正式通告，不代表已经发送到外部平台。 */
public record NoticeSnapshot(
        String noticeNumber,
        String title,
        EmergencyEvent event,
        String planId,
        long planVersion,
        List<ResourceRequirement> resourceRequirements,
        List<AllocatedResource> allocatedResources,
        List<ResourceShortage> resourceShortages,
        String rescuePlan,
        EventSeverity eventSeverity,
        String impactAssessment,
        String coordinationRequirements,
        String professionalOpinion,
        String commandOpinion,
        Instant publishedAt,
        EmergencyResponsePlanSnapshot responsePlan
) {
    public NoticeSnapshot {
        noticeNumber = requireText(noticeNumber, "通告编号不能为空");
        title = requireText(title, "通告标题不能为空");
        event = Objects.requireNonNull(event, "通告事件不能为空");
        planId = requireText(planId, "planId不能为空");
        if (planVersion < 1) throw new IllegalArgumentException("通告方案版本必须大于0");
        resourceRequirements = resourceRequirements == null
                ? List.of() : List.copyOf(resourceRequirements);
        allocatedResources = allocatedResources == null
                ? List.of() : List.copyOf(allocatedResources);
        resourceShortages = resourceShortages == null
                ? List.of() : List.copyOf(resourceShortages);
        rescuePlan = requireText(rescuePlan, "救援方案不能为空");
        eventSeverity = Objects.requireNonNull(eventSeverity, "事件初判等级不能为空");
        impactAssessment = requireText(impactAssessment, "影响研判不能为空");
        coordinationRequirements = normalize(coordinationRequirements);
        professionalOpinion = requireText(professionalOpinion, "专业意见不能为空");
        commandOpinion = normalize(commandOpinion);
        publishedAt = Objects.requireNonNull(publishedAt, "通告时间不能为空");
    }

    /** 兼容没有预案快照的历史通告。 */
    public NoticeSnapshot(
            String noticeNumber, String title, EmergencyEvent event,
            String planId, long planVersion,
            List<ResourceRequirement> resourceRequirements,
            List<AllocatedResource> allocatedResources,
            List<ResourceShortage> resourceShortages,
            String rescuePlan, EventSeverity eventSeverity, String impactAssessment,
            String coordinationRequirements, String professionalOpinion,
            String commandOpinion, Instant publishedAt
    ) {
        this(noticeNumber, title, event, planId, planVersion, resourceRequirements,
                allocatedResources, resourceShortages, rescuePlan, eventSeverity,
                impactAssessment, coordinationRequirements, professionalOpinion,
                commandOpinion, publishedAt, null);
    }

    /** 兼容旧测试构造。 */
    public NoticeSnapshot(
            String noticeNumber, String title, EmergencyEvent event,
            String planId, long planVersion, List<SuggestedResource> suggestedResources,
            String rescuePlan, EventSeverity eventSeverity, String impactAssessment,
            String coordinationRequirements, String professionalOpinion,
            String commandOpinion, Instant publishedAt
    ) {
        this(noticeNumber, title, event, planId, planVersion,
                suggestedResources == null ? List.of() : suggestedResources.stream()
                        .map(item -> new ResourceRequirement(
                                item.resourceType(), item.resourceType(), item.quantity(),
                                item.unit(), item.purpose()))
                        .toList(),
                suggestedResources == null ? List.of() : suggestedResources.stream()
                        .map(item -> new AllocatedResource(
                                item.resourceName(), item.resourceType(), item.resourceType(),
                                item.resourceName(),
                                event.cityCode() == null ? "000000" : event.cityCode(),
                                event.cityName() == null ? "历史数据" : event.cityName(),
                                item.quantity(), item.unit(), item.purpose(), 0,
                                DispatchScope.LOCAL))
                        .toList(),
                List.of(), rescuePlan, eventSeverity, impactAssessment,
                coordinationRequirements, professionalOpinion, commandOpinion, publishedAt, null);
    }

    public List<SuggestedResource> suggestedResources() {
        return allocatedResources.stream().map(AllocatedResource::compatibilityProjection).toList();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
