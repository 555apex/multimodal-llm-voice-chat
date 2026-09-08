package cn.fj.roadagent.interfaces.rest.facility;

import cn.fj.roadagent.application.facility.FacilityAlertCounts;
import cn.fj.roadagent.application.facility.FacilityAlertPage;

import java.time.Instant;
import java.util.List;

public record FacilityAlertPageResponse(
        List<FacilityAlertResponse> items, int page, int size, long total, Counts counts
) {
    public static FacilityAlertPageResponse from(FacilityAlertPage page) {
        return new FacilityAlertPageResponse(
                page.items().stream().map(FacilityAlertResponse::from).toList(),
                page.page(), page.size(), page.total(), Counts.from(page.counts())
        );
    }

    public record Counts(
            long pending, long confirmed, long closed,
            long activeWarning, long activeSevere, long activeEmergency,
            Instant dataAsOf
    ) {
        static Counts from(FacilityAlertCounts counts) {
            return new Counts(
                    counts.pending(), counts.confirmed(), counts.closed(),
                    counts.activeWarning(), counts.activeSevere(), counts.activeEmergency(),
                    counts.dataAsOf()
            );
        }
    }
}
