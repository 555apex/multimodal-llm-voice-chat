package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;

/** 读取一份通过一致性检查的 MySQL 交通候选快照。 */
@FunctionalInterface
public interface HighwayTrafficSnapshotSource {
    HighwayTrafficSnapshot loadCandidate();
}
