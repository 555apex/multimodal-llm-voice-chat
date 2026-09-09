package cn.fj.roadagent.domain.traffic;

/** 项目约定的道路通行能力三级评估口径。 */
public enum CapacityLevel {
    NORMAL("正常"),
    BOTTLENECK("瓶颈"),
    SEVERE_BOTTLENECK("严重瓶颈");

    /** 当前模拟数据的瓶颈起始阈值；数据恢复后可将此值改回0.80。 */
    public static final double BOTTLENECK_THRESHOLD = 0.15;
    /** 严重瓶颈线为起始阈值的2倍，上限为1.00。 */
    public static final double SEVERE_BOTTLENECK_THRESHOLD = Math.min(1.00, BOTTLENECK_THRESHOLD * 2);

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
        if (utilizationRatio >= SEVERE_BOTTLENECK_THRESHOLD) {
            return SEVERE_BOTTLENECK;
        }
        if (utilizationRatio >= BOTTLENECK_THRESHOLD) {
            return BOTTLENECK;
        }
        return NORMAL;
    }
}
