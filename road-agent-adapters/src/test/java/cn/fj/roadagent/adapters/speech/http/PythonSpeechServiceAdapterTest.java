package cn.fj.roadagent.adapters.speech.http;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.speech.SynthesizeSpeechCommand;
import cn.fj.roadagent.application.speech.TranscribeSpeechCommand;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import cn.fj.roadagent.application.exception.ExternalServiceException;

class PythonSpeechServiceAdapterTest {
    private HttpServer server;
    private PythonSpeechServiceAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health/ready", exchange -> send(
                exchange, "application/json",
                ("{\"status\":\"UP\",\"asrAvailable\":true,\"ttsAvailable\":true,"
                        + "\"asrModel\":\"small\",\"ttsVoice\":\"Xiaoxiao\"}")
                        .getBytes(StandardCharsets.UTF_8)
        ));
        server.createContext("/v1/asr/transcriptions", exchange -> {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            byte[] body = exchange.getRequestBody().readAllBytes();
            assertTrue(contentType.startsWith("multipart/form-data"));
            assertTrue(new String(body, StandardCharsets.ISO_8859_1).contains("recording.webm"));
            send(exchange, "application/json", """
                    {"text":"厦门思明区路况","language":"zh","durationMs":1300}
                    """.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/v1/tts/speech", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("道路通行正常"));
            send(exchange, "audio/mpeg", new byte[]{1, 2, 3});
        });
        server.createContext("/silent/v1/asr/transcriptions", exchange -> send(
                exchange, 422, "application/json",
                "{\"detail\":\"No speech was recognized\"}".getBytes(StandardCharsets.UTF_8)
        ));
        server.start();
        adapter = new PythonSpeechServiceAdapter(
                RestClient.create(), "http://127.0.0.1:" + server.getAddress().getPort()
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldMapCapabilitiesTranscriptionAndAudio() {
        var capabilities = adapter.queryCapabilities();
        var transcription = adapter.transcribe(new TranscribeSpeechCommand(
                new byte[]{1, 2}, "audio/webm", "recording.webm", 1400
        ));
        var audio = adapter.synthesize(new SynthesizeSpeechCommand("道路通行正常。"));

        assertTrue(capabilities.asrAvailable());
        assertEquals("small", capabilities.asrModel());
        assertEquals("厦门思明区路况", transcription.text());
        assertArrayEquals(new byte[]{1, 2, 3}, audio.content());
    }

    @Test
    void shouldDistinguishNoSpeechFromServiceFailure() {
        var silentAdapter = new PythonSpeechServiceAdapter(
                RestClient.create(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/silent"
        );

        ExternalServiceException exception = assertThrows(
                ExternalServiceException.class,
                () -> silentAdapter.transcribe(new TranscribeSpeechCommand(
                        new byte[]{1, 2}, "audio/webm", "recording.webm", 1400
                ))
        );

        assertEquals("ASR_NO_SPEECH", exception.errorCode());
        assertEquals("未检测到有效语音", exception.getMessage());
    }

    private void send(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        send(exchange, 200, contentType, body);
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Test
    void shouldPreserveNoSpeechBusyAndInvalidAudioInsteadOfReportingGatewayFailure() {
        server.removeContext("/v1/asr/transcriptions");
        for (var example : new Object[][]{{422, "ASR_NO_SPEECH"}, {415, "ASR_INVALID_AUDIO"}, {429, "ASR_BUSY"}, {504, "ASR_TIMEOUT"}, {503, "ASR_UNAVAILABLE"}}) {
            server.createContext("/v1/asr/transcriptions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders((int) example[0], -1); exchange.close();
            });
            var failure = assertThrows(ExternalServiceException.class, () -> adapter.transcribe(
                    new TranscribeSpeechCommand(new byte[]{1}, "audio/webm", "recording.webm", 1000)));
            assertEquals(example[1], failure.errorCode());
            server.removeContext("/v1/asr/transcriptions");
        }
    }
}
