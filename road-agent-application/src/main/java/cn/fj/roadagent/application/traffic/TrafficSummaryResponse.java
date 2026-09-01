package cn.fj.roadagent.application.traffic;

/** 模型必须严格返回的交通摘要结构。 */
public record TrafficSummaryResponse(String summary) {
    public TrafficSummaryResponse {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("模型交通摘要不能为空");
        }
        summary = summary.trim();
        long sentenceCount = summary.chars()
                .filter(value -> value == '。' || value == '！' || value == '？' || value == '!' || value == '?')
                .count();
        // Prompt仍要求3至5句、80至600字；这里保留更宽的安全边界，避免模型仅因
        // 标点或少量字数偏差导致已经生成的业务事实、表格和图表全部无法返回。
        if (sentenceCount < 2 || sentenceCount > 6 || summary.length() < 50 || summary.length() > 800) {
            throw new IllegalArgumentException("模型交通摘要必须是2至6句、50至800字的中文研判");
        }
        boolean containsChinese = summary.codePoints()
                .anyMatch(value -> Character.UnicodeScript.of(value) == Character.UnicodeScript.HAN);
        if (!containsChinese) {
            throw new IllegalArgumentException("模型交通摘要必须使用中文");
        }
    }
}
