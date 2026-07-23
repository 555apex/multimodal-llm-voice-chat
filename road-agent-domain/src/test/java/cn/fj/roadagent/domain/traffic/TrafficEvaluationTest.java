package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrafficEvaluationTest {

    @Test
    void shouldCalculateRatiosAndAverageFromAllSegments() {
        TrafficEvaluation evaluation = TrafficEvaluation.from(List.of(
                new RoadSegmentStatus("成功大道", "北向南", CongestionLevel.CONGESTED, 12.0, null),
                new RoadSegmentStatus("环岛东路", "南向北", CongestionLevel.SLOW, 24.0, null),
                new RoadSegmentStatus("厦禾路", "东向西", CongestionLevel.SMOOTH, null, null),
                new RoadSegmentStatus("文曾路", "", CongestionLevel.UNKNOWN, null, null)
        ));

        assertEquals(4, evaluation.totalSegments());
        assertEquals(1, evaluation.congestedSegments());
        assertEquals(0.25, evaluation.congestedRatio());
        assertEquals(18.0, evaluation.averageSpeedKmh());
    }

    @Test
    void shouldMapAreaScopeToAmapRoadLevel() {
        assertEquals(5, TrafficQueryScope.AREA_ALL.amapRoadLevel());
        assertEquals(4, TrafficQueryScope.AREA_MAJOR.amapRoadLevel());
    }
}
