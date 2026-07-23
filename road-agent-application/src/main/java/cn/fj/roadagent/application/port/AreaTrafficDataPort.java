package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.traffic.AreaTrafficProgressListener;
import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;

public interface AreaTrafficDataPort {
    AreaTrafficSnapshot query(AreaTrafficQuery query, AreaTrafficProgressListener listener);
}
