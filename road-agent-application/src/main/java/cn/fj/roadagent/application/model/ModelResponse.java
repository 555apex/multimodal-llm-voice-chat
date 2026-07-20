package cn.fj.roadagent.application.model;

public record ModelResponse(String content, String provider, String model) {

    public ModelResponse {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("模型返回内容不能为空");
        }
        content = content.trim();
        provider = provider == null ? "UNKNOWN" : provider;
        model = model == null ? "UNKNOWN" : model;
    }
}
