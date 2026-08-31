package cn.fj.roadagent.domain.traffic;

import java.util.Arrays;

/** 协作者数据库定义的五级交通状态，不在应用侧重新计算。 */
public enum TrafficStatus {
    SMOOTH(10, "畅通"),
    LIGHT_CONGESTION(20, "轻度拥堵"),
    MODERATE_CONGESTION(30, "中度拥堵"),
    SEVERE_CONGESTION(40, "重度拥堵"),
    BLOCKED(50, "堵塞");

    private final int code;
    private final String displayName;

    TrafficStatus(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public int code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public boolean abnormal() {
        return code >= 20;
    }

    public static TrafficStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("交通状态不能为空");
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("无法识别交通状态：" + value, exception);
        }
        return Arrays.stream(values())
                .filter(status -> status.code == parsed)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无法识别交通状态：" + value));
    }
}
