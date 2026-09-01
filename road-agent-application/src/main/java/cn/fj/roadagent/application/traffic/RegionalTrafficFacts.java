package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

public record RegionalTrafficFacts(
        TrafficQueryType queryType,
        String title,
        List<SelectedRegionResultItem> selectedRegions,
        List<TransportHubResultItem> hubRows,
        List<RegionPressureResultItem> regionRows,
        List<RoutePressureResultItem> routeRows,
        int totalHubCount,
        int totalRegionCount,
        int totalRouteCount,
        Instant acquiredAt
) {
    public RegionalTrafficFacts {
        selectedRegions = selectedRegions == null ? List.of() : List.copyOf(selectedRegions);
        hubRows = hubRows == null ? List.of() : List.copyOf(hubRows);
        regionRows = regionRows == null ? List.of() : List.copyOf(regionRows);
        routeRows = routeRows == null ? List.of() : List.copyOf(routeRows);
    }
}
