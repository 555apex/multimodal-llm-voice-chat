package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import cn.fj.roadagent.core.traffic.VehicleAnalysisDateParser;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对新增交通业务的高置信问法做确定性识别，避免模型把产品内置示例误判为不支持。
 * 模糊表达和依赖会话上下文的追问仍交给模型规划。
 */
final class KnownTrafficQuestionClassifier {
    private static final Pattern ROUTE_CODE = Pattern.compile("(?i)(?<![A-Z0-9])([GS])\\s*(\\d{3})(?!\\d)");

    private KnownTrafficQuestionClassifier() {
    }

    static Optional<AgentDecision> classify(String message) {
        return classify(message, List.of());
    }

    static Optional<AgentDecision> classify(String message, List<HighwayRoute> routes) {
        if (message == null || message.isBlank()) return Optional.empty();
        String normalized = message.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        Optional<String> direct = directAnswer(normalized);
        if (direct.isPresent()) return Optional.of(directAnswerDecision(direct.get()));
        if (containsAny(normalized, "事故", "塌方", "水毁", "应急调度", "救援", "物资调度")) {
            return Optional.empty();
        }

        if (isRouteCatalogQuestion(normalized)) {
            return Optional.of(trafficDecision(TrafficQueryType.ROUTE_CATALOG, List.of(), null, null, false));
        }

        boolean rejectsOd = containsAny(normalized, "不做od", "无需od", "不是od", "不要od");
        Optional<TrafficQueryType> odType = rejectsOd ? Optional.empty() : odType(normalized);
        if (odType.isPresent()) {
            List<String> cities = mentionedCities(normalized);
            String clarification = null;
            if (containsAny(normalized, "上个月", "上月", "去年", "上周", "指定日期", "昨天", "前天", "未来", "预测")
                    || normalized.matches(".*20\\d{2}[-年/]\\d{1,2}.*")
                    || normalized.matches(".*\\d{1,2}月\\d{1,2}日.*")) {
                clarification = "目前可查询数据库当前7日目的地联系倾向，是否改为该统计范围？";
            } else if (containsAny(normalized, "真实od", "实际od", "净流入", "净流出", "驶往", "车辆来源", "车辆去向", "实际流向")) {
                clarification = "是否改为查看所选城市的目的地联系倾向或城市联系矩阵？";
            } else if (containsAny(normalized, "通行能力", "利用率", "拥堵", "瓶颈")) {
                clarification = "OD流量统计与路况、通行能力是不同分析，请问本轮先查看哪一项？";
            } else if (cities.isEmpty() && containsAny(normalized, "两市", "两个城市", "这几个城市", "两地", "这两个")) {
                clarification = "请说明需要分析的城市名称，可选择福建九市中的一个或多个城市。";
            }
            // 不能把“福州和南京”缩减成“福州”；无法识别的地点需要用户澄清。
            if (hasUnknownOdCity(normalized)) {
                clarification = "请选择福建九个地级市范围内的城市，不能将省外城市、区县或平潭合并到本次统计。";
            }
            return Optional.of(new AgentDecision("TRAFFIC_QUERY", odType.get().name(), null, null,
                    routeCode(normalized), null, cities, null, null, null, null, null,
                    null, null, null, null, List.of(), clarification));
        }

        Optional<TrafficQueryType> vehicleType = vehicleType(normalized);
        if (vehicleType.isPresent()) {
            List<String> cities = mentionedCities(normalized);
            String analysisCity = cities.size() == 1 ? cities.get(0) : null;
            String analysisDate = VehicleAnalysisDateParser.parse(normalized)
                    .map(java.time.LocalDate::toString).orElse(null);
            String clarification = cities.isEmpty()
                    ? "请选择福州或厦门进行车型出行特征分析。"
                    : cities.size() > 2
                    ? "车型分析当前支持福州和厦门，请调整城市范围。"
                    : null;
            return Optional.of(decision(vehicleType.get(), cities, analysisCity, clarification, analysisDate));
        }

        Optional<TrafficQueryType> capacityType = capacityType(normalized);
        if (capacityType.isPresent()) {
            String routeName = matchedRouteName(normalized, routes);
            if (containsAny(normalized, "高速") && routeCode(normalized) == null && routeName == null) {
                return Optional.of(directAnswerDecision("暂不支持高速公路通行能力查询；目前可查询福建普通国道、省道的路线级通行能力。"));
            }
            return Optional.of(trafficDecision(capacityType(normalized, routeName).orElse(capacityType.get()),
                    mentionedCities(normalized), routeCode(normalized), routeName, wantsTrend(normalized)));
        }

        Optional<TrafficQueryType> regionalType = regionalType(normalized);
        if (regionalType.isPresent()) {
            List<String> cities = mentionedCities(normalized);
            String clarification = !cities.isEmpty() && cities.size() < 3
                    ? "区域交通联系分析至少需要三个城市，请再补充一个或多个福建地级市。"
                    : null;
            return Optional.of(decision(regionalType.get(), cities, null, clarification));
        }
        String roadRouteName = matchedRouteName(normalized, routes);
        if (roadRouteName != null && containsAny(normalized,
                "交通态势", "当前态势", "交通情况", "通行情况", "通行状态",
                "运行状态", "当前状态", "路况", "拥堵", "缓行", "畅通", "异常路段")) {
            return Optional.of(trafficDecision(TrafficQueryType.ROUTE_DETAIL, mentionedCities(normalized),
                    routeCode(normalized), roadRouteName, wantsTrend(normalized)));
        }
        Optional<TrafficQueryType> roadType = roadConditionType(normalized);
        if (roadType.isPresent()) {
            String routeName = roadRouteName;
            TrafficQueryType resolved = routeName == null ? roadType.get() : TrafficQueryType.ROUTE_DETAIL;
            return Optional.of(trafficDecision(resolved, mentionedCities(normalized),
                    routeCode(normalized), routeName, wantsTrend(normalized)));
        }
        return Optional.empty();
    }

