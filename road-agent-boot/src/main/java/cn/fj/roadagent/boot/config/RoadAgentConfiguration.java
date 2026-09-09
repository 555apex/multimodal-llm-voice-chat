package cn.fj.roadagent.boot.config;

import cn.fj.roadagent.adapters.memory.InMemoryConversationMemoryAdapter;
import cn.fj.roadagent.adapters.model.openai.OpenAiCompatibleChatModelAdapter;
import cn.fj.roadagent.adapters.speech.http.PythonSpeechServiceAdapter;
import cn.fj.roadagent.adapters.transaction.SpringUnitOfWork;
import cn.fj.roadagent.adapters.traffic.mysql.InMemoryHighwayTrafficSnapshotCache;
import cn.fj.roadagent.adapters.traffic.mysql.InMemoryRoadCapacitySnapshotCache;
import cn.fj.roadagent.adapters.traffic.mysql.MysqlHighwayTrafficSnapshotSource;
import cn.fj.roadagent.adapters.traffic.mysql.MysqlRoadCapacitySnapshotSource;
import cn.fj.roadagent.adapters.traffic.mysql.MysqlTrafficContextEventRepository;
import cn.fj.roadagent.adapters.traffic.mysql.MysqlRegionalTrafficRepository;
import cn.fj.roadagent.core.traffic.OdTrafficService;
import cn.fj.roadagent.adapters.traffic.mysql.MysqlVehicleTravelPatternRepository;
import cn.fj.roadagent.application.agent.ConverseWithAgentUseCase;
import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.CityDistancePort;
import cn.fj.roadagent.application.port.ConversationMemoryPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.EmergencyWorkflowRepository;
import cn.fj.roadagent.application.port.EmergencyResponsePlanPort;
import cn.fj.roadagent.application.port.EventClassificationLogPort;
import cn.fj.roadagent.application.port.FacilityAlertPort;
import cn.fj.roadagent.application.port.ResourceAllocationPort;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotPort;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotSource;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotPort;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotSource;
import cn.fj.roadagent.application.port.RegionalTrafficDataPort;
import cn.fj.roadagent.application.port.VehicleTravelPatternPort;
import cn.fj.roadagent.application.port.SpeechCapabilityPort;
import cn.fj.roadagent.application.port.SpeechRecognitionPort;
import cn.fj.roadagent.application.port.SpeechSynthesisPort;
import cn.fj.roadagent.application.port.TrafficContextEventPort;
import cn.fj.roadagent.application.port.UnitOfWork;
import cn.fj.roadagent.core.agent.AgentRuntime;
import cn.fj.roadagent.core.agent.AgentSkill;
import cn.fj.roadagent.core.agent.IntentPlanner;
import cn.fj.roadagent.core.agent.SkillRegistry;
import cn.fj.roadagent.core.dispatch.DispatchApplicationService;
import cn.fj.roadagent.core.dispatch.EmergencyDispatchSkill;
import cn.fj.roadagent.core.dispatch.EmergencyEventClassificationService;
import cn.fj.roadagent.core.dispatch.EmergencyResourceAllocator;
import cn.fj.roadagent.core.facility.FacilityAlertService;
import cn.fj.roadagent.core.speech.SpeechApplicationService;
import cn.fj.roadagent.core.traffic.HighwayTrafficService;
import cn.fj.roadagent.core.traffic.HighwayTrafficSkill;
import cn.fj.roadagent.core.traffic.RoadCapacityService;
import cn.fj.roadagent.core.traffic.RegionalTrafficService;
import cn.fj.roadagent.core.traffic.UnifiedTrafficQueryService;
import cn.fj.roadagent.core.traffic.VehiclePatternService;
import cn.fj.roadagent.application.traffic.QueryHighwayTrafficUseCase;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
                model.isAuthEnabled(), model.getEnableThinking(), timeout
        );
    }

    @Bean
    HighwayTrafficSnapshotSource highwayTrafficSnapshotSource(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        return new MysqlHighwayTrafficSnapshotSource(
                jdbcTemplate, new TransactionTemplate(transactionManager), clock
        );
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    InMemoryHighwayTrafficSnapshotCache highwayTrafficSnapshotCache(
            HighwayTrafficSnapshotSource source,
            RoadAgentProperties properties,
            Clock clock
    ) {
        RoadAgentProperties.Traffic traffic = properties.getTraffic();
        return new InMemoryHighwayTrafficSnapshotCache(
                source,
                clock,
                Duration.ofSeconds(traffic.getSnapshotPollSeconds()),
                Duration.ofSeconds(traffic.getSnapshotStableSeconds())
        );
    }

    @Bean
    TrafficContextEventPort trafficContextEventPort(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        return new MysqlTrafficContextEventRepository(
                jdbcTemplate, new TransactionTemplate(transactionManager), objectMapper
        );
    }

    @Bean
    HighwayTrafficService highwayTrafficService(
            HighwayTrafficSnapshotPort snapshotPort,
            ChatModelPort chatModelPort,
            TrafficContextEventPort contextEventPort
    ) {
        return new HighwayTrafficService(snapshotPort, chatModelPort, contextEventPort);
    }

    @Bean
    RoadCapacitySnapshotSource roadCapacitySnapshotSource(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        return new MysqlRoadCapacitySnapshotSource(
                jdbcTemplate, new TransactionTemplate(transactionManager), clock
        );
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    InMemoryRoadCapacitySnapshotCache roadCapacitySnapshotCache(
            RoadCapacitySnapshotSource source,
            RoadAgentProperties properties,
            Clock clock
    ) {
        RoadAgentProperties.Traffic traffic = properties.getTraffic();
        return new InMemoryRoadCapacitySnapshotCache(
                source,
                clock,
                Duration.ofSeconds(traffic.getSnapshotPollSeconds()),
                Duration.ofSeconds(traffic.getSnapshotStableSeconds())
        );
    }

    @Bean
    RoadCapacityService roadCapacityService(
            RoadCapacitySnapshotPort snapshotPort,
            HighwayTrafficSnapshotPort trafficSnapshotPort,
            ChatModelPort chatModelPort
    ) {
        return new RoadCapacityService(snapshotPort, trafficSnapshotPort, chatModelPort);
    }

    @Bean
    RegionalTrafficDataPort regionalTrafficDataPort(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        return new MysqlRegionalTrafficRepository(
                jdbcTemplate, new TransactionTemplate(transactionManager), clock
        );
    }

    @Bean
    RegionalTrafficService regionalTrafficService(
            RegionalTrafficDataPort dataPort,
            ChatModelPort chatModelPort
    ) {
        return new RegionalTrafficService(dataPort, chatModelPort);
    }

    @Bean
    VehicleTravelPatternPort vehicleTravelPatternPort(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper
    ) {
        return new MysqlVehicleTravelPatternRepository(
                jdbcTemplate, new TransactionTemplate(transactionManager), objectMapper
        );
    }

    @Bean
    OdTrafficService odTrafficService(RegionalTrafficDataPort dataPort, ChatModelPort chatModelPort) {
        return new OdTrafficService(dataPort, chatModelPort);
    }

    @Bean
    VehiclePatternService vehiclePatternService(
            VehicleTravelPatternPort dataPort,
            ChatModelPort chatModelPort
    ) {
        return new VehiclePatternService(dataPort, chatModelPort);
    }

    @Bean
    QueryHighwayTrafficUseCase queryHighwayTrafficUseCase(
            HighwayTrafficService trafficService,
            RoadCapacityService capacityService,
            RegionalTrafficService regionalTrafficService,
            VehiclePatternService vehiclePatternService,
            OdTrafficService odTrafficService
    ) {
        return new UnifiedTrafficQueryService(
                trafficService, capacityService, regionalTrafficService, vehiclePatternService, odTrafficService
        );
    }

    @Bean
    HighwayTrafficSkill highwayTrafficSkill(
            HighwayTrafficService trafficService,
            RoadCapacityService capacityService,
            RegionalTrafficService regionalTrafficService,
            VehiclePatternService vehiclePatternService,
            OdTrafficService odTrafficService,
            ChatModelPort chatModelPort
    ) {
        return new HighwayTrafficSkill(
                trafficService, capacityService, regionalTrafficService, vehiclePatternService, odTrafficService, chatModelPort
        );
    }

    @Bean
    EmergencyDispatchSkill emergencyDispatchSkill() {
        return new EmergencyDispatchSkill();
    }

    @Bean
    FacilityAlertService facilityAlertService(FacilityAlertPort alertPort, Clock clock) {
        return new FacilityAlertService(alertPort, clock);
    }

    @Bean
    UnitOfWork unitOfWork(PlatformTransactionManager transactionManager) {
        return new SpringUnitOfWork(new TransactionTemplate(transactionManager));
    }

    @Bean
    EmergencyResourceAllocator emergencyResourceAllocator(CityDistancePort cityDistancePort) {
        return new EmergencyResourceAllocator(cityDistancePort);
    }

    @Bean
    DispatchApplicationService dispatchApplicationService(
            AbnormalEventPort eventPort,
            DispatchRepository repository,
            EmergencyWorkflowRepository workflowRepository,
            ChatModelPort chatModelPort,
            ResourceDataPort resourceDataPort,
            ResourceAllocationPort resourceAllocationPort,
            EmergencyResourceAllocator resourceAllocator,
            EventClassificationLogPort classificationLogPort,
            EmergencyResponsePlanPort responsePlanPort,
            UnitOfWork unitOfWork,
            RoadAgentProperties properties,
            Clock clock
    ) {
        return new DispatchApplicationService(
                eventPort,
                repository,
                workflowRepository,
                chatModelPort,
                resourceDataPort,
                resourceAllocationPort,
                resourceAllocator,
                classificationLogPort,
                responsePlanPort,
                unitOfWork,
                clock,
                Duration.ofSeconds(properties.getDispatch().getStaleGeneratingSeconds())
        );
    }

    @Bean
    EmergencyEventClassificationService emergencyEventClassificationService(
            AbnormalEventPort eventPort,
            EventClassificationLogPort classificationLogPort,
            ChatModelPort chatModelPort,
            UnitOfWork unitOfWork,
            RoadAgentProperties properties,
            Clock clock
    ) {
        return new EmergencyEventClassificationService(
                eventPort, classificationLogPort, chatModelPort, unitOfWork, clock,
                Duration.ofSeconds(properties.getDispatch().getClassificationRetrySeconds()),
                properties.getModel().getModelName()
        );
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "roadagent.dispatch", name = "classification-enabled",
            havingValue = "true", matchIfMissing = true)
    EmergencyClassificationPoller emergencyClassificationPoller(
            EmergencyEventClassificationService service,
            RoadAgentProperties properties
    ) {
        return new EmergencyClassificationPoller(
                service, Duration.ofSeconds(properties.getDispatch().getClassificationPollSeconds())
        );
    }

    @Bean
    PythonSpeechServiceAdapter pythonSpeechServiceAdapter(RoadAgentProperties properties) {
        RoadAgentProperties.Speech speech = properties.getSpeech();
        Duration connectTimeout = Duration.ofSeconds(speech.getConnectTimeoutSeconds());
        Duration requestTimeout = Duration.ofSeconds(speech.getRequestTimeoutSeconds());
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(requestTimeout);
        org.springframework.web.client.RestClient restClient =
                org.springframework.web.client.RestClient.builder()
                        .requestFactory(requestFactory)
                        .build();
        return new PythonSpeechServiceAdapter(restClient, speech.getServiceUrl());
    }

    @Bean
    SpeechApplicationService speechApplicationService(
            SpeechCapabilityPort capabilityPort,
            SpeechRecognitionPort recognitionPort,
            SpeechSynthesisPort synthesisPort,
            RoadAgentProperties properties
    ) {
        RoadAgentProperties.Speech speech = properties.getSpeech();
        return new SpeechApplicationService(
                capabilityPort,
                recognitionPort,
                synthesisPort,
                speech.isEnabled(),
                speech.getMaxRecordingSeconds(),
                speech.getMaxAudioBytes(),
                speech.getMaxTtsCharacters()
        );
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
    IntentPlanner intentPlanner(ChatModelPort chatModelPort, HighwayTrafficSnapshotPort snapshotPort) {
        return new IntentPlanner(chatModelPort, snapshotPort);
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

    private void requireSecret(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
    }
}
