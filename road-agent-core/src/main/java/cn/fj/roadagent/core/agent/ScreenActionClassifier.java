package cn.fj.roadagent.core.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only explicit, allowlisted screen commands may leave the agent as executable actions. */
final class ScreenActionClassifier {
    private static final Pattern MONITOR_AFTER = Pattern.compile("(?:打开|调出|播放|查看)(.{1,40}?)(?:监控|摄像头|视频)(?:画面|界面)?$");
    private static final Pattern MONITOR_BEFORE = Pattern.compile("(?:打开|调出|播放|查看)(?:监控|摄像头|视频)(?:画面)?(?:中|里|的)?(.{1,40})$");
    private static final Pattern LONGITUDE = Pattern.compile("(?:经度|lng)[:：]?(-?\\d{1,3}(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LATITUDE = Pattern.compile("(?:纬度|lat)[:：]?(-?\\d{1,2}(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> RESOURCES = Map.ofEntries(
            Map.entry("预警", "warningmarker"), Map.entry("路线", "route"),
            Map.entry("桥梁", "bridge"), Map.entry("隧道", "tunnel"),
            Map.entry("边坡", "slope"), Map.entry("服务区", "servicearea"),
            Map.entry("视频监控", "video"), Map.entry("情报板", "board"),
            Map.entry("交调站", "traffic"), Map.entry("机构", "organization"),
            Map.entry("应急仓库", "warehouse"), Map.entry("应急队伍", "team")
    );

    private ScreenActionClassifier() { }

    static Optional<Decision> classify(String message) {
        if (message == null || message.isBlank()) return Optional.empty();
        String text = message.replaceAll("[\\s“”‘’「」『』。！？?!]+", "")
                .replace("\"", "").replace("'", "");
        if (text.length() > 120) return Optional.empty();
        if (isCapabilityQuestion(text)) {
            return Optional.of(new Decision(null, Map.of(),
                    "我可以识别并提交大屏跳转指令，例如打开指定监控、现场指挥或物资界面。"
                            + "实际跳转需要当前页面接入大屏方的操作回调；直接打开私有域名的普通网页通常没有这个回调，"
                            + "可在已接入的大屏页面验证。"));
        }
        // Explanatory, permission and negated questions are not execution orders.
        if (text.matches(".*(?:如何|怎么|怎样|为什么|是否|能否|可不可以|可以吗|不要|别|无需|不必|禁止).*")) {
            return Optional.empty();
        }

        if (text.matches(".*(?:打开|进入|调出|切换到)(?:大屏)?(?:叫应|现场指挥)(?:界面|页面|系统)?.*")) {
            return Optional.of(action("OPEN_COMMAND", Map.of()));
        }
        if (text.matches(".*(?:打开|进入|调出|切换到)(?:大屏)?(?:物资|物资入口|物资界面|保障工作手册)(?:入口|界面|页面)?.*")) {
            return Optional.of(action("OPEN_MATERIALS", Map.of()));
        }
        if (text.matches(".*(?:搜索|查询|查找|显示)(?:这个|该|指定)?(?:位置|坐标|地点)?(?:附近|周边)(?:的)?(?:应急)?资源.*")
                || text.matches(".*(?:搜索|查询|查找)(?:应急)?资源(?:附近|周边).*")) {
            Matcher lng = LONGITUDE.matcher(text);
            Matcher lat = LATITUDE.matcher(text);
            if (!lng.find() || !lat.find()) {
                return Optional.of(new Decision(null, Map.of(), "请提供与大屏地图坐标系一致的经度和纬度，才能搜索周边资源。"));
            }
            double longitude = Double.parseDouble(lng.group(1));
            double latitude = Double.parseDouble(lat.group(1));
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                    || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                return Optional.of(new Decision(null, Map.of(), "经纬度超出有效范围，请核对后重试。"));
            }
            return Optional.of(action("SEARCH_NEARBY_RESOURCES", Map.of("lng", longitude, "lat", latitude)));
        }

        Matcher namedMonitor = MONITOR_AFTER.matcher(text);
        if (!namedMonitor.find()) namedMonitor = MONITOR_BEFORE.matcher(text);
        if (namedMonitor.find(0)) {
            String name = namedMonitor.group(1).replaceAll("^(?:大屏|的)", "").trim();
            if (!name.isEmpty() && !name.matches(".*(?:哪些|多少|如何|情况|列表).*")) {
                return Optional.of(action("OPEN_MONITOR", Map.of("name", name)));
            }
        }
        if (text.matches(".*(?:打开|调出|播放|查看)(?:监控|摄像头|视频)(?:画面|界面)?$")) {
            return Optional.of(new Decision(null, Map.of(), "请指定明确的监控设备名称。"));
        }
        if (!text.matches(".*(?:地图|点位|资源|显示|隐藏|开启|关闭).*")) return Optional.empty();
        if (text.matches(".*(?:蜂鸣|闪烁|全部播放|全部资源|重点桥梁|重点隧道|重点边坡).*")) {
            return Optional.of(new Decision(null, Map.of(), "该大屏操作暂未开放，请选择具体的普通地图资源。"));
        }
        LinkedHashMap<String, Boolean> states = new LinkedHashMap<>();
        Boolean currentState = null;
        for (String clause : text.split("[，,；;。]")) {
            if (clause.matches(".*(?:隐藏|关闭|取消显示|不显示).*")) currentState = false;
            else if (clause.matches(".*(?:显示|开启|打开|展示).*")) currentState = true;
            if (currentState == null) continue;
            for (var entry : RESOURCES.entrySet()) {
                if (clause.contains(entry.getKey())) states.put(entry.getValue(), currentState);
            }
        }
        if (!states.isEmpty()) {
            List<Map<String, Object>> items = new ArrayList<>();
            states.forEach((resource, enabled) -> items.add(Map.of("resource", resource, "enabled", enabled)));
            return Optional.of(action("SET_MAP_RESOURCES", Map.of("items", items)));
        }

        return Optional.empty();
    }

    private static Decision action(String name, Map<String, Object> params) {
        return new Decision(name, params, null);
    }

    private static boolean isCapabilityQuestion(String text) {
        return text.matches(".*(?:大屏跳转|大屏.*(?:操作|调度|控制)|(?:监控|叫应|物资).*跳转).*")
                && text.matches(".*(?:是否|能否|能不能|有没有|有无|具备吗|支持吗|可以吗|能吗|你.*支持.*吗|你.*具备.*吗).*");
    }

    record Decision(String action, Map<String, Object> params, String clarification) { }
}
