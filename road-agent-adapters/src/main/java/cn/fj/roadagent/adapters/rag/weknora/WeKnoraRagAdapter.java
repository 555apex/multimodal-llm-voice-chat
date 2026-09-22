package cn.fj.roadagent.adapters.rag.weknora;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.RagSearchPort;
import cn.fj.roadagent.application.rag.RagSearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WeKnoraRagAdapter implements RagSearchPort {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final List<String> knowledgeBaseIds;
    private final Duration requestTimeout;

    public WeKnoraRagAdapter(HttpClient httpClient, ObjectMapper objectMapper, String endpoint,
                             String apiKey, List<String> knowledgeBaseIds, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = trimTrailingSlash(endpoint);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : knowledgeBaseIds.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
        this.requestTimeout = requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                ? Duration.ofSeconds(15) : requestTimeout;
    }

    @Override
    public List<RagSearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        if (knowledgeBaseIds.isEmpty()) {
            throw new ExternalServiceException("RAG_SEARCH", "WEKNORA_KB_ID_REQUIRED",
                    "ROADAGENT_RAG_KB_IDS is required");
        }
        int limit = topK <= 0 ? 5 : topK;
        List<RagSearchResult> combined = new ArrayList<>();
        for (String knowledgeBaseId : knowledgeBaseIds) {
            combined.addAll(searchOne(knowledgeBaseId, query.trim(), limit));
        }
        return combined.stream().sorted(Comparator.comparingDouble(RagSearchResult::score).reversed())
                .limit(limit).toList();
    }

    private List<RagSearchResult> searchOne(String knowledgeBaseId, String query, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_text", query);
        body.put("vector_threshold", 0);
        body.put("keyword_threshold", 0);
        body.put("match_count", limit);
        body.put("disable_keywords_match", false);
        body.put("disable_vector_match", false);
        try {
            String encodedId = URLEncoder.encode(knowledgeBaseId, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint + "/api/v1/knowledge-bases/" + encodedId + "/hybrid-search"))
                    .timeout(requestTimeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (!apiKey.isBlank()) builder.header("X-API-Key", apiKey);
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException("RAG_SEARCH", "WEKNORA_HTTP_ERROR",
                        "WeKnora hybrid search returned HTTP " + response.statusCode());
            }
            return parseResults(response.body(), limit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("RAG_SEARCH", "WEKNORA_SEARCH_INTERRUPTED",
                    "WeKnora hybrid search was interrupted", exception);
        } catch (HttpTimeoutException exception) {
            throw new ExternalServiceException("RAG_SEARCH", "WEKNORA_TIMEOUT",
                    "WeKnora hybrid search timed out", exception);
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ExternalServiceException("RAG_SEARCH", "WEKNORA_SEARCH_FAILED",
                    "WeKnora hybrid search failed", exception);
        }
    }

    private List<RagSearchResult> parseResults(byte[] response, int limit) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            JsonNode results = data.isArray() ? data : data.path("results");
            if (!results.isArray()) results = root.path("results");
            if (!results.isArray()) return List.of();
            List<RagSearchResult> parsed = new ArrayList<>();
            for (JsonNode item : results) {
                String content = firstText(item, "content", "text");
                if (content.isBlank()) continue;
                parsed.add(new RagSearchResult(firstText(item, "id"), content,
                        firstText(item, "knowledge_title", "knowledgeTitle"),
                        firstText(item, "knowledge_filename", "knowledgeFilename"),
                        item.path("score").asDouble(0.0)));
                if (parsed.size() >= limit) break;
            }
            return parsed;
        } catch (IOException exception) {
            throw new ExternalServiceException("RAG_SEARCH", "WEKNORA_INVALID_RESPONSE",
                    "WeKnora returned invalid search results", exception);
        }
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        return "";
    }

    private String trimTrailingSlash(String value) {
        String normalized = value == null || value.isBlank() ? "http://127.0.0.1:6000" : value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
