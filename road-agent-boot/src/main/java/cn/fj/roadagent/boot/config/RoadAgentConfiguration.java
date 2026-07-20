package cn.fj.roadagent.boot.config;

import cn.fj.roadagent.adapters.model.openai.OpenAiCompatibleChatModelAdapter;
import cn.fj.roadagent.adapters.tool.QueryRealtimeTrafficTool;
import cn.fj.roadagent.adapters.traffic.amap.AmapTrafficDataAdapter;
import cn.fj.roadagent.adapters.traffic.mock.MockTrafficDataAdapter;
import cn.fj.roadagent.adapters.traffic.mock.MockTrafficScenario;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.application.port.TrafficQueryTool;
import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import cn.fj.roadagent.core.traffic.RealtimeTrafficSkill;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties(RoadAgentProperties.class)
public class RoadAgentConfiguration {

    @Bean
    Clock roadAgentClock() {
        return Clock.systemUTC();
    }

    @Bean
    TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "roadagent.traffic", name = "provider",
            havingValue = "mock", matchIfMissing = true)
    TrafficDataPort mockTrafficDataPort(RoadAgentProperties properties, Clock clock) {
        return new MockTrafficDataAdapter(
                MockTrafficScenario.from(properties.getTraffic().getMockScenario()),
                clock
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "roadagent.traffic", name = "provider", havingValue = "amap")
    TrafficDataPort amapTrafficDataPort(RoadAgentProperties properties, Clock clock) {
        RoadAgentProperties.Amap amap = properties.getTraffic().getAmap();
        requireSecret(amap.getApiKey(), "使用高德数据时必须设置AMAP_API_KEY");
        return new AmapTrafficDataAdapter(
                createRestClient(amap.getTimeoutSeconds()),
                amap.getEndpoint(),
                amap.getApiKey(),
                amap.getRoadLevel(),
                clock
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "roadagent.model", name = "provider",
            havingValue = "openai-compatible")
    ChatModelPort openAiCompatibleChatModelPort(RoadAgentProperties properties) {
        RoadAgentProperties.Model model = properties.getModel();
        requireSecret(model.getApiKey(), "启用模型摘要时必须设置ROADAGENT_MODEL_API_KEY");
        return new OpenAiCompatibleChatModelAdapter(
                createRestClient(model.getTimeoutSeconds()),
                model.getEndpoint(),
                model.getApiKey(),
                model.getModelName()
        );
    }

    @Bean
    TrafficQueryTool trafficQueryTool(TrafficDataPort trafficDataPort) {
        return new QueryRealtimeTrafficTool(trafficDataPort);
    }

    @Bean
    QueryRealtimeTrafficUseCase queryRealtimeTrafficUseCase(
            TrafficQueryTool trafficQueryTool,
            ObjectProvider<ChatModelPort> chatModelProvider,
            RoadAgentProperties properties,
            Clock clock
    ) {
        Optional<ChatModelPort> model = Optional.ofNullable(chatModelProvider.getIfAvailable());
        Duration staleAfter = Duration.ofMinutes(properties.getTraffic().getStaleAfterMinutes());
        return new RealtimeTrafficSkill(trafficQueryTool, model, clock, staleAfter);
    }

    private RestClient createRestClient(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private void requireSecret(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
