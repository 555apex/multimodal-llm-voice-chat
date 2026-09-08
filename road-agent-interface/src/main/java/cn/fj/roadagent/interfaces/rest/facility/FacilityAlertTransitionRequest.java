package cn.fj.roadagent.interfaces.rest.facility;

import cn.fj.roadagent.domain.facility.FacilityAlertResolution;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FacilityAlertTransitionRequest(
        @NotNull FacilityAlertStatus expectedStatus,
        @NotNull FacilityAlertStatus targetStatus,
        FacilityAlertResolution resolutionType,
        @NotBlank @Size(max = 200) String remark
) {
}
