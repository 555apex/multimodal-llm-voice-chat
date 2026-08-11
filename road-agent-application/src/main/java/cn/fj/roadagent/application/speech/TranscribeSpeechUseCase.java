package cn.fj.roadagent.application.speech;

public interface TranscribeSpeechUseCase {
    SpeechTranscription transcribe(TranscribeSpeechCommand command);
}
