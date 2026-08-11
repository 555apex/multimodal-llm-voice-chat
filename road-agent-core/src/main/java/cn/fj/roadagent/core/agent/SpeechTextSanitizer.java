package cn.fj.roadagent.core.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 无结构化朗读投影时的保守降级：保留正文，跳过表格、代码和裸链接。 */
final class SpeechTextSanitizer {
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)]\\([^)]*\\)");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern MARKDOWN_SYMBOL = Pattern.compile("[*_`#>]");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^[| :\\-]+$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(?:[-+•]|\\d+[.)、])\\s+.*$");

    private SpeechTextSanitizer() {
    }

    static String toSpeakableText(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        boolean inCodeBlock = false;
        boolean omittedStructuredContent = false;
        List<String> spokenLines = new ArrayList<>();
        List<String> sourceLines = source.lines().toList();
        boolean omitLongList = sourceLines.stream()
                .map(String::trim)
                .filter(SpeechTextSanitizer::isListItem)
                .count() > 3;
        for (String rawLine : sourceLines) {
            String line = rawLine.trim();
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                omittedStructuredContent = true;
                continue;
            }
            if (inCodeBlock || line.isBlank()) {
                continue;
            }
            if (isTableLine(line)) {
                omittedStructuredContent = true;
                continue;
            }
            if (omitLongList && isListItem(line)) {
                omittedStructuredContent = true;
                continue;
            }
            String cleaned = MARKDOWN_LINK.matcher(line).replaceAll("$1");
            cleaned = URL.matcher(cleaned).replaceAll("");
            cleaned = MARKDOWN_SYMBOL.matcher(cleaned).replaceAll("");
            cleaned = cleaned.replaceFirst("^[-+•]\\s+", "");
            cleaned = cleaned.replaceFirst("^\\d+[.)、]\\s*", "");
            cleaned = cleaned.replaceAll("\\s+", " ").trim();
            if (!cleaned.isBlank()) {
                spokenLines.add(cleaned);
            }
        }
        String result = String.join("。", spokenLines)
                .replaceAll("。{2,}", "。")
                .trim();
        if (!result.isBlank() && !result.matches(".*[。！？；]$")) {
            result += "。";
        }
        if (omittedStructuredContent && !result.endsWith("详细数据请查看页面。")) {
            result += "详细数据请查看页面。";
        }
        return result;
    }

    private static boolean isTableLine(String line) {
        if (TABLE_SEPARATOR.matcher(line).matches()) {
            return true;
        }
        return line.startsWith("|") && line.endsWith("|") && line.chars().filter(c -> c == '|').count() >= 2;
    }

    private static boolean isListItem(String line) {
        return LIST_ITEM.matcher(line).matches();
    }
}
