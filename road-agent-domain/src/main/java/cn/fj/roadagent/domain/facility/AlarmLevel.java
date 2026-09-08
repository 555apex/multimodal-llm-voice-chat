package cn.fj.roadagent.domain.facility;

public enum AlarmLevel {
    WARNING(1, "警告"),
    SEVERE(2, "严重"),
    EMERGENCY(3, "紧急");

    private final int databaseValue;
    private final String displayName;

    AlarmLevel(int databaseValue, String displayName) {
        this.databaseValue = databaseValue;
        this.displayName = displayName;
    }

    public int databaseValue() {
        return databaseValue;
    }

    public String displayName() {
        return displayName;
    }

    public static AlarmLevel fromDatabase(int value) {
        for (AlarmLevel level : values()) {
            if (level.databaseValue == value) return level;
        }
        throw new IllegalArgumentException("未知设施告警等级：" + value);
    }
}
