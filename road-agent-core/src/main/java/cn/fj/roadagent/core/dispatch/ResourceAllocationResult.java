package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.ResourceAllocation;
import cn.fj.roadagent.domain.dispatch.ResourceShortage;

import java.util.List;

record ResourceAllocationResult(
        List<ResourceAllocation> allocations,
        List<EmergencyResource> originalResources,
        List<EmergencyResource> updatedResources,
        List<ResourceShortage> shortages
) {
}
