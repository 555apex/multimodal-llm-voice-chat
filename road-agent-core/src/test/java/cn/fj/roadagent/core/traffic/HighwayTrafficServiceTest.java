package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.TrafficForecastSummaryResponse;
import cn.fj.roadagent.application.traffic.TrafficSummaryResponse;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.domain.traffic.TrafficStatus;
import cn.fj.roadagent.domain.traffic.TrafficContextEvent;
import cn.fj.roadagent.domain.traffic.TrafficContextEventType;
import cn.fj.roadagent.domain.traffic.TrafficContextScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighwayTrafficServiceTest {

    @Test
    void summaryRequiresAFullThreeToFiveSentenceChineseAssessment() {
        assertThrows(IllegalArgumentException.class, () -> new TrafficSummaryResponse(
                "当前路网态势已完成汇总。请合理安排出行。"
        ));
        TrafficSummaryResponse response = new TrafficSummaryResponse(
                "当前福建普通国省干线交通态势总体平稳，路线状态已依据最新交通数据完成汇总。"
                        + "主要路线通行状态保持良好，部分路段存在轻度拥堵，需要关注均速较低且拥堵指数较高的区段。"
                        + "建议出行前结合页面路线与路段明细选择通行线路，对异常路段适当预留时间并优先错峰通行。"
        );
        assertTrue(response.summary().length() >= 80);
    }

    @Test
    void forecastResponseKeepsTrendWhitelistAndNormalizesHarmlessWordingDifferences() {
        for (String trend : List.of("基本稳定", "持续拥堵", "可能加剧", "逐渐缓解", "局部分化")) {
            TrafficForecastSummaryResponse response = new TrafficForecastSummaryResponse(
                    currentSummary(), trend, "未来1至2小时，预计相关道路通行趋势为" + trend + "。"
            );
            assertEquals(trend, response.trend());
            assertTrue(response.combinedSummary().endsWith(response.trendForecast()));
        }

        assertThrows(IllegalArgumentException.class, () -> new TrafficForecastSummaryResponse(
                currentSummary(), "趋于好转", "未来1至2小时，预计相关道路趋于好转。"
        ));
        TrafficForecastSummaryResponse missingHorizon = new TrafficForecastSummaryResponse(
                currentSummary(), "基本稳定", "预计相关道路通行趋势基本稳定。"
        );
        assertEquals("未来1至2小时，预计相关道路通行态势基本稳定。", missingHorizon.trendForecast());
        TrafficForecastSummaryResponse missingCaution = new TrafficForecastSummaryResponse(
                currentSummary(), "基本稳定", "未来1至2小时，相关道路通行趋势基本稳定。"
        );
        assertTrue(missingCaution.trendForecast().contains("预计"));
        TrafficForecastSummaryResponse concreteValue = new TrafficForecastSummaryResponse(
                currentSummary(), "逐渐缓解", "未来1至2小时，预计均速恢复至60km/h并逐渐缓解。"
        );
        assertFalse(concreteValue.trendForecast().contains("60"));
        TrafficForecastSummaryResponse causeClaim = new TrafficForecastSummaryResponse(
                currentSummary(), "持续拥堵", "未来1至2小时，预计因施工而持续拥堵。"
        );
        assertFalse(causeClaim.trendForecast().contains("施工"));
        assertEquals("逐渐缓解", new TrafficForecastSummaryResponse(
                currentSummary(), "趋于缓解", "未来一到两小时，有望逐步缓解。"
        ).trend());
        assertThrows(IllegalArgumentException.class, () -> new TrafficForecastSummaryResponse(
                currentSummary().replace("部分路段存在轻度拥堵", "部分路段可能因事故拥堵"),
                "持续拥堵", "未来1至2小时，预计相关道路持续拥堵。"
        ));
    }

    @Test
    void overviewUsesAuthoritativeRouteStatusAndSortsByCode() {
        RecordingModel model = new RecordingModel();
        var result = service(model).query(query(TrafficQueryType.PROVINCE_OVERVIEW));

        assertEquals(List.of("G104", "S201"), result.routeSummaries().stream()
                .map(route -> route.routeCode()).toList());
        assertEquals(88.8, result.routeSummaries().get(0).averageSpeedKmh());
        assertTrue(result.segments().isEmpty());
        assertFalse(result.summary().contains("未来1至2小时"));
        assertTrue(model.lastRequest.userPrompt().contains("status=10/畅通"));
        assertTrue(model.lastRequest.userPrompt().contains("routeStatusCounts="));
        assertTrue(model.lastRequest.systemPrompt().contains("3至4句"));
        assertTrue(model.lastRequest.systemPrompt().contains("status是权威状态"));
        assertTrue(model.lastRequest.userPrompt().contains("forecastSegments:"));
        assertEquals(10, service(model).collectFacts(query(TrafficQueryType.PROVINCE_OVERVIEW))
                .forecastSegments().size());
        assertFalse(model.lastRequest.systemPrompt().contains("现有数据不足以判断原因"));
    }

    @Test
    void trendIsOnlyIncludedWhenRequestedWhilePublicTablesRemainUnchanged() {
        HighwayTrafficService service = service(new RecordingModel());
        List<HighwayTrafficQuery> queries = List.of(
                query(TrafficQueryType.PROVINCE_OVERVIEW),
                query(TrafficQueryType.PROVINCE_ABNORMAL),
                new HighwayTrafficQuery(TrafficQueryType.CITY_PAIR, "宁德", "福州", null, null, "trace"),
                new HighwayTrafficQuery(TrafficQueryType.ROUTE_DETAIL, null, null, "G104", null, "trace")
        );

        for (HighwayTrafficQuery trafficQuery : queries) {
            var result = service.query(trafficQuery);
            assertFalse(result.summary().contains("未来1至2小时"));
        }
        HighwayTrafficQuery withTrend = new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, "G104", null, List.of(), null, "trace", true);
        assertTrue(service.query(withTrend).summary().contains("未来1至2小时"));
        HighwayTrafficQuery trendOnly = new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, "G104", null, List.of(), null, "trace", true, true);
        assertTrue(service.query(trendOnly).summary().startsWith("未来1至2小时"));
        assertFalse(service.query(trendOnly).summary().contains("当前福建"));
        assertTrue(service.query(query(TrafficQueryType.PROVINCE_OVERVIEW)).segments().isEmpty());
    }

    @Test
    void verifiedHolidayOrActivityIsAddedOnlyForAbnormalMatchingScope() {
        TrafficContextEvent matching = contextEvent(
                "DEMO_FUZHOU", "福州市大型体育赛事（模拟）", TrafficContextEventType.SPORTS_EVENT,
                TrafficContextScope.CITY, "350100", Set.of("G104")
        );
        TrafficContextEvent unrelated = contextEvent(
                "DEMO_OTHER", "无关路线活动（模拟）", TrafficContextEventType.CONCERT,
                TrafficContextScope.ROUTE, null, Set.of("G999")
        );
        HighwayTrafficService service = new HighwayTrafficService(
                this::snapshot, new RecordingModel(), ignored -> List.of(unrelated, matching)
        );

        HighwayTrafficQuery query = new HighwayTrafficQuery(
                TrafficQueryType.CITY_PAIR, "宁德", "福州", null, null,
                List.of(), null, "trace", true
        );
        var result = service.query(query);

        assertTrue(result.summary().contains("福州市大型体育赛事（模拟）"));
        assertTrue(result.summary().contains("可能受到"));
        assertTrue(result.summary().contains("人车集散需求叠加影响"));
        assertFalse(result.summary().contains("无关路线活动"));
        assertTrue(result.summary().indexOf("叠加影响") < result.summary().indexOf("未来1至2小时"));
    }

    @Test
    void provinceHolidayMatchesButTrendOnlyAndSmoothResultsDoNotAddCause() {
        TrafficContextEvent holiday = contextEvent(
                "HOLIDAY_TEST", "测试节假日", TrafficContextEventType.HOLIDAY,
                TrafficContextScope.PROVINCE, null, Set.of()
        );
        HighwayTrafficService service = new HighwayTrafficService(
                this::snapshot, new RecordingModel(), ignored -> List.of(holiday)
        );
        var abnormal = service.query(query(TrafficQueryType.PROVINCE_ABNORMAL));
        assertTrue(abnormal.summary().contains("测试节假日"));
        assertTrue(abnormal.summary().contains("集中出行需求"));

        HighwayTrafficQuery trendOnly = new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, "G104", null,
                List.of(), null, "trace", true, true
        );
        assertFalse(service.query(trendOnly).summary().contains("测试节假日"));

        HighwayTrafficSnapshot smooth = new HighwayTrafficSnapshot(
                List.of(new HighwayRoute("G104", "北京-平潭", "国道", "宁德", "福州")),
                List.of(new RouteTrafficSummary("G104", "北京-平潭", 80d, TrafficStatus.SMOOTH)),
                List.of(new HighwayTrafficSegment("G104", "北京-平潭", "畅通段", 1d, 80d,
                        TrafficStatus.SMOOTH, 0d)),
                snapshot().acquiredAt(), "smooth"
        );
        HighwayTrafficService smoothService = new HighwayTrafficService(
                () -> smooth, new RecordingModel(), ignored -> List.of(holiday)
        );
        assertFalse(smoothService.query(new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, "G104", null, "trace"
        )).summary().contains("测试节假日"));
    }

    @Test
    void routeCatalogReturnsEveryActiveRouteAndSegmentWithoutModelGeneration() {
        RecordingModel model = new RecordingModel();
        var result = service(model).query(query(TrafficQueryType.ROUTE_CATALOG));

        assertEquals(List.of("G104", "S201"), result.routeSummaries().stream()
                .map(route -> route.routeCode()).toList());
        assertEquals(13, result.segments().size());
        assertTrue(result.summary().contains("已列出"));
        assertNull(model.lastRequest);
    }

    @Test
    void zeroSpeedNeverOverridesAuthoritativeSmoothStatus() {
        HighwayTrafficSnapshot zeroSpeed = new HighwayTrafficSnapshot(
                List.of(new HighwayRoute("G104", "北京-平潭", "国道", "宁德", "福州")),
                List.of(new RouteTrafficSummary("G104", "北京-平潭", 0d, TrafficStatus.SMOOTH)),
                List.of(new HighwayTrafficSegment(
                        "G104", "北京-平潭", "测试路段", 1d, 0d, TrafficStatus.SMOOTH, 1d
                )),
                Instant.parse("2026-08-13T01:00:00Z"), "zero-speed"
        );
        RecordingModel model = new RecordingModel();
        HighwayTrafficService service = new HighwayTrafficService(() -> zeroSpeed, model);

        var result = service.query(new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, "G104", null, "trace"
        ));

        assertEquals(10, result.segments().get(0).status());
        assertTrue(model.lastRequest.userPrompt().contains("均速=0.00|status=10/畅通"));
        assertTrue(model.lastRequest.userPrompt().contains("字段冲突时按status"));
    }

    @Test
    void abnormalKeepsOnlyStatusAtLeastTwentyAndUsesDeterministicOrderAndLimit() {
        RecordingModel model = new RecordingModel();
        var result = service(model).query(query(TrafficQueryType.PROVINCE_ABNORMAL));

        assertEquals(12, result.totalSegmentCount());
        assertEquals(10, result.displayedSegmentCount());
        assertTrue(result.truncated());
        assertEquals(50, result.segments().get(0).status());
        assertFalse(result.segments().stream().anyMatch(segment -> segment.status() == 10));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void cityPairMatchesBothDirectionsAndRouteNameNormalizesHyphens() {
        HighwayTrafficService service = service(new RecordingModel());
        var cityResult = service.query(new HighwayTrafficQuery(
                TrafficQueryType.CITY_PAIR, "宁德市", "福州", null, null, "trace"
        ));
        var routeResult = service.query(new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, null, "北京 — 平潭", "trace"
        ));

        assertEquals(List.of("G104"), cityResult.routeSummaries().stream()
                .map(route -> route.routeCode()).toList());
        assertEquals("G104", routeResult.routeSummaries().get(0).routeCode());
    }

    @Test
    void routeCodeHasPriorityAndMissingOrAmbiguousNameIsRejected() {
        HighwayTrafficService service = service(new RecordingModel());
        var result = service.query(new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, "g104", "不存在", "trace"
        ));
        assertEquals("G104", result.routeSummaries().get(0).routeCode());
        assertThrows(BusinessRuleException.class, () -> service.query(new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, null, "不存在", "trace"
        )));

        HighwayTrafficSnapshot ambiguous = new HighwayTrafficSnapshot(
                List.of(
                        new HighwayRoute("G104", "北京-平潭", "国道", "宁德", "福州"),
                        new HighwayRoute("S104", "北京 — 平潭", "省道", "宁德", "福州")
                ),
                List.of(
                        new RouteTrafficSummary("G104", "北京-平潭", 80d, TrafficStatus.SMOOTH),
                        new RouteTrafficSummary("S104", "北京 — 平潭", 60d, TrafficStatus.LIGHT_CONGESTION)
                ),
                List.of(
                        new HighwayTrafficSegment("G104", "北京-平潭", "A", 1d, 80d, TrafficStatus.SMOOTH, 0d),
                        new HighwayTrafficSegment("S104", "北京 — 平潭", "B", 1d, 60d, TrafficStatus.LIGHT_CONGESTION, 0.2)
                ),
                Instant.parse("2026-08-13T01:00:00Z"), "ambiguous"
        );
        HighwayTrafficService ambiguousService = new HighwayTrafficService(() -> ambiguous, new RecordingModel());
        BusinessRuleException exception = assertThrows(BusinessRuleException.class, () -> ambiguousService.query(
                new HighwayTrafficQuery(TrafficQueryType.ROUTE_DETAIL, null, null, null, "北京-平潭", "trace")
        ));
        assertEquals("TRAFFIC_ROUTE_AMBIGUOUS", exception.errorCode());
    }

    @Test
    void cityPairReturnsAllWhenSmallAndTopTwentyWhenLarge() {
        HighwayTrafficSnapshot original = snapshot();
        HighwayTrafficService small = new HighwayTrafficService(() -> original, new RecordingModel());
        assertEquals(7, small.query(new HighwayTrafficQuery(
                TrafficQueryType.CITY_PAIR, "宁德", "福州", null, null, "trace"
        )).displayedSegmentCount());

        List<HighwayTrafficSegment> many = new ArrayList<>();
        for (int index = 0; index < 25; index++) {
            many.add(new HighwayTrafficSegment(
                    "G104", "北京-平潭", "PAIR-" + index, 2d, 50d - index,
                    index == 24 ? TrafficStatus.BLOCKED : TrafficStatus.LIGHT_CONGESTION,
                    Math.min(1d, index / 25d)
            ));
        }
        HighwayTrafficSnapshot large = new HighwayTrafficSnapshot(
                List.of(original.routes().get(1)), List.of(original.routeSummaries().get(1)),
                many, original.acquiredAt(), "large"
        );
        HighwayTrafficService largeService = new HighwayTrafficService(() -> large, new RecordingModel());
        var result = largeService.query(new HighwayTrafficQuery(
                TrafficQueryType.CITY_PAIR, "宁德", "福州", null, null, "trace"
        ));

        assertEquals(25, result.totalSegmentCount());
        assertEquals(20, result.displayedSegmentCount());
        assertTrue(result.truncated());
        assertEquals(50, result.segments().get(0).status());
    }

    private HighwayTrafficService service(ChatModelPort model) {
        return new HighwayTrafficService(this::snapshot, model);
    }

    private HighwayTrafficQuery query(TrafficQueryType type) {
        return new HighwayTrafficQuery(type, null, null, null, null, "trace");
    }

    private HighwayTrafficSnapshot snapshot() {
        List<HighwayRoute> routes = List.of(
                new HighwayRoute("S201", "柘荣-霞浦", "省道", "霞浦市", "宁德市"),
                new HighwayRoute("G104", "北京-平潭", "国道", "宁德", "福州市")
        );
        List<RouteTrafficSummary> summaries = List.of(
                new RouteTrafficSummary("S201", "柘荣-霞浦", 55.5, TrafficStatus.LIGHT_CONGESTION),
                new RouteTrafficSummary("G104", "北京-平潭", 88.8, TrafficStatus.SMOOTH)
        );
        List<HighwayTrafficSegment> segments = new ArrayList<>();
        segments.add(new HighwayTrafficSegment(
                "G104", "北京-平潭", "畅通段", 10d, 90d, TrafficStatus.SMOOTH, 0d
        ));
        for (int index = 0; index < 12; index++) {
            TrafficStatus status = index == 6 ? TrafficStatus.BLOCKED
                    : index % 2 == 0 ? TrafficStatus.MODERATE_CONGESTION : TrafficStatus.LIGHT_CONGESTION;
            segments.add(new HighwayTrafficSegment(
                    index < 6 ? "G104" : "S201", index < 6 ? "北京-平潭" : "柘荣-霞浦",
                    "FJ" + index + "→FJ" + (index + 1), 5d + index, 45d - index,
                    status, 0.20 + index * 0.05
            ));
        }
        return new HighwayTrafficSnapshot(
                routes, summaries, segments, Instant.parse("2026-08-13T01:00:00Z"), "fp"
        );
    }

    private TrafficContextEvent contextEvent(
            String code,
            String name,
            TrafficContextEventType type,
            TrafficContextScope scope,
            String regionCode,
            Set<String> routes
    ) {
        return new TrafficContextEvent(
                1L, code, name, type,
                Instant.parse("2026-08-13T00:00:00Z"),
                Instant.parse("2026-08-13T02:00:00Z"),
                Instant.parse("2026-08-12T23:00:00Z"),
                Instant.parse("2026-08-13T03:00:00Z"),
                scope, regionCode, regionCode == null ? null : "福州市",
                routes, null, "DEMO"
        );
    }

    private static String currentSummary() {
        return "当前福建普通国省干线交通态势总体平稳，路线状态已依据最新交通数据完成汇总。"
                + "主要路线通行状态保持良好，部分路段存在轻度拥堵，需要关注均速较低且拥堵指数较高的区段。"
                + "建议出行前结合页面路线与路段明细选择通行线路，对异常路段适当预留时间并优先错峰通行。";
    }

    private static final class RecordingModel implements ChatModelPort {
        private ModelRequest lastRequest;

        @Override
        public ModelResponse generate(ModelRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            lastRequest = request;
            return resultType.cast(new TrafficForecastSummaryResponse(
                    currentSummary(),
                    "局部分化",
                    "未来1至2小时，预计相关道路通行趋势为局部分化。"
            ));
        }

        @Override
        public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
