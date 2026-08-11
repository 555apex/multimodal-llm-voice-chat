package cn.fj.roadagent.core.speech;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.SpeechCapabilityPort;
import cn.fj.roadagent.application.port.SpeechRecognitionPort;
import cn.fj.roadagent.application.port.SpeechSynthesisPort;
import cn.fj.roadagent.application.speech.SpeechAudio;
import cn.fj.roadagent.application.speech.SpeechProviderCapabilities;
import cn.fj.roadagent.application.speech.SpeechTranscription;
import cn.fj.roadagent.application.speech.SynthesizeSpeechCommand;
import cn.fj.roadagent.application.speech.TranscribeSpeechCommand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechApplicationServiceTest {
    @Test
    void shouldExposeProviderCapabilitiesAndLocalLimits() {
        SpeechApplicationService service = service(
                () -> new SpeechProviderCapabilities(true, true, "small", "Xiaoxiao"),
                command -> new SpeechTranscription("五四路现在拥堵吗", "zh", 1200),
                command -> new SpeechAudio(new byte[]{1}, "audio/mpeg"),
                true
        );

        var capabilities = service.capabilities();

        assertTrue(capabilities.asrAvailable());
        assertTrue(capabilities.ttsAvailable());
        assertEquals("small", capabilities.asrModel());
        assertEquals(60, capabilities.maxRecordingSeconds());
        assertEquals(10 * 1024 * 1024L, capabilities.maxAudioBytes());
    }

    @Test
    void shouldRemainAvailableForTextWhenSpeechContainerIsOffline() {
        SpeechApplicationService service = service(
                () -> {
                    throw new ExternalServiceException("SPEECH", "OFFLINE", "offline");
                },
                command -> {
                    throw new AssertionError();
                },
                command -> {
                    throw new AssertionError();
                },
                true
        );

        assertFalse(service.capabilities().asrAvailable());
        assertFalse(service.capabilities().ttsAvailable());
    }

    @Test
    void shouldNormalizeMediaTypeAndTranscribe() {
        SpeechApplicationService service = service(
                () -> new SpeechProviderCapabilities(true, true, "small", "Xiaoxiao"),
                command -> {
                    assertEquals("audio/webm", command.contentType());
                    return new SpeechTranscription("厦门思明区路况", "zh", 1500);
                },
                command -> new SpeechAudio(new byte[]{1}, "audio/mpeg"),
                true
        );

        var result = service.transcribe(new TranscribeSpeechCommand(
                new byte[]{1, 2}, "audio/webm;codecs=opus", "recording.webm", 1600
        ));

        assertEquals("厦门思明区路况", result.text());
    }

    @Test
    void shouldRejectUnsupportedAudioAndOversizedTtsSegment() {
        SpeechApplicationService service = service(
                () -> new SpeechProviderCapabilities(true, true, "small", "Xiaoxiao"),
                command -> new SpeechTranscription("unused", "zh", 1),
                command -> new SpeechAudio(new byte[]{1}, "audio/mpeg"),
                true
        );

        assertThrows(IllegalArgumentException.class, () -> service.transcribe(
                new TranscribeSpeechCommand(new byte[]{1}, "text/plain", "a.txt", 1000)
        ));
        assertThrows(IllegalArgumentException.class, () -> service.synthesize(
                new SynthesizeSpeechCommand("路".repeat(501))
        ));
    }

    @Test
    void shouldReturnProviderAudioWithoutChangingBytes() {
        SpeechApplicationService service = service(
                () -> new SpeechProviderCapabilities(true, true, "small", "Xiaoxiao"),
                command -> new SpeechTranscription("unused", "zh", 1),
                command -> new SpeechAudio(new byte[]{1, 2, 3}, "audio/mpeg"),
                true
        );

        assertArrayEquals(
                new byte[]{1, 2, 3},
                service.synthesize(new SynthesizeSpeechCommand("道路通行正常。")).content()
        );
    }

    private SpeechApplicationService service(
            SpeechCapabilityPort capabilities,
            SpeechRecognitionPort recognition,
            SpeechSynthesisPort synthesis,
            boolean enabled
    ) {
        return new SpeechApplicationService(
                capabilities, recognition, synthesis, enabled,
                60, 10 * 1024 * 1024L, 500
        );
    }
}
