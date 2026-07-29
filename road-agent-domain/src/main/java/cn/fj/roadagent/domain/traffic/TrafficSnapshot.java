package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 单路查询获得的结果聚合
 * 具体：某一时刻取得的单路的所有交通相关数据。
 */
public record TrafficSnapshot(  // record实现不可变的数据载体（数据为private final，方法包含构造方法和getter）
        TrafficQuery query,     // 查询条件
        List<RoadSegmentStatus> segments,   // 路段状态列表
        String source,      // 数据来源（MOCK或高德API等）
        Instant acquiredAt, // 数据获取时间
        boolean mock,       // 是否为mock数据（布尔判断位）
        String description  // 描述
) {
    public TrafficSnapshot {
        query = Objects.requireNonNull(query, "query不能为空");
        segments = segments == null ? List.of() : List.copyOf(segments);
        source = source == null || source.isBlank() ? "UNKNOWN" : source.trim();
        acquiredAt = Objects.requireNonNull(acquiredAt, "acquiredAt不能为空");
        description = description == null ? "" : description.trim();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public CongestionLevel worstCongestionLevel() {
        return CongestionLevel.worstOf(segments);
    }
}
