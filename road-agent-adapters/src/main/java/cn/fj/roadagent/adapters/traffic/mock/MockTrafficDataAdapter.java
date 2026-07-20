package cn.fj.roadagent.adapters.traffic.mock;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class MockTrafficDataAdapter implements TrafficDataPort {
// (Adapters层)的mock数据适配器MockTrafficDataAdapter 实现 (application层)提供的数据port：TrafficDataPort

    private final MockTrafficScenario scenario;     // 模拟的场景
    private final Clock clock;                      // 时钟

    // 构造函数
    public MockTrafficDataAdapter(MockTrafficScenario scenario, Clock clock) {
        this.scenario = scenario;
        this.clock = clock;
    }

    @Override
    public TrafficSnapshot query(TrafficQuery query) {
        return switch (scenario) {
            case NORMAL -> normal(query, clock.instant());  // 正常数据（路况缓行）
            case EMPTY -> empty(query, clock.instant());    // 空数据（没查到该路）
            case STALE -> normal(query, clock.instant().minus(Duration.ofMinutes(30))); // 过期数据
            case SERVER_ERROR -> throw new ExternalServiceException(
                    "MOCK_TRAFFIC", "MOCK_SERVER_ERROR", "Mock交通服务模拟了服务器错误"
            );
        };
    }

    private TrafficSnapshot normal(TrafficQuery query, Instant acquiredAt) {
        String requestedDirection = query.direction() == null ? "南向北" : query.direction();  // mock数据中：用户若没填方向，默认“南向北”
        List<RoadSegmentStatus> segments = List.of( // mock数据生成
                new RoadSegmentStatus(query.roadName(), requestedDirection,
                        CongestionLevel.SLOW, 24.0, "119.3031,26.0892;119.3050,26.0920"),
                new RoadSegmentStatus(query.roadName(), "北向南",
                        CongestionLevel.SMOOTH, 41.0, "119.3050,26.0920;119.3031,26.0892")
        );
        return new TrafficSnapshot(query, segments, "MOCK", acquiredAt, true,
                "教学Mock数据：道路总体缓行");
    }

    private TrafficSnapshot empty(TrafficQuery query, Instant acquiredAt) {
        return new TrafficSnapshot(query, List.of(), "MOCK", acquiredAt, true,
                "教学Mock数据：未找到匹配道路");
    }
}
