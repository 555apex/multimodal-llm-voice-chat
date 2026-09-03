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
                new OdTransportHub("DEMO-F1", "350100", "福州市", "G324", "演示路线", 7000, 900, 50, 4900L, 700L, 1400L),
                new OdTransportHub("DEMO-X1", "350200", "厦门市", "G324", "演示路线", 3500, 450, 40, 2450L, 350L, 700L));
        var service = new OdTrafficService((codes, vehicles) -> new OdTrafficSnapshot(rows, time, List.of()), model);
        var result = service.query(new HighwayTrafficQuery(TrafficQueryType.OD_OVERVIEW, null, null, null, null,
                List.of("福州", "厦门"), null, "synthetic-od"));
        assertFalse(result.summary().isBlank());
        assertFalse(result.summary().contains("未来"));
        assertEquals(10500, result.odChannelRows().get(0).weeklyTotalFlow());
        var planner = new IntentPlanner(model);
        var history = List.of(new ConversationMessage("user", "福州和厦门的OD情况如何？", time),
                new ConversationMessage("assistant", result.summary(), time));
        var second = planner.plan("只看第二张表", history);
        assertEquals("OD_KEY_CHANNELS", second.trafficScope());
        assertEquals(2, second.selectedCities().size());
        assertTrue(planner.missingFields(second).isEmpty());
        var expanded = planner.plan("再加上泉州", history);
        assertEquals("OD_OVERVIEW", expanded.trafficScope());
        assertEquals(3, expanded.selectedCities().size());
        assertTrue(planner.missingFields(expanded).isEmpty());
        var pressure = planner.plan("不要OD，只看两市的区域交通压力", history);
        assertEquals("REGIONAL_TRAFFIC_OVERVIEW", pressure.trafficScope());
    }
}
