package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.RegionalTrafficDataPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.RegionalChannelResultItem;
import cn.fj.roadagent.application.traffic.RegionalPairResultItem;
import cn.fj.roadagent.application.traffic.RegionalTrafficFacts;
import cn.fj.roadagent.application.traffic.RegionalTrafficSummaryResponse;
import cn.fj.roadagent.application.traffic.SelectedRegionResultItem;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.RegionalConnectionHub;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 需求1-5：以跨市路线为骨架，在城市对和重要通道两个层级分析区域联系。 */
public final class RegionalTrafficService {
    private static final int PAIR_LIMIT = 5;
    private static final int CHANNEL_LIMIT = 10;

    private static final Comparator<RegionalPairResultItem> PAIR_ORDER =
            Comparator.comparingLong(RegionalPairResultItem::weeklyTotalFlow).reversed()
                    .thenComparing(RegionalPairResultItem::dailyAverageFlow, Comparator.reverseOrder())
                    .thenComparing(RegionalPairResultItem::cityARegionCode)
                    .thenComparing(RegionalPairResultItem::cityBRegionCode);
    private static final Comparator<RegionalChannelResultItem> CHANNEL_ORDER =
            Comparator.comparingLong(RegionalChannelResultItem::weeklyTotalFlow).reversed()
                    .thenComparing(RegionalChannelResultItem::dailyAverageFlow, Comparator.reverseOrder())
                    .thenComparing(RegionalChannelResultItem::routeCode);

    private final RegionalTrafficDataPort dataPort;
    private final ChatModelPort chatModelPort;

    public RegionalTrafficService(RegionalTrafficDataPort dataPort, ChatModelPort chatModelPort) {
        this.dataPort = dataPort;
        this.chatModelPort = chatModelPort;
    }

    public RegionalTrafficFacts collectFacts(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null || !query.queryType().regionalTrafficQuery()) {
            throw new BusinessRuleException("REGIONAL_TRAFFIC_QUERY_TYPE_REQUIRED", "请说明区域交通联系查询类型");
        }
        List<FujianCity> selected = resolveCities(query);
        var snapshot = dataPort.load();
        Set<String> selectedCodes = selected.stream().map(FujianCity::adcode).collect(Collectors.toSet());
        List<RegionalConnectionHub> scope = snapshot.hubs().stream()
                .filter(hub -> selectedCodes.isEmpty()
                        || selectedCodes.contains(hub.cityARegionCode()) && selectedCodes.contains(hub.cityBRegionCode()))
                .toList();
        if (scope.isEmpty()) {
            throw new BusinessRuleException("REGIONAL_CONNECTION_NOT_FOUND", "所选城市之间没有可用的跨市路线卡口数据");
        }

        List<FujianCity> effectiveCities = selected.isEmpty() ? Arrays.asList(FujianCity.values()) : selected;
        List<SelectedRegionResultItem> regions = effectiveCities.stream()
                .map(city -> new SelectedRegionResultItem(city.adcode(), city.displayName() + "市")).toList();
        List<RegionalPairResultItem> allPairs = aggregatePairs(scope);
        List<RegionalChannelResultItem> allChannels = aggregateChannels(scope);
        List<String> warnings = new ArrayList<>();
        Set<String> connected = scope.stream().flatMap(hub -> java.util.stream.Stream.of(
                hub.cityARegionCode(), hub.cityBRegionCode())).collect(Collectors.toSet());
        effectiveCities.stream().filter(city -> !connected.contains(city.adcode()))
                .forEach(city -> warnings.add(city.displayName() + "市在当前所选范围内没有可用跨市路线卡口数据。"));

