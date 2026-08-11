package cn.fj.roadagent.application.speech;

/** 前端需要的语音能力及上传限制。 */
public record SpeechCapabilities(
        boolean asrAvailable,
        boolean ttsAvailable,
        String asrModel,
        String ttsVoice,
        int maxRecordingSeconds,
        long maxAudioBytes
) {
}
