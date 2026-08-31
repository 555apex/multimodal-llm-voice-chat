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
