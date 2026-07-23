package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;

import java.util.List;

public interface ResourceDataPort {
    List<EmergencyResource> search(ResourceQuery query);
}
