package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.model.openai.OpenAiCompatibleChatModelAdapter;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.core.traffic.HighwayTrafficService;
import cn.fj.roadagent.core.traffic.RegionalTrafficService;
import cn.fj.roadagent.core.traffic.RoadCapacityService;
import cn.fj.roadagent.core.traffic.VehiclePatternService;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RoadCapacity;
import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;
import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.domain.traffic.TrafficStatus;
import cn.fj.roadagent.domain.traffic.RegionalConnectionHub;
import cn.fj.roadagent.domain.traffic.VehicleHourlyFlow;
import cn.fj.roadagent.domain.traffic.VehicleTravelPatternSnapshot;
import cn.fj.roadagent.domain.traffic.VehicleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用完全构造的演示事实验证真实模型响应兼容性，不读取或外传共享MySQL数据。
 */
@EnabledIfEnvironmentVariable(named = "ROADAGENT_MODEL_LIVE_TEST", matches = "(?i)true")
class TrafficModelResponseCompatibilityIntegrationTest {
    private static ChatModelPort model;

    @BeforeAll
    static void createModel() {
        String endpoint = System.getenv().getOrDefault(
                "ROADAGENT_MODEL_ENDPOINT", "https://api.deepseek.com/chat/completions"
        );
        String modelName = System.getenv().getOrDefault("ROADAGENT_MODEL_NAME", "deepseek-v4-flash");
        model = new OpenAiCompatibleChatModelAdapter(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                new ObjectMapper().findAndRegisterModules(), endpoint,
                System.getenv("ROADAGENT_MODEL_API_KEY"), modelName, true, Duration.ofSeconds(90)
        );
    }

    @Test
    void acceptsRoadConditionSummaryAndQualitativeTrend() {
        HighwayTrafficSnapshot snapshot = new HighwayTrafficSnapshot(
                List.of(new HighwayRoute("G104", "北京-平潭", "国道", "宁德市", "福州市")),
                List.of(new RouteTrafficSummary("G104", "北京-平潭", 42.5, TrafficStatus.MODERATE_CONGESTION)),
                List.of(new HighwayTrafficSegment(
                        "G104", "北京-平潭", "演示交调站A至演示交调站B", 12.5, 35.2,
                        TrafficStatus.MODERATE_CONGESTION, 0.62
                )),
                Instant.parse("2026-08-28T08:00:00Z"), "synthetic-road"
        );
        var result = new HighwayTrafficService(() -> snapshot, model).query(new HighwayTrafficQuery(
                TrafficQueryType.PROVINCE_ABNORMAL, null, null, null, null, "synthetic"
        ));

        assertTrue(result.summary().contains("未来1至2小时"));
        assertFalse(result.segments().isEmpty());
    }

    @Test
    void capacityOverviewDoesNotDependOnExactTableWording() {
        List<RoadCapacity> rows = IntStream.range(0, 8)
                .mapToObj(index -> new RoadCapacity(
                        "S%03d".formatted(201 + index), "演示路线" + (index + 1),
                        300 + index * 20, 1800, 0.10 + index * 0.05
                )).toList();
        var result = new RoadCapacityService(
                () -> new RoadCapacitySnapshot(rows, Instant.parse("2026-08-28T08:00:00Z"), "synthetic-capacity"),
                model
        ).query(new HighwayTrafficQuery(
                TrafficQueryType.CAPACITY_OVERVIEW, null, null, null, null, "synthetic"
        ));

        assertFalse(result.summary().isBlank());
        assertTrue(result.capacityRows().size() == 8);
    }

    @Test
    void acceptsRegionalSummaryEvenWhenInsightWordingVaries() {
        RegionalTrafficSnapshot snapshot = new RegionalTrafficSnapshot(List.of(
                new RegionalConnectionHub("DEMO-FZ-1", "示例卡口1", "G104", "北京-平潭",
                        "350100", "福州市", "350900", "宁德市", 10d, 51.2, 4340, 620),
                new RegionalConnectionHub("DEMO-NP-1", "示例卡口2", "G316", "长乐-同仁",
                        "350100", "福州市", "350700", "南平市", 20d, 47.8, 4060, 580)
        ), Instant.parse("2026-08-28T08:00:00Z"), List.of());
        var result = new RegionalTrafficService(() -> snapshot, model).query(new HighwayTrafficQuery(
                TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW, null, null, null, null,
                List.of("福州", "宁德", "南平"), null, "synthetic"
        ));

        assertFalse(result.summary().isBlank());
        assertTrue(result.regionalPairRows().size() == 2);
    }

    @Test
    void acceptsVehiclePatternSummaryForLatestCityFacts() {
        VehicleTravelPatternSnapshot snapshot = new VehicleTravelPatternSnapshot(
                "福州市", volumes(900, 200, 100),
                List.of(
                        new VehicleHourlyFlow(LocalDateTime.of(2026, 8, 28, 8, 0), volumes(120, 30, 20)),
                        new VehicleHourlyFlow(LocalDateTime.of(2026, 8, 28, 18, 0), volumes(180, 20, 35))
                ),
                volumes(180, 40, 20), LocalDate.of(2026, 8, 28), Instant.parse("2026-08-28T08:00:00Z")
        );
        var result = new VehiclePatternService(city -> Optional.of(snapshot), model).query(
                new HighwayTrafficQuery(
                        TrafficQueryType.VEHICLE_PATTERN_OVERVIEW, null, null, null, null,
                        List.of(), "福州", "synthetic"
                )
        );

        assertFalse(result.summary().isBlank());
        assertTrue(result.hourlyVehicleSeries().size() == 24);
    }

    private static Map<VehicleType, Long> volumes(long car, long bus, long truck) {
        EnumMap<VehicleType, Long> result = new EnumMap<>(VehicleType.class);
        result.put(VehicleType.CAR, car);
        result.put(VehicleType.BUS, bus);
        result.put(VehicleType.TRUCK, truck);
        return result;
    }
}
