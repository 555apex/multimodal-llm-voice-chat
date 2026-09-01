package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;

/** 向业务层提供当前已发布的完整通行能力快照。 */
public interface RoadCapacitySnapshotPort {
    RoadCapacitySnapshot current();
}
