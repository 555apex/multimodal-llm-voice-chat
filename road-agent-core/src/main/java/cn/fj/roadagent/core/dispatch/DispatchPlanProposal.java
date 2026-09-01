package cn.fj.roadagent.core.dispatch;

import java.util.List;
import java.util.Map;

/** 模型只提出数据库资源类型白名单内的需求和处置文本。 */
public record DispatchPlanProposal(
        List<ProposedResource> resourceRequirements,
        Object rescuePlan
) {
    public DispatchPlanProposal {
        resourceRequirements = resourceRequirements == null
                ? List.of() : List.copyOf(resourceRequirements);
    }

    /**
     * 首选严格字符串；兼容部分模型将同一救援方案按自然章节返回为JSON对象。
     * 这里只做确定性文本投影，不接受数组、嵌套对象或其他不受控结构。
     */
    public String rescuePlanText() {
        if (rescuePlan instanceof String text) {
            return requireText(text, "rescuePlan不能为空");
        }
        if (!(rescuePlan instanceof Map<?, ?> sections)) {
            throw new IllegalArgumentException("rescuePlan必须是字符串或由文本章节组成的对象");
        }
        if (sections.isEmpty() || sections.size() > 12) {
            throw new IllegalArgumentException("rescuePlan分段数量必须在1到12之间");
        }
        StringBuilder normalized = new StringBuilder();
        for (Map.Entry<?, ?> section : sections.entrySet()) {
            if (!(section.getKey() instanceof String heading)
                    || !(section.getValue() instanceof String content)) {
                throw new IllegalArgumentException("rescuePlan分段标题和内容必须都是字符串");
            }
            heading = requireText(heading, "rescuePlan分段标题不能为空");
            content = requireText(content, "rescuePlan分段内容不能为空");
            if (heading.length() > 40 || content.length() > 4000) {
                throw new IllegalArgumentException("rescuePlan分段标题或内容过长");
            }
            if (!normalized.isEmpty()) {
                normalized.append('\n');
            }
            normalized.append(heading).append('：').append(content);
        }
        return normalized.toString();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public record ProposedResource(
            String resourceTypeCode,
            int quantity,
            String purpose
    ) {
        /** 兼容旧测试构造；正式模型响应只使用编码、数量和用途。 */
        public ProposedResource(
                String resourceType,
                String ignoredResourceName,
                int quantity,
                String ignoredUnit,
                String purpose
        ) {
            this(resourceType, quantity, purpose);
        }
    }
}
