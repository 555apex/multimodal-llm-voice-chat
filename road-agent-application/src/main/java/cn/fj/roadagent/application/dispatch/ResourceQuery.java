package cn.fj.roadagent.application.dispatch;

import java.util.List;

public record ResourceQuery(String city, List<String> resourceTypes) {
    public ResourceQuery {
        resourceTypes = resourceTypes == null ? List.of() : List.copyOf(resourceTypes);
    }
}
