package cn.fj.roadagent.adapters.speech.http;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.SpeechCapabilityPort;
import cn.fj.roadagent.application.port.SpeechRecognitionPort;
import cn.fj.roadagent.application.port.SpeechSynthesisPort;
import cn.fj.roadagent.application.speech.SpeechAudio;
import cn.fj.roadagent.application.speech.SpeechProviderCapabilities;
import cn.fj.roadagent.application.speech.SpeechTranscription;
import cn.fj.roadagent.application.speech.SynthesizeSpeechCommand;
import cn.fj.roadagent.application.speech.TranscribeSpeechCommand;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 通过内部HTTP访问Docker中的Python语音服务。 */
public final class PythonSpeechServiceAdapter implements
        SpeechCapabilityPort,
        SpeechRecognitionPort,
        SpeechSynthesisPort {
    private final RestClient restClient;
    private final String baseUrl;

    public PythonSpeechServiceAdapter(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("语音服务地址不能为空");
        }
        this.baseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public SpeechProviderCapabilities queryCapabilities() {
        try {
            ProviderCapabilities body = restClient.get()
                    .uri(baseUrl + "/health/ready")
                    .retrieve()
                    .body(ProviderCapabilities.class);
            if (body == null) {
                throw unavailable("SPEECH_EMPTY_CAPABILITIES", "语音服务没有返回能力信息", null);
            }
            return new SpeechProviderCapabilities(
                    body.asrAvailable(), body.ttsAvailable(), body.asrModel(), body.ttsVoice(), body.ttsStreamingAvailable()
            );
        } catch (RestClientException exception) {
            throw unavailable("SPEECH_UNAVAILABLE", "语音服务尚未就绪", exception);
        }
    }

    @Override
    public SpeechTranscription transcribe(TranscribeSpeechCommand command) {
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType(command.contentType()));
        partHeaders.setContentDisposition(ContentDisposition.formData()
                .name("audio")
                .filename(command.fileName())
                .build());
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("audio", new HttpEntity<>(
                new NamedByteArrayResource(command.audio(), command.fileName()),
                partHeaders
        ));
        try {
            TranscriptionResponse body = restClient.post()
                    .uri(baseUrl + "/v1/asr/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(TranscriptionResponse.class);
            if (body == null) {
                throw unavailable("ASR_EMPTY_RESPONSE", "语音识别没有返回结果", null);
            }
            return new SpeechTranscription(body.text(), body.language(), body.durationMs());
        } catch (RestClientException exception) {
            throw speechFailure("ASR", exception);
        }
    }

    @Override
    public SpeechAudio synthesize(SynthesizeSpeechCommand command) {
        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(baseUrl + "/v1/tts/speech")
                    .headers(headers -> { if (command.requestId() != null) headers.set("X-Speech-Request-Id", command.requestId()); })
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .body(new TtsRequest(command.text()))
                    .retrieve()
                    .toEntity(byte[].class);
            String contentType = response.getHeaders().getContentType() == null
                    ? "audio/mpeg" : response.getHeaders().getContentType().toString();
            return new SpeechAudio(response.getBody(), contentType);
        } catch (RestClientException exception) {
            throw speechFailure("TTS", exception);
        }
    }

    @Override
    public void stream(SynthesizeSpeechCommand command, java.io.OutputStream output) {
        restClient.post().uri(baseUrl + "/v1/tts/speech/stream")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.TEXT_EVENT_STREAM)
                .body(new TtsRequest(command.text()))
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw unavailable("TTS_STREAM_FAILED", "流式语音暂不可用，请重试", null);
                    }
                    try (var input = response.getBody()) {
                        byte[] bytes = new byte[8192];
                        int count;
                        while ((count = input.read(bytes)) != -1) {
                            output.write(bytes, 0, count);
                            output.flush();
                        }
                    }
                    return null;
                });
    }

    @Override
    public void cancel(String requestId) {
        try {
            restClient.delete().uri(baseUrl + "/v1/tts/requests/" + requestId).retrieve().toBodilessEntity();
        } catch (RestClientException failure) {
            throw speechFailure("TTS", failure);
        }
    }

    private ExternalServiceException unavailable(String code, String message, Throwable cause) {
        return cause == null
                ? new ExternalServiceException("SPEECH", code, message)
                : new ExternalServiceException("SPEECH", code, message, cause);
    }

    private ExternalServiceException speechFailure(String task, RestClientException failure) {
        if (failure instanceof RestClientResponseException response) {
            return switch (response.getStatusCode().value()) {
                case 422 -> "ASR".equals(task)
                        ? unavailable("ASR_NO_SPEECH", "未识别到清晰语音，请靠近麦克风重试", failure)
                        : unavailable("TTS_INVALID_TEXT", "朗读内容无效，请修改后重试", failure);
                case 400, 415 -> "ASR".equals(task)
                        ? unavailable("ASR_INVALID_AUDIO", "录音格式无法解码，请重新录音", failure)
                        : unavailable("TTS_INVALID_TEXT", "朗读内容无效，请修改后重试", failure);
                case 413 -> unavailable(task + "_TOO_LARGE", "录音或朗读内容超过限制，请缩短后重试", failure);
                case 429 -> unavailable(task + "_BUSY", "语音服务正忙，请稍后重试", failure);
                case 499 -> unavailable(task + "_CANCELLED", "语音任务已取消", failure);
                case 504 -> unavailable(task + "_TIMEOUT", "语音处理超时，请重试", failure);
                default -> unavailable(task + "_UNAVAILABLE", "语音服务暂不可用，请稍后重试", failure);
            };
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) {
                return unavailable(task + "_TIMEOUT", "语音处理超时，请重试", failure);
            }
        }
        return unavailable(task + "_UNAVAILABLE", "语音服务暂不可用，请稍后重试", failure);
    }

    private record ProviderCapabilities(
            String status,
            boolean asrAvailable,
            boolean ttsAvailable,
            String asrModel,
            String ttsVoice,
            boolean ttsStreamingAvailable
    ) {
    }

    private record TranscriptionResponse(String text, String language, long durationMs) {
    }

    private record TtsRequest(String text) {
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        private NamedByteArrayResource(byte[] bytes, String fileName) {
            super(bytes);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
