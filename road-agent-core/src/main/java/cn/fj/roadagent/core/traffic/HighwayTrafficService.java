package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficFacts;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.TrafficForecastSummaryResponse;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 用确定性 Java 产生事实，再要求模型只对这些事实做总结。 */
public final class HighwayTrafficService {
    private static final int ABNORMAL_LIMIT = 10;
    private static final int DETAIL_LIMIT = 20;
    private static final int OVERVIEW_FORECAST_LIMIT = 10;
    private static final Comparator<HighwayTrafficSegment> SEVERITY_ORDER =
            Comparator.<HighwayTrafficSegment>comparingInt(segment -> segment.status().code()).reversed()
                    .thenComparing(HighwayTrafficSegment::severity, Comparator.reverseOrder())
                    .thenComparing(HighwayTrafficSegment::averageSpeedKmh)
                    .thenComparing(HighwayTrafficSegment::routeCode)
                    .thenComparing(HighwayTrafficSegment::routeSection);

    private final HighwayTrafficSnapshotPort snapshotPort;
    private final ChatModelPort chatModelPort;

    public HighwayTrafficService(
            HighwayTrafficSnapshotPort snapshotPort,
            ChatModelPort chatModelPort
    ) {
        this.snapshotPort = snapshotPort;
        this.chatModelPort = chatModelPort;
    }

