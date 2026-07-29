package cn.fj.roadagent.domain.traffic;

import java.util.Objects;

/**
 * 区域查询条件
 * @param area 行政区对象（包含各类行政区信息：AdministrativeArea.java）
 * @param scope
 */
public record AreaTrafficQuery(AdministrativeArea area, TrafficQueryScope scope) {
    public AreaTrafficQuery {
        area = Objects.requireNonNull(area, "行政区不能为空");
        scope = Objects.requireNonNull(scope, "查询范围不能为空");
        if (!scope.isArea()) {
            throw new IllegalArgumentException("区域查询不能使用ROAD范围");
        }
    }

    public int roadLevel() {
        return scope.amapRoadLevel();
    }
}
