package cn.fj.roadagent.core.traffic;

import cn.fj.roadagent.application.exception.BusinessRuleException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.RegionalTrafficDataPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.OdDestinationTendencyResultItem;
import cn.fj.roadagent.application.traffic.OdMatrixCellResultItem;
import cn.fj.roadagent.application.traffic.OdMatrixRowResultItem;
import cn.fj.roadagent.application.traffic.OdTrafficFacts;
import cn.fj.roadagent.application.traffic.OdTrafficSummaryResponse;
import cn.fj.roadagent.application.traffic.SelectedRegionResultItem;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.RegionalConnectionHub;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** 需求1-7：根据跨市路线代表流量形成单城市目的地倾向或多城市联系矩阵。 */
public final class OdTrafficService {
    private static final int STRENGTH_SCALE = 2;
    private static final int RATIO_SCALE = 6;

    private final RegionalTrafficDataPort dataPort;
    private final ChatModelPort model;

    public OdTrafficService(RegionalTrafficDataPort dataPort, ChatModelPort model) {
        this.dataPort = dataPort;
        this.model = model;
    }

    public OdTrafficFacts collectFacts(HighwayTrafficQuery query) {
        if (query == null || query.queryType() == null || !query.queryType().odQuery()) {
            throw new BusinessRuleException("OD_QUERY_TYPE_REQUIRED", "请选择城市目的地联系分析类型");
        }
        List<FujianCity> selected = resolveCities(query);
        var snapshot = dataPort.load();
        List<PairStrength> pairs = aggregatePairStrengths(snapshot.hubs());
        if (pairs.isEmpty()) {
            throw new BusinessRuleException("OD_ANALYSIS_NOT_FOUND", "当前暂无可用城市联系数据");
        }
        Map<FujianCity, BigDecimal> denominators = fullNetworkDenominators(pairs);
        List<SelectedRegionResultItem> regions = selected.stream().map(this::selectedRegion).toList();

        List<OdDestinationTendencyResultItem> destinations = List.of();
        List<OdMatrixRowResultItem> matrix = List.of();
        String title;
        if (query.queryType() == TrafficQueryType.OD_DESTINATION_TENDENCY) {
            FujianCity analysisCity = selected.get(0);
            destinations = destinationRows(analysisCity, pairs, denominators);
            if (destinations.isEmpty()) {
                throw new BusinessRuleException("OD_ANALYSIS_NOT_FOUND", analysisCity.displayName() + "市暂无可用城市联系数据");
            }
            title = analysisCity.displayName() + "市目的地联系倾向分析";
        } else {
            matrix = matrixRows(selected, pairs, denominators);
            title = (query.selectedCities().isEmpty() ? "福建省九市" : selected.stream()
                    .map(city -> city.displayName() + "市").collect(Collectors.joining("、"))) + "目的地联系倾向矩阵";
        }
        return new OdTrafficFacts(query.queryType(), title, regions, destinations, matrix,
                snapshot.acquiredAt(), List.of());
    }

    public HighwayTrafficResult query(HighwayTrafficQuery query) {
        OdTrafficFacts facts = collectFacts(query);
        String serialized = serialize(facts);
        String summary;
        try {
            var response = model.generateStructuredStrict(new ModelRequest(summaryPrompt(), serialized, List.of(), 0.1),
                    OdTrafficSummaryResponse.class);
            summary = response.summary();
            ModelFactNumberValidator.validate(summary, serialized);
            if (invalidClaim(summary)) throw new IllegalArgumentException("OD摘要包含方向性或概率化断言");
        } catch (RuntimeException exception) {
            summary = deterministicSummary(facts);
        }
        return HighwayTrafficResult.fromOdFacts(facts, summary,
                query.traceId() == null || query.traceId().isBlank() ? UUID.randomUUID().toString() : query.traceId());
    }

    private List<FujianCity> resolveCities(HighwayTrafficQuery query) {
        List<String> inputs = new ArrayList<>(query.selectedCities());
        if (inputs.isEmpty()) {
            if (query.originCity() != null && !query.originCity().isBlank()) inputs.add(query.originCity());
            if (query.destinationCity() != null && !query.destinationCity().isBlank()) inputs.add(query.destinationCity());
        }
        LinkedHashSet<FujianCity> parsed = new LinkedHashSet<>();
        for (String input : inputs) parsed.add(FujianCity.fromName(input).orElseThrow(() ->
                new BusinessRuleException("OD_CITY_INVALID", "请选择福建九市范围内的城市")));
        if (query.queryType() == TrafficQueryType.OD_DESTINATION_TENDENCY) {
            if (parsed.size() != 1) {
                throw new BusinessRuleException("OD_SINGLE_CITY_REQUIRED", "目的地联系倾向分析请选择一个福建地级市");
            }
        } else {
            if (parsed.isEmpty()) parsed.addAll(Arrays.asList(FujianCity.values()));
            if (parsed.size() < 2) {
                throw new BusinessRuleException("OD_MATRIX_CITY_COUNT_REQUIRED", "城市联系矩阵至少需要两个福建地级市");
            }
        }
        if (parsed.size() > 9) throw new BusinessRuleException("OD_CITY_LIMIT", "一次最多选择九个福建地级市");
        return List.copyOf(parsed);
    }

