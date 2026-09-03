package cn.fj.roadagent.application.traffic;

public record OdChannelResultItem(
        String routeCode, String routeName, long weeklyTotalFlow,
        long carWeeklyFlow, long busWeeklyFlow, long truckWeeklyFlow
) { }
