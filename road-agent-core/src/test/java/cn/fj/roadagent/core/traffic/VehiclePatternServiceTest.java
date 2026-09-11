package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.VehicleTravelPatternPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.TrafficSummaryResponse;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.domain.traffic.VehicleHourlyFlow;
import cn.fj.roadagent.domain.traffic.VehicleTravelPatternSnapshot;
import cn.fj.roadagent.domain.traffic.VehicleType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehiclePatternServiceTest {

    @Test
    void overviewFillsMissingHoursCalculatesSharesAndDayTypes() {
        VehiclePatternService service = service(snapshot());

        var facts = service.collectFacts(query(TrafficQueryType.VEHICLE_PATTERN_OVERVIEW, "福州市"));

        assertEquals(24, facts.hourlySeries().size());
        assertEquals(0, facts.hourlySeries().get(0).car());
        assertEquals(165, facts.hourlySeries().get(10).car());
        assertEquals(3, facts.structureRows().size());
        assertEquals(0.75, facts.structureRows().get(0).shareRatio());
        assertEquals(750, facts.dayTypeRows().get(0).weekdayVolume());
        assertEquals(150, facts.dayTypeRows().get(0).weekendVolume());
        assertEquals(22, facts.missingHourCount());
        var result = cn.fj.roadagent.application.traffic.HighwayTrafficResult
                .fromVehicleFacts(facts, "测试摘要", "trace");
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("补0不代表实际无车")));
    }

    @Test
    void peakTieUsesEarlierHourAndSpecificQueryOnlyReturnsRelevantRows() {
        VehiclePatternService service = service(snapshot());

        var facts = service.collectFacts(query(TrafficQueryType.VEHICLE_HOURLY_PATTERN, "福州"));

        assertTrue(facts.structureRows().isEmpty());
        assertTrue(facts.dayTypeRows().isEmpty());
        assertEquals(24, facts.hourlySeries().size());
        assertEquals("10:00–10:59", facts.timeFeatureRows().get(0).peakHour());
    }

    @Test
    void keepsAvailableHourlyPointsWhenTheirDateDiffersFromRecordDate() {
        VehicleTravelPatternSnapshot mismatched = new VehicleTravelPatternSnapshot(
                "福州市", volumes(900, 200, 100),
                List.of(new VehicleHourlyFlow(LocalDateTime.of(2026, 8, 26, 10, 0), volumes(165, 20, 10))),
                volumes(150, 50, 20), LocalDate.of(2026, 8, 27),
                Instant.parse("2026-08-27T08:00:01Z")
        );
        VehiclePatternService service = service(mismatched);

        var facts = service.collectFacts(query(TrafficQueryType.VEHICLE_HOURLY_PATTERN, "福州"));

        assertEquals(165, facts.hourlySeries().get(10).car());
        assertEquals(LocalDate.of(2026, 8, 26), facts.hourlyDataDate());
        assertEquals(23, facts.missingHourCount());
    }

    @Test
    void explicitHistoricalDateUsesDateAwareRepositoryQuery() {
        class RecordingPort implements VehicleTravelPatternPort {
            LocalDate requestedDate;
            @Override public Optional<VehicleTravelPatternSnapshot> latestForCity(String cityName) {
                throw new AssertionError("历史查询不应读取无日期最新记录");
            }
            @Override public Optional<VehicleTravelPatternSnapshot> forCityOnDate(String cityName, LocalDate date) {
                requestedDate = date;
                return Optional.of(snapshot());
            }
        }
        RecordingPort port = new RecordingPort();
        VehiclePatternService service = new VehiclePatternService(port, new NoopModel());
        HighwayTrafficQuery historical = new HighwayTrafficQuery(
                TrafficQueryType.VEHICLE_PATTERN_OVERVIEW, null, null, null, null,
                List.of(), "福州", "trace", false, false, LocalDate.of(2026, 8, 27)
        );

        var facts = service.collectFacts(historical);

        assertEquals(LocalDate.of(2026, 8, 27), port.requestedDate);
        assertEquals(LocalDate.of(2026, 8, 27), facts.analysisDate());
        assertTrue(facts.title().contains("2026年8月27日"));
    }

    @Test
    void requiresExactlyOneSupportedAnalysisCity() {
        VehiclePatternService service = service(snapshot());
        assertEquals("VEHICLE_PATTERN_CITY_REQUIRED", assertThrows(BusinessRuleException.class, () ->
                service.collectFacts(query(TrafficQueryType.VEHICLE_STRUCTURE, null))).errorCode());
        assertEquals("VEHICLE_PATTERN_CITY_UNSUPPORTED", assertThrows(BusinessRuleException.class, () ->
                service.collectFacts(query(TrafficQueryType.VEHICLE_STRUCTURE, "泉州"))).errorCode());
    }

    @Test
    void rejectsTwoSelectedCitiesAndUsesOneSelectedCityAsFallback() {
        VehiclePatternService service = service(snapshot());
        HighwayTrafficQuery twoCities = new HighwayTrafficQuery(
                TrafficQueryType.VEHICLE_PATTERN_OVERVIEW, null, null, null, null,
                List.of("福州", "厦门"), null, "trace"
        );
        assertEquals("VEHICLE_PATTERN_CITY_REQUIRED", assertThrows(BusinessRuleException.class, () ->
                service.collectFacts(twoCities)).errorCode());

        HighwayTrafficQuery oneCity = new HighwayTrafficQuery(
                TrafficQueryType.VEHICLE_PATTERN_OVERVIEW, null, null, null, null,
                List.of("福州"), null, "trace"
        );
        assertEquals("福州市", service.collectFacts(oneCity).analysisCity());
    }

    @Test
    void unsupportedModelNumberUsesFactSafeVehicleSummaryInsteadOfFailing() {
        ChatModelPort inaccurateModel = new ChatModelPort() {
            @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
            @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
                return resultType.cast(new TrafficSummaryResponse(
                        "福州市当前车型出行特征已经完成分析，小型客车通行量达到999999辆。"
                                + "分车型结构和峰值时段已形成相应统计，可结合下方图表观察全天规律。"
                                + "建议持续跟踪后续批次变化，并加强重点车型和重点时段的运行监测。"
                ));
            }
            @Override public void stream(ModelRequest request, ModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };
        VehiclePatternService service = new VehiclePatternService(city -> Optional.of(snapshot()), inaccurateModel);

        var result = service.query(query(TrafficQueryType.VEHICLE_PATTERN_OVERVIEW, "福州"));

        assertTrue(!result.summary().contains("999999"));
        assertTrue(result.summary().contains("最新记录"));
        assertTrue(result.summary().contains("缺失时段统一按0展示"));
    }

    private VehiclePatternService service(VehicleTravelPatternSnapshot snapshot) {
        return new VehiclePatternService(city -> Optional.of(snapshot), new NoopModel());
    }

    private HighwayTrafficQuery query(TrafficQueryType type, String city) {
        return new HighwayTrafficQuery(type, null, null, null, null, List.of(), city, "trace");
    }

    private VehicleTravelPatternSnapshot snapshot() {
        return new VehicleTravelPatternSnapshot(
                "福州市", volumes(900, 200, 100),
                List.of(
                        new VehicleHourlyFlow(LocalDateTime.of(2026, 8, 27, 10, 0), volumes(165, 20, 10)),
                        new VehicleHourlyFlow(LocalDateTime.of(2026, 8, 27, 11, 0), volumes(165, 30, 10))
                ),
                volumes(150, 50, 20), LocalDate.of(2026, 8, 27),
                Instant.parse("2026-08-27T08:00:01Z")
        );
    }

    private Map<VehicleType, Long> volumes(long car, long bus, long truck) {
        EnumMap<VehicleType, Long> result = new EnumMap<>(VehicleType.class);
        result.put(VehicleType.CAR, car);
        result.put(VehicleType.BUS, bus);
        result.put(VehicleType.TRUCK, truck);
        return result;
    }

    private static final class NoopModel implements ChatModelPort {
        @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
        @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) { throw new UnsupportedOperationException(); }
        @Override public void stream(ModelRequest request, ModelStreamListener listener) { throw new UnsupportedOperationException(); }
    }
}
