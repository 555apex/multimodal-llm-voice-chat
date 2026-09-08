package cn.fj.roadagent.interfaces.rest.emergency;

import cn.fj.roadagent.application.dispatch.ClassifyEmergencyEventsUseCase;
import cn.fj.roadagent.interfaces.rest.common.GlobalExceptionHandler;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventClassificationControllerTest {
    private MockMvc mockMvc;
    private StubUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new StubUseCase();
        mockMvc = MockMvcBuilders.standaloneSetup(new EventClassificationController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldRetryOneIncidentByCNo() throws Exception {
        mockMvc.perform(post("/api/v1/emergency-events/CNO-DEMO-001/classification-retries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals("CNO-DEMO-001", useCase.retriedEventId);
    }

    @Test
    void shouldRetryOldestClassificationFailureImmediately() throws Exception {
        useCase.hasPending = true;

        mockMvc.perform(post("/api/v1/emergency-events/classification-retries/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    private static final class StubUseCase implements ClassifyEmergencyEventsUseCase {
        private String retriedEventId;
        private boolean hasPending;

        @Override
        public boolean classifyNext() {
            return false;
        }

        @Override
        public boolean retryNext() {
            return hasPending;
        }

        @Override
        public void retry(String eventId) {
            retriedEventId = eventId;
        }
    }
}
