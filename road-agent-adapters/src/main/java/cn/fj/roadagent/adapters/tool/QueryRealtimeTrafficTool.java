package cn.fj.roadagent.adapters.tool;

import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.application.port.TrafficQueryTool;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

import java.util.Objects;

/**
 * Agent核心管理Tool的调用；这个类把一次原子Tool调用交给具体数据适配器，所以属于adapters层
 * 在road-agent-adapters实现了road-agent-application的接口TrafficQueryTool
 */
public final class QueryRealtimeTrafficTool implements TrafficQueryTool {   // 实现TrafficQueryTool接口
// (application层)的实时交通查询QueryRealtimeTrafficTool实现(application层)提供的tool port：TrafficQueryTool

    private final TrafficDataPort trafficDataPort;  // TrafficDataPort（外部数据源接口，port)
    // 以port的概念，实现了application层和adapters层的分离，相当于此处不考虑数据来源于（mock或高德），直接创建对象trafficDataPort

    // 构造函数
    public QueryRealtimeTrafficTool(TrafficDataPort trafficDataPort) {
        this.trafficDataPort = Objects.requireNonNull(trafficDataPort); // 不允许传null
    }

    @Override
    public TrafficSnapshot execute(TrafficQuery query) {
        return trafficDataPort.query(query);
    }   // 返回数据接口的数据
}
