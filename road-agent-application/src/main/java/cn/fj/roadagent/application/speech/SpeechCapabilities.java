package cn.fj.roadagent.application.speech;

/** 前端需要的语音能力及上传限制。 */
public record SpeechCapabilities(
        boolean asrAvailable,
        boolean ttsAvailable,
        String asrModel,
        String ttsVoice,
        int maxRecordingSeconds,
        long maxAudioBytes,
        boolean ttsStreamingAvailable,
        String ttsEngine,
        String ttsModel,
        int ttsSampleRate
) {
    public SpeechCapabilities(boolean asr, boolean tts, String model, String voice, int seconds, long bytes) {
        this(asr, tts, model, voice, seconds, bytes, false, null, null, 0);
    }
}
