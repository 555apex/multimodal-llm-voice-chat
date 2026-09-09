package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.TrafficSummaryResponse;
import cn.fj.roadagent.domain.traffic.CapacityLevel;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RoadCapacity;
import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadCapacityServiceTest {

    @Test
    void overviewReturnsEveryRouteSortedAndReportsThreeLevelCounts() {
        RecordingModel model = new RecordingModel();
        RoadCapacityService service = service(model, List.of(
                row("S201", "柘荣-霞浦", 1200, 0.16),
                row("G104", "北京-平潭", 1600, 0.30),
                row("G316", "长乐-同仁", 0, 0.10)
        ));

        var result = service.query(query(TrafficQueryType.CAPACITY_OVERVIEW, null, null));

        assertEquals(List.of("G104", "G316", "S201"), result.capacityRows().stream()
                .map(value -> value.routeCode()).toList());
        assertEquals(3, result.totalSegmentCount());
        assertEquals("SEVERE_BOTTLENECK", result.capacityRows().get(0).capacityLevel());
        assertTrue(model.lastRequest.userPrompt().contains("正常:1；瓶颈:1；严重瓶颈:1"));
        assertTrue(model.lastRequest.systemPrompt().contains("不得重新计算"));
        assertTrue(result.routeSummaries().isEmpty());
        assertTrue(result.segments().isEmpty());
    }

    @Test
    void bottleneckRankingFiltersNormalAndReturnsHighestTenInStableOrder() {
        List<RoadCapacity> rows = new ArrayList<>();
        rows.add(row("G001", "正常路线", 100, 0.1));
        for (int index = 0; index < 12; index++) {
            double ratio = 0.15 + index * 0.02;
            rows.add(row("S%03d".formatted(index), "路线" + index, 300 + index, ratio));
        }
        RoadCapacityService service = service(new RecordingModel(), rows);

        var result = service.query(query(TrafficQueryType.CAPACITY_BOTTLENECKS, null, null));

        assertEquals(12, result.totalSegmentCount());
        assertEquals(10, result.displayedSegmentCount());
        assertTrue(result.truncated());
        assertFalse(result.capacityRows().stream().anyMatch(row -> row.capacityLevel().equals("NORMAL")));
        assertEquals("S011", result.capacityRows().get(0).routeCode());
        assertEquals("S010", result.capacityRows().get(1).routeCode());
        assertEquals("SEVERE_BOTTLENECK", result.capacityRows().get(0).capacityLevel());
    }

    @Test
    void zeroIsValidNormalValueAndDatabaseValuesAreNotRecalculated() {
        RoadCapacityService service = service(new RecordingModel(), List.of(
                new RoadCapacity("G104", "北京-平潭", 0, 1920, 0)
        ));

        var result = service.query(query(TrafficQueryType.CAPACITY_ROUTE_DETAIL, "g104", null));

        assertEquals(0, result.capacityRows().get(0).actualCapacityVph());
        assertEquals(1920, result.capacityRows().get(0).designCapacityVph());
        assertEquals(0, result.capacityRows().get(0).utilizationRatio());
        assertEquals(CapacityLevel.NORMAL.name(), result.capacityRows().get(0).capacityLevel());
    }

    @Test
    void routeCodeHasPriorityAndNormalizedNameCanBeAmbiguous() {
        RoadCapacityService service = service(new RecordingModel(), List.of(
                row("G104", "北京-平潭", 100, 0.1),
                row("S104", "北京 — 平潭", 200, 0.2)
        ));

        var result = service.query(query(TrafficQueryType.CAPACITY_ROUTE_DETAIL, "g104", "不存在"));
        assertEquals("G104", result.capacityRows().get(0).routeCode());

        BusinessRuleException ambiguous = assertThrows(BusinessRuleException.class, () ->
                service.query(query(TrafficQueryType.CAPACITY_ROUTE_DETAIL, null, "北京 - 平潭")));
        assertEquals("CAPACITY_ROUTE_AMBIGUOUS", ambiguous.errorCode());
        assertEquals("CAPACITY_ROUTE_NOT_FOUND", assertThrows(BusinessRuleException.class, () ->
                service.query(query(TrafficQueryType.CAPACITY_ROUTE_DETAIL, null, "不存在"))).errorCode());
    }

    @Test
    void noBottlenecksReturnsEmptyTableWithoutTruncation() {
        RoadCapacityService service = service(new RecordingModel(), List.of(
                row("G104", "北京-平潭", 160, 0.08),
                row("S201", "柘荣-霞浦", 192, 0.10)
        ));

        var result = service.query(query(TrafficQueryType.CAPACITY_BOTTLENECKS, null, null));

        assertTrue(result.capacityRows().isEmpty());
        assertEquals(0, result.totalSegmentCount());
        assertFalse(result.truncated());
    }

    @Test
    void allHighUtilizationBatchMarksEveryRouteSevereAndStillReturnsTopTen() {
        List<RoadCapacity> rows = java.util.stream.IntStream.range(0, 48)
                .mapToObj(index -> row("S%03d".formatted(index), "路线" + index, 600, 0.30))
                .toList();
        RoadCapacityService service = service(new RecordingModel(), rows);

        var facts = service.collectFacts(query(TrafficQueryType.CAPACITY_BOTTLENECKS, null, null));

        assertEquals(48, facts.totalCount());
        assertEquals(48, facts.severeBottleneckCount());
        assertEquals(0, facts.bottleneckCount());
        assertEquals(0, facts.normalCount());
        assertEquals(10, facts.rows().size());
        assertTrue(facts.rows().stream().allMatch(row -> row.level() == CapacityLevel.SEVERE_BOTTLENECK));
    }

    @Test
    void modelMayUseNaturalWordingWithoutExactCountsRouteListOrTableKeyword() {
        ChatModelPort invalidModel = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
                return resultType.cast(new TrafficSummaryResponse(
                        "当前福建国省干线路线通行能力已完成汇总，整体评估结果可结合页面信息进行查看。"
                                + "部分路线需要持续关注，相关实际通行能力、设计通行能力和利用率已经形成结构化结果。"
                                + "建议运行监测人员结合页面表格开展后续巡查，并根据现场管理需要合理安排调度工作。"
                ));
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        RoadCapacitySnapshot snapshot = new RoadCapacitySnapshot(
                List.of(row("G104", "北京-平潭", 400, 0.20)),
                Instant.parse("2026-08-13T01:00:00Z"), "capacity-fp"
        );
        RoadCapacityService service = new RoadCapacityService(() -> snapshot, invalidModel);

        var result = service.query(query(TrafficQueryType.CAPACITY_BOTTLENECKS, null, null));

        assertTrue(result.summary().contains("页面信息"));
        assertEquals(1, result.capacityRows().size());
    }

    @Test
    void unsupportedModelNumberUsesFactSafeSummaryInsteadOfFailingTheRequest() {
        ChatModelPort inaccurateModel = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
                return resultType.cast(new TrafficSummaryResponse(
                        "当前国省道通行能力已完成总体评估，共有999条路线需要重点关注。"
                                + "相关路线的实际通行能力和利用率已经形成明细，可据此安排后续巡查。"
                                + "建议持续跟踪重点路线运行情况，并结合后续批次变化开展交通组织。"
                ));
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        RoadCapacitySnapshot snapshot = new RoadCapacitySnapshot(
                List.of(row("G104", "北京-平潭", 400, 0.20)),
                Instant.parse("2026-08-13T01:00:00Z"), "capacity-fp"
        );

        var result = new RoadCapacityService(() -> snapshot, inaccurateModel).query(
                query(TrafficQueryType.CAPACITY_OVERVIEW, null, null)
        );

        assertFalse(result.summary().contains("999"));
        assertTrue(result.summary().contains("瓶颈路线1条"));
    }

    @Test
    void twoCityCapacityQueryUsesWholeRoutesWhoseRegisteredEndpointsMatchEitherDirection() {
        RoadCapacitySnapshot capacitySnapshot = new RoadCapacitySnapshot(
                List.of(
                        row("G104", "北京-平潭", 180, 0.18),
                        row("S201", "柘荣-霞浦", 200, 0.2),
                        row("G324", "福州-昆明", 300, 0.3)
                ), Instant.parse("2026-08-13T01:00:00Z"), "capacity-fp");
        HighwayTrafficSnapshot trafficSnapshot = new HighwayTrafficSnapshot(
                List.of(
                        new HighwayRoute("G104", "北京-平潭", "国道", "宁德", "福州"),
                        new HighwayRoute("S201", "柘荣-霞浦", "省道", "福州", "宁德"),
                        new HighwayRoute("G324", "福州-昆明", "国道", "福州", "漳州")
                ), List.of(), List.of(), Instant.parse("2026-08-13T01:00:00Z"), "traffic-fp");
        RoadCapacityService service = new RoadCapacityService(
                () -> capacitySnapshot, () -> trafficSnapshot, new RecordingModel());
        HighwayTrafficQuery query = new HighwayTrafficQuery(
                TrafficQueryType.CAPACITY_BOTTLENECKS, "福州", "宁德", null, null,
                List.of(), null, "trace", false);

        var result = service.query(query);

        assertEquals(List.of("S201", "G104"), result.capacityRows().stream()
                .map(row -> row.routeCode()).toList());
        assertTrue(result.title().contains("登记起终点关联路线"));
    }

    private RoadCapacityService service(RecordingModel model, List<RoadCapacity> rows) {
        RoadCapacitySnapshot snapshot = new RoadCapacitySnapshot(
                rows, Instant.parse("2026-08-13T01:00:00Z"), "capacity-fp"
        );
        return new RoadCapacityService(() -> snapshot, model);
    }

    private RoadCapacity row(String code, String name, double actual, double ratio) {
        return new RoadCapacity(code, name, actual, 1920, ratio);
    }

    private HighwayTrafficQuery query(TrafficQueryType type, String routeCode, String routeName) {
        return new HighwayTrafficQuery(type, null, null, routeCode, routeName, "trace");
    }

    private static final class RecordingModel implements ChatModelPort {
        private ModelRequest lastRequest;

        @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            lastRequest = request;
            java.util.regex.Matcher counts = java.util.regex.Pattern.compile(
                    "levelCounts=正常:(\\d+)；瓶颈:(\\d+)；严重瓶颈:(\\d+)"
            ).matcher(request.userPrompt());
            assertTrue(counts.find());
            int bottleneckCount = Integer.parseInt(counts.group(2));
            int severeCount = Integer.parseInt(counts.group(3));
            String mentionLine = request.userPrompt().lines()
                    .filter(line -> line.startsWith("bottleneckRoutesToMention="))
                    .findFirst().orElse("bottleneckRoutesToMention=")
                    .substring("bottleneckRoutesToMention=".length());
            String focus = mentionLine.isBlank()
                    ? "当前未发现需要列出的瓶颈路线，所有路线的详细能力结果均可在页面表格中核对。"
                    : "其中需要重点关注的路线包括" + mentionLine + "其实际通行能力和利用率均已按数据库结果展示。";
            if (bottleneckCount + severeCount > 5) {
                focus += "其余瓶颈路线请查看页面表格。";
            }
            return resultType.cast(new TrafficSummaryResponse(
                    "当前福建普通国省干线路线通行能力已完成评估，正常路线" + counts.group(1)
                            + "条、瓶颈路线" + counts.group(2) + "条、严重瓶颈路线" + counts.group(3) + "条。"
                            + focus
                            + "建议运行监测人员结合页面表格优先关注严重瓶颈和瓶颈路线，并据此安排后续巡查与调度工作。"
            ));
        }

        @Override public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
