package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;

/** 区域卡口交通压力数据只读端口。 */
public interface RegionalTrafficDataPort {
    RegionalTrafficSnapshot load();
}
