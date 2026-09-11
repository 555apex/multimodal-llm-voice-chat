package cn.fj.roadagent.core.speech;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.SpeechCapabilityPort;
import cn.fj.roadagent.application.port.SpeechRecognitionPort;
import cn.fj.roadagent.application.port.SpeechSynthesisPort;
import cn.fj.roadagent.application.speech.QuerySpeechCapabilitiesUseCase;
import cn.fj.roadagent.application.speech.SpeechAudio;
import cn.fj.roadagent.application.speech.SpeechCapabilities;
import cn.fj.roadagent.application.speech.SpeechProviderCapabilities;
import cn.fj.roadagent.application.speech.SpeechTranscription;
import cn.fj.roadagent.application.speech.SynthesizeSpeechCommand;
import cn.fj.roadagent.application.speech.SynthesizeSpeechUseCase;
import cn.fj.roadagent.application.speech.TranscribeSpeechCommand;
import cn.fj.roadagent.application.speech.TranscribeSpeechUseCase;

import java.util.Locale;
import java.util.Set;

/** 语音用例编排；语音失败与Agent业务运行状态相互独立。 */
public final class SpeechApplicationService implements
        QuerySpeechCapabilitiesUseCase,
        TranscribeSpeechUseCase,
        SynthesizeSpeechUseCase {
    private static final Set<String> SUPPORTED_AUDIO_TYPES = Set.of(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/mpeg", "audio/wav", "audio/x-wav"
    );

    private final SpeechCapabilityPort capabilityPort;
    private final SpeechRecognitionPort recognitionPort;
    private final SpeechSynthesisPort synthesisPort;
    private final boolean enabled;
    private final int maxRecordingSeconds;
    private final long maxAudioBytes;
    private final int maxTtsCharacters;

    public SpeechApplicationService(
            SpeechCapabilityPort capabilityPort,
            SpeechRecognitionPort recognitionPort,
            SpeechSynthesisPort synthesisPort,
            boolean enabled,
            int maxRecordingSeconds,
            long maxAudioBytes,
            int maxTtsCharacters
    ) {
        this.capabilityPort = capabilityPort;
        this.recognitionPort = recognitionPort;
        this.synthesisPort = synthesisPort;
        this.enabled = enabled;
        this.maxRecordingSeconds = requirePositive(maxRecordingSeconds, "最大录音时长必须大于0");
        this.maxAudioBytes = requirePositive(maxAudioBytes, "最大音频大小必须大于0");
        this.maxTtsCharacters = requirePositive(maxTtsCharacters, "最大朗读长度必须大于0");
    }

    @Override
    public SpeechCapabilities capabilities() {
        if (!enabled) {
            return unavailable();
        }
        try {
            SpeechProviderCapabilities provider = capabilityPort.queryCapabilities();
            return new SpeechCapabilities(
                    provider.asrAvailable(), provider.ttsAvailable(),
                    provider.asrModel(), provider.ttsVoice(),
                    maxRecordingSeconds, maxAudioBytes, provider.ttsStreamingAvailable()
            );
        } catch (RuntimeException exception) {
            return unavailable();
        }
    }

    @Override
    public SpeechTranscription transcribe(TranscribeSpeechCommand command) {
        requireEnabled();
        if (command == null) {
            throw new IllegalArgumentException("录音不能为空");
        }
        byte[] audio = command.audio();
        if (audio.length == 0) {
            throw new IllegalArgumentException("录音不能为空");
        }
        if (audio.length > maxAudioBytes) {
            throw new IllegalArgumentException("录音文件不能超过10MB");
        }
        String contentType = normalizeMediaType(command.contentType());
        if (!SUPPORTED_AUDIO_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("暂不支持该录音格式");
        }
        long maxDurationMs = maxRecordingSeconds * 1000L;
        if (command.declaredDurationMs() <= 0 || command.declaredDurationMs() > maxDurationMs) {
            throw new IllegalArgumentException("录音时长必须大于0且不超过%d秒".formatted(maxRecordingSeconds));
        }
        SpeechTranscription result = recognitionPort.transcribe(new TranscribeSpeechCommand(
                audio, contentType, command.fileName(), command.declaredDurationMs()
        ));
        if (result.text().isBlank()) {
            throw new IllegalArgumentException("没有识别到清晰语音，请重试");
        }
        // Codec frame padding can extend a browser's 60-second stop by a few milliseconds.
        if (result.durationMs() > maxDurationMs + 250) {
            throw new IllegalArgumentException("录音不能超过%d秒".formatted(maxRecordingSeconds));
        }
        return result;
    }

    @Override
    public SpeechAudio synthesize(SynthesizeSpeechCommand command) {
        requireEnabled();
        if (command == null || command.text() == null || command.text().isBlank()) {
            throw new IllegalArgumentException("朗读文本不能为空");
        }
        String text = command.text().trim();
        if (text.length() > maxTtsCharacters) {
            throw new IllegalArgumentException(
                    "单个朗读片段不能超过%d个字符".formatted(maxTtsCharacters)
            );
        }
        SpeechAudio audio = synthesisPort.synthesize(new SynthesizeSpeechCommand(text, command.requestId()));
        if (audio.content().length == 0) {
            throw new ExternalServiceException(
                    "SPEECH", "TTS_EMPTY_AUDIO", "语音服务没有返回可播放音频"
            );
        }
        return audio;
    }

    @Override
    public void stream(SynthesizeSpeechCommand command, java.io.OutputStream output) throws java.io.IOException {
        requireEnabled();
        if (command == null || command.text() == null || command.text().isBlank()
                || command.text().trim().length() > maxTtsCharacters) {
            throw new IllegalArgumentException("朗读文本为空或超过长度限制");
        }
        synthesisPort.stream(new SynthesizeSpeechCommand(command.text().trim()), output);
    }

    @Override
    public void cancel(String requestId) {
        requireEnabled();
        new SynthesizeSpeechCommand("cancel", requestId);
        synthesisPort.cancel(requestId);
    }

    private SpeechCapabilities unavailable() {
        return new SpeechCapabilities(
                false, false, null, null, maxRecordingSeconds, maxAudioBytes
        );
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ExternalServiceException(
                    "SPEECH", "SPEECH_DISABLED", "语音功能当前未启用"
            );
        }
    }

    private String normalizeMediaType(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
        int separator = normalized.indexOf(';');
        return separator < 0 ? normalized : normalized.substring(0, separator).trim();
    }

    private static int requirePositive(int value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static long requirePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
