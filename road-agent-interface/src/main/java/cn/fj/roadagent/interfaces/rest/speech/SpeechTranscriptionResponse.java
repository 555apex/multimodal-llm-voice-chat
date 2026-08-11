package cn.fj.roadagent.interfaces.rest.speech;

import cn.fj.roadagent.application.speech.SpeechTranscription;

public record SpeechTranscriptionResponse(String text, String language, long durationMs) {
    static SpeechTranscriptionResponse from(SpeechTranscription transcription) {
        return new SpeechTranscriptionResponse(
                transcription.text(), transcription.language(), transcription.durationMs()
        );
    }
}
