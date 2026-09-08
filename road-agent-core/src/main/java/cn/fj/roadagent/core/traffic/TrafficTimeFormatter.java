package cn.fj.roadagent.core.traffic;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class TrafficTimeFormatter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private TrafficTimeFormatter() {
    }

    static String asiaShanghai(Instant instant) {
        return instant == null ? "未提供" : FORMATTER.format(instant);
    }
}
