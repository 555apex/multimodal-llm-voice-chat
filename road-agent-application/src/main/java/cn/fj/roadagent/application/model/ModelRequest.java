package cn.fj.roadagent.application.model;

import java.util.List;

/**
 * 与模型厂商无关的请求。history由我方保存，不能依赖模型服务替我们记住会话。
 */
public record ModelRequest(
        String systemPrompt,
        String userPrompt,
        List<ModelMessage> history,
        double temperature,
        int maxOutputTokens
) {
    public ModelRequest {
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
        history = history == null ? List.of() : List.copyOf(history);
        if (maxOutputTokens < 1) throw new IllegalArgumentException("maxOutputTokens must be positive");
    }

    public ModelRequest(String systemPrompt, String userPrompt, List<ModelMessage> history, double temperature) {
        this(systemPrompt, userPrompt, history, temperature, 512);
    }

    // 兼容原交通查询代码：没有历史消息时仍可使用三个参数构造。
    public ModelRequest(String systemPrompt, String userPrompt, double temperature) {
        this(systemPrompt, userPrompt, List.of(), temperature);
    }
}
