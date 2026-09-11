package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.LocalDate;
import java.util.List;

public record HighwayTrafficQuery(
        TrafficQueryType queryType,
        String originCity,
        String destinationCity,
        String routeCode,
        String routeName,
        List<String> selectedCities,
        String analysisCity,
        String traceId,
        boolean includeTrend,
        boolean trendOnly,
        LocalDate analysisDate
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
            List<String> selectedCities,
            String analysisCity,
            String traceId
    ) {
        this(queryType, originCity, destinationCity, routeCode, routeName,
                selectedCities, analysisCity, traceId, false, false, null);
    }

    public HighwayTrafficQuery(
            TrafficQueryType queryType,
            String originCity,
            String destinationCity,
            String routeCode,
            String routeName,
            List<String> selectedCities,
            String analysisCity,
            String traceId,
            boolean includeTrend
    ) {
        this(queryType, originCity, destinationCity, routeCode, routeName,
                selectedCities, analysisCity, traceId, includeTrend, false, null);
    }

    public HighwayTrafficQuery(
            TrafficQueryType queryType,
            String originCity,
            String destinationCity,
            String routeCode,
            String routeName,
            String traceId
    ) {
        this(queryType, originCity, destinationCity, routeCode, routeName, List.of(), null,
                traceId, false, false, null);
    }

    /** 兼容增加分析日期前的完整构造方式。 */
    public HighwayTrafficQuery(
            TrafficQueryType queryType,
            String originCity,
            String destinationCity,
            String routeCode,
            String routeName,
            List<String> selectedCities,
            String analysisCity,
            String traceId,
            boolean includeTrend,
            boolean trendOnly
    ) {
        this(queryType, originCity, destinationCity, routeCode, routeName,
                selectedCities, analysisCity, traceId, includeTrend, trendOnly, null);
    }
}
