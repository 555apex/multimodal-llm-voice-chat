package cn.fj.roadagent.domain.traffic;

/**
 * 我方系统内部统一的拥堵等级，不直接使用任何外部平台的状态编码。
 */
public enum CongestionLevel {
    UNKNOWN(0),
    SMOOTH(1),
    SLOW(2),
    CONGESTED(3);

    private final int severity;

    CongestionLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public static CongestionLevel worstOf(Iterable<RoadSegmentStatus> segments) {
        CongestionLevel worst = UNKNOWN;
        for (RoadSegmentStatus segment : segments) {
            if (segment.congestionLevel().severity() > worst.severity()) {
                worst = segment.congestionLevel();
            }
        }
        return worst;
    }
}
