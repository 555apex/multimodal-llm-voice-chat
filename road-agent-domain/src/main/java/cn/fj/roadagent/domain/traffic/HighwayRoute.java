package cn.fj.roadagent.domain.traffic;

/** w_highway_network 中用于交通问答的国省干线路线信息。 */
public record HighwayRoute(
        String routeCode,
        String routeName,
        String roadType,
        String startPlace,
        String endPlace
) {
    public HighwayRoute {
        routeCode = requireText(routeCode, "路线编号不能为空").toUpperCase();
        routeName = requireText(routeName, "路线名称不能为空");
        roadType = requireText(roadType, "道路行政等级不能为空");
        startPlace = requireText(startPlace, "路线起点不能为空");
        endPlace = requireText(endPlace, "路线终点不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