    private static Optional<TrafficQueryType> capacityType(String text) {
        return capacityType(text, null);
    }

    private static Optional<TrafficQueryType> capacityType(String text, String routeName) {
        if (!containsAny(text, "通行能力", "能力利用率", "利用率", "能力瓶颈", "瓶颈路线", "严重瓶颈", "实际能力", "设计能力", "容量数据")) {
            return Optional.empty();
        }
        if (routeCode(text) != null || routeName != null) {
            return Optional.of(TrafficQueryType.CAPACITY_ROUTE_DETAIL);
        }
        if (containsAny(text, "哪些瓶颈", "能力瓶颈", "瓶颈路线", "严重瓶颈", "利用率最高", "能力压力最大", "瓶颈排行", "瓶颈排名",
                "高利用率", "利用率较高", "利用率偏高", "哪些路线利用率高")) {
            return Optional.of(TrafficQueryType.CAPACITY_BOTTLENECKS);
        }
        return Optional.of(TrafficQueryType.CAPACITY_OVERVIEW);
    }

    private static Optional<TrafficQueryType> odType(String text) {
        boolean explicit = Pattern.compile("(?<![a-z])od(?![a-z])").matcher(text).find()
                || containsAny(text, "目的地联系倾向", "目的地倾向", "出行目的地", "主要去哪些城市",
                "主要联系哪些城市", "去往哪些城市", "前往哪些城市", "主要去向", "出行去向",
                "目的地分布", "目的城市", "出行需求强度", "城市联系结构", "城市联系矩阵", "od矩阵");
        if (!explicit) return Optional.empty();
        List<String> cities = mentionedCities(text);
        boolean matrix = cities.size() != 1 || containsAny(text, "矩阵", "九市", "全省", "多城市", "各城市");
        return Optional.of(matrix ? TrafficQueryType.OD_CONNECTION_MATRIX : TrafficQueryType.OD_DESTINATION_TENDENCY);
    }

    private static boolean hasUnknownOdCity(String text) {
        String scope = text.replaceFirst("^(?:(?:请|帮我|你|帮忙|分析|查询|查看|对比|比较|看看|一下|统计|做|进行))+", "");
        Matcher matcher = Pattern.compile("(?:^|[和与、及至到，,])([一-龥]{2,8}?)(?:市)?(?=[和与、及至到，,]|之间|的(?:城市|关键)?od|od)").matcher(scope);
        while (matcher.find()) {
            String value = matcher.group(1).replaceFirst("(?:的|有哪些|有什么|当前|目前|关键|进行).*$", "");
            boolean endsInKnownCity = Arrays.stream(FujianCity.values()).anyMatch(c ->
                    value.endsWith(c.displayName()) || value.endsWith(c.displayName() + "市"));
            if (!endsInKnownCity && FujianCity.fromName(value).isEmpty()
                    && !containsAny(value, "福建", "全省", "城市", "两市", "多市", "通道", "流量", "车型", "客车", "货车", "车速")) {
                return true;
            }
        }
        return false;
    }

