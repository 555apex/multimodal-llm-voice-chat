package cn.fj.roadagent.application.speech;

public interface SynthesizeSpeechUseCase {
    SpeechAudio synthesize(SynthesizeSpeechCommand command);
    default void cancel(String requestId) { }
    default void stream(SynthesizeSpeechCommand command, java.io.OutputStream output) throws java.io.IOException {
        throw new UnsupportedOperationException("Streaming speech unavailable");
    }
}
