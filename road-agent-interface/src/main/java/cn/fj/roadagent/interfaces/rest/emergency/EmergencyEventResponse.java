package cn.fj.roadagent.interfaces.rest.emergency;

import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyEventType;

import java.time.Instant;

public record EmergencyEventResponse(
        String eventId,
        String customId,
        Instant occurrenceTime,
        String eventType,
        String eventTypeName,
        String description,
        String cityCode,
        String cityName,
        String sourceName,
        String sourceOrgName,
        String place,
        String routeNo,
        String routeName
) {
    public static EmergencyEventResponse from(EmergencyEvent event) {
        return new EmergencyEventResponse(
                event.eventId(),
                event.customId(),
                event.occurrenceTime(),
                event.eventType(),
                typeName(event.eventType()),
                event.description(),
                event.cityCode(),
                event.cityName(),
                event.sourceName(),
                event.sourceOrgName(),
                event.place(),
                event.routeNo(),
                event.routeName()
        );
    }

    private static String typeName(String type) {
        return EmergencyEventType.fromCode(type)
                .map(EmergencyEventType::displayName).orElse("异常事件");
    }
}
