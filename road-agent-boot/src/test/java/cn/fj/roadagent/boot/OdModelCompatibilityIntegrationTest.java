package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.model.openai.OpenAiCompatibleChatModelAdapter;
import cn.fj.roadagent.application.agent.ConversationMessage;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.core.agent.IntentPlanner;
import cn.fj.roadagent.core.traffic.OdTrafficService;
import cn.fj.roadagent.domain.traffic.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.http.HttpClient;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 只把本文件构造的演示事实交给已配置模型，不读取或发送共享数据库内容。 */
@EnabledIfEnvironmentVariable(named = "ROADAGENT_MODEL_LIVE_TEST", matches = "(?i)true")
class OdModelCompatibilityIntegrationTest {
    private OpenAiCompatibleChatModelAdapter model() {
        return new OpenAiCompatibleChatModelAdapter(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                new ObjectMapper().findAndRegisterModules(), System.getenv("ROADAGENT_MODEL_ENDPOINT"),
                System.getenv("ROADAGENT_MODEL_API_KEY"), System.getenv("ROADAGENT_MODEL_NAME"),
                Boolean.parseBoolean(System.getenv().getOrDefault("ROADAGENT_MODEL_AUTH_ENABLED", "true")), Duration.ofSeconds(90));
    }

    @Test void modelSummarizesSyntheticFactsAndPlannerKeepsOdContext() {
        var model = model();
        var time = Instant.parse("2026-09-03T04:00:00Z");
        var rows = List.of(
                new RegionalConnectionHub("DEMO-F1", "演示卡口1", "G324", "演示路线", "350100", "福州市",
                        "350200", "厦门市", 10d, 50, 7000, 1000),
                new RegionalConnectionHub("DEMO-F2", "演示卡口2", "G324", "演示路线", "350100", "福州市",
                        "350200", "厦门市", 20d, 45, 3500, 500),
                new RegionalConnectionHub("DEMO-N1", "演示卡口3", "G104", "演示路线2", "350100", "福州市",
                        "350900", "宁德市", 30d, 40, 1750, 250));
        var service = new OdTrafficService(() -> new RegionalTrafficSnapshot(rows, time, List.of()), model);
        var result = service.query(new HighwayTrafficQuery(TrafficQueryType.OD_DESTINATION_TENDENCY, null, null, null, null,
                List.of("福州"), null, "synthetic-od"));
        assertFalse(result.summary().isBlank());
        assertFalse(result.summary().contains("未来"));
        assertEquals(5250, result.odDestinationRows().get(0).weeklyConnectionStrength());
        var planner = new IntentPlanner(model);
        var history = List.of(new ConversationMessage("user", "福州的出行主要联系哪些城市？", time),
                new ConversationMessage("assistant", result.summary(), time));
        var matrix = planner.plan("再加上厦门和泉州，看城市联系矩阵", history);
        assertEquals("OD_CONNECTION_MATRIX", matrix.trafficScope());
        assertEquals(List.of("福州", "厦门", "泉州"), matrix.selectedCities());
        assertTrue(planner.missingFields(matrix).isEmpty());
        var pressure = planner.plan("不要OD，只看福州和厦门的区域交通压力", history);
        assertEquals("REGIONAL_TRAFFIC_OVERVIEW", pressure.trafficScope());
    }
}
