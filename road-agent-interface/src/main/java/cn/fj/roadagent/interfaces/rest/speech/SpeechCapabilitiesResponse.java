package cn.fj.roadagent.interfaces.rest.speech;

import cn.fj.roadagent.application.speech.SpeechCapabilities;

public record SpeechCapabilitiesResponse(
        boolean asrAvailable,
        boolean ttsAvailable,
        String asrModel,
        String ttsVoice,
        int maxRecordingSeconds,
        long maxAudioBytes,
        boolean ttsStreamingAvailable
) {
    static SpeechCapabilitiesResponse from(SpeechCapabilities capabilities) {
        return new SpeechCapabilitiesResponse(
                capabilities.asrAvailable(),
                capabilities.ttsAvailable(),
                capabilities.asrModel(),
                capabilities.ttsVoice(),
                capabilities.maxRecordingSeconds(),
                capabilities.maxAudioBytes(),
                capabilities.ttsStreamingAvailable()
        );
    }
}
