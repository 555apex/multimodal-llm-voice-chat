package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyResponsePlanSnapshot;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 以已发布预案为主体，只允许模型填充模板变量和附加建议。 */
final class ResponsePlanRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("【([^\u3010\u3011]+)】");

    String render(
            EmergencyResponsePlanSnapshot plan,
            EmergencyEvent event,
            Map<String, String> modelVariables,
            String supplementalAdvice
    ) {
        if (plan == null) throw new IllegalArgumentException("工单缺少应急预案快照");
        Map<String, String> variables = modelVariables == null ? Map.of() : modelVariables;
        Set<String> allowed = placeholders(plan.rescuePlanTemplate());
        boolean concise = allowed.contains("现场事实简述");
        for (Map.Entry<String, String> item : variables.entrySet()) {
            if (!allowed.contains(item.getKey())) {
                throw new IllegalArgumentException("模型返回了预案不存在的填充项：" + item.getKey());
            }
            if (item.getValue() != null && item.getValue().length() > (concise ? 50 : 500)) {
                throw new IllegalArgumentException("预案填充项过长：" + item.getKey());
            }
        }
        Matcher matcher = PLACEHOLDER.matcher(plan.rescuePlanTemplate());
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String value = systemValue(key, event);
            if (value == null) value = normalize(variables.get(key));
            if (concise && (value == null || value.equals("待核实") || value.equals("未知"))) {
                value = "现场影响范围及人员情况尚待核实";
            }
            if (value == null) value = "待核实";
            // 变量末尾的句读由模板负责，避免“。。”或“。，”；不触碰数字与桩号。
            int end = matcher.end();
            if (end < plan.rescuePlanTemplate().length()
                    && "，。；：！？".indexOf(plan.rescuePlanTemplate().charAt(end)) >= 0) {
                value = value.replaceAll("[，。；：！？]+$", "");
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output);
        String advice = normalize(supplementalAdvice);
        if (advice != null) {
            if (advice.length() > (concise ? 50 : 1500)) throw new IllegalArgumentException("模型补充建议超过长度限制");
            output.append("\n\n补充建议：").append(advice);
        }
        String rendered = output.toString().trim()
                .replaceAll("([，。；：！？])\\1+", "$1")
                .replaceAll("[，；：]+([。！？])", "$1")
                .replaceAll("([。！？])[，；：]+", "$1");
        if (concise) {
            int length = rendered.replaceAll("\\s", "").length();
            if (length < 300 || length > 400) throw new IllegalArgumentException("救援方案正文需为300至400字，当前为" + length + "字");
        }
        return rendered;
    }

    Set<String> placeholders(String template) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) result.add(matcher.group(1).trim());
        return Set.copyOf(result);
    }

    private String systemValue(String key, EmergencyEvent event) {
        if ("事件编号".equals(key)) {
            return event.customId().isBlank() ? event.eventId() : event.customId();
        }
        if ("数据库分配资源".equals(key)) return "已匹配资源清单所列力量";
        if (key.contains("事件地点") || key.equals("地点") || key.startsWith("地点/")
                || key.startsWith("路段/") || key.equals("塌陷点")) {
            return location(event);
        }
        return null;
    }

    private String location(EmergencyEvent event) {
        StringBuilder value = new StringBuilder();
        append(value, event.cityName());
        append(value, event.place());
        append(value, event.routeName() == null ? event.routeNo() : event.routeName());
        return value.isEmpty() ? "待核实事发地点" : value.toString();
    }

    private void append(StringBuilder value, String part) {
        if (part == null || part.isBlank() || value.toString().contains(part.trim())) return;
        if (!value.isEmpty()) value.append("、");
        value.append(part.trim());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
