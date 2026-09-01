package cn.fj.roadagent.application.traffic;

public record VehicleDayTypeResultItem(
        String vehicleType,
        String vehicleTypeName,
        long weekdayVolume,
        long weekendVolume
) {
}
