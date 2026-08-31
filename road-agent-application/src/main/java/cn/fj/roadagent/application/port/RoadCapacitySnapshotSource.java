package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;

/** 从外部数据源加载并校验候选通行能力快照。 */
public interface RoadCapacitySnapshotSource {
    RoadCapacitySnapshot loadCandidate();
}
