package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

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
        if (message == null || message.isBlank()) return Optional.empty();
        String normalized = message.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "事故", "塌方", "水毁", "应急调度", "救援", "物资调度")) {
            return Optional.empty();
        }

        // 否定或纠正表达交由理解完整句义的规划器，不能仅凭关键词抢占业务。
        if (containsAny(normalized, "不要", "不是", "不看", "不需要", "而是", "别分析", "别看", "不做od", "无需od")) return Optional.empty();
        Optional<TrafficQueryType> odType = odType(normalized);
        if (odType.isPresent()) {
            List<String> cities = mentionedCities(normalized);
            String clarification = null;
            if (containsAny(normalized, "上个月", "上月", "去年", "上周", "指定日期", "昨天", "前天", "未来", "预测")
                    || normalized.matches(".*20\\d{2}[-年/]\\d{1,2}.*")
                    || normalized.matches(".*\\d{1,2}月\\d{1,2}日.*")) {
                clarification = "目前可查询数据库最新7天统计，是否改为该统计范围？";
            } else if (containsAny(normalized, "真实od", "实际od", "净流入", "净流出", "驶往", "车辆来源", "车辆去向", "实际流向")) {
                clarification = "本项分析按所选城市卡口汇总，不区分车辆方向，是否查看城市七日流量与关键通道统计？";
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
            String clarification = cities.size() == 1
                    ? null : "请选择福州市或厦门市中的一个城市进行车型出行特征分析。";
            return Optional.of(decision(vehicleType.get(), cities, analysisCity, clarification));
        }

        Optional<TrafficQueryType> capacityType = capacityType(normalized);
        if (capacityType.isPresent()) {
            return Optional.of(trafficDecision(capacityType.get(), mentionedCities(normalized), routeCode(normalized)));
        }

        Optional<TrafficQueryType> regionalType = regionalType(normalized);
        if (regionalType.isPresent()) {
            return Optional.of(decision(regionalType.get(), mentionedCities(normalized), null, null));
        }
        Optional<TrafficQueryType> roadType = roadConditionType(normalized);
        if (roadType.isPresent()) {
            return Optional.of(trafficDecision(roadType.get(), mentionedCities(normalized), routeCode(normalized)));
        }
        return Optional.empty();
    }

    private static Optional<TrafficQueryType> capacityType(String text) {
        if (!containsAny(text, "通行能力", "能力利用率", "利用率", "瓶颈路线", "严重瓶颈", "实际能力", "设计能力")) {
            return Optional.empty();
        }
        if (routeCode(text) != null) {
            return Optional.of(TrafficQueryType.CAPACITY_ROUTE_DETAIL);
        }
        if (containsAny(text, "哪些瓶颈", "瓶颈路线", "严重瓶颈", "利用率最低", "能力最低", "瓶颈排行", "瓶颈排名",
                "低利用率", "利用率较低", "利用率偏低", "哪些路线利用率低")) {
            return Optional.of(TrafficQueryType.CAPACITY_BOTTLENECKS);
        }
        return Optional.of(TrafficQueryType.CAPACITY_OVERVIEW);
    }

    private static Optional<TrafficQueryType> odType(String text) {
        boolean explicit = Pattern.compile("(?<![a-z])od(?![a-z])").matcher(text).find()
                || containsAny(text, "区域流量不平衡", "城市流量不平衡");
        boolean week = containsAny(text, "7天", "七天", "7日", "七日", "近一周");
        boolean cityFlow = containsAny(text, "城市", "各市", "九市", "全省")
                || !mentionedCities(text).isEmpty();
        if (!explicit && !(week && cityFlow && text.contains("流量")
                && !containsAny(text, "车型占比", "车型结构", "出行规律", "工作日", "周末"))) return Optional.empty();
        boolean channel = containsAny(text, "通道", "路线", "国省道", "车型", "货车", "客车")
                || routeCode(text) != null;
        boolean city = containsAny(text, "不平衡", "均衡", "城市流量", "各市流量", "城市区域流量", "流量分布");
        if (containsAny(text, "综合", "全面") || (channel && city)) return Optional.of(TrafficQueryType.OD_OVERVIEW);
        if (channel) return Optional.of(TrafficQueryType.OD_KEY_CHANNELS);
        if (city || !explicit) return Optional.of(TrafficQueryType.OD_CITY_FLOW);
        return Optional.of(TrafficQueryType.OD_OVERVIEW);
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
        if (containsAny(text, "工作日", "周末", "周中", "双休日")
                && (vehicleContext || containsAny(text, "出行", "通行量", "交通运输"))) {
            return Optional.of(TrafficQueryType.VEHICLE_DAY_TYPE_COMPARISON);
        }
        if (containsAny(text, "24小时", "二十四小时", "分时出行", "出行规律", "早高峰", "晚高峰", "峰值时段")
                && (vehicleContext || containsAny(text, "出行规律", "交通运输", "交通量", "通行量", "流量"))) {
            return Optional.of(TrafficQueryType.VEHICLE_HOURLY_PATTERN);
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
        boolean pressureContext = containsAny(text,
                "交通压力", "日均流量", "日总流量", "承担流量", "流量较大", "活跃卡口",
                "流量最大", "压力最大", "交通负荷",
                "排名", "排行", "top", "前20", "前10", "前5", "统计", "分析", "情况");
        boolean checkpoint = containsAny(text,
                "哪些卡口", "卡口枢纽", "卡口交通压力", "卡口日均流量", "卡口排行", "卡口top",
                "卡口情况", "交调站", "交通枢纽");
        boolean city = containsAny(text,
                "哪些城市", "哪个城市", "哪个市", "各城市", "各市", "各地市", "地级市", "城市交通压力", "地市交通压力",
                "城市流量", "城市排行", "活跃卡口数");
        boolean route = containsAny(text,
                "哪些国省道", "哪些路线", "哪些道路", "哪条路线", "哪条道路", "路线交通压力",
                "道路交通压力", "国省道日均流量", "路线日总流量", "路线流量", "路线排行");
        int dimensions = (checkpoint ? 1 : 0) + (city ? 1 : 0) + (route ? 1 : 0);

        if (containsAny(text, "区域交通联系", "区域交通压力", "交通压力分布")
                || (text.contains("交通联系") && !mentionedCities(text).isEmpty())
                || (pressureContext && dimensions > 1)) {
            return Optional.of(TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW);
        }
        if (pressureContext && checkpoint) return Optional.of(TrafficQueryType.CHECKPOINT_PRESSURE);
        if (pressureContext && city) return Optional.of(TrafficQueryType.CITY_PRESSURE);
        if (pressureContext && route) return Optional.of(TrafficQueryType.ROUTE_PRESSURE);
        return Optional.empty();
    }

    private static Optional<TrafficQueryType> roadConditionType(String text) {
        boolean roadContext = containsAny(text,
                "交通态势", "交通情况", "通行情况", "通行状态", "路况", "拥堵", "缓行", "畅通", "异常路段", "交通异常");
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
        if (containsAny(text, "福建省", "全省", "国省道", "整体", "总体", "目前")) {
            return Optional.of(TrafficQueryType.PROVINCE_OVERVIEW);
        }
        return Optional.empty();
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
            String routeCode
    ) {
        String origin = type == TrafficQueryType.CITY_PAIR && cities.size() == 2 ? cities.get(0) : null;
        String destination = type == TrafficQueryType.CITY_PAIR && cities.size() == 2 ? cities.get(1) : null;
        return new AgentDecision(
                "TRAFFIC_QUERY", type.name(), origin, destination, routeCode, null,
                List.of(), null, null, null, null, null,
                null, null, null, null, List.of(), null
        );
    }

    private static AgentDecision decision(
            TrafficQueryType type,
            List<String> selectedCities,
            String analysisCity,
            String clarification
    ) {
        return new AgentDecision(
                "TRAFFIC_QUERY", type.name(), null, null, null, null,
                selectedCities, analysisCity, null, null, null, null,
                null, null, null, null, List.of(), clarification
        );
    }
}
