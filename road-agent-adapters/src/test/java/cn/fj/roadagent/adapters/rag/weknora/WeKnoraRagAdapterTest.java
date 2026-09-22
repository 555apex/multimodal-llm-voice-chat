package cn.fj.roadagent.adapters.rag.weknora;

import cn.fj.roadagent.application.exception.ExternalServiceException;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeKnoraRagAdapterTest {
    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void tearDown() { server.stop(0); }

    @Test
    void parsesWrappedResponseAndSendsApiKey() {
        AtomicReference<String> apiKey = new AtomicReference<>();
        server.createContext("/api/v1/knowledge-bases/kb-1/hybrid-search", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            send(exchange, 200, """
                    {"data":{"results":[
                      {"id":"2","content":"second","knowledge_title":"B","score":0.7},
                      {"id":"1","content":"first","knowledge_filename":"a.pdf","score":0.9}
                    ]}}
                    """);
        });
        server.start();

        var results = adapter(List.of("kb-1")).search("养护要求", 2);

        assertEquals("secret", apiKey.get());
        assertEquals(2, results.size());
        assertEquals("first", results.get(0).content());
        assertEquals("a.pdf", results.get(0).knowledgeFilename());
    }

    @Test
    void combinesKnowledgeBasesUsingGlobalTopK() {
        server.createContext("/api/v1/knowledge-bases/kb-1/hybrid-search",
                exchange -> send(exchange, 200, "{\"data\":[{\"content\":\"one\",\"score\":0.4}]}"));
        server.createContext("/api/v1/knowledge-bases/kb-2/hybrid-search",
                exchange -> send(exchange, 200, "{\"results\":[{\"content\":\"two\",\"score\":0.8}]}"));
        server.start();

        var results = adapter(List.of("kb-1", "kb-2")).search("query", 1);

        assertEquals(1, results.size());
        assertEquals("two", results.get(0).content());
    }

    @Test
    void rejectsMissingKnowledgeBaseIdsAndInvalidJson() {
        assertEquals(List.of(), adapter(List.of()).search("", 5));
        assertEquals("WEKNORA_KB_ID_REQUIRED", assertThrows(ExternalServiceException.class,
                () -> adapter(List.of()).search("query", 5)).errorCode());
        server.createContext("/api/v1/knowledge-bases/kb/hybrid-search",
                exchange -> send(exchange, 200, "not-json"));
        server.start();
        assertEquals("WEKNORA_INVALID_RESPONSE", assertThrows(ExternalServiceException.class,
                () -> adapter(List.of("kb")).search("query", 5)).errorCode());
    }

    @Test
    void reportsNonSuccessfulHttpResponse() {
        server.createContext("/api/v1/knowledge-bases/kb/hybrid-search",
                exchange -> send(exchange, 503, "{}"));
        server.start();
        ExternalServiceException failure = assertThrows(ExternalServiceException.class,
                () -> adapter(List.of("kb")).search("query", 5));
        assertEquals("WEKNORA_HTTP_ERROR", failure.errorCode());
        assertTrue(failure.getMessage().contains("503"));
    }

    @Test
    void reportsRequestTimeout() {
        server.createContext("/api/v1/knowledge-bases/kb/hybrid-search", exchange -> {
            try {
                Thread.sleep(200);
                send(exchange, 200, "{\"data\":[]}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();

        ExternalServiceException failure = assertThrows(ExternalServiceException.class,
                () -> adapter(List.of("kb"), Duration.ofMillis(30)).search("query", 5));

        assertEquals("WEKNORA_TIMEOUT", failure.errorCode());
    }

    private WeKnoraRagAdapter adapter(List<String> ids) {
        return adapter(ids, Duration.ofSeconds(2));
    }

    private WeKnoraRagAdapter adapter(List<String> ids, Duration timeout) {
        return new WeKnoraRagAdapter(HttpClient.newHttpClient(), new ObjectMapper(), endpoint,
                "secret", ids, timeout);
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
