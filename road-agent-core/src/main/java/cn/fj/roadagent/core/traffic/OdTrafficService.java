package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.OdTrafficDataPort;
import cn.fj.roadagent.application.traffic.*;
import cn.fj.roadagent.domain.traffic.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/** 需求1-7：城市并集内的七日卡口统计，不计算真实城市对OD或方向。 */
public final class OdTrafficService {
    private final OdTrafficDataPort dataPort;
    private final ChatModelPort model;

    public OdTrafficService(OdTrafficDataPort dataPort, ChatModelPort model) {
        this.dataPort = dataPort;
        this.model = model;
    }

    public OdTrafficFacts collectFacts(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null || !query.queryType().odQuery()) {
            throw new BusinessRuleException("OD_QUERY_TYPE_REQUIRED", "请选择城市OD统计类型");
        }
        List<String> inputs = new ArrayList<>(query.selectedCities());
        if (inputs.isEmpty()) {
            if (query.originCity() != null && !query.originCity().isBlank()) inputs.add(query.originCity());
            if (query.destinationCity() != null && !query.destinationCity().isBlank()) inputs.add(query.destinationCity());
        }
        List<FujianCity> cities = inputs.isEmpty() ? Arrays.asList(FujianCity.values()) : inputs.stream()
                .map(name -> FujianCity.fromName(name).orElseThrow(() ->
                        new BusinessRuleException("OD_CITY_INVALID", "请选择福建九市范围内的城市")))
                .distinct().toList();
        var selected = cities.stream().map(c -> new SelectedRegionResultItem(c.adcode(), c.displayName() + "市")).toList();
        var codes = cities.stream().map(FujianCity::adcode).toList();
        boolean vehicles = query.queryType() != TrafficQueryType.OD_CITY_FLOW;
        var snapshot = dataPort.load(codes, vehicles);
        var hubs = snapshot.hubs().stream().filter(h -> codes.contains(h.regionCode()))
                .filter(h -> query.routeCode() == null || query.routeCode().isBlank()
                        || query.routeCode().trim().equalsIgnoreCase(h.routeCode()))
                .toList();
        if (hubs.isEmpty()) throw new BusinessRuleException("OD_ANALYSIS_NOT_FOUND", "所选范围暂无可用卡口统计数据");
        Set<String> available = hubs.stream().map(OdTransportHub::regionCode).collect(Collectors.toSet());
        var missing = selected.stream().filter(c -> !available.contains(c.regionCode())).toList();
        List<String> warnings = new ArrayList<>(snapshot.warnings());
        if (!missing.isEmpty()) warnings.add("所选范围暂无卡口数据的城市：" + missing.stream()
                .map(SelectedRegionResultItem::regionName).collect(Collectors.joining("、")));
        try {
            List<OdCityFlowResultItem> cityRows = query.queryType() == TrafficQueryType.OD_KEY_CHANNELS ? List.of()
                    : hubs.stream().collect(Collectors.groupingBy(OdTransportHub::regionCode)).entrySet().stream()
                    .map(e -> {
                        var rows = e.getValue();
                        double speed = rows.stream().map(h -> BigDecimal.valueOf(h.averageSpeedKmh()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP).doubleValue();
                        return new OdCityFlowResultItem(e.getKey(), rows.get(0).regionName(), rows.size(),
                                sum(rows, OdTransportHub::weeklyTotalFlow), sum(rows, OdTransportHub::dailyAverageFlow), speed);
                    }).sorted(Comparator.comparingLong(OdCityFlowResultItem::weeklyTotalFlow).reversed()
                            .thenComparing(OdCityFlowResultItem::regionCode)).toList();
            List<OdChannelResultItem> channels = !vehicles ? List.of()
                    : hubs.stream().collect(Collectors.groupingBy(OdTransportHub::routeCode)).entrySet().stream()
                    .map(e -> {
                        var rows = e.getValue();
                        String name = rows.stream().map(OdTransportHub::routeName)
                                .filter(n -> n != null && !n.isBlank() && !n.equals("未提供")).findFirst().orElse("未提供");
                        return new OdChannelResultItem(e.getKey(), name,
                                sum(rows, OdTransportHub::weeklyTotalFlow), sum(rows, h -> h.carWeeklyFlow()),
                                sum(rows, h -> h.busWeeklyFlow()), sum(rows, h -> h.truckWeeklyFlow()));
                    }).sorted(Comparator.comparingLong(OdChannelResultItem::weeklyTotalFlow).reversed()
                            .thenComparing(OdChannelResultItem::routeCode)).toList();
            String scope = inputs.isEmpty() ? "福建省" : selected.stream()
                    .map(SelectedRegionResultItem::regionName).collect(Collectors.joining("、"));
            if (query.routeCode() != null && !query.routeCode().isBlank()) scope += " " + query.routeCode().trim().toUpperCase(Locale.ROOT);
            String title = scope + switch (query.queryType()) {
                case OD_CITY_FLOW -> "城市区域流量不平衡分析";
                case OD_KEY_CHANNELS -> "城市交通关键OD通道分析";
                default -> "城市OD综合分析";
            };
            return new OdTrafficFacts(query.queryType(), title, selected, missing, cityRows, channels,
                    hubs.size(), sum(hubs, OdTransportHub::weeklyTotalFlow), sum(hubs, OdTransportHub::dailyAverageFlow),
                    snapshot.acquiredAt(), warnings);
        } catch (ArithmeticException e) {
            throw new BusinessRuleException("OD_ANALYSIS_DATA_INVALID", "流量汇总超出数值范围");
        }
    }

    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        var facts = collectFacts(query);
        String serialized = serialize(facts);
        String prompt = """
                你是福建城市OD统计分析助手。只根据结构化事实返回JSON对象，唯一字段summary。
                用4至6句中文综合分析：概括所选城市范围及卡口流量，再比较城市流量分布、重点路线和车型构成，最后给出简洁监测建议。只讨论提供的表格。
                本业务OD是所选城市卡口并集的统计口径。城市区域流量不平衡表示城市间统计流量差异，不是进出城平衡；关键OD通道表示范围内高流量路线，不表示城市间真实共同通道。
                使用“所选城市范围内”“按卡口汇总”等客观表述，不要讨论系统实现。
                禁止声称车辆从某城驶向另一城、真实OD流量、车辆来源、净流入净流出、共同路线或这些路线直接连接所选城市；不推测事故施工天气原因。
                只引用提供的数值、差额和占比，不自行计算或把流量改写成去重出行人数。不使用万/亿缩写以免改变数值。
                temp_2与日均流量分别采用数据库统计，不要求七倍关系。0速度不能据此判断拥堵。
                这是数据库最新7天统计口径，不是实时路况；不生成历史日期区间、未来趋势预测或拥堵判断。
                全零时说明统计流量为零，不生成占比或倍数。只使用给定城市和路线名称，不编造其他城市或路线。
                """;
        var response = model.generateStructuredStrict(new ModelRequest(prompt, serialized, List.of(), 0.1), OdTrafficSummaryResponse.class);
        String summary = response.summary();
        try {
            ModelFactNumberValidator.validate(summary, serialized);
            if (summary.matches("(?is).*(净流入|净流出|驶往|流向|车辆来源|真实OD|共同路线|直接连接|未来[一二12]|预计.*拥堵|事故|施工|天气|从.{1,12}到.{1,12}的车辆).*")) {
                throw new IllegalArgumentException("不支持的方向或预测结论");
            }
            for (var city : FujianCity.values()) {
                if (summary.contains(city.displayName()) && facts.selectedRegions().stream()
                        .noneMatch(r -> r.regionCode().equals(city.adcode()))) {
                    throw new IllegalArgumentException("未查询的城市");
                }
            }
            var routes = java.util.regex.Pattern.compile("(?i)(?<![A-Z0-9])[GS]\\d{3}(?!\\d)").matcher(summary);
            while (routes.find()) {
                String route = routes.group().toUpperCase(Locale.ROOT);
                if (facts.channelRows().stream().noneMatch(r -> r.routeCode().equals(route))) {
                    throw new IllegalArgumentException("未提供的路线");
                }
            }
        } catch (IllegalArgumentException e) {
            summary = safeSummary(facts);
        }
        return HighwayTrafficResult.fromOdFacts(facts, summary,
                query.traceId() == null || query.traceId().isBlank() ? UUID.randomUUID().toString() : query.traceId());
    }

    private String serialize(OdTrafficFacts facts) {
        StringBuilder text = new StringBuilder(facts.toString());
        text.append("\n统计天数=7；展示的是卡口累计观测流量；缺失城市不是零流量。\n");
        for (var row : facts.cityRows()) {
            if (facts.weeklyTotalFlow() > 0) text.append(row.regionName()).append("流量占比=")
                    .append(BigDecimal.valueOf(row.weeklyTotalFlow()).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(facts.weeklyTotalFlow()), 2, RoundingMode.HALF_UP)).append("%\n");
        }
        if (facts.cityRows().size() > 1) text.append("最高与最低城市7天流量差额=")
                .append(facts.cityRows().get(0).weeklyTotalFlow() - facts.cityRows().get(facts.cityRows().size() - 1).weeklyTotalFlow());
        return text.toString();
    }

    private String safeSummary(OdTrafficFacts facts) {
        String text = "已完成所选城市范围内的卡口统计，共计" + facts.checkpointCount()
                + "个卡口，7天总流量为" + facts.weeklyTotalFlow() + "辆，日均流量汇总为" + facts.dailyAverageFlow() + "辆/日。";
        if (!facts.cityRows().isEmpty()) {
            var first = facts.cityRows().get(0);
            text += facts.weeklyTotalFlow() == 0 ? "本次城市统计流量均为零。"
                    : first.regionName() + "的7天统计流量在当前范围内排名靠前，可结合卡口数量比较各城市流量分布。";
        }
        if (!facts.channelRows().isEmpty()) {
            text += facts.weeklyTotalFlow() == 0 ? "各路线当前7天统计总流量为零。"
                    : facts.channelRows().get(0).routeCode() + "是当前范围内统计流量靠前的路线，可结合分车型流量关注重点通道。";
        }
        return text + "各项数据按所选城市卡口汇总，详细统计请见下方表格。";
    }

    private long sum(List<OdTransportHub> rows, ToLongFunction<OdTransportHub> value) {
        long total = 0;
        for (var row : rows) total = Math.addExact(total, value.applyAsLong(row));
        return total;
    }
}
