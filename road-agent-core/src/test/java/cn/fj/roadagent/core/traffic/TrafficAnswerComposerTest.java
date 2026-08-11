package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.traffic.Freshness;
import cn.fj.roadagent.domain.traffic.AdministrativeArea;
import cn.fj.roadagent.domain.traffic.AdministrativeAreaLevel;
import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.CongestionLevel;
import cn.fj.roadagent.domain.traffic.GeoBoundary;
import cn.fj.roadagent.domain.traffic.GeoPoint;
import cn.fj.roadagent.domain.traffic.RoadSegmentStatus;
import cn.fj.roadagent.domain.traffic.TrafficCoverage;
import cn.fj.roadagent.domain.traffic.TrafficEvaluation;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;
import cn.fj.roadagent.domain.traffic.TrafficSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficAnswerComposerTest {
    private static final Instant NOW = Instant.parse("2026-08-05T14:08:04Z");
    private final TrafficAnswerComposer composer = new TrafficAnswerComposer();

    @Test
    void shouldQualifyPartialAreaFactsWithoutExposingMetadata() {
        List<RoadSegmentStatus> segments = List.of(
                segment("嘉禾路", "南向北", CongestionLevel.SLOW, 20.0),
                segment("嘉禾路", "南向北", CongestionLevel.SLOW, 15.0),
                segment("思明南路", "北向南", CongestionLevel.CONGESTED, 12.0),
                segment("成功大道", "双向", CongestionLevel.SMOOTH, 52.0)
        );

        String answer = composer.compose(areaSnapshot(segments, TrafficCoverage.of(3, 2, 1)), Freshness.FRESH);

        assertTrue(answer.startsWith("思明区当前已获取的路况中存在拥堵路段，并伴有局部缓行。"));
        assertTrue(answer.contains("思明南路（北向南，拥堵，约12 km/h）"));
        assertTrue(answer.contains("嘉禾路（南向北，缓行，约15 km/h）"));
        assertTrue(answer.contains("本次返回的全部路段："));
        assertTrue(answer.contains("成功大道（双向，畅通，约52 km/h）"));
        String details = answer.split("本次返回的全部路段：", 2)[1];
        assertEquals(3, details.lines().filter(line -> line.startsWith("- ")).count());
        assertFalse(details.contains("嘉禾路（南向北，缓行，约20 km/h）"));
        assertFalse(answer.contains("高德"));
        assertFalse(answer.contains("采集时间"));
        assertFalse(answer.contains("覆盖率"));
        assertFalse(answer.contains("切片"));
        assertFalse(answer.contains("总数"));
    }

    @Test
    void shouldLimitFocusSummaryToFiveButListEveryReturnedSegment() {
        List<RoadSegmentStatus> segments = List.of(
                segment("缓行一", "东向西", CongestionLevel.SLOW, 10.0),
                segment("拥堵三", "东向西", CongestionLevel.CONGESTED, 18.0),
                segment("拥堵二", "东向西", CongestionLevel.CONGESTED, 12.0),
                segment("拥堵一", "东向西", CongestionLevel.CONGESTED, 8.0),
                segment("缓行二", "东向西", CongestionLevel.SLOW, 15.0),
                segment("缓行三", "东向西", CongestionLevel.SLOW, 20.0)
        );

        String answer = composer.compose(areaSnapshot(segments, TrafficCoverage.of(1, 1, 0)), Freshness.FRESH);

        String[] sections = answer.split("本次返回的全部路段：", 2);
        String summary = sections[0];
        String details = sections[1];

        assertTrue(summary.indexOf("拥堵一") < summary.indexOf("拥堵二"));
        assertTrue(summary.indexOf("拥堵二") < summary.indexOf("拥堵三"));
        assertTrue(summary.indexOf("拥堵三") < summary.indexOf("缓行一"));
        assertTrue(summary.contains("缓行二"));
        assertFalse(summary.contains("缓行三"));
        assertTrue(details.contains("缓行三（东向西，缓行，约20 km/h）"));
        assertEquals(
                segments.size(),
                details.lines().filter(line -> line.startsWith("- ")).count()
        );
        for (RoadSegmentStatus segment : segments) {
            assertTrue(details.contains(segment.roadName()));
        }
    }

    @Test
    void shouldCreateShortSpeakableSummaryWithoutAllSegments() {
        List<RoadSegmentStatus> segments = List.of(
                segment("拥堵一", "东向西", CongestionLevel.CONGESTED, 8.0),
                segment("拥堵二", "东向西", CongestionLevel.CONGESTED, 12.0),
                segment("缓行一", "东向西", CongestionLevel.SLOW, 18.0),
                segment("缓行二", "东向西", CongestionLevel.SLOW, 20.0),
                segment("畅通道路", "双向", CongestionLevel.SMOOTH, 50.0)
        );

        String speech = composer.composeSpeech(
                areaSnapshot(segments, TrafficCoverage.of(1, 1, 0)),
                Freshness.FRESH
        );

        assertTrue(speech.contains("拥堵一"));
        assertTrue(speech.contains("拥堵二"));
        assertTrue(speech.contains("缓行一"));
        assertFalse(speech.contains("缓行二"));
        assertFalse(speech.contains("畅通道路"));
        assertFalse(speech.contains("本次返回的全部路段"));
        assertTrue(speech.endsWith("详细数据请查看页面。"));
    }

    @Test
    void shouldKeepWorstRecordWhenSameRoadAndDirectionAreRepeated() {
        List<RoadSegmentStatus> segments = List.of(
                segment("环岛干道", "北向南", CongestionLevel.SMOOTH, 45.0),
                segment("环岛干道", "北向南", CongestionLevel.CONGESTED, 18.0),
                segment("环岛干道", "北向南", CongestionLevel.CONGESTED, 12.0)
        );

        String answer = composer.compose(
                areaSnapshot(segments, TrafficCoverage.of(1, 1, 0)),
                Freshness.FRESH
        );
        String details = answer.split("本次返回的全部路段：", 2)[1];

        assertEquals(1, details.lines().filter(line -> line.startsWith("- ")).count());
        assertTrue(details.contains("环岛干道（北向南，拥堵，约12 km/h）"));
        assertFalse(details.contains("约18 km/h"));
        assertFalse(details.contains("约45 km/h"));
    }

    @Test
    void shouldAvoidAbsoluteAreaClaimWhenUnknownSegmentsExist() {
        List<RoadSegmentStatus> segments = List.of(
                segment("成功大道", "双向", CongestionLevel.SMOOTH, 50.0),
                segment("未知路段", "方向未知", CongestionLevel.UNKNOWN, null)
        );

        String answer = composer.compose(areaSnapshot(segments, TrafficCoverage.of(1, 1, 0)), Freshness.FRESH);

        assertTrue(answer.contains("未发现明确的拥堵或缓行"));
        assertTrue(answer.contains("部分路段状态尚不明确"));
    }

    @Test
    void shouldMarkStaleRoadFactsAsNotRealtime() {
        TrafficSnapshot snapshot = new TrafficSnapshot(
                new TrafficQuery("350100", "五四路", null),
                List.of(segment("五四路", "南向北", CongestionLevel.SLOW, 25.0)),
                "AMAP", NOW, false, ""
        );

        String answer = composer.compose(snapshot, Freshness.STALE);

        assertTrue(answer.startsWith("五四路现有路况中"));
        assertTrue(answer.contains("实时状态暂无法确认，建议重新查询后再安排出行。"));
        assertTrue(answer.endsWith("- 五四路（南向北，缓行，约25 km/h）"));
    }

    @Test
    void shouldReturnConservativeAnswerWhenNoSegmentsAreAvailable() {
        TrafficSnapshot snapshot = new TrafficSnapshot(
                new TrafficQuery("350100", "五四路", null),
                List.of(), "AMAP", NOW, false, ""
        );

        assertEquals(
                "暂未获取到五四路的有效路况信息，当前无法判断通行状态。",
                composer.compose(snapshot, Freshness.FRESH)
        );
    }

    private AreaTrafficSnapshot areaSnapshot(
            List<RoadSegmentStatus> segments,
            TrafficCoverage coverage
    ) {
        AdministrativeArea area = new AdministrativeArea(
                "思明区", "厦门", "350203", AdministrativeAreaLevel.DISTRICT,
                new GeoBoundary(List.of(List.of(
                        new GeoPoint(118.08, 24.44),
                        new GeoPoint(118.10, 24.44),
                        new GeoPoint(118.10, 24.46),
                        new GeoPoint(118.08, 24.44)
                )))
        );
        return new AreaTrafficSnapshot(
                new AreaTrafficQuery(area, TrafficQueryScope.AREA_ALL),
                segments,
                TrafficEvaluation.from(segments),
                coverage,
                "AMAP",
                NOW,
                "",
                coverage.complete() ? List.of() : List.of("PARTIAL_AREA_COVERAGE")
        );
    }

    private RoadSegmentStatus segment(
            String roadName,
            String direction,
            CongestionLevel level,
            Double speed
    ) {
        return new RoadSegmentStatus(roadName, direction, level, speed, null);
    }
}