        return new RegionalTrafficFacts(query.queryType(), title(query.queryType(), selected), regions,
                includesPairs(query.queryType()) ? allPairs.stream().limit(PAIR_LIMIT).toList() : List.of(),
                includesChannels(query.queryType()) ? allChannels.stream().limit(CHANNEL_LIMIT).toList() : List.of(),
                allPairs.size(), allChannels.size(), snapshot.acquiredAt(), warnings);
    }

    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        RegionalTrafficFacts facts = collectFacts(query);
        String summary;
        try {
            RegionalTrafficSummaryResponse response = chatModelPort.generateStructuredStrict(
                    summaryRequest(facts), RegionalTrafficSummaryResponse.class);
            summary = safeSummary(facts, response.summary());
            ModelFactNumberValidator.validate(summary, serializeFacts(facts));
        } catch (RuntimeException exception) {
            summary = deterministicSummary(facts);
        }
        String traceId = query.traceId() == null || query.traceId().isBlank()
                ? UUID.randomUUID().toString() : query.traceId();
        return HighwayTrafficResult.fromRegionalFacts(facts, summary, traceId);
    }

    private List<FujianCity> resolveCities(HighwayTrafficQuery query) {
        List<String> inputs = new ArrayList<>(query.selectedCities());
        if (inputs.isEmpty()) {
            if (query.originCity() != null && !query.originCity().isBlank()) inputs.add(query.originCity());
            if (query.destinationCity() != null && !query.destinationCity().isBlank()) inputs.add(query.destinationCity());
        }
        LinkedHashSet<FujianCity> cities = new LinkedHashSet<>();
        for (String input : inputs) cities.add(FujianCity.fromName(input).orElseThrow(() ->
                new BusinessRuleException("REGIONAL_TRAFFIC_CITY_INVALID", "请选择有效的福建地级市")));
        if (!cities.isEmpty() && cities.size() < 3) {
            throw new BusinessRuleException("REGIONAL_TRAFFIC_CITY_COUNT_REQUIRED", "区域交通联系分析至少需要三个城市，请再补充一个或多个福建地级市");
        }
        if (cities.size() > 9) throw new BusinessRuleException("REGIONAL_TRAFFIC_CITY_LIMIT", "一次最多选择九个城市进行区域交通分析");
        return List.copyOf(cities);
    }

    private List<RegionalPairResultItem> aggregatePairs(List<RegionalConnectionHub> scope) {
        return scope.stream().collect(Collectors.groupingBy(this::pairKey, LinkedHashMap::new, Collectors.toList()))
                .values().stream().map(hubs -> {
                    RegionalConnectionHub first = hubs.get(0);
                    return new RegionalPairResultItem(first.cityARegionCode(), first.cityAName(), first.cityBRegionCode(),
                            first.cityBName(), Math.toIntExact(hubs.stream().map(RegionalConnectionHub::routeCode).distinct().count()),
                            hubs.size(), sumWeekly(hubs), sumDaily(hubs), averageSpeed(hubs));
                }).sorted(PAIR_ORDER).toList();
    }

    private List<RegionalChannelResultItem> aggregateChannels(List<RegionalConnectionHub> scope) {
        return scope.stream().collect(Collectors.groupingBy(hub -> pairKey(hub) + "|" + hub.routeCode(),
                        LinkedHashMap::new, Collectors.toList())).values().stream().map(hubs -> {
                    RegionalConnectionHub first = hubs.get(0);
                    return new RegionalChannelResultItem(first.cityARegionCode(), first.cityAName(), first.cityBRegionCode(),
                            first.cityBName(), first.routeCode(), first.routeName(), hubs.size(), sumWeekly(hubs),
                            sumDaily(hubs), averageSpeed(hubs));
                }).sorted(CHANNEL_ORDER).toList();
    }

    private long sumWeekly(List<RegionalConnectionHub> hubs) {
        long sum = 0;
        for (RegionalConnectionHub hub : hubs) sum = Math.addExact(sum, hub.weeklyTotalFlow());
        return sum;
    }
    private long sumDaily(List<RegionalConnectionHub> hubs) {
        long sum = 0;
        for (RegionalConnectionHub hub : hubs) sum = Math.addExact(sum, hub.dailyAverageFlow());
        return sum;
    }
    private double averageSpeed(List<RegionalConnectionHub> hubs) {
        return hubs.stream().mapToDouble(RegionalConnectionHub::averageSpeedKmh).average().orElse(0d);
    }
    private String pairKey(RegionalConnectionHub hub) { return hub.cityARegionCode() + "|" + hub.cityBRegionCode(); }

    private ModelRequest summaryRequest(RegionalTrafficFacts facts) {
        String prompt = """
                你是福建省普通国省干线区域交通联系分析助手，只能依据所给结构化事实回答。
                输出严格JSON，且只能包含summary字段。summary写4至6句、120至700字的连贯中文，先概括所选城市范围内跨市联系结构，再判断哪些城市之间交通压力较大、联系较紧密，并指出7日总流量较高的重要跨市路线，最后给出监测建议。
                摘要只分析城市对和路线，不得提及关键卡口、卡口编号或卡口排名。
                这里的“城市对”是无方向的路线起终点组合；不得写成从某市驶往某市，不得声称是真实OD、净流入、净流出、车辆来源或途经城市。
                排名以卡口temp_2的7日总流量为主，日均流量为辅助。不得重算、修改或补充数值，不推测事故、天气、施工等原因，不讨论系统缺点，不使用Markdown。
                """.strip();
        return new ModelRequest(prompt, serializeFacts(facts), List.of(), 0.1);
    }

    private String serializeFacts(RegionalTrafficFacts facts) {
        StringBuilder out = new StringBuilder("queryType=").append(facts.queryType()).append('\n')
                .append("title=").append(facts.title()).append('\n')
                .append("dataTimeAsiaShanghai=").append(TrafficTimeFormatter.asiaShanghai(facts.acquiredAt())).append('\n')
                .append("selectedRegions=");
        facts.selectedRegions().forEach(r -> out.append(r.regionName()).append('；'));
        out.append("\ntotalPairs=").append(facts.totalPairCount()).append(";totalChannels=")
                .append(facts.totalChannelCount()).append('\n');
        out.append("pairRows:\n");
        facts.pairRows().forEach(r -> out.append("- ").append(r.cityAName()).append('—').append(r.cityBName())
                .append("|路线=").append(r.routeCount())
                .append("|7日总流量=").append(r.weeklyTotalFlow()).append("|日均=").append(r.dailyAverageFlow())
                .append("|均速=").append(format(r.averageSpeedKmh())).append('\n'));
        out.append("channelRows:\n");
        facts.channelRows().forEach(r -> out.append("- ").append(r.cityAName()).append('—').append(r.cityBName())
                .append('|').append(r.routeCode()).append('|').append(r.routeName())
                .append("|7日总流量=").append(r.weeklyTotalFlow()).append("|日均=").append(r.dailyAverageFlow())
                .append("|均速=").append(format(r.averageSpeedKmh())).append('\n'));
        return out.toString();
    }

    private String safeSummary(RegionalTrafficFacts facts, String summary) {
        if (summary == null || summary.isBlank() || hasUnsupportedClaim(summary)) return deterministicSummary(facts);
        return summary;
    }

    private boolean hasUnsupportedClaim(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("真实od") || text.contains("净流入") || text.contains("净流出")
                || text.contains("驶往") || text.contains("车辆来源") || text.contains("途经")
                || text.contains("卡口");
    }

    private String deterministicSummary(RegionalTrafficFacts facts) {
        String scope = facts.selectedRegions().stream().map(SelectedRegionResultItem::regionName).collect(Collectors.joining("、"));
        List<String> sentences = new ArrayList<>();
        sentences.add("本次对" + scope + "范围内的普通国省干线跨市联系进行无方向统计，共识别"
                + facts.totalPairCount() + "个城市对和" + facts.totalChannelCount() + "条跨市路线。");
        if (!facts.pairRows().isEmpty()) {
            var top = facts.pairRows().get(0);
            sentences.add(top.cityAName() + "与" + top.cityBName() + "的7日汇总流量在当前城市对中较高，共涉及"
                    + top.routeCount() + "条路线，区域流量联系相对紧密。");
        }
        if (!facts.channelRows().isEmpty()) {
            var top = facts.channelRows().get(0);
            sentences.add("重要通道中，" + top.routeCode() + " " + top.routeName() + "承担的7日汇总流量较高，可作为跨市运行监测重点。");
        }
        sentences.add("建议结合城市对和路线两个层级持续关注高流量通道，并根据后续批次变化及时调整监测重点。");
        return String.join("", sentences);
    }

    private boolean includesPairs(TrafficQueryType type) { return type == TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW || type == TrafficQueryType.REGIONAL_PAIR_PRESSURE; }
    private boolean includesChannels(TrafficQueryType type) { return type == TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW || type == TrafficQueryType.REGIONAL_KEY_CHANNELS; }

    private String title(TrafficQueryType type, List<FujianCity> cities) {
        String scope = cities.isEmpty() ? "福建省九市" : cities.stream().map(c -> c.displayName() + "市").collect(Collectors.joining("、"));
        return switch (type) {
            case REGIONAL_PAIR_PRESSURE -> scope + "城市对交通联系压力";
            case REGIONAL_KEY_CHANNELS -> scope + "重要跨市通道";
            default -> scope + "跨区域交通联系综合分析";
        };
    }
    private String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
