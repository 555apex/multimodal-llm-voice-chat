package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

/**
 * 对Agent而言，一次实时交通查询是一项原子Tool能力，但在application层仅声明我具备这个能力
 * Agent视角：我具备查询路况的能力
 * 暴露给interface层，然后由interface层具体实现该接口
 **/
@FunctionalInterface
public interface TrafficQueryTool {

    TrafficSnapshot execute(TrafficQuery query);
}
