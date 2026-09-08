package cn.fj.roadagent.core.facility;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.facility.FacilityAlertCounts;
import cn.fj.roadagent.application.facility.FacilityAlertTransitionCommand;
import cn.fj.roadagent.application.port.FacilityAlertPort;
import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlert;
import cn.fj.roadagent.domain.facility.FacilityAlertResolution;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;
import cn.fj.roadagent.domain.facility.FacilityHealthState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityAlertServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-08T02:00:00Z");

    @Test
    void shouldBuildDeterministicReportAndFocusOrderFromActiveAlerts() {
        FakePort port = new FakePort(List.of(
                alert(1, "青云山隧道", "拱顶沉降", AlarmLevel.SEVERE,
                        FacilityAlertStatus.PENDING, NOW.minusSeconds(600)),
                alert(2, "闽江大桥", "主梁应变", AlarmLevel.EMERGENCY,
                        FacilityAlertStatus.CONFIRMED, NOW.minusSeconds(300)),
                alert(3, "闽江大桥", "桥墩位移", AlarmLevel.WARNING,
                        FacilityAlertStatus.PENDING, NOW.minusSeconds(900)),
                alert(4, "已关闭设施", "裂缝宽度", AlarmLevel.EMERGENCY,
                        FacilityAlertStatus.CLOSED, NOW.minusSeconds(1200))
        ));
        FacilityAlertService service = service(port);

        var report = service.healthReport();

        assertEquals(3, report.activeAlertCount());
        assertEquals(2, report.affectedFacilityCount());
        assertEquals(FacilityHealthState.DANGER, report.overallHealth());
        assertEquals("闽江大桥", report.facilities().get(0).facilityName());
        assertEquals(List.of("主梁应变", "桥墩位移"), report.facilities().get(0).metricNames());
        assertTrue(report.summary().contains("紧急1条、严重1条、警告1条"));
        assertEquals(List.of("闽江大桥"), service.focus(1).stream()
                .map(item -> item.facilityName()).toList());
    }

    @Test
    void shouldEnforceTwoStepTransitionAndOverwriteLatestRemark() {
        FakePort port = new FakePort(List.of(alert(
                9, "G3边坡", "表面位移", AlarmLevel.SEVERE,
                FacilityAlertStatus.PENDING, NOW.minusSeconds(60)
        )));
        FacilityAlertService service = service(port);

        var confirmed = service.transition(new FacilityAlertTransitionCommand(
                9, FacilityAlertStatus.PENDING, FacilityAlertStatus.CONFIRMED,
                null, "  现场人员已出发  "
        ));
        assertEquals(FacilityAlertStatus.CONFIRMED, confirmed.status());
        assertEquals("【已确认】现场人员已出发", confirmed.remark());

        var closed = service.transition(new FacilityAlertTransitionCommand(
                9, FacilityAlertStatus.CONFIRMED, FacilityAlertStatus.CLOSED,
                FacilityAlertResolution.RESOLVED, "传感器复核恢复正常"
        ));
        assertEquals(FacilityAlertStatus.CLOSED, closed.status());
        assertEquals("【已消除】传感器复核恢复正常", closed.remark());
    }

    @Test
    void shouldRejectSkippingConfirmation() {
        FacilityAlertService service = service(new FakePort(List.of(alert(
                1, "桥梁A", "位移", AlarmLevel.WARNING,
                FacilityAlertStatus.PENDING, NOW
        ))));

        BusinessRuleException error = assertThrows(BusinessRuleException.class,
                () -> service.transition(new FacilityAlertTransitionCommand(
                        1, FacilityAlertStatus.PENDING, FacilityAlertStatus.CLOSED,
                        FacilityAlertResolution.IGNORED, "误报"
                )));
        assertEquals("FACILITY_ALERT_INVALID_TRANSITION", error.errorCode());
    }

    private FacilityAlertService service(FakePort port) {
        return new FacilityAlertService(port, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static FacilityAlert alert(
            long id, String name, String metric, AlarmLevel level,
            FacilityAlertStatus status, Instant trigger
    ) {
        return new FacilityAlert(
                id, name, metric, new BigDecimal("12.5"), null,
                BigDecimal.ZERO, BigDecimal.TEN, level,
                trigger.plusSeconds(30), trigger, status, null
        );
    }

    private static final class FakePort implements FacilityAlertPort {
        private final List<FacilityAlert> alerts;

        private FakePort(List<FacilityAlert> alerts) {
            this.alerts = new ArrayList<>(alerts);
        }

        @Override
        public List<FacilityAlert> findPage(
                FacilityAlertStatus status, AlarmLevel level, int offset, int limit
        ) {
            return alerts.stream().filter(alert -> alert.status() == status)
                    .filter(alert -> level == null || alert.alarmLevel() == level)
                    .skip(offset).limit(limit).toList();
        }

        @Override
        public long count(FacilityAlertStatus status, AlarmLevel level) {
            return findPage(status, level, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public FacilityAlertCounts counts() {
            return new FacilityAlertCounts(
                    alerts.stream().filter(a -> a.status() == FacilityAlertStatus.PENDING).count(),
                    alerts.stream().filter(a -> a.status() == FacilityAlertStatus.CONFIRMED).count(),
                    alerts.stream().filter(a -> a.status() == FacilityAlertStatus.CLOSED).count(),
                    1, 1, 1, NOW
            );
        }

        @Override
        public List<FacilityAlert> findActive() {
            return alerts.stream().filter(a -> a.status() != FacilityAlertStatus.CLOSED).toList();
        }

        @Override
        public Optional<FacilityAlert> findById(long alertId) {
            return alerts.stream().filter(alert -> alert.alertId() == alertId).findFirst();
        }

        @Override
        public boolean updateStatus(
                long alertId, FacilityAlertStatus expectedStatus,
                FacilityAlertStatus targetStatus, String remark
        ) {
            for (int index = 0; index < alerts.size(); index++) {
                FacilityAlert current = alerts.get(index);
                if (current.alertId() == alertId && current.status() == expectedStatus) {
                    alerts.set(index, new FacilityAlert(
                            current.alertId(), current.facilityName(), current.metricName(),
                            current.actualValue(), current.actualStringValue(),
                            current.thresholdMin(), current.thresholdMax(), current.alarmLevel(),
                            current.collectTime(), current.triggerTime(), targetStatus, remark
                    ));
                    return true;
                }
            }
            return false;
        }
    }
}
