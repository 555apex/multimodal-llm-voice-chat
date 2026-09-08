package cn.fj.roadagent.domain.facility;

public enum FacilityHealthState {
    NO_ACTIVE_ALERT("当前无活动告警"),
    ATTENTION("关注"),
    ABNORMAL("异常"),
    DANGER("危险");

    private final String displayName;

    FacilityHealthState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static FacilityHealthState from(AlarmLevel level) {
        if (level == null) return NO_ACTIVE_ALERT;
        return switch (level) {
            case WARNING -> ATTENTION;
            case SEVERE -> ABNORMAL;
            case EMERGENCY -> DANGER;
        };
    }
}