    private List<PairStrength> aggregatePairStrengths(List<RegionalConnectionHub> hubs) {
        Map<RouteKey, List<RegionalConnectionHub>> routes = hubs.stream().collect(Collectors.groupingBy(
                hub -> new RouteKey(hub.cityARegionCode(), hub.cityBRegionCode(), hub.routeCode()),
                LinkedHashMap::new, Collectors.toList()));
        Map<PairKey, PairAccumulator> pairs = new LinkedHashMap<>();
        routes.forEach((key, routeHubs) -> {
            BigDecimal routeStrength = routeHubs.stream()
                    .map(hub -> BigDecimal.valueOf(hub.weeklyTotalFlow()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(routeHubs.size()), STRENGTH_SCALE, RoundingMode.HALF_UP);
            PairKey pair = new PairKey(key.cityARegionCode(), key.cityBRegionCode());
            pairs.computeIfAbsent(pair, ignored -> new PairAccumulator()).add(routeStrength);
        });
        return pairs.entrySet().stream().map(entry -> {
            FujianCity a = FujianCity.fromAdcode(entry.getKey().cityARegionCode()).orElseThrow();
            FujianCity b = FujianCity.fromAdcode(entry.getKey().cityBRegionCode()).orElseThrow();
            return new PairStrength(a, b, entry.getValue().routeCount, entry.getValue().strength);
        }).sorted(Comparator.comparing(PairStrength::strength).reversed()
                .thenComparing(pair -> pair.cityA().adcode())
                .thenComparing(pair -> pair.cityB().adcode())).toList();
    }

    private Map<FujianCity, BigDecimal> fullNetworkDenominators(List<PairStrength> pairs) {
        Map<FujianCity, BigDecimal> totals = new EnumMap<>(FujianCity.class);
        for (PairStrength pair : pairs) {
            totals.merge(pair.cityA(), pair.strength(), BigDecimal::add);
            totals.merge(pair.cityB(), pair.strength(), BigDecimal::add);
        }
        return totals;
    }

    private List<OdDestinationTendencyResultItem> destinationRows(
            FujianCity analysisCity, List<PairStrength> pairs, Map<FujianCity, BigDecimal> denominators) {
        BigDecimal denominator = denominators.getOrDefault(analysisCity, BigDecimal.ZERO);
        if (denominator.signum() == 0) return List.of();
        return pairs.stream().filter(pair -> pair.includes(analysisCity)).map(pair -> {
            FujianCity destination = pair.other(analysisCity);
            return new OdDestinationTendencyResultItem(analysisCity.adcode(), analysisCity.displayName() + "市",
                    destination.adcode(), destination.displayName() + "市", pair.routeCount(),
                    pair.strength().doubleValue(), ratio(pair.strength(), denominator));
        }).sorted(Comparator.comparingDouble(OdDestinationTendencyResultItem::tendencyRatio).reversed()
                .thenComparing(OdDestinationTendencyResultItem::destinationRegionCode)).toList();
    }

    private List<OdMatrixRowResultItem> matrixRows(
            List<FujianCity> selected, List<PairStrength> pairs, Map<FujianCity, BigDecimal> denominators) {
        Map<Set<FujianCity>, PairStrength> byCities = pairs.stream().collect(Collectors.toMap(
                pair -> Set.of(pair.cityA(), pair.cityB()), pair -> pair));
        return selected.stream().map(origin -> {
            BigDecimal denominator = denominators.getOrDefault(origin, BigDecimal.ZERO);
            List<OdMatrixCellResultItem> cells = selected.stream().map(destination -> {
                if (origin == destination) {
                    return new OdMatrixCellResultItem(destination.adcode(), destination.displayName() + "市", null, null);
                }
                PairStrength pair = byCities.get(Set.of(origin, destination));
                if (pair == null || denominator.signum() == 0) {
                    return new OdMatrixCellResultItem(destination.adcode(), destination.displayName() + "市", null, null);
                }
                return new OdMatrixCellResultItem(destination.adcode(), destination.displayName() + "市",
                        pair.strength().doubleValue(), ratio(pair.strength(), denominator));
            }).toList();
            return new OdMatrixRowResultItem(origin.adcode(), origin.displayName() + "市", cells);
        }).toList();
    }

    private double ratio(BigDecimal value, BigDecimal denominator) {
        return value.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP).doubleValue();
    }

    private String summaryPrompt() {
        return """
                你是福建省城市出行联系分析助手，只能依据所给结构化事实生成JSON，且只能包含summary字段。
                summary写4至6句、120至700字的连贯中文。单城市查询需指出目的地联系倾向最高的2至3个城市；矩阵查询需概括所选范围内联系倾向突出的城市组合和联系范围较广的城市，最后给出交通组织或持续监测建议。
                统一使用“目的地联系倾向”“跨市出行联系较强”等表述，不解释计算公式、数据表、卡口归属或系统限制。
                不得写成“百分之多少的车辆去了某市”，不得声称是真实车辆去向、真实OD概率、净流入、净流出或具体行驶方向。
                只能引用结构化事实中的城市、路线数量、7日联系强度和百分比，不得重算、修改或补充数值，不推测事故、天气、施工等原因，不使用Markdown。
                """.strip();
    }

