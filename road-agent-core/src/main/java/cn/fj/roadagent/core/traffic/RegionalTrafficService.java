package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.RegionalTrafficDataPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.RegionInsight;
import cn.fj.roadagent.application.traffic.RegionPressureResultItem;
import cn.fj.roadagent.application.traffic.RegionalTrafficFacts;
import cn.fj.roadagent.application.traffic.RegionalTrafficSummaryResponse;
import cn.fj.roadagent.application.traffic.RoutePressureResultItem;
import cn.fj.roadagent.application.traffic.SelectedRegionResultItem;
import cn.fj.roadagent.application.traffic.TransportHubResultItem;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.domain.traffic.TransportHub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 需求1-5：在全省或所选城市卡口范围内确定性统计交通压力。 */
public final class RegionalTrafficService {
    private static final int HUB_LIMIT = 20;
    private static final int REGION_LIMIT = 5;
    private static final int ROUTE_LIMIT = 10;
    private static final long ACTIVE_FLOW_THRESHOLD = 100L;

    private static final Comparator<TransportHub> HUB_ORDER =
            Comparator.comparingLong(TransportHub::dailyAverageFlow).reversed()
                    .thenComparing(TransportHub::checkpointNo);
    private static final Comparator<RegionPressureResultItem> REGION_ORDER =
            Comparator.comparingLong(RegionPressureResultItem::totalDailyFlow).reversed()
                    .thenComparing(RegionPressureResultItem::activeHubCount, Comparator.reverseOrder())
                    .thenComparing(RegionPressureResultItem::regionCode);
    private static final Comparator<RoutePressureResultItem> ROUTE_ORDER =
            Comparator.comparingLong(RoutePressureResultItem::totalDailyFlow).reversed()
                    .thenComparing(RoutePressureResultItem::checkpointCount, Comparator.reverseOrder())
                    .thenComparing(RoutePressureResultItem::routeCode);

    private final RegionalTrafficDataPort dataPort;
    private final ChatModelPort chatModelPort;

    public RegionalTrafficService(RegionalTrafficDataPort dataPort, ChatModelPort chatModelPort) {
        this.dataPort = dataPort;
        this.chatModelPort = chatModelPort;
    }

    public RegionalTrafficFacts collectFacts(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null || !query.queryType().regionalTrafficQuery()) {
            throw new BusinessRuleException("REGIONAL_TRAFFIC_QUERY_TYPE_REQUIRED", "请说明区域交通压力查询类型");
        }
        List<FujianCity> selectedCities = resolveCities(query);
        RegionalTrafficSnapshot snapshot = dataPort.load();
        Set<String> selectedCodes = selectedCities.stream().map(FujianCity::adcode).collect(Collectors.toSet());
        List<TransportHub> scope = snapshot.hubs().stream()
                .filter(hub -> selectedCodes.isEmpty() || selectedCodes.contains(hub.regionCode()))
                .toList();
        if (scope.isEmpty()) {
            throw new BusinessRuleException("REGIONAL_TRAFFIC_NOT_FOUND", "所选城市范围内没有可用卡口数据");
        }

        List<SelectedRegionResultItem> regions = scope.stream()
                .collect(Collectors.toMap(
                        TransportHub::regionCode,
                        hub -> new SelectedRegionResultItem(hub.regionCode(), hub.regionName()),
                        (left, right) -> left,
                        LinkedHashMap::new
                )).values().stream()
                .sorted(Comparator.comparing(SelectedRegionResultItem::regionCode))
                .toList();

        List<TransportHubResultItem> hubRows = includesHubs(query.queryType())
                ? scope.stream().sorted(HUB_ORDER).limit(HUB_LIMIT).map(this::hubItem).toList()
                : List.of();
        List<RegionPressureResultItem> allRegions = aggregateRegions(scope);
        List<RegionPressureResultItem> regionRows = includesRegions(query.queryType())
                ? allRegions.stream().limit(REGION_LIMIT).toList() : List.of();
        List<RoutePressureResultItem> allRoutes = aggregateRoutes(scope);
        List<RoutePressureResultItem> routeRows = includesRoutes(query.queryType())
                ? allRoutes.stream().limit(ROUTE_LIMIT).toList() : List.of();

