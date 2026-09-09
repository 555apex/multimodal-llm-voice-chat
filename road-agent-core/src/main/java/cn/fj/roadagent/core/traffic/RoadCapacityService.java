package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotPort;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.RoadCapacityFacts;
import cn.fj.roadagent.application.traffic.TrafficSummaryResponse;
import cn.fj.roadagent.domain.traffic.CapacityLevel;
import cn.fj.roadagent.domain.traffic.RoadCapacity;
import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

/** 使用数据库已计算结果完成路线通行能力评估，不在 Java 中重新计算指标。 */
public final class RoadCapacityService {
    private static final int BOTTLENECK_LIMIT = 10;
    private static final Comparator<RoadCapacity> BOTTLENECK_ORDER =
            Comparator.comparingDouble(RoadCapacity::utilizationRatio).reversed()
                    .thenComparing(Comparator.comparingDouble(RoadCapacity::actualCapacityVph).reversed())
                    .thenComparing(RoadCapacity::routeCode);

    private final RoadCapacitySnapshotPort snapshotPort;
    private final HighwayTrafficSnapshotPort trafficSnapshotPort;
    private final ChatModelPort chatModelPort;

    public RoadCapacityService(RoadCapacitySnapshotPort snapshotPort, ChatModelPort chatModelPort) {
        this(snapshotPort, null, chatModelPort);
    }

    public RoadCapacityService(RoadCapacitySnapshotPort snapshotPort,
            HighwayTrafficSnapshotPort trafficSnapshotPort, ChatModelPort chatModelPort) {
        this.snapshotPort = snapshotPort;
        this.trafficSnapshotPort = trafficSnapshotPort;
        this.chatModelPort = chatModelPort;
    }

