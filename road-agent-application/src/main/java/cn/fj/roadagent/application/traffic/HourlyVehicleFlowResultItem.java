package cn.fj.roadagent.application.traffic;

public record HourlyVehicleFlowResultItem(
        String hour,
        long car,
        long bus,
        long truck
) {
}
