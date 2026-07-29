package cn.fj.roadagent.interfaces.rest.emergency;

import cn.fj.roadagent.domain.dispatch.EmergencyEvent;

import java.time.Instant;

public record EmergencyEventResponse(
        String eventId,
        String customId,
        Instant occurrenceTime,
        String eventType,
        String eventTypeName,
        String description
) {
    public static EmergencyEventResponse from(EmergencyEvent event) {
        return new EmergencyEventResponse(
                event.eventId(),
                event.customId(),
                event.occurrenceTime(),
                event.eventType(),
                typeName(event.eventType()),
                event.description()
        );
    }

    private static String typeName(String type) {
        return switch (type) {
            case "DT01" -> "崩塌";
            case "ET101" -> "拥堵";
            default -> "异常事件";
        };
    }
}
