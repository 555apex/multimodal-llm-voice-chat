package cn.fj.roadagent.application.speech;

/** 语音提供方当前可用能力，不暴露具体HTTP协议。 */
public record SpeechProviderCapabilities(
        boolean asrAvailable,
        boolean ttsAvailable,
        String asrModel,
        String ttsVoice
) {
}
