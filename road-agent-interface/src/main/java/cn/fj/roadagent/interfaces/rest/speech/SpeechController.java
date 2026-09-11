package cn.fj.roadagent.interfaces.rest.speech;

import cn.fj.roadagent.application.speech.QuerySpeechCapabilitiesUseCase;
import cn.fj.roadagent.application.speech.SpeechAudio;
import cn.fj.roadagent.application.speech.SynthesizeSpeechCommand;
import cn.fj.roadagent.application.speech.SynthesizeSpeechUseCase;
import cn.fj.roadagent.application.speech.TranscribeSpeechCommand;
import cn.fj.roadagent.application.speech.TranscribeSpeechUseCase;
import cn.fj.roadagent.interfaces.rest.common.ApiResponse;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/speech")
public final class SpeechController {
    private final QuerySpeechCapabilitiesUseCase capabilitiesUseCase;
    private final TranscribeSpeechUseCase transcribeUseCase;
    private final SynthesizeSpeechUseCase synthesizeUseCase;

    public SpeechController(
            QuerySpeechCapabilitiesUseCase capabilitiesUseCase,
            TranscribeSpeechUseCase transcribeUseCase,
            SynthesizeSpeechUseCase synthesizeUseCase
    ) {
        this.capabilitiesUseCase = capabilitiesUseCase;
        this.transcribeUseCase = transcribeUseCase;
        this.synthesizeUseCase = synthesizeUseCase;
    }

    @GetMapping("/capabilities")
    public ApiResponse<SpeechCapabilitiesResponse> capabilities(HttpServletRequest request) {
        return ApiResponse.success(
                SpeechCapabilitiesResponse.from(capabilitiesUseCase.capabilities()),
                traceId(request)
        );
    }

    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SpeechTranscriptionResponse> transcribe(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam("durationMs") long durationMs,
            HttpServletRequest request
    ) throws IOException {
        String contentType = audio.getContentType() == null ? "" : audio.getContentType();
        String fileName = audio.getOriginalFilename() == null ? "recording" : audio.getOriginalFilename();
        return ApiResponse.success(
                SpeechTranscriptionResponse.from(transcribeUseCase.transcribe(
                        new TranscribeSpeechCommand(
                                audio.getBytes(), contentType, fileName, durationMs
                        )
                )),
                traceId(request)
        );
    }

    @PostMapping(value = "/syntheses", produces = "audio/mpeg")
    public ResponseEntity<byte[]> synthesize(
            @Valid @RequestBody SpeechSynthesisRequest body,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Speech-Request-Id", required = false) String requestId
    ) {
        SpeechAudio audio = synthesizeUseCase.synthesize(
                new SynthesizeSpeechCommand(body.text(), requestId)
        );
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(audio.contentType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.valueOf("audio/mpeg");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename("speech.mp3", StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(audio.content());
    }

    @PostMapping(value = "/syntheses/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> stream(
            @Valid @RequestBody SpeechSynthesisRequest body) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore()).header("X-Accel-Buffering", "no")
                .body(output -> {
                    try {
                        synthesizeUseCase.stream(new SynthesizeSpeechCommand(body.text()), output);
                    } catch (RuntimeException failure) {
                        output.write(("event: audio.failed\ndata: {\"message\":\"语音合成中断，请重试\"}\n\n")
                                .getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    }
                });
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/syntheses/{requestId}")
    public ResponseEntity<Void> cancel(@org.springframework.web.bind.annotation.PathVariable String requestId) {
        synthesizeUseCase.cancel(java.util.UUID.fromString(requestId).toString());
        return ResponseEntity.noContent().build();
    }

    private String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
