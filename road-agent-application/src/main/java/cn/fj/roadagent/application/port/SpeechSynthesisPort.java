package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.speech.SpeechAudio;
import cn.fj.roadagent.application.speech.SynthesizeSpeechCommand;

public interface SpeechSynthesisPort {
    SpeechAudio synthesize(SynthesizeSpeechCommand command);
}
