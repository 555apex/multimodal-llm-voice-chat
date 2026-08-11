package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.speech.SpeechTranscription;
import cn.fj.roadagent.application.speech.TranscribeSpeechCommand;

public interface SpeechRecognitionPort {
    SpeechTranscription transcribe(TranscribeSpeechCommand command);
}
