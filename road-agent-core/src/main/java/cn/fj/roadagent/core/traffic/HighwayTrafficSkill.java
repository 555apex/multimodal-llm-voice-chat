package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.AgentEventSink;
import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.agent.TrafficAgentResult;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.core.agent.AgentExecutionContext;
import cn.fj.roadagent.core.agent.AgentSkill;
import cn.fj.roadagent.core.agent.AgentSkillResult;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.util.Map;

/** MySQL 国省干线查询 Agent Skill。模型成功前不发布任何回答或表格事件。 */
public final class HighwayTrafficSkill implements AgentSkill {
    private final HighwayTrafficService trafficService;
    private final RoadCapacityService capacityService;
    private final RegionalTrafficService regionalTrafficService;
    private final VehiclePatternService vehiclePatternService;
    private final OdTrafficService odTrafficService;
    private cn.fj.roadagent.application.port.ChatModelPort streamingModel;

    public HighwayTrafficSkill(HighwayTrafficService trafficService) {
        this(trafficService, null, null, null);
    }

    public HighwayTrafficSkill(
            HighwayTrafficService trafficService,
            RoadCapacityService capacityService
    ) {
        this(trafficService, capacityService, null, null);
    }

    public HighwayTrafficSkill(
            HighwayTrafficService trafficService,
            RoadCapacityService capacityService,
            RegionalTrafficService regionalTrafficService,
            VehiclePatternService vehiclePatternService
    ) {
        this(trafficService, capacityService, regionalTrafficService, vehiclePatternService, null);
    }

    public HighwayTrafficSkill(HighwayTrafficService trafficService, RoadCapacityService capacityService,
            RegionalTrafficService regionalTrafficService, VehiclePatternService vehiclePatternService,
            OdTrafficService odTrafficService) {
        this.trafficService = trafficService;
        this.capacityService = capacityService;
        this.regionalTrafficService = regionalTrafficService;
        this.vehiclePatternService = vehiclePatternService;
        this.odTrafficService = odTrafficService;
    }

    public HighwayTrafficSkill(HighwayTrafficService traffic, RoadCapacityService capacity,
            RegionalTrafficService regional, VehiclePatternService vehicle, OdTrafficService od,
            cn.fj.roadagent.application.port.ChatModelPort model) {
        this(traffic, capacity, regional, vehicle, od);
        this.streamingModel = model;
    }

    @Override
    public AgentIntent intent() {
        return AgentIntent.TRAFFIC_QUERY;
    }

