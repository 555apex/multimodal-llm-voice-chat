package cn.fj.roadagent.adapters.model.openai;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.port.ChatModelPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * 直接调用OpenAI兼容HTTP接口，不依赖LangChain。
 **/
public final class OpenAiCompatibleChatModelAdapter implements ChatModelPort {

    private final RestClient restClient;
    private final String endpoint;
    private final String apiKey;
    private final String modelName;

    public OpenAiCompatibleChatModelAdapter(
            RestClient restClient,
            String endpoint,
            String apiKey,
            String modelName
    ) {
        this.restClient = restClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        ChatCompletionRequest body = new ChatCompletionRequest(
                modelName,
                List.of(
                        new Message("system", request.systemPrompt()),
                        new Message("user", request.userPrompt())
                ),
                request.temperature(),
                false
        );

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(body)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            String content = extractContent(response);
            return new ModelResponse(content, "OPENAI_COMPATIBLE", modelName);
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ExternalServiceException(
                    "CHAT_MODEL", "MODEL_UPSTREAM_ERROR", "模型摘要服务调用失败", exception
            );
        }
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            throw new ExternalServiceException(
                    "CHAT_MODEL", "MODEL_EMPTY_RESPONSE", "模型没有返回可用的摘要"
            );
        }
        return response.choices().get(0).message().content().trim();
    }

    private record ChatCompletionRequest(
            String model,
            List<Message> messages,
            double temperature,
            boolean stream
    ) {
    }

    private record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(
            String id,
            List<Choice> choices,
            @JsonProperty("model") String returnedModel
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(int index, Message message) {
    }
}
