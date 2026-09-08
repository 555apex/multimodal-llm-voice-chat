package cn.fj.roadagent.domain.facility;

import java.math.BigDecimal;
import java.time.Instant;

public record FacilityAlert(
        long alertId,
        String facilityName,
        String metricName,
        BigDecimal actualValue,
        String actualStringValue,
        BigDecimal thresholdMin,
        BigDecimal thresholdMax,
        AlarmLevel alarmLevel,
        Instant collectTime,
        Instant triggerTime,
        FacilityAlertStatus status,
        String remark
) {
    public FacilityAlert {
        if (alertId <= 0) throw new IllegalArgumentException("设施告警ID必须大于0");
        facilityName = normalize(facilityName, "设施名称缺失（ID " + alertId + "）");
        metricName = normalize(metricName, "异常指标缺失");
        if (alarmLevel == null) throw new IllegalArgumentException("设施告警等级不能为空");
        if (collectTime == null) throw new IllegalArgumentException("采集时间不能为空");
        if (triggerTime == null) throw new IllegalArgumentException("触发时间不能为空");
        if (status == null) throw new IllegalArgumentException("设施告警状态不能为空");
        actualStringValue = trimToNull(actualStringValue);
        remark = trimToNull(remark);
    }

    private static String normalize(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
