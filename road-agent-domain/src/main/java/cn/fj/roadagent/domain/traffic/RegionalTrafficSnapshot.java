package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.List;

/** 一次只读一致性事务得到的跨城市路线及卡口事实。 */
public record RegionalTrafficSnapshot(List<RegionalConnectionHub> hubs, Instant acquiredAt, List<String> warnings) {
    public RegionalTrafficSnapshot {
        hubs = hubs == null ? List.of() : List.copyOf(hubs);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (acquiredAt == null) {
            throw new IllegalArgumentException("区域交通数据时间不能为空");
        }
    }
}
