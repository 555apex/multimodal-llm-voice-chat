package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;

/** 跨区域路线与卡口交通联系数据只读端口。 */
public interface RegionalTrafficDataPort {
    RegionalTrafficSnapshot load();
}
