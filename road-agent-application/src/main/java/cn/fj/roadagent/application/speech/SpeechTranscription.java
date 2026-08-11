package cn.fj.roadagent.application.speech;

public record SpeechTranscription(String text, String language, long durationMs) {
    public SpeechTranscription {
        text = text == null ? "" : text.trim();
        language = language == null ? "" : language.trim();
    }
}
