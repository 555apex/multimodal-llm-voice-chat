package cn.fj.roadagent.application.traffic;

/**
 * 前端接口层只依赖这个用例，不需要知道Agent内部怎样规划和调用工具。
 */
public interface QueryRealtimeTrafficUseCase {

    TrafficQueryResult query(TrafficQueryCommand command);  // 实时交通查询业务的引用，从而该接口实现调用方和实现方的解耦
    // 接口仅定义query方法，这个方法接受TrafficQueryCommand，返回TrafficQueryResult
}
