package cn.fj.roadagent.core.facility;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.facility.FacilityAlertCounts;
import cn.fj.roadagent.application.facility.FacilityAlertPage;
import cn.fj.roadagent.application.facility.FacilityAlertTransitionCommand;
import cn.fj.roadagent.application.facility.FacilityFocusItem;
import cn.fj.roadagent.application.facility.FacilityHealthReport;
import cn.fj.roadagent.application.facility.QueryFacilityAlertsUseCase;
import cn.fj.roadagent.application.facility.TransitionFacilityAlertUseCase;
import cn.fj.roadagent.application.port.FacilityAlertPort;
import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlert;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;
import cn.fj.roadagent.domain.facility.FacilityHealthState;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** w_realtime_abnormal 的确定性查询、汇总和状态流转。 */
public final class FacilityAlertService implements
        QueryFacilityAlertsUseCase, TransitionFacilityAlertUseCase {

    private static final Comparator<FacilityFocusItem> FOCUS_ORDER = Comparator
            .comparingInt((FacilityFocusItem item) -> item.highestAlarmLevel().databaseValue())
            .reversed()
            .thenComparing(Comparator.comparingLong(FacilityFocusItem::activeAlertCount).reversed())
            .thenComparing(FacilityFocusItem::oldestTriggerTime)
            .thenComparing(FacilityFocusItem::facilityName);

    private final FacilityAlertPort alertPort;
    private final Clock clock;

    public FacilityAlertService(FacilityAlertPort alertPort, Clock clock) {
        this.alertPort = alertPort;
        this.clock = clock;
    }

    @Override
    public FacilityAlertPage query(
            FacilityAlertStatus status, AlarmLevel alarmLevel, int page, int size
    ) {
        FacilityAlertStatus resolvedStatus = status == null ? FacilityAlertStatus.PENDING : status;
        if (page < 0) throw new IllegalArgumentException("页码不能小于0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("每页数量必须在1到100之间");
        return new FacilityAlertPage(
                alertPort.findPage(resolvedStatus, alarmLevel, page * size, size),
                page,
                size,
                alertPort.count(resolvedStatus, alarmLevel),
                alertPort.counts()
        );
    }

    @Override
    public FacilityHealthReport healthReport() {
        List<FacilityAlert> active = alertPort.findActive();
        List<FacilityFocusItem> facilities = aggregate(active);
        long warning = countLevel(active, AlarmLevel.WARNING);
        long severe = countLevel(active, AlarmLevel.SEVERE);
        long emergency = countLevel(active, AlarmLevel.EMERGENCY);
        long pending = countStatus(active, FacilityAlertStatus.PENDING);
        long confirmed = countStatus(active, FacilityAlertStatus.CONFIRMED);
        AlarmLevel highest = active.stream().map(FacilityAlert::alarmLevel)
                .max(Comparator.comparingInt(AlarmLevel::databaseValue)).orElse(null);
        FacilityAlertCounts counts = alertPort.counts();
        String summary = summary(active.size(), facilities, warning, severe, emergency);
        return new FacilityHealthReport(
                clock.instant(), counts.dataAsOf(), active.size(), facilities.size(),
                warning, severe, emergency, pending, confirmed,
                FacilityHealthState.from(highest), summary, facilities
        );
    }

    @Override
    public List<FacilityFocusItem> focus(int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("重点关注对象数量必须在1到50之间");
        }
        return aggregate(alertPort.findActive()).stream().limit(limit).toList();
    }

    @Override
    public FacilityAlert transition(FacilityAlertTransitionCommand command) {
        if (command == null) throw new IllegalArgumentException("状态流转请求不能为空");
        if (command.alertId() <= 0) throw new IllegalArgumentException("设施告警ID必须大于0");
        if (command.expectedStatus() == null || command.targetStatus() == null) {
            throw new IllegalArgumentException("原状态和目标状态不能为空");
        }
        String remark = normalizeRemark(command.remark());
        String storedRemark;
        if (command.expectedStatus() == FacilityAlertStatus.PENDING
                && command.targetStatus() == FacilityAlertStatus.CONFIRMED) {
            if (command.resolutionType() != null) {
                throw invalidTransition("确认告警时不能填写结束类型");
            }
            storedRemark = "【已确认】" + remark;
        } else if (command.expectedStatus() == FacilityAlertStatus.CONFIRMED
                && command.targetStatus() == FacilityAlertStatus.CLOSED) {
            if (command.resolutionType() == null) {
                throw invalidTransition("结束告警时必须选择已消除或已忽略");
            }
            storedRemark = "【" + command.resolutionType().displayName() + "】" + remark;
        } else {
            throw invalidTransition("设施告警只允许待确认→处理中→已结束");
        }

        if (alertPort.updateStatus(
                command.alertId(), command.expectedStatus(), command.targetStatus(), storedRemark
        )) {
            return requireAlert(command.alertId());
        }

        FacilityAlert current = requireAlert(command.alertId());
        if (current.status() == command.targetStatus()) return current;
        throw new BusinessRuleException(
                "FACILITY_ALERT_STATUS_CONFLICT", "告警状态已发生变化，请刷新后重试"
        );
    }

    private List<FacilityFocusItem> aggregate(List<FacilityAlert> alerts) {
        Map<String, Aggregate> groups = new TreeMap<>();
        for (FacilityAlert alert : alerts) {
            groups.computeIfAbsent(alert.facilityName(), Aggregate::new).add(alert);
        }
        return groups.values().stream().map(Aggregate::toItem).sorted(FOCUS_ORDER).toList();
    }

    private long countLevel(List<FacilityAlert> alerts, AlarmLevel level) {
        return alerts.stream().filter(alert -> alert.alarmLevel() == level).count();
    }

    private long countStatus(List<FacilityAlert> alerts, FacilityAlertStatus status) {
        return alerts.stream().filter(alert -> alert.status() == status).count();
    }

    private String summary(
            int activeCount,
            List<FacilityFocusItem> facilities,
            long warning,
            long severe,
            long emergency
    ) {
        if (activeCount == 0) {
            return "当前未发现待确认或处理中告警；该结果仅表示告警表中无活动记录，不等同于全量设施均处于健康状态。";
        }
        String names = facilities.stream().limit(3).map(FacilityFocusItem::facilityName)
                .reduce((left, right) -> left + "、" + right).orElse("相关设施");
        return "当前共有%d条活动告警，涉及%d处设施，其中紧急%d条、严重%d条、警告%d条；应优先关注%s。"
                .formatted(activeCount, facilities.size(), emergency, severe, warning, names);
    }

    private String normalizeRemark(String remark) {
        if (remark == null || remark.trim().isEmpty()) {
            throw new IllegalArgumentException("处理备注不能为空");
        }
        String normalized = remark.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("处理备注不能超过200字");
        }
        return normalized;
    }

    private FacilityAlert requireAlert(long alertId) {
        return alertPort.findById(alertId).orElseThrow(() -> new BusinessRuleException(
                "FACILITY_ALERT_NOT_FOUND", "设施告警不存在"
        ));
    }

    private BusinessRuleException invalidTransition(String message) {
        return new BusinessRuleException("FACILITY_ALERT_INVALID_TRANSITION", message);
    }

    private static final class Aggregate {
        private final String facilityName;
        private AlarmLevel highest;
        private long highestCount;
        private long pending;
        private long confirmed;
        private final LinkedHashSet<String> metrics = new LinkedHashSet<>();
        private Instant oldestTrigger;
        private Instant latestCollect;

        private Aggregate(String facilityName) {
            this.facilityName = facilityName;
        }

        private void add(FacilityAlert alert) {
            if (highest == null || alert.alarmLevel().databaseValue() > highest.databaseValue()) {
                highest = alert.alarmLevel();
                highestCount = 1;
            } else if (alert.alarmLevel() == highest) {
                highestCount++;
            }
            if (alert.status() == FacilityAlertStatus.PENDING) pending++;
            if (alert.status() == FacilityAlertStatus.CONFIRMED) confirmed++;
            metrics.add(alert.metricName());
            if (oldestTrigger == null || alert.triggerTime().isBefore(oldestTrigger)) {
                oldestTrigger = alert.triggerTime();
            }
            if (latestCollect == null || alert.collectTime().isAfter(latestCollect)) {
                latestCollect = alert.collectTime();
            }
        }

        private FacilityFocusItem toItem() {
            long total = pending + confirmed;
            String reason = "存在%d项活动告警，其中%d项为%s级，最早告警尚未结束"
                    .formatted(total, highestCount, highest.displayName());
            return new FacilityFocusItem(
                    facilityName, FacilityHealthState.from(highest), highest,
                    total, pending, confirmed, new ArrayList<>(metrics),
                    oldestTrigger, latestCollect, reason
            );
        }
    }
}
