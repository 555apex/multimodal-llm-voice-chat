package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.QueryHighwayTrafficUseCase;

/** REST 交通查询统一入口，按查询类型路由到独立路况或容量服务。 */
public final class UnifiedTrafficQueryService implements QueryHighwayTrafficUseCase {
    private final HighwayTrafficService trafficService;
    private final RoadCapacityService capacityService;
    private final RegionalTrafficService regionalTrafficService;
    private final VehiclePatternService vehiclePatternService;
    private final OdTrafficService odTrafficService;

    public UnifiedTrafficQueryService(
            HighwayTrafficService trafficService,
            RoadCapacityService capacityService,
            RegionalTrafficService regionalTrafficService,
            VehiclePatternService vehiclePatternService
    ) {
        this(trafficService, capacityService, regionalTrafficService, vehiclePatternService, null);
    }

    public UnifiedTrafficQueryService(HighwayTrafficService trafficService, RoadCapacityService capacityService,
            RegionalTrafficService regionalTrafficService, VehiclePatternService vehiclePatternService,
            OdTrafficService odTrafficService) {
        this.trafficService = trafficService;
        this.capacityService = capacityService;
        this.regionalTrafficService = regionalTrafficService;
        this.vehiclePatternService = vehiclePatternService;
        this.odTrafficService = odTrafficService;
    }

    @Override
    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null) {
            throw new BusinessRuleException("TRAFFIC_QUERY_TYPE_REQUIRED", "请说明要查询的交通范围");
        }
        if (query.queryType().odQuery()) return odTrafficService.query(query);
        if (query.queryType().capacityQuery()) return capacityService.query(query);
        if (query.queryType().regionalTrafficQuery()) return regionalTrafficService.query(query);
        if (query.queryType().vehiclePatternQuery()) return vehiclePatternService.query(query);
        return trafficService.query(query);
    }
}