    @Override
    public AgentSkillResult execute(AgentExecutionContext context, AgentEventSink sink) {
        TrafficQueryType queryType = context.decision().parsedTrafficQueryType()
                .orElseThrow(() -> new IllegalArgumentException("交通查询类型不正确"));
        boolean capacityQuery = queryType.capacityQuery();
        boolean regionalQuery = queryType.regionalTrafficQuery();
        boolean vehicleQuery = queryType.vehiclePatternQuery();
        boolean odQuery = queryType.odQuery();
        String tool = odQuery ? "query_mysql_city_destination_tendency" : capacityQuery ? "query_mysql_road_capacity"
                : regionalQuery ? "query_mysql_transport_hubs"
                : vehicleQuery ? "query_mysql_vehicle_pattern"
                : "query_mysql_highway_traffic";
        String label = odQuery ? "正在分析城市目的地联系倾向" : capacityQuery ? "正在读取国省干线通行能力数据"
                : regionalQuery ? "正在统计跨区域交通联系"
                : vehicleQuery ? "正在分析最新车型出行特征"
                : "正在读取国省干线交通数据";
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "TOOL_CALLING",
                "label", label
        )));
        sink.emit(new AgentEvent("tool.started", Map.of("tool", tool)));

        HighwayTrafficQuery query = new HighwayTrafficQuery(
                queryType,
                context.decision().originCity(),
                context.decision().destinationCity(),
                context.decision().routeCode(),
                effectiveRouteName(context),
                context.decision().selectedCities(),
                effectiveAnalysisCity(context),
                context.command().traceId(),
                Boolean.TRUE.equals(context.decision().includeTrend()),
                trendOnly(context.command().message())
        );
        if (streamingModel != null && !query.includeTrend() && !query.trendOnly()) return live(query, context, sink);
        HighwayTrafficResult result = odQuery ? odTrafficService.query(query) : capacityQuery ? requireCapacityService().query(query)
                : regionalQuery ? requireRegionalService().query(query)
                : vehicleQuery ? requireVehicleService().query(query)
                : trafficService.query(query);

        // 到这里说明数据库查询和模型结构化总结均已完成，可一次性对外发布。
        sink.emit(new AgentEvent("tool.completed", Map.of(
                "tool", tool,
                "source", "MYSQL",
                "routeCount", result.routeSummaries().size(),
                "segmentCount", result.segments().size(),
                "capacityRowCount", result.capacityRows().size(),
                "odDestinationRowCount", result.odDestinationRows().size(),
                "odMatrixRowCount", result.odMatrixRows().size(),
                "vehicleRowCount", result.vehicleStructureRows().size()
        )));
        sink.emit(new AgentEvent("stage.changed", Map.of(
                "stage", "ANSWERING", "label", "正在发布交通研判结果"
        )));
        String publishedSummary = presentationOnly(context.command().message())
                ? "已按要求仅展示对应结果。" : result.summary();
        sink.emit(new AgentEvent("answer.delta", Map.of("content", publishedSummary)));
        if (!query.trendOnly()) {
            sink.emit(new AgentEvent("result.traffic", TrafficAgentResult.from(result)));
        }
        return new AgentSkillResult(publishedSummary, publishedSummary);
    }

    private AgentSkillResult live(HighwayTrafficQuery query, AgentExecutionContext context, AgentEventSink sink) {
        Object facts;
        HighwayTrafficResult result;
        String trace = context.command().traceId();
        if (query.queryType().odQuery()) {
            var f = odTrafficService.collectFacts(query); facts=odTrafficService.serialize(f);
            result=HighwayTrafficResult.fromOdFacts(f, odTrafficService.deterministicSummary(f), trace);
        } else if (query.queryType().capacityQuery()) {
            var f = requireCapacityService().collectFacts(query); facts=capacityService.serializeFacts(f);
            result=HighwayTrafficResult.fromCapacityFacts(f, capacityService.deterministicSummary(f), trace);
        } else if (query.queryType().regionalTrafficQuery()) {
            var f = requireRegionalService().collectFacts(query); facts=regionalTrafficService.serializeFacts(f);
            result=HighwayTrafficResult.fromRegionalFacts(f, regionalTrafficService.deterministicSummary(f), trace);
        } else if (query.queryType().vehiclePatternQuery()) {
            var f = requireVehicleService().collectFacts(query); facts=vehiclePatternService.serializeFacts(f);
            result=HighwayTrafficResult.fromVehicleFacts(f, vehiclePatternService.deterministicSummary(f), trace);
        } else {
            var f = trafficService.collectFacts(query); facts=trafficService.serializeFacts(f);
            String summary=f.title()+"：已查询到"+f.routeSummaries().size()+"条路线概况、"+f.segments().size()+"条路段明细。具体状态与采集时间见下方数据。";
            summary += trafficService.verifiedCause(query, f);
            result=HighwayTrafficResult.fromFacts(f, summary, trace);
        }
        sink.emit(new AgentEvent("tool.completed", Map.of("source", "MYSQL")));
        return TrafficLiveResponder.respond(result, facts, streamingModel, sink,
                query.includeTrend(), query.trendOnly(), presentationOnly(context.command().message()));
    }

    private RoadCapacityService requireCapacityService() {
        if (capacityService == null) {
            throw new IllegalStateException("道路通行能力查询服务尚未配置");
        }
        return capacityService;
    }

    private RegionalTrafficService requireRegionalService() {
        if (regionalTrafficService == null) {
            throw new IllegalStateException("跨区域交通联系查询服务尚未配置");
        }
        return regionalTrafficService;
    }

    private VehiclePatternService requireVehicleService() {
        if (vehiclePatternService == null) {
            throw new IllegalStateException("车型出行特征查询服务尚未配置");
        }
        return vehiclePatternService;
    }

    private String effectiveRouteName(AgentExecutionContext context) {
        if (context.decision().routeName() != null && !context.decision().routeName().isBlank()) {
            return context.decision().routeName();
        }
        // 兼容模型偶尔仍将路线名放在旧roadName字段。
        return context.decision().roadName();
    }

    private String effectiveAnalysisCity(AgentExecutionContext context) {
        if (context.decision().analysisCity() != null && !context.decision().analysisCity().isBlank()) {
            return context.decision().analysisCity();
        }
        return context.decision().city();
    }

    private boolean presentationOnly(String message) {
        if (message == null) return false;
        String normalized = message.replaceAll("\\s+", "");
        return normalized.contains("只展示") || normalized.contains("只保留")
                || normalized.contains("只看") || normalized.contains("不需要其他");
    }

    private boolean trendOnly(String message) {
        if (message == null) return false;
        String normalized = message.replaceAll("\\s+", "");
        return normalized.contains("只给出定性趋势") || normalized.contains("只看趋势")
                || normalized.contains("只要趋势");
    }
}
