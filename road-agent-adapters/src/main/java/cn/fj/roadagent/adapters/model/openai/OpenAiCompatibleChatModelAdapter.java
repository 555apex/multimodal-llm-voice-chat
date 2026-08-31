package cn.fj.roadagent.adapters.model.openai;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.model.ModelMessage;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 直接调用OpenAI兼容HTTP接口，不依赖LangChain。
 * DeepSeek和未来兼容OpenAI协议的服务器模型都可以使用该适配器。
 */
public final class OpenAiCompatibleChatModelAdapter implements ChatModelPort {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String modelName;
    private final boolean authEnabled;
    private final Duration requestTimeout;

    public OpenAiCompatibleChatModelAdapter(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey,
            String modelName,
            boolean authEnabled,
            Duration requestTimeout
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(endpoint);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelName = modelName;
        this.authEnabled = authEnabled;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String content = complete(request, false);
        return new ModelResponse(content, "OPENAI_COMPATIBLE", modelName);
    }

    @Override
    public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
        String first = complete(request, true);
        try {
            return objectMapper.readValue(cleanJson(first), resultType);
        } catch (JsonProcessingException firstException) {
            // 仅修复一次格式；把具体字段错误和原响应反馈给模型，避免重复相同错误。
            ModelRequest repairRequest = new ModelRequest(
                    request.systemPrompt(),
                    request.userPrompt() + repairInstruction(first, firstException, resultType),
                    request.history(),
                    0.0
            );
            String repaired = complete(repairRequest, true);
            try {
                return objectMapper.readValue(cleanJson(repaired), resultType);
            } catch (JsonProcessingException secondException) {
                throw new ExternalServiceException(
                        // 保留既有错误码，避免破坏已按错误码处理的客户端。
                        "CHAT_MODEL", "MODEL_INVALID_JSON",
                        "模型连续两次返回的JSON不符合目标数据结构", secondException
                );
            }
        }
    }

    @Override
    public <T> T generateStructuredStrict(ModelRequest request, Class<T> resultType) {
        // “严格”表示最终结果必须通过目标类型校验且绝不发布半成品；首次响应仅有
        // JSON结构、句数或字段格式偏差时，允许在服务端静默修复一次再作最终判定。
        return generateStructured(request, resultType);
    }

    @Override
    public void stream(ModelRequest request, ModelStreamListener listener) {
        HttpRequest httpRequest = buildHttpRequest(request, true, false);
        boolean receivedContent = false;
        try {
            HttpResponse<Stream<String>> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofLines()
            );
            requireSuccess(response.statusCode(), "模型流式请求失败");

            try (Stream<String> lines = response.body()) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next().trim();
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String delta = extractDelta(data);
                    if (!delta.isEmpty()) {
                        receivedContent = true;
                        listener.onDelta(delta);
                    }
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw modelError("模型流式请求被中断", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ExternalServiceException external) {
                throw external;
            }
            throw modelError("模型流式请求失败", exception);
        }
        if (!receivedContent) {
            throw new ExternalServiceException(
                    "CHAT_MODEL", "MODEL_EMPTY_RESPONSE", "模型没有返回可用的流式文本"
            );
        }
    }

    private String complete(ModelRequest request, boolean structured) {
        HttpRequest httpRequest = buildHttpRequest(request, false, structured);
        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString()
            );
            requireSuccess(response.statusCode(), "模型请求失败");
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new ExternalServiceException(
                        "CHAT_MODEL", "MODEL_EMPTY_RESPONSE", "模型没有返回可用内容"
                );
            }
            return content.asText().trim();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw modelError("模型请求被中断", exception);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ExternalServiceException external) {
                throw external;
            }
            throw modelError("模型请求失败", exception);
        }
    }

    private HttpRequest buildHttpRequest(ModelRequest request, boolean stream, boolean structured) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", buildMessages(request));
        body.put("temperature", request.temperature());
        body.put("stream", stream);
        if (structured) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            if (authEnabled) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            return builder.build();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化模型请求", exception);
        }
    }

    private List<Map<String, String>> buildMessages(ModelRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (!request.systemPrompt().isBlank()) {
            messages.add(message("system", request.systemPrompt()));
        }
        for (ModelMessage history : request.history()) {
            if (!history.content().isBlank()) {
                messages.add(message(history.role(), history.content()));
            }
        }
        if (!request.userPrompt().isBlank()) {
            messages.add(message("user", request.userPrompt()));
        }
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private String extractDelta(String data) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(data);
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            throw new ExternalServiceException(
                    "CHAT_MODEL", "MODEL_UPSTREAM_ERROR", "模型流式响应包含错误：" + error
            );
        }
        JsonNode content = root.path("choices").path(0).path("delta").path("content");
        return content.isTextual() ? content.asText() : "";
    }

    private String cleanJson(String content) {
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "");
        }
        return cleaned;
    }

    private String repairInstruction(
            String invalidContent,
            JsonProcessingException exception,
            Class<?> resultType
    ) {
        return """


                上次响应不符合目标JSON数据结构，请根据下面的解析反馈完整重写。
                目标类型：%s
                解析反馈：%s
                上次响应：
                %s
                只输出修复后的严格JSON对象，不要解释，不要使用Markdown代码块。
                """.formatted(
                resultType.getSimpleName(),
                truncate(singleLine(exception.getOriginalMessage()), 1000),
                truncate(invalidContent, 6000)
        );
    }

    private String singleLine(String value) {
        return value == null ? "未知结构错误" : value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }

    private void requireSuccess(int statusCode, String message) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new ExternalServiceException(
                    "CHAT_MODEL", "MODEL_HTTP_" + statusCode, message + "，HTTP状态码：" + statusCode
            );
        }
    }

    private ExternalServiceException modelError(String message, Throwable cause) {
        return new ExternalServiceException(
                "CHAT_MODEL", "MODEL_UPSTREAM_ERROR", message, cause
        );
    }
}
