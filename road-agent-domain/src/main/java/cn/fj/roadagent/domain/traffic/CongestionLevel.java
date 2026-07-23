package cn.fj.roadagent.domain.traffic;

/**
 * 功能：拥堵等级描述
 * 我方系统内部统一的拥堵等级（可使用官方定义的状态编码，此处仅给出一个定义）
 */
public enum CongestionLevel {
    UNKNOWN(0), // 未知道路
    SMOOTH(1),  // 畅通
    SLOW(2),    // 缓行
    CONGESTED(3); // 拥堵
    /*
    这个数字是我方系统内部的编码，不直接使用高德的拥堵编码。高德可能有自己的编码体系
    适配器层会做映射转换，domain 层只认自己的等级
     */

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
