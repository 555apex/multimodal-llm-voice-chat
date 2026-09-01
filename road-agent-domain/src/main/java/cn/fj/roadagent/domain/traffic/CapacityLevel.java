package cn.fj.roadagent.domain.traffic;

/** 项目约定的道路通行能力三级评估口径。 */
public enum CapacityLevel {
    NORMAL("正常"),
    BOTTLENECK("瓶颈"),
    SEVERE_BOTTLENECK("严重瓶颈");

    private final String displayName;

    CapacityLevel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static CapacityLevel fromUtilization(double utilizationRatio) {
        if (!Double.isFinite(utilizationRatio) || utilizationRatio < 0 || utilizationRatio > 1) {
            throw new IllegalArgumentException("通行能力利用率必须在0到1之间");
        }
        if (utilizationRatio >= 0.80) {
            return NORMAL;
        }
        if (utilizationRatio <= 0.30) {
            return SEVERE_BOTTLENECK;
        }
        return BOTTLENECK;
    }
}
