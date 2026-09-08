package cn.fj.roadagent.domain.facility;

public enum FacilityAlertStatus {
    PENDING(1, "待确认"),
    CONFIRMED(2, "处理中"),
    CLOSED(3, "已结束");

    private final int databaseValue;
    private final String displayName;

    FacilityAlertStatus(int databaseValue, String displayName) {
        this.databaseValue = databaseValue;
        this.displayName = displayName;
    }

    public int databaseValue() {
        return databaseValue;
    }

    public String displayName() {
        return displayName;
    }

    public static FacilityAlertStatus fromDatabase(int value) {
        for (FacilityAlertStatus status : values()) {
            if (status.databaseValue == value) return status;
        }
        throw new IllegalArgumentException("未知设施告警状态：" + value);
    }
}