    private String serialize(OdTrafficFacts facts) {
        StringBuilder out = new StringBuilder("queryType=").append(facts.queryType()).append('\n')
                .append("title=").append(facts.title()).append('\n')
                .append("dataTimeAsiaShanghai=").append(TrafficTimeFormatter.asiaShanghai(facts.acquiredAt())).append('\n')
                .append("selectedRegions=");
        facts.selectedRegions().forEach(region -> out.append(region.regionName()).append('；'));
        out.append("\ndestinationRows:\n");
        facts.destinationRows().forEach(row -> out.append("- ").append(row.analysisCityName()).append("—")
                .append(row.destinationCityName()).append("|路线数=").append(row.routeCount())
                .append("|7日联系强度=").append(formatStrength(row.weeklyConnectionStrength()))
                .append("|目的地联系倾向=").append(formatPercent(row.tendencyRatio())).append("%\n"));
        out.append("matrixCells:\n");
        facts.matrixRows().forEach(row -> row.cells().stream().filter(cell -> cell.tendencyRatio() != null)
                .forEach(cell -> out.append("- ").append(row.analysisCityName()).append("—")
                        .append(cell.destinationCityName()).append("|7日联系强度=")
                        .append(formatStrength(cell.weeklyConnectionStrength())).append("|目的地联系倾向=")
                        .append(formatPercent(cell.tendencyRatio())).append("%\n")));
        return out.toString();
    }

    private boolean invalidClaim(String summary) {
        String normalized = summary == null ? "" : summary.toLowerCase(Locale.ROOT);
        return normalized.isBlank() || normalized.contains("真实od") || normalized.contains("od概率")
                || summary.contains("净流入") || summary.contains("净流出") || summary.contains("驶往")
                || summary.contains("流入") || summary.contains("流出") || summary.contains("车辆去了")
                || summary.matches("(?s).*\\d+(?:\\.\\d+)?%的(?:车辆|车).*" );
    }

    private String deterministicSummary(OdTrafficFacts facts) {
        if (!facts.destinationRows().isEmpty()) {
            var rows = facts.destinationRows();
            String city = rows.get(0).analysisCityName();
            String leaders = rows.stream().limit(3).map(row -> row.destinationCityName() + "（"
                    + formatPercent(row.tendencyRatio()) + "%）").collect(Collectors.joining("、"));
            return city + "的跨市出行联系呈现较为清晰的目的地倾向结构。当前目的地联系倾向较高的城市为"
                    + leaders + "。其中，" + rows.get(0).destinationCityName()
                    + "在现有城市联系网络中的关联程度最为突出。建议持续关注主要关联城市之间的交通运行变化，并结合重点时段做好跨市通道组织。";
        }
        List<MatrixEntry> entries = facts.matrixRows().stream().flatMap(row -> row.cells().stream()
                .filter(cell -> cell.tendencyRatio() != null)
                .map(cell -> new MatrixEntry(row.analysisCityName(), cell.destinationCityName(), cell.tendencyRatio())))
                .sorted(Comparator.comparingDouble(MatrixEntry::ratio).reversed()).toList();
        String scope = facts.selectedRegions().stream().map(SelectedRegionResultItem::regionName)
                .collect(Collectors.joining("、"));
        if (entries.isEmpty()) return scope + "当前城市联系矩阵中暂无可列出的联系倾向。";
        var top = entries.get(0);
        return "已完成" + scope + "的城市目的地联系倾向矩阵分析。所选范围内，" + top.origin() + "与"
                + top.destination() + "的联系倾向较为突出，占" + formatPercent(top.ratio())
                + "%。联系倾向可用于比较不同城市在全省跨市联系结构中的相对重要程度。建议重点关注矩阵中的高值城市组合，并持续跟踪其跨市交通运行变化。";
    }

    private SelectedRegionResultItem selectedRegion(FujianCity city) {
        return new SelectedRegionResultItem(city.adcode(), city.displayName() + "市");
    }
    private String formatStrength(Double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private String formatPercent(double ratio) { return String.format(Locale.ROOT, "%.2f", ratio * 100); }

    private record RouteKey(String cityARegionCode, String cityBRegionCode, String routeCode) { }
    private record PairKey(String cityARegionCode, String cityBRegionCode) { }
    private record PairStrength(FujianCity cityA, FujianCity cityB, int routeCount, BigDecimal strength) {
        boolean includes(FujianCity city) { return cityA == city || cityB == city; }
        FujianCity other(FujianCity city) { return cityA == city ? cityB : cityA; }
    }
    private static final class PairAccumulator {
        private BigDecimal strength = BigDecimal.ZERO;
        private int routeCount;
        void add(BigDecimal routeStrength) { strength = strength.add(routeStrength); routeCount++; }
    }
    private record MatrixEntry(String origin, String destination, double ratio) { }
}
