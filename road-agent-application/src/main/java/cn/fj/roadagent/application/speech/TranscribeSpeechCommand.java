package cn.fj.roadagent.application.speech;

import java.util.Arrays;

public record TranscribeSpeechCommand(
        byte[] audio,
        String contentType,
        String fileName,
        long declaredDurationMs
) {
    public TranscribeSpeechCommand {
        audio = audio == null ? new byte[0] : Arrays.copyOf(audio, audio.length);
        contentType = contentType == null ? "" : contentType.trim();
        fileName = fileName == null || fileName.isBlank() ? "recording" : fileName.trim();
    }

    @Override
    public byte[] audio() {
        return Arrays.copyOf(audio, audio.length);
    }
}
