package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;

import java.util.List;
import java.util.Set;

public interface ResourceDataPort {
    List<EmergencyResource> search(ResourceQuery query);

    List<EmergencyResource> listActiveForPlanning(String eventType);

    List<EmergencyResource> lockByTypeCodes(Set<String> typeCodes);

    List<EmergencyResource> lockByResourceIds(Set<String> resourceIds);

    boolean updateInventory(EmergencyResource resource, long expectedLockVersion);
}