    public HighwayTrafficFacts collectFacts(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null) {
            throw new BusinessRuleException("TRAFFIC_QUERY_TYPE_REQUIRED", "请说明要查询的交通范围");
        }
        HighwayTrafficSnapshot snapshot = snapshotPort.current();
        return switch (query.queryType()) {
            case PROVINCE_OVERVIEW -> overview(snapshot);
            case PROVINCE_ABNORMAL -> abnormal(snapshot);
            case CITY_PAIR -> cityPair(snapshot, query.originCity(), query.destinationCity());
            case ROUTE_DETAIL -> routeDetail(snapshot, query.routeCode(), query.routeName());
            case OD_OVERVIEW, OD_CITY_FLOW, OD_KEY_CHANNELS ->
                    throw new BusinessRuleException("TRAFFIC_QUERY_TYPE_INVALID", "该查询属于城市OD七日统计分析");
            case CAPACITY_OVERVIEW, CAPACITY_BOTTLENECKS, CAPACITY_ROUTE_DETAIL ->
                    throw new BusinessRuleException("TRAFFIC_QUERY_TYPE_INVALID", "该查询属于道路通行能力评估");
            case REGIONAL_TRAFFIC_OVERVIEW, CHECKPOINT_PRESSURE, CITY_PRESSURE, ROUTE_PRESSURE ->
                    throw new BusinessRuleException("TRAFFIC_QUERY_TYPE_INVALID", "该查询属于区域交通压力分析");
            case VEHICLE_PATTERN_OVERVIEW, VEHICLE_STRUCTURE, VEHICLE_HOURLY_PATTERN,
                 VEHICLE_DAY_TYPE_COMPARISON ->
                    throw new BusinessRuleException("TRAFFIC_QUERY_TYPE_INVALID", "该查询属于车型出行特征分析");
        };
    }

    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        HighwayTrafficFacts facts = collectFacts(query);
        TrafficForecastSummaryResponse response = chatModelPort.generateStructuredStrict(
                summaryRequest(facts), TrafficForecastSummaryResponse.class
        );
        String traceId = query.traceId() == null || query.traceId().isBlank()
                ? UUID.randomUUID().toString() : query.traceId();
        return HighwayTrafficResult.fromFacts(facts, response.combinedSummary(), traceId);
    }

    private HighwayTrafficFacts overview(HighwayTrafficSnapshot snapshot) {
        List<RouteTrafficSummary> summaries = snapshot.routeSummaries().stream()
                .sorted(Comparator.comparing(RouteTrafficSummary::routeCode))
                .toList();
        return facts(TrafficQueryType.PROVINCE_OVERVIEW, "福建省国省道整体交通态势",
                summaries, List.of(), forecastSegments(snapshot.segments(), OVERVIEW_FORECAST_LIMIT),
                0, snapshot, List.of());
    }

    private HighwayTrafficFacts abnormal(HighwayTrafficSnapshot snapshot) {
        List<HighwayTrafficSegment> all = snapshot.segments().stream()
                .filter(segment -> segment.status().abnormal())
                .sorted(SEVERITY_ORDER)
                .toList();
        return facts(TrafficQueryType.PROVINCE_ABNORMAL, "福建省拥堵异常路段",
                List.of(), all.stream().limit(ABNORMAL_LIMIT).toList(),
                all.stream().limit(ABNORMAL_LIMIT).toList(), all.size(), snapshot,
                List.of());
    }

    private HighwayTrafficFacts cityPair(
            HighwayTrafficSnapshot snapshot,
            String originInput,
            String destinationInput
    ) {
        FujianCity origin = requireCity(originInput, "出发城市");
        FujianCity destination = requireCity(destinationInput, "到达城市");
        if (origin == destination) {
            throw new BusinessRuleException("TRAFFIC_CITY_PAIR_INVALID", "出发城市和到达城市不能相同");
        }
        String originName = origin.displayName() + "市";
        String destinationName = destination.displayName() + "市";
        Set<String> routeCodes = snapshot.routes().stream()
                .filter(route -> isCityPair(route, originName, destinationName))
                .map(HighwayRoute::routeCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (routeCodes.isEmpty()) {
            throw new BusinessRuleException(
                    "TRAFFIC_ROUTE_NOT_FOUND", "未找到以%s和%s为起终点的国省道".formatted(originName, destinationName)
            );
        }
        List<RouteTrafficSummary> summaries = snapshot.routeSummaries().stream()
                .filter(summary -> routeCodes.contains(summary.routeCode()))
                .sorted(Comparator.comparing(RouteTrafficSummary::routeCode))
                .toList();
        List<HighwayTrafficSegment> all = sortedSegments(snapshot, routeCodes);
        String title = originName + "—" + destinationName + "交通情况";
        List<HighwayTrafficSegment> displayed = all.stream().limit(DETAIL_LIMIT).toList();
        return facts(TrafficQueryType.CITY_PAIR, title, summaries,
                displayed, displayed, all.size(), snapshot,
                List.of());
    }

    private HighwayTrafficFacts routeDetail(
            HighwayTrafficSnapshot snapshot,
            String routeCodeInput,
            String routeNameInput
    ) {
        List<HighwayRoute> matches = matchRoutes(snapshot.routes(), routeCodeInput, routeNameInput);
        if (matches.isEmpty()) {
            throw new BusinessRuleException("TRAFFIC_ROUTE_NOT_FOUND", "未找到对应的国省道路线");
        }
        if (matches.size() > 1) {
            String choices = matches.stream()
                    .map(route -> route.routeCode() + " " + route.routeName())
                    .collect(Collectors.joining("、"));
            throw new BusinessRuleException(
                    "TRAFFIC_ROUTE_AMBIGUOUS", "找到多条候选路线，请明确路线编号：" + choices
            );
        }
        HighwayRoute route = matches.get(0);
        List<RouteTrafficSummary> summaries = snapshot.routeSummaries().stream()
                .filter(summary -> summary.routeCode().equals(route.routeCode()))
                .toList();
        List<HighwayTrafficSegment> all = sortedSegments(snapshot, Set.of(route.routeCode()));
        List<HighwayTrafficSegment> displayed = all.stream().limit(DETAIL_LIMIT).toList();
        return facts(TrafficQueryType.ROUTE_DETAIL, route.routeCode() + " " + route.routeName() + "交通情况",
                summaries, displayed, displayed, all.size(), snapshot,
                List.of());
    }

    private List<HighwayRoute> matchRoutes(
            List<HighwayRoute> routes,
            String routeCodeInput,
            String routeNameInput
    ) {
        String code = normalizeCode(routeCodeInput);
        if (!code.isBlank()) {
            return routes.stream().filter(route -> route.routeCode().equals(code)).toList();
        }
        String name = normalizeRouteName(routeNameInput);
        if (name.isBlank()) {
            throw new BusinessRuleException("TRAFFIC_ROUTE_REQUIRED", "请提供G/S路线编号或路线名称");
        }
        return routes.stream()
                .filter(route -> normalizeRouteName(route.routeName()).equals(name))
                .toList();
    }

    private List<HighwayTrafficSegment> sortedSegments(
            HighwayTrafficSnapshot snapshot,
            Set<String> routeCodes
    ) {
        return snapshot.segments().stream()
                .filter(segment -> routeCodes.contains(segment.routeCode()))
                .sorted(SEVERITY_ORDER)
                .toList();
    }

    private List<HighwayTrafficSegment> forecastSegments(
            List<HighwayTrafficSegment> segments,
            int limit
    ) {
        return segments.stream().sorted(SEVERITY_ORDER).limit(limit).toList();
    }

    private HighwayTrafficFacts facts(
            TrafficQueryType type,
            String title,
            List<RouteTrafficSummary> summaries,
            List<HighwayTrafficSegment> segments,
            List<HighwayTrafficSegment> forecastSegments,
            int totalSegmentCount,
            HighwayTrafficSnapshot snapshot,
            List<String> warnings
    ) {
        return new HighwayTrafficFacts(type, title, summaries, segments, forecastSegments, totalSegmentCount,
                totalSegmentCount > segments.size(), snapshot.acquiredAt(), warnings);
    }

    private ModelRequest summaryRequest(HighwayTrafficFacts facts) {
        String systemPrompt = """
                你是福建普通国省干线交通态势研判助手。只能根据用户消息中的结构化事实生成专业摘要。
                必须输出严格JSON对象，且只能有summary、trend、trendForecast三个字段。
                summary写3至4句连贯中文，建议120至320字，内容充实但不逐项复述表格。先给出总体结论，再说明状态分布或异常规模，随后点出2至4条最值得关注的路线或路段，并给出简洁可执行的通行建议。
                trend只能严格选择以下一个标签：基本稳定、持续拥堵、可能加剧、逐渐缓解、局部分化。
                trendForecast必须恰好写1句，明确包含“未来1至2小时”和所选trend标签，并使用“预计”“可能”或“有望”等非确定性措辞。
                短时趋势是根据当前status、uniform_speed、severity和路况常识作出的启发式定性研判；status是权威状态，uniform_speed和severity只能辅助研判。
                当uniform_speed为0但status为10畅通时，必须按畅通理解，不得把该路段改写为堵塞。
                不要输出能力边界、数据限制、数据不足、无法判断等说明，不要讨论系统实现和数据来源。
                不得新增道路、路段、速度、状态、原因或规定研判时段之外的时间；不得重新计算或修改Java给出的状态。
                状态含义固定为：10畅通、20轻度拥堵、30中度拥堵、40重度拥堵、50堵塞。
                status>=20时可给出当前拥堵提示、错峰通行、预留时间等一般性建议。
                趋势不得给出未来具体速度、流量、概率、恢复时刻或解除时间，不得假装使用了历史序列或实时增量数据。
                用户询问拥堵原因时，不分析或猜测事故、施工、天气、流量变化等原因，直接聚焦当前通行状态、重点路段和建议。
                不要复述全部表格，不要使用Markdown，不要将国省干线事实改写为城市道路数据。
                """.strip();
        return new ModelRequest(systemPrompt, serializeFacts(facts), List.of(), 0.1);
    }

    private String serializeFacts(HighwayTrafficFacts facts) {
        StringBuilder value = new StringBuilder();
        value.append("queryType=").append(facts.queryType()).append('\n');
        value.append("title=").append(facts.title()).append('\n');
        value.append("acquiredAt=").append(facts.acquiredAt()).append('\n');
        value.append("totalSegmentCount=").append(facts.totalSegmentCount()).append('\n');
        value.append("displayedSegmentCount=").append(facts.segments().size()).append('\n');
        value.append("truncated=").append(facts.truncated()).append('\n');
        value.append("forecastHorizon=未来1至2小时\n");
        value.append("forecastPolicy=status为权威；uniform_speed和severity仅作辅助；字段冲突时按status\n");
        appendStatusCounts(value, "routeStatusCounts", facts.routeSummaries().stream()
                .map(RouteTrafficSummary::status).toList());
        appendStatusCounts(value, "displayedSegmentStatusCounts", facts.segments().stream()
                .map(HighwayTrafficSegment::status).toList());
        value.append("routeSummaries:\n");
        for (RouteTrafficSummary route : facts.routeSummaries()) {
            value.append("- ").append(route.routeCode()).append('|').append(route.routeName())
                    .append("|均速=").append(format(route.averageSpeedKmh()))
                    .append("|status=").append(route.status().code()).append('/').append(route.status().displayName())
                    .append('\n');
        }
        value.append("segments:\n");
        for (HighwayTrafficSegment segment : facts.segments()) {
            value.append("- ").append(segment.routeCode()).append('|').append(segment.routeName())
                    .append('|').append(segment.routeSection())
                    .append("|距离=").append(format(segment.distanceKm()))
                    .append("|均速=").append(format(segment.averageSpeedKmh()))
                    .append("|status=").append(segment.status().code()).append('/').append(segment.status().displayName())
                    .append("|severity=").append(String.format(Locale.ROOT, "%.2f", segment.severity()))
                    .append('\n');
        }
        value.append("forecastSegments:\n");
        for (HighwayTrafficSegment segment : facts.forecastSegments()) {
            value.append("- ").append(segment.routeCode()).append('|').append(segment.routeName())
                    .append('|').append(segment.routeSection())
                    .append("|均速=").append(format(segment.averageSpeedKmh()))
                    .append("|status=").append(segment.status().code()).append('/').append(segment.status().displayName())
                    .append("|severity=").append(String.format(Locale.ROOT, "%.2f", segment.severity()))
                    .append('\n');
        }
        return value.toString();
    }

    private void appendStatusCounts(
            StringBuilder value,
            String label,
            List<cn.fj.roadagent.domain.traffic.TrafficStatus> statuses
    ) {
        value.append(label).append('=');
        for (cn.fj.roadagent.domain.traffic.TrafficStatus status
                : cn.fj.roadagent.domain.traffic.TrafficStatus.values()) {
            long count = statuses.stream().filter(status::equals).count();
            value.append(status.code()).append('/').append(status.displayName())
                    .append(':').append(count).append('；');
        }
        value.append('\n');
    }

    private String format(Double value) {
        return value == null ? "未提供" : String.format(Locale.ROOT, "%.2f", value);
    }

    private boolean isCityPair(HighwayRoute route, String origin, String destination) {
        String start = normalizeCity(route.startPlace());
        String end = normalizeCity(route.endPlace());
        String normalizedOrigin = normalizeCity(origin);
        String normalizedDestination = normalizeCity(destination);
        return start.equals(normalizedOrigin) && end.equals(normalizedDestination)
                || start.equals(normalizedDestination) && end.equals(normalizedOrigin);
    }

    private FujianCity requireCity(String input, String label) {
        return FujianCity.fromName(input).orElseThrow(() ->
                new BusinessRuleException("TRAFFIC_CITY_REQUIRED", "请提供有效的福建省" + label));
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeCity(String value) {
        return value == null ? "" : value.trim().replace("福建省", "").replace("市", "");
    }

    private String normalizeRouteName(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("[\\p{Pd}\\s]", "")
                .toLowerCase(Locale.ROOT);
    }
}
