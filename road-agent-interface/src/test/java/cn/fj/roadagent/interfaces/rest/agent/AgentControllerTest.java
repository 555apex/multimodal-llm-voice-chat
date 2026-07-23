package cn.fj.roadagent.interfaces.rest.agent;

import cn.fj.roadagent.application.agent.AgentEvent;
import cn.fj.roadagent.application.agent.ConverseWithAgentUseCase;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ConverseWithAgentUseCase useCase = (command, sink) -> {
            sink.emit(new AgentEvent("run.started", Map.of("runId", "run-1")));
            sink.emit(new AgentEvent("tool.progress", Map.of(
                    "tool", "query_area_traffic", "totalTiles", 3,
                    "completedTiles", 1, "failedTiles", 0
            )));
            sink.emit(new AgentEvent("answer.delta", Map.of("content", "五四路缓行")));
            sink.emit(new AgentEvent("run.completed", Map.of("runId", "run-1")));
        };
        AgentController controller = new AgentController(useCase, Runnable::run);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldReturnNamedSseEvents() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/conversations/conversation-1/messages/stream")
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"福州五四路堵吗\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:run.started")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:tool.progress")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:answer.delta")));
    }
}
