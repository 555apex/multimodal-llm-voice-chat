package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;

/** 已筛选为应急事件、但尚未补齐event_type的贴源记录。 */
public record UnclassifiedEmergencyEvent(
        String eventId,
        Instant occurrenceTime,
        String description,
        String sourceName,
        String sourceOrgName,
        String place,
        String routeNo,
        String routeName
) {
    public UnclassifiedEmergencyEvent {
        eventId = requireText(eventId, "事件c_no不能为空");
        description = requireText(description, "事件描述不能为空");
        sourceName = normalize(sourceName);
        sourceOrgName = normalize(sourceOrgName);
        place = normalize(place);
        routeNo = normalize(routeNo);
        routeName = normalize(routeName);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
