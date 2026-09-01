package cn.fj.roadagent.domain.traffic;

/** MySQL 国省干线交通与通行能力查询支持的业务范围。 */
public enum TrafficQueryType {
    PROVINCE_OVERVIEW,
    PROVINCE_ABNORMAL,
    CITY_PAIR,
    ROUTE_DETAIL,
    CAPACITY_OVERVIEW,
    CAPACITY_BOTTLENECKS,
    CAPACITY_ROUTE_DETAIL,
    REGIONAL_TRAFFIC_OVERVIEW,
    CHECKPOINT_PRESSURE,
    CITY_PRESSURE,
    ROUTE_PRESSURE,
    VEHICLE_PATTERN_OVERVIEW,
    VEHICLE_STRUCTURE,
    VEHICLE_HOURLY_PATTERN,
    VEHICLE_DAY_TYPE_COMPARISON;

    public boolean capacityQuery() {
        return this == CAPACITY_OVERVIEW
                || this == CAPACITY_BOTTLENECKS
                || this == CAPACITY_ROUTE_DETAIL;
    }

    public boolean regionalTrafficQuery() {
        return this == REGIONAL_TRAFFIC_OVERVIEW
                || this == CHECKPOINT_PRESSURE
                || this == CITY_PRESSURE
                || this == ROUTE_PRESSURE;
    }

    public boolean vehiclePatternQuery() {
        return this == VEHICLE_PATTERN_OVERVIEW
                || this == VEHICLE_STRUCTURE
                || this == VEHICLE_HOURLY_PATTERN
                || this == VEHICLE_DAY_TYPE_COMPARISON;
    }
}
