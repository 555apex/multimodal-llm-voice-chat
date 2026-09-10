package cn.fj.roadagent.application.agent;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.util.List;
import java.util.Optional;

/** DeepSeek生成的计划草案。所有字段仍需Java再次校验。 */
public record AgentDecision(
        String intent,
        String trafficScope,
        String originCity,
        String destinationCity,
        String routeCode,
        String routeName,
        List<String> selectedCities,
        String analysisCity,
        String city,
        String areaName,
        String roadName,
        String direction,
        String eventType,
        String location,
        String severity,
        String eventDescription,
        List<String> resourceTypes,
        String clarification,
        Boolean includeTrend,
        String analysisDate
) {
    public AgentDecision {
        selectedCities = selectedCities == null ? List.of() : List.copyOf(selectedCities);
        resourceTypes = resourceTypes == null ? List.of() : List.copyOf(resourceTypes);
    }

    /** 保留旧测试与应急调用方的构造方式；新增交通字段默认为空。 */
    public AgentDecision(
            String intent,
            String trafficScope,
            String city,
            String areaName,
            String roadName,
            String direction,
            String eventType,
            String location,
            String severity,
            String eventDescription,
            List<String> resourceTypes,
            String clarification
    ) {
        this(intent, trafficScope, null, null, null, null, List.of(), null,
                city, areaName, roadName, direction,
                eventType, location, severity, eventDescription, resourceTypes, clarification, null, null);
    }

    /** 保留交通功能扩展前的完整构造方式。 */
    public AgentDecision(
            String intent,
            String trafficScope,
            String originCity,
            String destinationCity,
            String routeCode,
            String routeName,
            String city,
            String areaName,
            String roadName,
            String direction,
            String eventType,
            String location,
            String severity,
            String eventDescription,
            List<String> resourceTypes,
            String clarification
    ) {
        this(intent, trafficScope, originCity, destinationCity, routeCode, routeName,
                List.of(), null, city, areaName, roadName, direction, eventType, location,
                severity, eventDescription, resourceTypes, clarification, null, null);
    }

    /** 保留增加趋势开关前的完整构造契约。 */
    public AgentDecision(
            String intent,
            String trafficScope,
            String originCity,
            String destinationCity,
            String routeCode,
            String routeName,
            List<String> selectedCities,
            String analysisCity,
            String city,
            String areaName,
            String roadName,
            String direction,
            String eventType,
            String location,
            String severity,
            String eventDescription,
            List<String> resourceTypes,
            String clarification
    ) {
        this(intent, trafficScope, originCity, destinationCity, routeCode, routeName,
                selectedCities, analysisCity, city, areaName, roadName, direction, eventType, location,
                severity, eventDescription, resourceTypes, clarification, null, null);
    }

    /** 兼容增加车型分析日期前的完整构造契约。 */
    public AgentDecision(
            String intent,
            String trafficScope,
            String originCity,
            String destinationCity,
            String routeCode,
            String routeName,
            List<String> selectedCities,
            String analysisCity,
            String city,
            String areaName,
            String roadName,
            String direction,
            String eventType,
            String location,
            String severity,
            String eventDescription,
            List<String> resourceTypes,
            String clarification,
            Boolean includeTrend
    ) {
        this(intent, trafficScope, originCity, destinationCity, routeCode, routeName,
                selectedCities, analysisCity, city, areaName, roadName, direction, eventType, location,
                severity, eventDescription, resourceTypes, clarification, includeTrend, null);
    }

    public AgentIntent parsedIntent() {
        try {
            return AgentIntent.valueOf(intent == null ? "" : intent.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return AgentIntent.UNSUPPORTED;
        }
    }

    public Optional<TrafficQueryType> parsedTrafficQueryType() {
        try {
            return Optional.of(TrafficQueryType.valueOf(
                    trafficScope == null ? "" : trafficScope.trim().toUpperCase()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

}
