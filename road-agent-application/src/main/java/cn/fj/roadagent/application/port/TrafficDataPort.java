package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

/**
 * Agent访问交通数据的稳定边界。当前高德和未来甲方接口都实现该接口。
 * 数据视角：我有获取交通数据的通道
 */
@FunctionalInterface    // 表示该接口仅允许有一个抽象方法
public interface TrafficDataPort {

    TrafficSnapshot query(TrafficQuery query);  // 查询数据的方法
}
