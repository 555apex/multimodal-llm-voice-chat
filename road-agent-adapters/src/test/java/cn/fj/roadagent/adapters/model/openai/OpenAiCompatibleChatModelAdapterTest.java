package cn.fj.roadagent.adapters.model.openai;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.model.ModelRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleChatModelAdapterTest {

    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldReadTextAndSendBearerHeader() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, """
                    {"choices":[{"message":{"content":"五四路当前缓行。"}}]}
                    """);
        });
        server.start();

        var response = adapter().generate(new ModelRequest("system", "traffic data", 0.2));

        assertEquals("五四路当前缓行。", response.content());
        assertEquals("Bearer test-secret", authorization.get());
        assertTrue(!requestBody.get().contains("chat_template_kwargs"));
    }

    @Test
    void shouldSendOptionalThinkingFlagWithoutBearerHeader() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, """
                    {"choices":[{"message":{"content":"严格JSON模式已启用。"}}]}
                    """);
        });
        server.start();

        var response = adapter(false, false).generate(new ModelRequest("system", "answer", 0.0));

        assertEquals("严格JSON模式已启用。", response.content());
        assertEquals(null, authorization.get());
        assertTrue(requestBody.get().contains("\"chat_template_kwargs\":{\"enable_thinking\":false}"));
    }

    @Test
    void shouldParseStructuredJsonAndStreamDeltas() {
        server.createContext("/chat/completions", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (requestBody.contains("\"stream\":true")) {
                sendSse(exchange, """
                        data: {"choices":[{"delta":{"content":"五四路"}}]}

                        data: {"choices":[{"delta":{"content":"当前缓行。"}}]}

                        data: [DONE]

                        """);
            } else {
                sendJson(exchange, """
                        {"choices":[{"message":{"content":"{\\\"intent\\\":\\\"TRAFFIC_QUERY\\\"}"}}]}
                        """);
            }
        });
        server.start();
        OpenAiCompatibleChatModelAdapter adapter = adapter();

        IntentJson structured = adapter.generateStructured(
                new ModelRequest("请输出json", "query", 0.0), IntentJson.class
        );
        StringBuilder streamed = new StringBuilder();
        adapter.stream(new ModelRequest("system", "answer", 0.2), streamed::append);

        assertEquals("TRAFFIC_QUERY", structured.intent());
        assertEquals("五四路当前缓行。", streamed.toString());
    }

    @Test
    void shouldRejectEmptyModelResponse() {
        server.createContext("/chat/completions", exchange -> sendJson(exchange, "{\"choices\":[]}"));
        server.start();

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> adapter().generate(new ModelRequest("system", "traffic data", 0.2)));

        assertTrue(exception.errorCode().startsWith("MODEL_"));
    }

    @Test
    void shouldRepairInvalidStructuredJsonOnce() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/chat/completions", exchange -> {
            if (calls.incrementAndGet() == 1) {
                sendJson(exchange, """
                        {"choices":[{"message":{"content":"not-json"}}]}
                        """);
            } else {
                sendJson(exchange, """
                        {"choices":[{"message":{"content":"{\\\"intent\\\":\\\"TRAFFIC_QUERY\\\"}"}}]}
                        """);
            }
        });
        server.start();

        IntentJson result = adapter().generateStructured(
                new ModelRequest("请输出json", "query", 0.0), IntentJson.class
        );

        assertEquals("TRAFFIC_QUERY", result.intent());
        assertEquals(2, calls.get());
    }

    @Test
    void shouldFailWhenStructuredJsonIsInvalidTwice() {
        server.createContext("/chat/completions", exchange -> sendJson(exchange, """
                {"choices":[{"message":{"content":"still-not-json"}}]}
                """));
        server.start();

        ExternalServiceException exception = assertThrows(ExternalServiceException.class,
                () -> adapter().generateStructured(
                        new ModelRequest("请输出json", "query", 0.0), IntentJson.class
                ));

        assertEquals("MODEL_INVALID_JSON", exception.errorCode());
    }

    private OpenAiCompatibleChatModelAdapter adapter() {
        return adapter(true, null);
    }

    private OpenAiCompatibleChatModelAdapter adapter(boolean authEnabled, Boolean enableThinking) {
        return new OpenAiCompatibleChatModelAdapter(
                HttpClient.newHttpClient(), new ObjectMapper(), endpoint,
                "test-secret", "deepseek-v4-flash", authEnabled, enableThinking, Duration.ofSeconds(3)
        );
    }

    private void sendJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void sendSse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record IntentJson(String intent) {
    }
}
