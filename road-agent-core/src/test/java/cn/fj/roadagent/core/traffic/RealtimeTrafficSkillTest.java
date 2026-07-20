package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.TrafficQueryTool;
import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.application.traffic.SummarySource;
import cn.fj.roadagent.application.traffic.TrafficQueryCommand;
import cn.fj.roadagent.application.traffic.TrafficQueryResult;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeTrafficSkillTest {

    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldKeepFixedWorkflowOrder() {
        RealtimeTrafficSkill skill = skill(normalTool(), Optional.empty());

        assertEquals(List.of(
                TrafficWorkflowStep.VALIDATE_QUERY,
                TrafficWorkflowStep.QUERY_TRAFFIC,
                TrafficWorkflowStep.VALIDATE_FRESHNESS,
                TrafficWorkflowStep.SUMMARIZE,
                TrafficWorkflowStep.PUBLISH_RESULT
        ), skill.workflow());
    }

    @Test
    void shouldUseModelOnlyForSummary() {
        ChatModelPort model = request -> new ModelResponse("五四路当前总体缓行。", "DeepSeek", "test-model");
        RealtimeTrafficSkill skill = skill(normalTool(), Optional.of(model));

        TrafficQueryResult result = skill.query(command());

        assertEquals(SummarySource.MODEL, result.summarySource());
        assertEquals("五四路当前总体缓行。", result.summary());
        assertEquals(CongestionLevel.SLOW, result.segments().get(0).congestionLevel());
    }

    @Test
    void shouldFallBackToRuleSummaryWhenModelFails() {
        ChatModelPort failedModel = request -> {
            throw new IllegalStateException("timeout");
        };
        RealtimeTrafficSkill skill = skill(normalTool(), Optional.of(failedModel));

        TrafficQueryResult result = skill.query(command());

        assertEquals(SummarySource.RULE_FALLBACK, result.summarySource());
        assertTrue(result.warnings().contains("MODEL_SUMMARY_FALLBACK"));
    }

    @Test
    void shouldMarkStaleData() {
        TrafficQueryTool staleTool = query -> new TrafficSnapshot(
                query, List.of(), "MOCK", NOW.minus(Duration.ofMinutes(30)), true, ""
        );

        TrafficQueryResult result = skill(staleTool, Optional.empty()).query(command());

        assertEquals(Freshness.STALE, result.freshness());
        assertTrue(result.warnings().contains("EMPTY_TRAFFIC_DATA"));
        assertTrue(result.warnings().contains("TRAFFIC_DATA_STALE"));
    }

    private RealtimeTrafficSkill skill(TrafficQueryTool tool, Optional<ChatModelPort> model) {
        return new RealtimeTrafficSkill(tool, model, CLOCK, Duration.ofMinutes(5));
    }

    private TrafficQueryTool normalTool() {
        return query -> new TrafficSnapshot(
                query,
                List.of(new RoadSegmentStatus("五四路", "南向北", CongestionLevel.SLOW, 25.0, null)),
                "MOCK",
                NOW,
                true,
                "五四路总体缓行"
        );
    }

    private TrafficQueryCommand command() {
        return new TrafficQueryCommand("350100", "五四路", "南向北", "trace-test");
    }
}
