package cn.fj.roadagent.application.agent;

import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

import java.util.List;
import java.util.Optional;

/** DeepSeek生成的计划草案。所有字段仍需Java再次校验。 */
public record AgentDecision(
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
    public AgentDecision {
        resourceTypes = resourceTypes == null ? List.of() : List.copyOf(resourceTypes);
    }

    public AgentIntent parsedIntent() {
        try {
            return AgentIntent.valueOf(intent == null ? "" : intent.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return AgentIntent.UNSUPPORTED;
        }
    }

    public Optional<TrafficQueryScope> parsedTrafficScope() {
        try {
            return Optional.of(TrafficQueryScope.valueOf(
                    trafficScope == null ? "" : trafficScope.trim().toUpperCase()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
