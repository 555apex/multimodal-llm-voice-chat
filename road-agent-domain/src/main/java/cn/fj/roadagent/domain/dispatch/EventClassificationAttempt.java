package cn.fj.roadagent.domain.dispatch;

import java.time.Instant;

/** 事件分类过程的只增不改留痕。 */
public record EventClassificationAttempt(
        String classificationId,
        String eventId,
        int attemptNumber,
        EventClassificationMethod method,
        EventClassificationStatus status,
        String eventType,
        double confidence,
        String evidence,
        String modelName,
        String errorMessage,
        String idempotencyKey,
        Instant createdAt
) {
    public EventClassificationAttempt {
        if (classificationId == null || classificationId.isBlank()) throw new IllegalArgumentException("分类记录ID不能为空");
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("事件c_no不能为空");
        if (attemptNumber < 1) throw new IllegalArgumentException("分类尝试次数必须大于0");
        if (method == null || status == null || createdAt == null) throw new IllegalArgumentException("分类留痕字段不完整");
        eventType = normalize(eventType);
        if (eventType != null) eventType = EmergencyEventType.require(eventType).name();
        confidence = Math.max(0, Math.min(1, confidence));
        evidence = normalize(evidence);
        modelName = normalize(modelName);
        errorMessage = normalize(errorMessage);
        idempotencyKey = normalize(idempotencyKey);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
