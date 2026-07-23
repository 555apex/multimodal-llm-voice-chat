package cn.fj.roadagent.adapters.tool;

import cn.fj.roadagent.application.port.AdministrativeAreaPort;
import cn.fj.roadagent.application.port.AreaTrafficDataPort;
import cn.fj.roadagent.application.port.AreaTrafficQueryTool;
import cn.fj.roadagent.application.traffic.AreaTrafficProgressListener;
import cn.fj.roadagent.domain.traffic.AreaTrafficQuery;
import cn.fj.roadagent.domain.traffic.AreaTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TrafficQueryScope;

import java.util.Objects;

/** 将自然语言中的行政区名称转换为一次可信的区域交通查询。 */
public final class QueryAreaTrafficTool implements AreaTrafficQueryTool {
    private final AdministrativeAreaPort administrativeAreaPort;
    private final AreaTrafficDataPort areaTrafficDataPort;

    public QueryAreaTrafficTool(
            AdministrativeAreaPort administrativeAreaPort,
            AreaTrafficDataPort areaTrafficDataPort
    ) {
        this.administrativeAreaPort = Objects.requireNonNull(administrativeAreaPort);
        this.areaTrafficDataPort = Objects.requireNonNull(areaTrafficDataPort);
    }

    @Override
    public AreaTrafficSnapshot execute(
            String city,
            String areaName,
            TrafficQueryScope scope,
            AreaTrafficProgressListener listener
    ) {
        var area = administrativeAreaPort.resolve(city, areaName);
        return areaTrafficDataPort.query(new AreaTrafficQuery(area, scope), listener);
    }
}
