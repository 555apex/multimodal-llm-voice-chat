package cn.fj.roadagent.interfaces.rest.facility;

import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlert;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record FacilityAlertResponse(
        long alertId, String facilityName, String metricName,
        BigDecimal actualValue, String actualStringValue,
        BigDecimal thresholdMin, BigDecimal thresholdMax,
        AlarmLevel alarmLevel, String alarmLevelName,
        Instant collectTime, Instant triggerTime,
        FacilityAlertStatus status, String statusName,
        String remark, String thresholdAssessment, boolean sourceConsistent
) {
    public static FacilityAlertResponse from(FacilityAlert alert) {
        Assessment assessment = assess(alert);
        return new FacilityAlertResponse(
                alert.alertId(), alert.facilityName(), alert.metricName(),
                alert.actualValue(), alert.actualStringValue(),
                alert.thresholdMin(), alert.thresholdMax(), alert.alarmLevel(),
                alert.alarmLevel().displayName(), alert.collectTime(), alert.triggerTime(),
                alert.status(), alert.status().displayName(), alert.remark(),
                assessment.label(), assessment.consistent()
        );
    }

    private static Assessment assess(FacilityAlert alert) {
        BigDecimal actual = alert.actualValue();
        if (actual != null) {
            if (alert.thresholdMax() != null && actual.compareTo(alert.thresholdMax()) > 0) {
                return new Assessment("超过上限", true);
            }
            if (alert.thresholdMin() != null && actual.compareTo(alert.thresholdMin()) < 0) {
                return new Assessment("低于下限", true);
            }
            if (alert.thresholdMin() != null || alert.thresholdMax() != null) {
                return new Assessment("与阈值快照不一致", false);
            }
        }
        if (alert.actualStringValue() != null) {
            return new Assessment("状态型异常", true);
        }
        return new Assessment("异常依据待核验", false);
    }

    private record Assessment(String label, boolean consistent) {
    }
}