    public RoadCapacityFacts collectFacts(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null || !query.queryType().capacityQuery()) {
            throw new BusinessRuleException("CAPACITY_QUERY_TYPE_REQUIRED", "请说明要查询的通行能力范围");
        }
        RoadCapacitySnapshot snapshot = snapshotPort.current();
        return switch (query.queryType()) {
            case CAPACITY_OVERVIEW -> overview(snapshot, query.originCity(), query.destinationCity());
            case CAPACITY_BOTTLENECKS -> bottlenecks(snapshot, query.originCity(), query.destinationCity());
            case CAPACITY_ROUTE_DETAIL -> routeDetail(snapshot, query.routeCode(), query.routeName());
            default -> throw new BusinessRuleException("CAPACITY_QUERY_TYPE_INVALID", "该查询不属于通行能力评估");
        };
    }

    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        RoadCapacityFacts facts = collectFacts(query);
        TrafficSummaryResponse response = chatModelPort.generateStructuredStrict(
                summaryRequest(facts), TrafficSummaryResponse.class
        );
        String summary = factSafeSummary(facts, response.summary());
        String traceId = query.traceId() == null || query.traceId().isBlank()
                ? UUID.randomUUID().toString() : query.traceId();
        return HighwayTrafficResult.fromCapacityFacts(facts, summary, traceId);
    }

    private RoadCapacityFacts overview(RoadCapacitySnapshot snapshot, String origin, String destination) {
        List<RoadCapacity> all = cityPairScope(snapshot.capacities(), origin, destination).stream()
                .sorted(Comparator.comparing(RoadCapacity::routeCode))
                .toList();
        return facts(TrafficQueryType.CAPACITY_OVERVIEW, capacityTitle("国省道通行能力总览", origin, destination),
                all, all.size(), false, snapshot, all);
    }

    private RoadCapacityFacts bottlenecks(RoadCapacitySnapshot snapshot, String origin, String destination) {
        List<RoadCapacity> scope = cityPairScope(snapshot.capacities(), origin, destination);
        List<RoadCapacity> all = scope.stream()
                .filter(capacity -> capacity.level() != CapacityLevel.NORMAL)
                .sorted(BOTTLENECK_ORDER)
                .toList();
        return facts(TrafficQueryType.CAPACITY_BOTTLENECKS, capacityTitle("瓶颈路线排行", origin, destination),
                all.stream().limit(BOTTLENECK_LIMIT).toList(), all.size(),
                all.size() > BOTTLENECK_LIMIT, snapshot, scope);
    }

    private List<RoadCapacity> cityPairScope(List<RoadCapacity> capacities, String origin, String destination) {
        if (isBlank(origin) && isBlank(destination)) return capacities;
        if (isBlank(origin) || isBlank(destination)) {
            throw new BusinessRuleException("CAPACITY_CITY_PAIR_REQUIRED", "请同时提供两个福建地级市");
        }
        if (trafficSnapshotPort == null) {
            throw new BusinessRuleException("CAPACITY_CITY_PAIR_UNAVAILABLE", "当前无法按两市登记起终点筛选容量路线");
        }
        String start = normalizeCity(origin);
        String end = normalizeCity(destination);
        Set<String> routeCodes = trafficSnapshotPort.current().routes().stream()
                .filter(route -> matchesCities(route, start, end))
                .map(HighwayRoute::routeCode).collect(Collectors.toSet());
        return capacities.stream().filter(row -> routeCodes.contains(row.routeCode())).toList();
    }

    private boolean matchesCities(HighwayRoute route, String origin, String destination) {
        String start = normalizeCity(route.startPlace());
        String end = normalizeCity(route.endPlace());
        return start.equals(origin) && end.equals(destination)
                || start.equals(destination) && end.equals(origin);
    }

    private String capacityTitle(String suffix, String origin, String destination) {
        if (isBlank(origin) || isBlank(destination)) return "福建省" + suffix;
        return origin.replace("市", "") + "市—" + destination.replace("市", "")
                + "市登记起终点关联路线" + suffix;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeCity(String value) {
        return value == null ? "" : value.trim().replace("福建省", "").replace("市", "");
    }

    private RoadCapacityFacts routeDetail(
            RoadCapacitySnapshot snapshot,
            String routeCodeInput,
            String routeNameInput
    ) {
        List<RoadCapacity> matches = match(snapshot.capacities(), routeCodeInput, routeNameInput);
        if (matches.isEmpty()) {
            throw new BusinessRuleException("CAPACITY_ROUTE_NOT_FOUND", "未找到对应路线的通行能力数据");
        }
        if (matches.size() > 1) {
            String choices = matches.stream()
                    .map(capacity -> capacity.routeCode() + " " + capacity.routeName())
                    .collect(Collectors.joining("、"));
            throw new BusinessRuleException(
                    "CAPACITY_ROUTE_AMBIGUOUS", "找到多条候选路线，请明确路线编号：" + choices
            );
        }
        RoadCapacity capacity = matches.get(0);
        return facts(TrafficQueryType.CAPACITY_ROUTE_DETAIL,
                capacity.routeCode() + " " + capacity.routeName() + "通行能力",
                List.of(capacity), 1, false, snapshot, List.of(capacity));
    }

    private List<RoadCapacity> match(
            List<RoadCapacity> capacities,
            String routeCodeInput,
            String routeNameInput
    ) {
        String code = normalizeCode(routeCodeInput);
        if (!code.isBlank()) {
            return capacities.stream().filter(capacity -> capacity.routeCode().equals(code)).toList();
        }
        String name = normalizeRouteName(routeNameInput);
        if (name.isBlank()) {
            throw new BusinessRuleException("CAPACITY_ROUTE_REQUIRED", "请提供G/S路线编号或路线名称");
        }
        return capacities.stream()
                .filter(capacity -> normalizeRouteName(capacity.routeName()).equals(name))
                .toList();
    }

    private RoadCapacityFacts facts(
            TrafficQueryType type,
            String title,
            List<RoadCapacity> rows,
            int totalCount,
            boolean truncated,
            RoadCapacitySnapshot snapshot,
            List<RoadCapacity> countUniverse
    ) {
        return new RoadCapacityFacts(
                type, title, rows, totalCount,
                count(countUniverse, CapacityLevel.NORMAL),
                count(countUniverse, CapacityLevel.BOTTLENECK),
                count(countUniverse, CapacityLevel.SEVERE_BOTTLENECK),
                truncated, snapshot.acquiredAt(), List.of()
        );
    }

    private int count(List<RoadCapacity> capacities, CapacityLevel level) {
        return Math.toIntExact(capacities.stream().filter(value -> value.level() == level).count());
    }

    private ModelRequest summaryRequest(RoadCapacityFacts facts) {
        String systemPrompt = """
                你是福建普通国省干线路线通行能力研判助手。只能根据用户消息中的结构化事实生成专业摘要。
                必须输出严格JSON对象，且只能有summary字段。summary写3至5句连贯中文，建议120至320字。
                先给出总体结论并说明正常、瓶颈、严重瓶颈三类路线数量；再结合实际通行能力和利用率说明重点路线；最后给出简洁的调度关注建议。表达方式可以自然变化，不要求使用固定句式。
                当前项目口径为：利用率低于15%为正常，不低于15%且低于30%为瓶颈，不低于30%为严重瓶颈。利用率越高，通行能力压力越大。
                必须直接采用结构化事实中的实际通行能力、设计通行能力、利用率和评估等级，不得重新计算、修正或补充数值。
                优先点出利用率最高的路线；路线较多时概括最值得关注的部分，并提示用户可继续查看下方明细。
                不推测瓶颈原因，不讨论数据限制、系统实现或数据来源，不使用Markdown，不逐项复述整张表格。
                """.strip();
        return new ModelRequest(systemPrompt, serializeFacts(facts), List.of(), 0.1);
    }

    private String serializeFacts(RoadCapacityFacts facts) {
        StringBuilder value = new StringBuilder();
        value.append("queryType=").append(facts.queryType()).append('\n');
        value.append("title=").append(facts.title()).append('\n');
        value.append("dataTimeAsiaShanghai=").append(TrafficTimeFormatter.asiaShanghai(facts.acquiredAt())).append('\n');
        value.append("totalCount=").append(facts.totalCount()).append('\n');
        value.append("displayedCount=").append(facts.rows().size()).append('\n');
        value.append("truncated=").append(facts.truncated()).append('\n');
        value.append("capacityPolicy=利用率低于15%为正常；不低于15%且低于30%为瓶颈；不低于30%为严重瓶颈；利用率越高压力越大\n");
        value.append("summaryFocusLimit=5\n");
        value.append("levelCounts=正常:").append(facts.normalCount())
                .append("；瓶颈:").append(facts.bottleneckCount())
                .append("；严重瓶颈:").append(facts.severeBottleneckCount()).append('\n');
        List<RoadCapacity> bottlenecks = facts.rows().stream()
                .filter(row -> row.level() != CapacityLevel.NORMAL)
                .sorted(BOTTLENECK_ORDER)
                .toList();
        int mentionCount = Math.min(5, bottlenecks.size());
        value.append("bottleneckRoutesToMention=");
        for (RoadCapacity row : bottlenecks.stream().limit(mentionCount).toList()) {
            value.append(row.routeCode()).append(' ').append(row.routeName()).append('；');
        }
        value.append('\n');
        value.append("remainingBottleneckRoutes=")
                .append(Math.max(0, facts.bottleneckCount() + facts.severeBottleneckCount() - mentionCount))
                .append('\n');
        value.append("capacityRows:\n");
        for (RoadCapacity row : facts.rows()) {
            value.append("- ").append(row.routeCode()).append('|').append(row.routeName())
                    .append("|实际通行能力=").append(format(row.actualCapacityVph())).append("辆/小时")
                    .append("|设计通行能力=").append(format(row.designCapacityVph())).append("辆/小时")
                    .append("|利用率=").append(format(row.utilizationRatio() * 100)).append('%')
                    .append("|评估等级=").append(row.level().displayName()).append('\n');
        }
        return value.toString();
    }

    private String factSafeSummary(RoadCapacityFacts facts, String summary) {
        // 数量、能力和利用率仍须来自结构化事实；不再因模型没有使用某个固定词、
        // 固定句式或没有逐字列出路线名称而中断整次业务回答。
        try {
            ModelFactNumberValidator.validate(summary, serializeFacts(facts));
            return summary;
        } catch (IllegalArgumentException ignored) {
            return deterministicSummary(facts);
        }
    }

    private String deterministicSummary(RoadCapacityFacts facts) {
        String first = "本次共评估%d条国省道路线，其中正常路线%d条、瓶颈路线%d条、严重瓶颈路线%d条。"
                .formatted(facts.totalCount(), facts.normalCount(), facts.bottleneckCount(),
                        facts.severeBottleneckCount());
        List<RoadCapacity> focus = facts.rows().stream()
                .filter(row -> row.level() != CapacityLevel.NORMAL)
                .sorted(BOTTLENECK_ORDER).limit(5).toList();
        String second = focus.isEmpty()
                ? "当前展示范围内各路线均处于正常等级，可结合明细继续关注利用率变化。"
                : "当前应优先关注" + focus.stream()
                .map(row -> row.routeCode() + " " + row.routeName())
                .collect(Collectors.joining("、")) + "等路线，具体能力数值和利用率见下方明细。";
        return first + second + "建议按评估等级持续跟踪重点路线，并结合后续批次变化安排巡查与调度。";
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeRouteName(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("[\\p{Pd}\\s]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
