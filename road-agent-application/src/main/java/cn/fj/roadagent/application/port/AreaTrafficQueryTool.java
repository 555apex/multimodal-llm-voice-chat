package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.traffic.AreaTrafficProgressListener;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

public interface AreaTrafficQueryTool {
    AreaTrafficSnapshot execute(
            String city,
            String areaName,
            TrafficQueryScope scope,
            AreaTrafficProgressListener listener
    );
}
