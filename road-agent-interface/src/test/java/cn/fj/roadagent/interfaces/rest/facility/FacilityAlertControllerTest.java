package cn.fj.roadagent.interfaces.rest.facility;

import cn.fj.roadagent.application.facility.FacilityAlertCounts;
import cn.fj.roadagent.application.facility.FacilityAlertPage;
import cn.fj.roadagent.application.facility.FacilityHealthReport;
import cn.fj.roadagent.application.facility.QueryFacilityAlertsUseCase;
import cn.fj.roadagent.application.facility.TransitionFacilityAlertUseCase;
import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlert;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;
import cn.fj.roadagent.domain.facility.FacilityHealthState;
import cn.fj.roadagent.interfaces.rest.common.GlobalExceptionHandler;
import cn.fj.roadagent.interfaces.rest.common.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FacilityAlertControllerTest {
    private static final long ALERT_ID = 2_097_166_786_449_965_057L;
    private static final Instant NOW = Instant.parse("2026-09-08T02:00:00Z");
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FacilityAlert alert = alert(FacilityAlertStatus.PENDING, null);
        FacilityAlertCounts counts = new FacilityAlertCounts(6, 1, 2, 3, 2, 2, NOW);
        QueryFacilityAlertsUseCase query = new QueryFacilityAlertsUseCase() {
            @Override
            public FacilityAlertPage query(
                    FacilityAlertStatus status, AlarmLevel alarmLevel, int page, int size
            ) {
                return new FacilityAlertPage(List.of(alert), page, size, 1, counts);
            }

            @Override
            public FacilityHealthReport healthReport() {
                return new FacilityHealthReport(
                        NOW, NOW, 1, 1, 0, 0, 1, 1, 0,
                        FacilityHealthState.DANGER, "存在1条活动告警", List.of()
                );
            }

            @Override
            public List<cn.fj.roadagent.application.facility.FacilityFocusItem> focus(int limit) {
                return List.of();
            }
        };
        TransitionFacilityAlertUseCase transition = command ->
                alert(FacilityAlertStatus.CONFIRMED, "【已确认】" + command.remark());
        mockMvc = MockMvcBuilders.standaloneSetup(new FacilityAlertController(query, transition))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new TraceIdFilter())
                .build();
    }

    @Test
    void shouldReturnWarningSnapshotAndThresholdAssessment() throws Exception {
        mockMvc.perform(get("/api/v1/facility-alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].facilityName").value("闽江大桥"))
                .andExpect(jsonPath("$.data.items[0].alertId").value(Long.toString(ALERT_ID)))
                .andExpect(jsonPath("$.data.items[0].metricName").value("主梁应变"))
                .andExpect(jsonPath("$.data.items[0].thresholdAssessment").value("超过上限"))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.counts.pending").value(6));
    }

    @Test
    void shouldValidateAndReturnTransitionedAlert() throws Exception {
        mockMvc.perform(post("/api/v1/facility-alerts/" + ALERT_ID + "/status-transitions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "expectedStatus":"PENDING",
                                  "targetStatus":"CONFIRMED",
                                  "remark":"已派员核查"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.remark").value("【已确认】已派员核查"));

        mockMvc.perform(post("/api/v1/facility-alerts/" + ALERT_ID + "/status-transitions")
                        .contentType("application/json")
                        .content("{\"expectedStatus\":\"PENDING\"}"))
                .andExpect(status().isBadRequest());
    }

    private FacilityAlert alert(FacilityAlertStatus status, String remark) {
        return new FacilityAlert(
                ALERT_ID, "闽江大桥", "主梁应变", new BigDecimal("12.5"), null,
                BigDecimal.ZERO, BigDecimal.TEN, AlarmLevel.EMERGENCY,
                NOW.minusSeconds(30), NOW.minusSeconds(60), status, remark
        );
    }
}
