package cn.fj.roadagent.application.speech;

public interface SynthesizeSpeechUseCase {
    SpeechAudio synthesize(SynthesizeSpeechCommand command);
}
