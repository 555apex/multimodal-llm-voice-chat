package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.speech.SpeechProviderCapabilities;

public interface SpeechCapabilityPort {
    SpeechProviderCapabilities queryCapabilities();
}
