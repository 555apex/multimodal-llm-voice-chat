package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.ResourceAllocation;

import java.util.List;

public interface ResourceAllocationPort {
    boolean insert(ResourceAllocation allocation);

    List<ResourceAllocation> findByPlanVersion(String planId, long planVersion);

    boolean update(ResourceAllocation allocation, ResourceAllocation expected);
}
