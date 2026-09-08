package cn.fj.roadagent.interfaces.rest.facility;

import cn.fj.roadagent.application.facility.FacilityHealthReport;
import cn.fj.roadagent.domain.facility.FacilityHealthState;

import java.time.Instant;
import java.util.List;

public record FacilityHealthReportResponse(
        Instant generatedAt, Instant dataAsOf,
        long activeAlertCount, long affectedFacilityCount,
        long warningCount, long severeCount, long emergencyCount,
        long pendingCount, long confirmedCount,
        FacilityHealthState overallHealth, String overallHealthName,
        String summary, List<FacilityFocusResponse> facilities
) {
    public static FacilityHealthReportResponse from(FacilityHealthReport report) {
        return new FacilityHealthReportResponse(
                report.generatedAt(), report.dataAsOf(), report.activeAlertCount(),
                report.affectedFacilityCount(), report.warningCount(), report.severeCount(),
                report.emergencyCount(), report.pendingCount(), report.confirmedCount(),
                report.overallHealth(), report.overallHealth().displayName(), report.summary(),
                report.facilities().stream().map(FacilityFocusResponse::from).toList()
        );
    }
}
