package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.traffic.OdTrafficSnapshot;
import java.util.List;

public interface OdTrafficDataPort {
    OdTrafficSnapshot load(List<String> regionCodes, boolean includeVehicles);
}
