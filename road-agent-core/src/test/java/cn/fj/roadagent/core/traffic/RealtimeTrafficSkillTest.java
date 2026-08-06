package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentMessageCommand;
import cn.fj.roadagent.application.port.AreaTrafficQueryTool;
import cn.fj.roadagent.application.traffic.AreaTrafficProgress;
import cn.fj.roadagent.application.port.TrafficQueryTool;
import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.application.traffic.TrafficQueryCommand;
import cn.fj.roadagent.application.traffic.TrafficQueryResult;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.AdministrativeArea;
import cn.fj.roadagent.domain.traffic.AdministrativeAreaLevel;
import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.GeoBoundary;
import cn.fj.roadagent.domain.traffic.GeoPoint;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficCoverage;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeTrafficSkillTest {

    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldKeepFixedWorkflowOrder() {
        RealtimeTrafficSkill skill = skill(normalTool());

        assertEquals(List.of(
                TrafficWorkflowStep.VALIDATE_QUERY,
                TrafficWorkflowStep.QUERY_TRAFFIC,
                TrafficWorkflowStep.VALIDATE_FRESHNESS,
                TrafficWorkflowStep.SUMMARIZE,
                TrafficWorkflowStep.PUBLISH_RESULT
        ), skill.workflow());
    }

    @Test
    void shouldBuildDeterministicProfessionalAnswer() {
        TrafficQueryResult result = skill(normalTool()).query(command());

        assertEquals(SummarySource.DETERMINISTIC, result.summarySource());
        assertEquals(
                "五四路当前未发现拥堵，但部分路段通行缓慢。 "
                        + "重点路段：五四路（南向北，缓行，约25 km/h）。 "
                        + "建议途经上述缓行路段时适当预留通行时间。\n"
                        + "本次返回的全部路段：\n"
                        + "- 五四路（南向北，缓行，约25 km/h）",
                result.summary()
        );
        assertEquals(1, result.segments().size());
        assertEquals(CongestionLevel.SLOW, result.segments().get(0).congestionLevel());
        assertEquals(25.0, result.segments().get(0).averageSpeedKmh());
    }

    @Test
    void shouldMarkStaleData() {
        TrafficQueryTool staleTool = query -> new TrafficSnapshot(
                query, List.of(), "AMAP", NOW.minus(Duration.ofMinutes(30)), false, ""
        );

        TrafficQueryResult result = skill(staleTool).query(command());

        assertEquals(Freshness.STALE, result.freshness());
        assertTrue(result.warnings().contains("EMPTY_TRAFFIC_DATA"));
        assertTrue(result.warnings().contains("TRAFFIC_DATA_STALE"));
    }

    @Test
    void shouldWarnWhenRequestedDirectionIsNotCovered() {
        TrafficQueryCommand oppositeDirection = new TrafficQueryCommand(
                "350100", "五四路", "北向南", "trace-direction-test"
        );

        TrafficQueryResult result = skill(normalTool()).query(oppositeDirection);

        assertTrue(result.warnings().contains("REQUESTED_DIRECTION_NOT_COVERED"));
    }

    @Test
    void shouldExecuteAreaQueryAndPublishProgress() {
        AreaTrafficQueryTool areaTool = (city, areaName, scope, listener) -> {
            listener.onProgress(new AreaTrafficProgress(2, 1, 0));
            listener.onProgress(new AreaTrafficProgress(2, 2, 1));
            AdministrativeArea area = new AdministrativeArea(
                    "思明区", "厦门", "350203", AdministrativeAreaLevel.DISTRICT,
                    new GeoBoundary(List.of(List.of(
                            new GeoPoint(118.08, 24.44), new GeoPoint(118.10, 24.44),
                            new GeoPoint(118.10, 24.46), new GeoPoint(118.08, 24.46)
                    )))
            );
            List<RoadSegmentStatus> segments = List.of(
                    new RoadSegmentStatus("成功大道", "北向南", CongestionLevel.CONGESTED, 12.0, null)
            );
            return new AreaTrafficSnapshot(
                    new AreaTrafficQuery(area, scope), segments, TrafficEvaluation.from(segments),
                    TrafficCoverage.of(2, 1, 1), "AMAP", NOW, "", List.of("PARTIAL_AREA_COVERAGE")
            );
        };
        RealtimeTrafficSkill skill = new RealtimeTrafficSkill(
                normalTool(), areaTool, CLOCK, Duration.ofMinutes(5)
        );
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "AREA_MAJOR", "厦门", "思明区", null, null,
                null, null, null, null, List.of(), null
        );
        AgentExecutionContext context = new AgentExecutionContext(
                new AgentMessageCommand("conversation-1", "思明区交通要道如何", "trace-area"),
                decision, List.of()
        );
        List<AgentEvent> events = new ArrayList<>();

        skill.execute(context, events::add);

        assertEquals(2, events.stream().filter(event -> "tool.progress".equals(event.name())).count());
        AgentEvent resultEvent = events.stream()
                .filter(event -> "result.traffic".equals(event.name())).findFirst().orElseThrow();
        assertTrue(resultEvent.data().toString().contains("AREA_MAJOR"));
    }

    private RealtimeTrafficSkill skill(TrafficQueryTool tool) {
        AreaTrafficQueryTool areaTool = (city, areaName, scope, listener) -> {
            throw new UnsupportedOperationException("本组道路查询测试不调用区域Tool");
        };
        return new RealtimeTrafficSkill(tool, areaTool, CLOCK, Duration.ofMinutes(5));
    }

    private TrafficQueryTool normalTool() {
        return query -> new TrafficSnapshot(
                query,
                List.of(
                        new RoadSegmentStatus("五四路", "南向北", CongestionLevel.SLOW, 30.0, null),
                        new RoadSegmentStatus("五四路", "南向北", CongestionLevel.SLOW, 25.0, null)
                ),
                "AMAP",
                NOW,
                false,
                "五四路总体缓行"
        );
    }

    private TrafficQueryCommand command() {
        return new TrafficQueryCommand("350100", "五四路", "南向北", "trace-test");
    }

}
