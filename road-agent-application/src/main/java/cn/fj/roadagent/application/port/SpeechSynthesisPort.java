package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.speech.SpeechAudio;
import cn.fj.roadagent.application.speech.SynthesizeSpeechCommand;

public interface SpeechSynthesisPort {
    SpeechAudio synthesize(SynthesizeSpeechCommand command);
    default void stream(SynthesizeSpeechCommand command, java.io.OutputStream output) throws java.io.IOException {
        throw new UnsupportedOperationException("Streaming speech unavailable");
    }
}
