package cn.fj.roadagent.adapters.speech.http;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private void send(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