    private static Optional<TrafficQueryType> vehicleType(String text) {
        boolean vehicleContext = containsAny(text,
                "车型", "分车型", "小型客车", "中型客车", "大型货车", "小客车", "中客车", "货车");
        if (containsAny(text, "交通运输特征", "运输特征分析", "车型出行特征", "出行特征分析",
                "出行特征", "车型分析", "分车型分析")) {
            return Optional.of(TrafficQueryType.VEHICLE_PATTERN_OVERVIEW);
        }
        if (containsAny(text, "24小时", "二十四小时", "分时出行", "出行规律", "早高峰", "晚高峰", "峰值时段")
                && (vehicleContext || containsAny(text, "出行规律", "交通运输", "交通量", "通行量", "流量"))) {
            return Optional.of(TrafficQueryType.VEHICLE_HOURLY_PATTERN);
        }
        if (containsAny(text, "工作日", "周末", "周中", "双休日")
                && (vehicleContext || containsAny(text, "出行", "通行量", "交通运输"))) {
            return Optional.of(TrafficQueryType.VEHICLE_DAY_TYPE_COMPARISON);
        }
        if (containsAny(text, "车型结构", "车型构成", "车型占比", "车型分布", "各类车型", "分车型通行量")
                || (vehicleContext && containsAny(text, "占比", "比例", "分别多少"))) {
            return Optional.of(TrafficQueryType.VEHICLE_STRUCTURE);
        }
        return Optional.empty();
    }

    private static Optional<TrafficQueryType> regionalType(String text) {
        if (containsAny(text, "通行能力", "能力利用率", "瓶颈路线", "严重瓶颈")) {
            return Optional.empty();
        }
        boolean regional = containsAny(text, "区域交通联系", "跨区域交通联系", "跨市交通联系", "区域联系",
                "跨市通道", "城市对", "城市之间的联系", "多城市交通联系");
        boolean pressure = containsAny(text, "交通压力", "承担流量", "日均流量", "日总流量", "流量较大", "流量最大", "压力最大", "交通负荷", "排行", "排名", "top");
        if (!regional && !pressure) return Optional.empty();
        if (containsAny(text, "城市和国省道", "城市与国省道", "城市、路线", "城市和路线",
                "城市、卡口", "卡口、城市", "城市对、路线", "城市对和路线", "综合分析")) {
            return Optional.of(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW);
        }
        if (containsAny(text, "关键卡口", "通道卡口", "卡口枢纽", "交通枢纽", "哪些卡口", "卡口排行", "交调站")) {
            return Optional.of(TrafficQueryType.REGIONAL_KEY_CHANNELS);
        }
        if (containsAny(text, "哪些城市对", "哪个城市对", "城市对流量", "城市对压力", "城市之间压力",
                "哪些城市", "哪个城市", "各市", "各地市", "城市排行", "城市交通压力")) {
            return Optional.of(TrafficQueryType.REGIONAL_PAIR_PRESSURE);
        }
        if (containsAny(text, "主要通道", "重要通道", "关键通道", "哪些路线", "哪些道路", "跨市路线", "国省道", "路线排行", "路线日总流量")) {
            return Optional.of(TrafficQueryType.REGIONAL_KEY_CHANNELS);
        }
        return Optional.of(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW);
    }

    private static Optional<TrafficQueryType> roadConditionType(String text) {
        boolean roadContext = containsAny(text,
                "交通态势", "当前态势", "交通情况", "通行情况", "通行状态", "运行状态", "当前状态", "路况",
                "拥堵", "堵车", "为什么会堵", "拥堵原因", "节假日影响", "活动影响",
                "缓行", "畅通", "异常路段", "交通异常", "定性趋势");
        if (!roadContext) return Optional.empty();

        if (routeCode(text) != null) {
            return Optional.of(TrafficQueryType.ROUTE_DETAIL);
        }
        List<String> cities = mentionedCities(text);
        if (cities.size() == 2 && containsAny(text, "到", "至", "和", "与", "之间", "两地")) {
            return Optional.of(TrafficQueryType.CITY_PAIR);
        }
        if (containsAny(text,
                "哪些路段", "哪些道路拥堵", "拥堵异常", "异常状态", "异常路段", "最拥堵", "拥堵排行", "拥堵排名")) {
            return Optional.of(TrafficQueryType.PROVINCE_ABNORMAL);
        }
        if (containsAny(text, "福建省", "福建路况", "全省", "国省道", "整体", "总体", "目前", "当前态势", "定性趋势", "当前状态")) {
            return Optional.of(TrafficQueryType.PROVINCE_OVERVIEW);
        }
        return Optional.empty();
    }

