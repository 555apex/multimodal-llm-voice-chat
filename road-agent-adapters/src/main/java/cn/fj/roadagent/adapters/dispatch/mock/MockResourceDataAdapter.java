package cn.fj.roadagent.adapters.dispatch.mock;

import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.GeoPoint;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 没有甲方资源接口前，用可控台账打通调度流程。 */
public final class MockResourceDataAdapter implements ResourceDataPort {

    private final List<EmergencyResource> resources = List.of(
            new EmergencyResource("RES-FZ-001", "抢险队伍", "福州公路抢险一队", "福州", "道路抢通、边坡处置", true),
            new EmergencyResource("RES-FZ-002", "挖掘机", "福州应急挖掘机组", "福州", "土方清理、塌方处置", true),
            new EmergencyResource("RES-XM-001", "抢险队伍", "厦门道路应急队", "厦门", "道路抢通、交通组织", true),
            new EmergencyResource("RES-QZ-001", "吊车", "泉州重型吊装组", "泉州", "车辆移除、障碍吊装", true),
            new EmergencyResource("RES-QZ-002", "警示设施", "泉州安全设施库", "泉州", "警示牌、锥桶、照明", true),
            new EmergencyResource("RES-ND-001", "抢险队伍", "宁德公路抢险队", "宁德", "水毁和边坡处置", false)
    );

    @Override
    public List<EmergencyResource> search(ResourceQuery query) {
        return resources.stream()
                .filter(EmergencyResource::available)
                .filter(resource -> sameCity(resource.city(), query.city()))
                .filter(resource -> matchesType(resource, query.resourceTypes()))
                .toList();
    }

    @Override
    public List<EmergencyResource> listActiveForPlanning(String eventType) {
        return resources.stream().filter(EmergencyResource::available).toList();
    }

    @Override
    public List<EmergencyResource> lockByTypeCodes(Set<String> typeCodes) {
        return resources.stream().filter(item -> typeCodes.contains(item.typeCode())).toList();
    }

    @Override
    public List<EmergencyResource> lockByResourceIds(Set<String> resourceIds) {
        return resources.stream().filter(item -> resourceIds.contains(item.resourceId())).toList();
    }

    @Override
    public Map<String, GeoPoint> cityCenters() {
        return Map.of("000000", new GeoPoint(119.2965, 26.0745));
    }

    @Override
    public boolean updateInventory(EmergencyResource resource, long expectedLockVersion) {
        throw new UnsupportedOperationException("Mock资源不支持正式库存更新");
    }

    private boolean sameCity(String resourceCity, String queryCity) {
        return queryCity != null && queryCity.contains(resourceCity);
    }

    private boolean matchesType(EmergencyResource resource, List<String> types) {
        if (types == null || types.isEmpty()) {
            return true;
        }
        String searchable = (resource.type() + resource.name() + resource.capability()).toLowerCase(Locale.ROOT);
        return types.stream().anyMatch(type -> type != null && searchable.contains(type.toLowerCase(Locale.ROOT)));
    }
}
