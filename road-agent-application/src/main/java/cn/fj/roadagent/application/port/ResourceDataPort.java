package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.GeoPoint;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ResourceDataPort {
    List<EmergencyResource> search(ResourceQuery query);

    List<EmergencyResource> listActiveForPlanning(String eventType);

    /** 返回全部启用资源；正式工单的资源判断不再受预案或事件类型静态清单限制。 */
    default List<EmergencyResource> listAllActiveForPlanning() {
        return listActiveForPlanning(null);
    }

    List<EmergencyResource> lockByTypeCodes(Set<String> typeCodes);

    List<EmergencyResource> lockByResourceIds(Set<String> resourceIds);

    /** 从资源库读取城市中心坐标，用于同一次分配的稳定距离计算。 */
    Map<String, GeoPoint> cityCenters();

    boolean updateInventory(EmergencyResource resource, long expectedLockVersion);
}
