package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** w_festival_data 中经过校验、可用于路况原因提示的一条时空背景事实。 */
public record TrafficContextEvent(
        long id,
        String eventCode,
        String eventName,
        TrafficContextEventType eventType,
        Instant eventStartAt,
        Instant eventEndAt,
        Instant impactStartAt,
        Instant impactEndAt,
        TrafficContextScope scope,
        String regionCode,
        String regionName,
        Set<String> affectedRouteCodes,
        String venue,
        String dataStatus
) {
    public TrafficContextEvent {
        eventCode = requireText(eventCode, "事件编码不能为空");
        eventName = requireText(eventName, "事件名称不能为空");
        eventType = Objects.requireNonNull(eventType, "事件类型不能为空");
        eventStartAt = Objects.requireNonNull(eventStartAt, "事件开始时间不能为空");
        eventEndAt = Objects.requireNonNull(eventEndAt, "事件结束时间不能为空");
        impactStartAt = Objects.requireNonNull(impactStartAt, "影响开始时间不能为空");
        impactEndAt = Objects.requireNonNull(impactEndAt, "影响结束时间不能为空");
        scope = Objects.requireNonNull(scope, "影响范围不能为空");
        dataStatus = requireText(dataStatus, "数据状态不能为空");
        if (eventEndAt.isBefore(eventStartAt)) {
            throw new IllegalArgumentException("事件结束时间不能早于开始时间");
        }
        if (impactStartAt.isAfter(eventStartAt) || impactEndAt.isBefore(eventEndAt)) {
            throw new IllegalArgumentException("影响时间必须覆盖事件时间");
        }
        LinkedHashSet<String> routes = new LinkedHashSet<>();
        if (affectedRouteCodes != null) {
            affectedRouteCodes.stream().map(TrafficContextEvent::normalizeRoute)
                    .filter(value -> !value.isBlank()).forEach(routes::add);
        }
        affectedRouteCodes = Set.copyOf(routes);
        regionCode = normalize(regionCode);
        regionName = normalize(regionName);
        venue = normalize(venue);
        if (scope == TrafficContextScope.CITY && regionCode == null) {
            throw new IllegalArgumentException("城市级活动必须提供行政区划代码");
        }
        if (scope == TrafficContextScope.ROUTE && affectedRouteCodes.isEmpty()) {
            throw new IllegalArgumentException("路线级活动必须提供受影响路线");
        }
    }

    public boolean activeAt(Instant instant) {
        return instant != null && !instant.isBefore(impactStartAt) && !instant.isAfter(impactEndAt);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeRoute(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase();
    }
}