        return new RegionalTrafficFacts(
                query.queryType(), title(query.queryType(), selectedCities), regions,
                hubRows, regionRows, routeRows, scope.size(), allRegions.size(), allRoutes.size(),
                snapshot.acquiredAt()
        );
    }

    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        RegionalTrafficFacts facts = collectFacts(query);
        RegionalTrafficSummaryResponse response = chatModelPort.generateStructuredStrict(
                summaryRequest(facts), RegionalTrafficSummaryResponse.class
        );
        List<RegionPressureResultItem> interpreted = applySafeInsights(facts, response);
        String summary = factSafeSummary(facts, response.summary());
        String allModelText = summary + "\n" + interpreted.stream()
                .map(RegionPressureResultItem::interpretation).collect(Collectors.joining("\n"));
        try {
            ModelFactNumberValidator.validate(allModelText, serializeFacts(facts));
        } catch (IllegalArgumentException ignored) {
            summary = deterministicSummary(facts);
            interpreted = defaultInsights(facts);
        }
        String traceId = query.traceId() == null || query.traceId().isBlank()
                ? UUID.randomUUID().toString() : query.traceId();
        return HighwayTrafficResult.fromRegionalFacts(facts, summary, interpreted, traceId);
    }

    private List<FujianCity> resolveCities(HighwayTrafficQuery query) {
        List<String> inputs = new ArrayList<>(query.selectedCities());
        if (inputs.isEmpty()) {
            if (query.originCity() != null && !query.originCity().isBlank()) inputs.add(query.originCity());
            if (query.destinationCity() != null && !query.destinationCity().isBlank()) inputs.add(query.destinationCity());
        }
        LinkedHashSet<FujianCity> cities = new LinkedHashSet<>();
        for (String input : inputs) {
            FujianCity city = FujianCity.fromName(input).orElseThrow(() ->
                    new BusinessRuleException("REGIONAL_TRAFFIC_CITY_INVALID", "请选择有效的福建地级市"));
            cities.add(city);
        }
        if (cities.size() > 2) {
            throw new BusinessRuleException("REGIONAL_TRAFFIC_CITY_LIMIT", "一次最多选择两个城市进行区域交通分析");
        }
        return List.copyOf(cities);
    }

    private List<RegionPressureResultItem> aggregateRegions(List<TransportHub> scope) {
        int denominator = scope.size();
        return scope.stream().collect(Collectors.groupingBy(
                        TransportHub::regionCode, LinkedHashMap::new, Collectors.toList()
                )).entrySet().stream()
                .map(entry -> {
                    List<TransportHub> hubs = entry.getValue();
                    int active = Math.toIntExact(hubs.stream()
                            .filter(hub -> hub.dailyAverageFlow() > ACTIVE_FLOW_THRESHOLD).count());
                    long flow = hubs.stream().mapToLong(TransportHub::dailyAverageFlow).sum();
                    return new RegionPressureResultItem(
                            entry.getKey(), hubs.get(0).regionName(), active, flow,
                            denominator == 0 ? 0d : (double) active / denominator, ""
                    );
                })
                .sorted(REGION_ORDER)
                .toList();
    }

    private List<RoutePressureResultItem> aggregateRoutes(List<TransportHub> scope) {
        return scope.stream().collect(Collectors.groupingBy(
                        TransportHub::routeCode, LinkedHashMap::new, Collectors.toList()
                )).entrySet().stream()
                .map(entry -> {
                    List<TransportHub> hubs = entry.getValue();
                    return new RoutePressureResultItem(
                            entry.getKey(), hubs.get(0).routeName(),
                            hubs.stream().mapToLong(TransportHub::dailyAverageFlow).sum(),
                            hubs.size(), hubs.stream().mapToDouble(TransportHub::averageSpeedKmh).average().orElse(0d)
                    );
                })
                .sorted(ROUTE_ORDER)
                .toList();
    }

    private TransportHubResultItem hubItem(TransportHub hub) {
        return new TransportHubResultItem(
                hub.checkpointNo(), hub.routeCode(), hub.routeName(),
                hub.averageSpeedKmh(), hub.dailyAverageFlow()
        );
    }

    private ModelRequest summaryRequest(RegionalTrafficFacts facts) {
        String prompt = """
                你是福建省区域公路交通压力分析助手。只能依据用户消息中的结构化卡口、城市和路线统计事实回答。
                必须输出严格JSON对象，且只能包含summary和regionInsights两个字段。
                summary必须是3至5句、80至600字的连贯中文：先概括当前查询范围，再指出日均流量较高的卡口、城市或路线，最后给出简洁监测建议；只讨论实际提供的表格类别。
                regionInsights必须是数组，对regionRows中的每个regionCode恰好返回一项，字段只能为regionCode和interpretation；没有regionRows时返回空数组。
                interpretation写一条10至60字中文，只根据该城市活跃卡口数、日总流量和枢纽占比评价其当前交通压力，不新增数值。
                两城市查询代表两市卡口数据的合并统计，只能表述为“两市范围内交通压力分布”；不得声称存在城市间OD流量、交通流向、车辆来源、共同路线或从某市驶往另一市的车辆数量。
                不得重新计算、修改或补充数值，不推测事故、施工、天气等原因，不讨论数据限制和系统实现，不使用Markdown。
                """.strip();
        return new ModelRequest(prompt, serializeFacts(facts), List.of(), 0.1);
    }

    private String serializeFacts(RegionalTrafficFacts facts) {
        StringBuilder out = new StringBuilder();
        out.append("queryType=").append(facts.queryType()).append('\n');
        out.append("title=").append(facts.title()).append('\n');
        out.append("acquiredAt=").append(facts.acquiredAt()).append('\n');
        out.append("scopeHubCount=").append(facts.totalHubCount()).append('\n');
        out.append("rankingLimits=卡口20；城市5；路线10；最多选择城市2\n");
        out.append("selectedRegions=");
        facts.selectedRegions().forEach(region -> out.append(region.regionCode()).append('/')
                .append(region.regionName()).append('；'));
        out.append("\nhubRows:\n");
        facts.hubRows().forEach(row -> out.append("- ").append(row.checkpointNo()).append('|')
                .append(row.routeCode()).append('|').append(row.routeName())
                .append("|均速=").append(format(row.averageSpeedKmh()))
                .append("|日均流量=").append(row.dailyAverageFlow()).append('\n'));
        out.append("regionRows:\n");
        facts.regionRows().forEach(row -> out.append("- ").append(row.regionCode()).append('|')
                .append(row.regionName()).append("|活跃卡口=").append(row.activeHubCount())
                .append("|日总流量=").append(row.totalDailyFlow())
                .append("|枢纽占比=").append(format(row.hubShareRatio() * 100)).append("%\n"));
        out.append("routeRows:\n");
        facts.routeRows().forEach(row -> out.append("- ").append(row.routeCode()).append('|')
                .append(row.routeName()).append("|日总流量=").append(row.totalDailyFlow())
                .append("|卡口数=").append(row.checkpointCount())
                .append("|均速=").append(format(row.averageSpeedKmh())).append('\n'));
        return out.toString();
    }

    private List<RegionPressureResultItem> applySafeInsights(
            RegionalTrafficFacts facts,
            RegionalTrafficSummaryResponse response
    ) {
        Map<String, RegionInsight> insights = new LinkedHashMap<>();
        response.regionInsights().forEach(insight -> {
            if (insight != null && !insight.regionCode().isBlank()) {
                insights.putIfAbsent(insight.regionCode(), insight);
            }
        });
        return facts.regionRows().stream().map(row -> {
            RegionInsight insight = insights.get(row.regionCode());
            String text = insight == null ? "" : insight.interpretation();
            if (text.length() < 10 || text.length() > 60 || !containsChinese(text)
                    || hasUnsupportedOdClaim(text)) {
                text = defaultInterpretation(row);
            }
            return row.withInterpretation(text);
        }).toList();
    }

    private String factSafeSummary(RegionalTrafficFacts facts, String summary) {
        if (hasUnsupportedOdClaim(summary)) {
            return deterministicSummary(facts);
        }
        return summary;
    }

    private boolean hasUnsupportedOdClaim(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        return upper.contains("OD流量") || text.contains("流向") || text.contains("驶往")
                || text.contains("车辆来源") || text.contains("共同路线");
    }

    private List<RegionPressureResultItem> defaultInsights(RegionalTrafficFacts facts) {
        return facts.regionRows().stream()
                .map(row -> row.withInterpretation(defaultInterpretation(row)))
                .toList();
    }

    private String defaultInterpretation(RegionPressureResultItem row) {
        if (row.activeHubCount() == 0) {
            return row.regionName() + "当前卡口整体流量压力相对平稳，可保持常态监测";
        }
        return row.regionName() + "当前活跃卡口较为集中，建议持续关注重点卡口运行状态";
    }

    private String deterministicSummary(RegionalTrafficFacts facts) {
        String scope = facts.selectedRegions().isEmpty() ? "福建省" : facts.selectedRegions().stream()
                .map(SelectedRegionResultItem::regionName).collect(Collectors.joining("、"));
        String first = "已完成" + scope + "范围内卡口、城市和路线交通压力统计，排名结果均按当前卡口流量汇总形成。";
        String second;
        if (!facts.hubRows().isEmpty()) {
            TransportHubResultItem top = facts.hubRows().get(0);
            second = "当前高流量卡口以" + top.checkpointNo() + "为代表，可优先关注其所在路线的运行状态。";
        } else if (!facts.regionRows().isEmpty()) {
            second = facts.regionRows().get(0).regionName() + "在当前查询范围内的交通压力排名靠前，应加强重点卡口监测。";
        } else if (!facts.routeRows().isEmpty()) {
            second = facts.routeRows().get(0).routeCode() + " " + facts.routeRows().get(0).routeName()
                    + "在当前路线压力排名中靠前，建议持续跟踪沿线卡口运行情况。";
        } else {
            second = "当前查询范围内未形成需要重点列出的排名对象，可保持常态监测。";
        }
        return first + second + "详细卡口、城市或路线排名请结合下方对应表格查看。";
    }

    private boolean containsChinese(String value) {
        return value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code)
                == Character.UnicodeScript.HAN);
    }

    private boolean includesHubs(TrafficQueryType type) {
        return type == TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW || type == TrafficQueryType.CHECKPOINT_PRESSURE;
    }

    private boolean includesRegions(TrafficQueryType type) {
        return type == TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW || type == TrafficQueryType.CITY_PRESSURE;
    }

    private boolean includesRoutes(TrafficQueryType type) {
        return type == TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW || type == TrafficQueryType.ROUTE_PRESSURE;
    }

    private String title(TrafficQueryType type, List<FujianCity> cities) {
        String scope = cities.isEmpty() ? "福建省" : cities.stream()
                .map(city -> city.displayName() + "市").collect(Collectors.joining("、"));
        return switch (type) {
            case CHECKPOINT_PRESSURE -> scope + "高流量卡口枢纽";
            case CITY_PRESSURE -> scope + "城市交通压力";
            case ROUTE_PRESSURE -> scope + "重点路线交通压力";
            default -> scope + "区域交通压力综合分析";
        };
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
