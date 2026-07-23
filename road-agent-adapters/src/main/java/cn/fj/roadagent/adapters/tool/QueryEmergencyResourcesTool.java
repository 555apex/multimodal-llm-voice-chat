package cn.fj.roadagent.adapters.tool;

import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.application.port.ResourceQueryTool;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;

import java.util.List;
import java.util.Objects;

public final class QueryEmergencyResourcesTool implements ResourceQueryTool {
    private final ResourceDataPort resourceDataPort;

    public QueryEmergencyResourcesTool(ResourceDataPort resourceDataPort) {
        this.resourceDataPort = Objects.requireNonNull(resourceDataPort);
    }

    @Override
    public List<EmergencyResource> execute(ResourceQuery query) {
        return resourceDataPort.search(query);
    }
}
