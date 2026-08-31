package cn.fj.roadagent.core.traffic;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 防止模型在摘要或逐行解读中补充结构化事实之外的阿拉伯数字。 */
final class ModelFactNumberValidator {
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![A-Za-z])(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?%?"
    );

    private ModelFactNumberValidator() {
    }

    static void validate(String modelText, String serializedFacts) {
        Set<String> allowed = extract(serializedFacts);
        for (String value : extract(modelText)) {
            if (!allowed.contains(value)) {
                throw new IllegalArgumentException("模型摘要引用了结构化事实之外的数值：" + value);
            }
        }
    }

    private static Set<String> extract(String value) {
        Set<String> result = new HashSet<>();
        if (value == null || value.isBlank()) return result;
        Matcher matcher = NUMBER.matcher(value);
        while (matcher.find()) {
            result.add(normalize(matcher.group()));
        }
        return result;
    }

    private static String normalize(String raw) {
        boolean percentage = raw.endsWith("%");
        String numeric = raw.replace(",", "").replace("%", "");
        String normalized = new BigDecimal(numeric).stripTrailingZeros().toPlainString();
        return percentage ? normalized + "%" : normalized;
    }
}
