package cn.fj.roadagent.application.speech;

import java.util.Arrays;

public record SpeechAudio(byte[] content, String contentType) {
    public SpeechAudio {
        content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
        contentType = contentType == null || contentType.isBlank()
                ? "audio/mpeg" : contentType.trim();
    }

    @Override
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