    private static Optional<String> directAnswer(String text) {
        boolean hasTrafficObject = routeCode(text) != null || !mentionedCities(text).isEmpty()
                || containsAny(text, "福建路况", "全省路况", "哪些路段", "哪条路线");
        if (!hasTrafficObject && containsAny(text, "当前态势总结和未来1至2小时定性趋势放在同一段", "只给出定性趋势")) {
            return Optional.of("可以。当问题指向拥堵、异常或未来趋势时，结果会按要求合并展示当前态势与未来1至2小时定性趋势，或仅返回定性趋势；不编造未来具体数值和解除时间。");
        }
        if (!hasTrafficObject && containsAny(text, "现有数据无法支撑具体原因", "仍然准确说明当前状态和趋势")) {
            return Optional.of("可以。系统会基于已发布交通快照说明当前状态，并在时间及影响范围匹配时结合节假日或重大活动给出谨慎的原因提示，同时避免猜测事故、施工或天气原因。");
        }
        if (!hasTrafficObject && containsAny(text, "只有部分活动路线有容量数据", "有多少展示多少")) {
            return Optional.of("可以。通行能力结果只展示当前已有合法数据的活动路线，不补齐、不伪造其他路线。");
        }
        if (containsAny(text, "城市表标题请按实际展示城市数命名")) {
            return Optional.of("可以。城市压力表标题会按实际返回的城市数量显示，不使用与结果不符的固定标题。");
        }
        if (containsAny(text, "合并两市卡口统计") && containsAny(text, "不要把结果描述成真实od", "不描述成真实od")) {
            return Optional.of("可以明确区分：跨区域交通联系分析按路线起终点建立无方向城市对，需选择至少三个福建地级市；结果展示城市对和重要跨市路线，不描述为真实OD、净流入或净流出。");
        }
        if (containsAny(text, "正在更新但还没有完整数据", "请不要用不完整记录作答")) {
            return Optional.of("可以。请继续说明要查询的交通范围；系统只会使用已完整发布的交通快照回答。 ");
        }
        if (containsAny(text, "只根据数据库状态判断路况", "不要因为某个均速值异常就擅自改写状态")) {
            return Optional.of("可以。路况回答将以数据库status为权威状态，均速和拥堵指数只用于辅助排序和定性研判。 ");
        }
        if (containsAny(text, "缺少某些小时的时候，请补0", "缺少某些小时的时候请补0")) {
            return Optional.of("可以。24小时车型曲线会完整展示00:00至23:00，缺失小时按项目规则补0，并避免把补0直接描述为实际没有车辆。 ");
        }
        if (containsAny(text, "图表中的车型名称请使用", "不要直接显示car、bus、truck")) {
            return Optional.of("可以。车型图表统一显示“小型客车、中型客车、大型货车”，不向用户展示内部英文枚举名。 ");
        }
        if (containsAny(text, "拥堵状况指数", "拥堵指数0到1", "拥堵指数0至1")) {
            return Optional.of("拥堵状况指数取值为0至1，数值越大表示相对拥堵程度越高；具体路况状态仍以数据库status字段为权威，指数只作为排序和研判辅助。 ");
        }
        if (text.contains("利用率等于15%") || text.contains("利用率等于15％")) {
            return Optional.of("按当前项目口径，通行能力利用率等于15%时判定为正常；严格高于20%才进入瓶颈等级。 ");
        }
        if (text.contains("利用率等于20%") || text.contains("利用率等于20％")) {
            return Optional.of("按当前项目口径，通行能力利用率等于20%时仍判定为正常，严格高于20%才判定为瓶颈。 ");
        }
        if (text.contains("利用率等于30%") || text.contains("利用率等于30％")) {
            return Optional.of("按本项目口径，通行能力利用率等于30%时判定为瓶颈，严格高于30%才判定为严重瓶颈。 ");
        }
        if (containsAny(text, "实际通行能力为0", "实际能力为0")) {
            return Optional.of("实际通行能力为0是有效数据库值，应按0辆/小时展示；瓶颈等级采用数据库利用率并按项目阈值判定，不把0当作缺失值。 ");
        }
        if (containsAny(text, "解释实际通行能力", "实际通行能力、设计通行能力", "实际通行能力和设计通行能力")) {
            return Optional.of("实际通行能力是数据库记录的当前小时能力值，设计通行能力是路线设计能力，通行能力利用率用于反映二者在项目口径下的利用程度。本项目直接采用数据库结果，不重新估算。 ");
        }
        if (containsAny(text, "日均流量刚好为100", "日均流量等于100")) {
            return Optional.of("不算。按本项目口径，只有日均流量严格大于100的卡口才计为活跃卡口，等于100不计入。 ");
        }
        if (containsAny(text, "枢纽占比是如何统计", "枢纽占比如何统计", "枢纽占比怎么算")) {
            return Optional.of("城市交通枢纽占比按“该城市活跃卡口数÷当前所选范围内全部卡口数”统计；活跃卡口要求日均流量严格大于100。 ");
        }
        if (containsAny(text, "数据来源", "数据的新鲜度", "多久更新一次", "最新一批交通数据", "数据快照时间", "使用的是哪条最新记录", "创建时间是什么")) {
            return Optional.of("暂不支持查询交通数据来源、更新时间或底层记录元数据；您可以继续查询具体国省道、路段、通行能力或跨区域交通联系。 ");
        }
        if (containsAny(text, "有哪些与车速", "有哪些字段", "如何区分这些路段", "出现重复记录", "逻辑删除", "无效的交通记录",
                "没有数据，系统会怎样", "区分实时数据和历史数据", "是否已经接入天气", "名称是否一致", "正在更新但还没有完整数据")) {
            return Optional.of("暂不支持查询或核对交通知识库的字段结构、批次处理及内部数据治理规则；您可以改问具体国省道、路段、通行能力或交通压力。 ");
        }
        return Optional.empty();
    }

