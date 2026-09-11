package cn.fj.roadagent.domain.traffic;

/** 项目约定的道路通行能力三级评估口径。 */
public enum CapacityLevel {
    NORMAL("正常"),
    BOTTLENECK("瓶颈"),
    SEVERE_BOTTLENECK("严重瓶颈");

    /** 利用率严格大于该值时进入瓶颈等级。 */
    public static final double BOTTLENECK_THRESHOLD = 0.20;
    /** 利用率严格大于该值时进入严重瓶颈等级。 */
    public static final double SEVERE_BOTTLENECK_THRESHOLD = 0.30;

    private final String displayName;

    CapacityLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static CapacityLevel fromUtilization(double utilizationRatio) {
        if (!Double.isFinite(utilizationRatio) || utilizationRatio < 0) {
            throw new IllegalArgumentException("通行能力利用率必须是非负有限数");
        }
        if (utilizationRatio > SEVERE_BOTTLENECK_THRESHOLD) {
            return SEVERE_BOTTLENECK;
        }
        if (utilizationRatio > BOTTLENECK_THRESHOLD) {
            return BOTTLENECK;
        }
        return NORMAL;
    }
}
