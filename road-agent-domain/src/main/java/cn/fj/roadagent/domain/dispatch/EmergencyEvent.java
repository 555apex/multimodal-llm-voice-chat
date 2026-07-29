package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;

/** 来自 w_abnormal_event 的正式应急事件。 */
public record EmergencyEvent(
        String eventId,
        String customId,
        Instant occurrenceTime,
        String eventType,
        String description
) {
    public EmergencyEvent {
        eventId = requireText(eventId, "事件ID不能为空");
        customId = customId == null ? "" : customId.trim();
        eventType = requireText(eventType, "事件类型不能为空");
        description = requireText(description, "事件描述不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