    private static AgentDecision directAnswerDecision(String answer) {
        return new AgentDecision("DIRECT_ANSWER", null, null, null, null, null,
                List.of(), null, null, null, null, null,
                null, null, null, null, List.of(), answer);
    }

    private static boolean isRouteCatalogQuestion(String text) {
        return containsAny(text, "覆盖了福建省哪些国道和省道", "列出当前知识库中有数据的国省道",
                "路线编号、路线名称和路段名称之间的对应关系", "路线编号路线名称和路段名称之间的对应关系");
    }

    private static String matchedRouteName(String text, List<HighwayRoute> routes) {
        if (routes == null || routes.isEmpty()) return null;
        String normalizedText = normalizeRouteName(text);
        List<String> names = routes.stream().map(HighwayRoute::routeName).distinct()
                .filter(name -> normalizedText.contains(normalizeRouteName(name)))
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();
        return names.isEmpty() ? null : names.get(0);
    }

    private static String normalizeRouteName(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Pd}\\s]", "").toLowerCase(Locale.ROOT);
    }

    private static boolean wantsTrend(String text) {
        return containsAny(text, "拥堵", "堵车", "异常", "趋势", "未来", "缓解", "加剧", "持续");
    }

    private static List<String> mentionedCities(String text) {
        return Arrays.stream(FujianCity.values())
                .filter(city -> text.contains(city.displayName()))
                .sorted(Comparator.comparingInt(city -> text.indexOf(city.displayName())))
                .map(FujianCity::displayName)
                .toList();
    }

    private static boolean containsAny(String text, String... values) {
        return Arrays.stream(values).anyMatch(text::contains);
    }

    private static String routeCode(String text) {
        Matcher matcher = ROUTE_CODE.matcher(text);
        return matcher.find() ? (matcher.group(1) + matcher.group(2)).toUpperCase(Locale.ROOT) : null;
    }

    private static AgentDecision trafficDecision(
            TrafficQueryType type,
            List<String> cities,
            String routeCode,
            String routeName,
            boolean includeTrend
    ) {
        boolean cityBound = type == TrafficQueryType.CITY_PAIR || type.capacityQuery();
        String origin = cityBound && cities.size() == 2 ? cities.get(0) : null;
        String destination = cityBound && cities.size() == 2 ? cities.get(1) : null;
        return new AgentDecision(
                "TRAFFIC_QUERY", type.name(), origin, destination, routeCode, routeName,
                List.of(), null, null, null, null, null,
                null, null, null, null, List.of(), null, includeTrend
        );
    }

    private static AgentDecision decision(
            TrafficQueryType type,
            List<String> selectedCities,
            String analysisCity,
            String clarification
    ) {
        return decision(type, selectedCities, analysisCity, clarification, null);
    }

    private static AgentDecision decision(
            TrafficQueryType type,
            List<String> selectedCities,
            String analysisCity,
            String clarification,
            String analysisDate
    ) {
        return new AgentDecision(
                "TRAFFIC_QUERY", type.name(), null, null, null, null,
                selectedCities, analysisCity, null, null, null, null,
                null, null, null, null, List.of(), clarification, false, analysisDate
        );
    }
}
