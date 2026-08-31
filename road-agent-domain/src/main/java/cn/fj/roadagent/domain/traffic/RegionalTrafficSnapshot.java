package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.List;

/** 一次只读一致性事务得到的区域卡口数据。 */
public record RegionalTrafficSnapshot(List<TransportHub> hubs, Instant acquiredAt) {
    public RegionalTrafficSnapshot {
        hubs = hubs == null ? List.of() : List.copyOf(hubs);
        if (acquiredAt == null) {
            throw new IllegalArgumentException("区域交通数据时间不能为空");
        }
    }
}
