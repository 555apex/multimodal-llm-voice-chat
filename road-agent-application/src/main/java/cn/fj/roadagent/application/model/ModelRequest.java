package cn.fj.roadagent.application.model;

public record ModelRequest(
        String systemPrompt,
        String userPrompt,
        double temperature
) {
}
