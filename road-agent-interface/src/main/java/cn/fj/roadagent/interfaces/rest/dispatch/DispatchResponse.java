package cn.fj.roadagent.interfaces.rest.dispatch;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.DispatchTask;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.WorkOrderReference;

import java.time.Instant;
import java.util.List;

public record DispatchResponse(
        String planId,
        EmergencyEvent event,
        String summary,
        List<DispatchTask> tasks,
        List<EmergencyResource> resources,
        List<String> warnings,
        DispatchStatus status,
        long version,
        Instant createdAt,
        WorkOrderReference workOrder,
        boolean approvalRequired
) {
    public static DispatchResponse from(DispatchPlan plan) {
        return new DispatchResponse(
                plan.planId(), plan.event(), plan.summary(), plan.tasks(), plan.resources(), plan.warnings(),
                plan.status(), plan.version(), plan.createdAt(), plan.workOrder(),
                plan.status() == DispatchStatus.WAITING_APPROVAL
        );
    }
}
