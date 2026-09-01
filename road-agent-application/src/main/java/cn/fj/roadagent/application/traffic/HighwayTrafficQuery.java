package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.util.List;

public record HighwayTrafficQuery(
        TrafficQueryType queryType,
        String originCity,
        String destinationCity,
        String routeCode,
        String routeName,
        List<String> selectedCities,
        String analysisCity,
        String traceId
) {
    public HighwayTrafficQuery {
        selectedCities = selectedCities == null ? List.of() : List.copyOf(selectedCities);
    }

    public HighwayTrafficQuery(
            TrafficQueryType queryType,
            String originCity,
            String destinationCity,
            String routeCode,
            String routeName,
            String traceId
    ) {
        this(queryType, originCity, destinationCity, routeCode, routeName, List.of(), null, traceId);
    }
}
