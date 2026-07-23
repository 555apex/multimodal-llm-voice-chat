package cn.fj.roadagent.boot.config;

import cn.fj.roadagent.adapters.dispatch.memory.InMemoryDispatchRepository;
import cn.fj.roadagent.adapters.dispatch.mock.MockResourceDataAdapter;
import cn.fj.roadagent.adapters.dispatch.mock.MockWorkOrderAdapter;
import cn.fj.roadagent.adapters.memory.InMemoryConversationMemoryAdapter;
import cn.fj.roadagent.adapters.model.openai.OpenAiCompatibleChatModelAdapter;
import cn.fj.roadagent.adapters.tool.QueryAreaTrafficTool;
import cn.fj.roadagent.adapters.tool.QueryEmergencyResourcesTool;
import cn.fj.roadagent.adapters.tool.QueryRealtimeTrafficTool;
import cn.fj.roadagent.adapters.traffic.amap.AmapAdministrativeAreaAdapter;
import cn.fj.roadagent.adapters.traffic.amap.AmapAreaTrafficDataAdapter;
import cn.fj.roadagent.adapters.traffic.amap.AmapTrafficDataAdapter;
import cn.fj.roadagent.application.agent.ConverseWithAgentUseCase;
import cn.fj.roadagent.application.port.AdministrativeAreaPort;
import cn.fj.roadagent.application.port.AreaTrafficDataPort;
import cn.fj.roadagent.application.port.AreaTrafficQueryTool;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.ConversationMemoryPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.application.port.ResourceQueryTool;
import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.application.port.TrafficQueryTool;
import cn.fj.roadagent.application.port.WorkOrderPort;
import cn.fj.roadagent.core.agent.AgentRuntime;
import cn.fj.roadagent.core.agent.AgentSkill;
import cn.fj.roadagent.core.agent.IntentPlanner;
import cn.fj.roadagent.core.agent.SkillRegistry;
import cn.fj.roadagent.core.dispatch.DispatchApplicationService;
import cn.fj.roadagent.core.dispatch.EmergencyDispatchSkill;
import cn.fj.roadagent.core.traffic.RealtimeTrafficSkill;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 在此统一选择Port的具体实现，核心业务不关心外部服务。 */
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

    @Bean(destroyMethod = "shutdown")
    ExecutorService agentExecutor() {
        return Executors.newFixedThreadPool(8);
    }

    @Bean
    @ConditionalOnProperty(prefix = "roadagent.traffic", name = "provider",
            havingValue = "amap", matchIfMissing = true)
    TrafficDataPort amapTrafficDataPort(RoadAgentProperties properties, Clock clock) {
        RoadAgentProperties.Amap amap = properties.getTraffic().getAmap();
        requireSecret(amap.getApiKey(), "使用高德数据时必须设置AMAP_API_KEY");
        return new AmapTrafficDataAdapter(
                createHttpRestClient(amap.getTimeoutSeconds()),
                amap.getEndpoint(), amap.getApiKey(), amap.getRoadLevel(), clock
        );
    }

    @Bean
    @ConditionalOnProperty(prefix = "roadagent.model", name = "provider",
            havingValue = "openai-compatible", matchIfMissing = true)
    ChatModelPort openAiCompatibleChatModelPort(
            RoadAgentProperties properties,
            ObjectMapper objectMapper
    ) {
        RoadAgentProperties.Model model = properties.getModel();
        if (model.isAuthEnabled()) {
            requireSecret(model.getApiKey(), "启用模型Bearer鉴权时必须设置ROADAGENT_MODEL_API_KEY");
        }
        Duration timeout = Duration.ofSeconds(model.getTimeoutSeconds());
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new OpenAiCompatibleChatModelAdapter(
                client, objectMapper, model.getEndpoint(), model.getApiKey(), model.getModelName(),
                model.isAuthEnabled(), timeout
        );
    }

    @Bean
    TrafficQueryTool trafficQueryTool(TrafficDataPort trafficDataPort) {
        return new QueryRealtimeTrafficTool(trafficDataPort);
    }

    @Bean
    AdministrativeAreaPort administrativeAreaPort(RoadAgentProperties properties, Clock clock) {
        RoadAgentProperties.Amap amap = properties.getTraffic().getAmap();
        return new AmapAdministrativeAreaAdapter(
                createHttpRestClient(amap.getTimeoutSeconds()), amap.getDistrictEndpoint(),
                amap.getApiKey(), clock, Duration.ofHours(amap.getDistrictCacheHours())
        );
    }

    @Bean(destroyMethod = "close")
    AreaTrafficDataPort areaTrafficDataPort(RoadAgentProperties properties, Clock clock) {
        RoadAgentProperties.Amap amap = properties.getTraffic().getAmap();
        return new AmapAreaTrafficDataAdapter(
                createHttpRestClient(amap.getTimeoutSeconds()), amap.getAreaEndpoint(),
                amap.getApiKey(), clock, amap.getAreaTileSizeKm(), amap.getAreaMaxTiles(),
                amap.getAreaConcurrency(), Duration.ofSeconds(amap.getAreaCacheSeconds())
        );
    }

    @Bean
    AreaTrafficQueryTool areaTrafficQueryTool(
            AdministrativeAreaPort administrativeAreaPort,
            AreaTrafficDataPort areaTrafficDataPort
    ) {
        return new QueryAreaTrafficTool(administrativeAreaPort, areaTrafficDataPort);
    }

    @Bean
    RealtimeTrafficSkill realtimeTrafficSkill(
            TrafficQueryTool trafficQueryTool,
            AreaTrafficQueryTool areaTrafficQueryTool,
            ChatModelPort chatModelPort,
            RoadAgentProperties properties,
            Clock clock
    ) {
        Duration staleAfter = Duration.ofMinutes(properties.getTraffic().getStaleAfterMinutes());
        return new RealtimeTrafficSkill(
                trafficQueryTool, areaTrafficQueryTool, chatModelPort, clock, staleAfter
        );
    }

    @Bean
    ResourceDataPort resourceDataPort() {
        return new MockResourceDataAdapter();
    }

    @Bean
    ResourceQueryTool resourceQueryTool(ResourceDataPort dataPort) {
        return new QueryEmergencyResourcesTool(dataPort);
    }

    @Bean
    DispatchRepository dispatchRepository() {
        return new InMemoryDispatchRepository();
    }

    @Bean
    WorkOrderPort workOrderPort(Clock clock) {
        return new MockWorkOrderAdapter(clock);
    }

    @Bean
    EmergencyDispatchSkill emergencyDispatchSkill(
            ResourceQueryTool resourceQueryTool,
            ChatModelPort chatModelPort,
            DispatchRepository dispatchRepository,
            Clock clock
    ) {
        return new EmergencyDispatchSkill(resourceQueryTool, chatModelPort, dispatchRepository, clock);
    }

    @Bean
    DispatchApplicationService dispatchApplicationService(
            DispatchRepository repository,
            WorkOrderPort workOrderPort
    ) {
        return new DispatchApplicationService(repository, workOrderPort);
    }

    @Bean
    ConversationMemoryPort conversationMemoryPort(
            RoadAgentProperties properties,
            Clock clock
    ) {
        var memory = properties.getMemory();
        return new InMemoryConversationMemoryAdapter(
                memory.getMaxMessages(), Duration.ofMinutes(memory.getIdleMinutes()), clock
        );
    }

    @Bean
    IntentPlanner intentPlanner(ChatModelPort chatModelPort) {
        return new IntentPlanner(chatModelPort);
    }

    @Bean
    SkillRegistry skillRegistry(List<AgentSkill> skills) {
        return new SkillRegistry(skills);
    }

    @Bean
    ConverseWithAgentUseCase agentRuntime(
            IntentPlanner planner,
            SkillRegistry registry,
            ConversationMemoryPort memoryPort,
            Clock clock
    ) {
        return new AgentRuntime(planner, registry, memoryPort, clock);
    }

    private org.springframework.web.client.RestClient createHttpRestClient(int timeoutSeconds) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(timeout);
        return org.springframework.web.client.RestClient.builder().requestFactory(requestFactory).build();
    }

    private void requireSecret(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
