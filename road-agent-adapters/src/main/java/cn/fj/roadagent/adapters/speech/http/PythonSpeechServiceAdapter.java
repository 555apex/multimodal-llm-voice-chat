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
            throw unavailable("ASR_REQUEST_FAILED", "语音识别服务调用失败", exception);
        }
    }

    @Override
    public SpeechAudio synthesize(SynthesizeSpeechCommand command) {
        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(baseUrl + "/v1/tts/speech")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.valueOf("audio/mpeg"))
                    .body(new TtsRequest(command.text()))
                    .retrieve()
                    .toEntity(byte[].class);
            String contentType = response.getHeaders().getContentType() == null
                    ? "audio/mpeg" : response.getHeaders().getContentType().toString();
            return new SpeechAudio(response.getBody(), contentType);
        } catch (RestClientException exception) {
            throw unavailable("TTS_REQUEST_FAILED", "语音合成服务调用失败", exception);
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

    private ExternalServiceException unavailable(String code, String message, Throwable cause) {
        return cause == null
                ? new ExternalServiceException("SPEECH", code, message)
                : new ExternalServiceException("SPEECH", code, message, cause);
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
