package cn.fj.roadagent.interfaces.rest.dispatch;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;
import cn.fj.roadagent.domain.dispatch.AllocatedResource;
import cn.fj.roadagent.domain.dispatch.ResourceRequirement;
import cn.fj.roadagent.domain.dispatch.ResourceShortage;

import java.time.Instant;
import java.util.List;

public record DispatchResponse(
        String planId,
        EmergencyEvent event,
        List<SuggestedResource> suggestedResources,
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
        boolean approvalRequired
) {
    public static DispatchResponse from(DispatchPlan plan) {
        return new DispatchResponse(
                plan.planId(),
                plan.event(),
                plan.suggestedResources(),
                plan.resourceRequirements(),
                plan.allocatedResources(),
                plan.resourceShortages(),
                plan.rescuePlan(),
                plan.status(),
                plan.version(),
                plan.createdAt(),
                plan.updatedAt(),
                plan.rejectionReason(),
                plan.errorMessage(),
                plan.status() == DispatchStatus.WAITING_APPROVAL
        );
    }
}
