package cn.fj.roadagent.application.facility;

import cn.fj.roadagent.domain.facility.FacilityAlert;

import java.util.List;

public record FacilityAlertPage(
        List<FacilityAlert> items,
        int page,
        int size,
        long total,
        FacilityAlertCounts counts
) {
    public FacilityAlertPage {
        items = List.copyOf(items);
    }
}
