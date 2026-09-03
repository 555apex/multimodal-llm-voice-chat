package cn.fj.roadagent.domain.traffic;

import java.time.Instant;
import java.util.List;

public record OdTrafficSnapshot(List<OdTransportHub> hubs, Instant acquiredAt, List<String> warnings) {
    public OdTrafficSnapshot {
        hubs = List.copyOf(hubs);
        warnings = List.copyOf(warnings);
    }
}
