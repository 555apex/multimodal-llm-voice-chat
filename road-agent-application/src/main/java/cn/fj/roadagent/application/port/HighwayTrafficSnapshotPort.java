package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;

/** 向业务层提供最近一次完整且稳定的交通快照。 */
@FunctionalInterface
public interface HighwayTrafficSnapshotPort {
    HighwayTrafficSnapshot current();
}
