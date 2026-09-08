package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;

/** 来自 w_lw_incident 的正式应急事件，eventId始终为 c_no。 */
public record EmergencyEvent(
        String eventId,
        String customId,
        Instant occurrenceTime,
        String eventType,
        String description,
        String cityCode,
        String cityName,
        String sourceName,
        String sourceOrgName,
        String place,
        String routeNo,
        String routeName,
        Double longitude,
        Double latitude
) {
    public EmergencyEvent {
        eventId = requireText(eventId, "事件ID不能为空");
        customId = customId == null ? "" : customId.trim();
        eventType = EmergencyEventType.require(requireText(eventType, "事件类型不能为空")).name();
        description = requireText(description, "事件描述不能为空");
        cityCode = normalize(cityCode);
        cityName = normalize(cityName);
        if ((cityCode == null) != (cityName == null)) {
            throw new IllegalArgumentException("事件城市编码和名称必须同时提供");
        }
        sourceName = normalize(sourceName);
        sourceOrgName = normalize(sourceOrgName);
        place = normalize(place);
        routeNo = normalize(routeNo);
        routeName = normalize(routeName);
    }

    public EmergencyEvent(
            String eventId, String customId, Instant occurrenceTime, String eventType,
            String description, String cityCode, String cityName
    ) {
        this(eventId, customId, occurrenceTime, eventType, description, cityCode, cityName,
                null, null, null, null, null, null, null);
    }

    public EmergencyEvent(
            String eventId,
            String customId,
            Instant occurrenceTime,
            String eventType,
            String description
    ) {
        this(eventId, customId, occurrenceTime, eventType, description, null, null,
                null, null, null, null, null, null, null);
    }

    public boolean hasStructuredCity() {
        return cityCode != null && cityName != null;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
