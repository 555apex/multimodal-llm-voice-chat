package cn.fj.roadagent.adapters.model.openai;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.model.ModelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleChatModelAdapterTest {

    private MockRestServiceServer server;
    private OpenAiCompatibleChatModelAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new OpenAiCompatibleChatModelAdapter(
                builder.build(),
                "https://api.deepseek.com/chat/completions",
                "test-secret",
                "deepseek-v4-flash"
        );
    }

    @Test
    void shouldReadOpenAiCompatibleResponse() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-secret"))
                .andRespond(withSuccess("""
                        {"id":"1","choices":[{"index":0,"message":{"role":"assistant","content":"五四路当前缓行。"}}]}
                        """, MediaType.APPLICATION_JSON));

        var response = adapter.generate(new ModelRequest("system", "traffic data", 0.2));

        assertEquals("五四路当前缓行。", response.content());
        server.verify();
    }

    @Test
    void shouldRejectEmptyModelResponse() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThrows(ExternalServiceException.class,
                () -> adapter.generate(new ModelRequest("system", "traffic data", 0.2)));
    }
}
