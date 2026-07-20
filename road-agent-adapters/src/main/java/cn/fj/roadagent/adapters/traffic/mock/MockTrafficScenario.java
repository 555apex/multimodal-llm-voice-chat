package cn.fj.roadagent.adapters.traffic.mock;

public enum MockTrafficScenario {
    NORMAL,
    EMPTY,
    STALE,
    SERVER_ERROR;

    public static MockTrafficScenario from(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;  // 若无配置，mock数据默认生成normal状态的
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "不支持的Mock场景：%s，可选值为normal、empty、stale、server_error".formatted(value),
                    exception
            );
        }
    }
}
