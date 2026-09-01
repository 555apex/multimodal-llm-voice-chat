package cn.fj.roadagent.application.traffic;

public record VehicleStructureResultItem(
        String vehicleType,
        String vehicleTypeName,
        long weeklyVolume,
        double shareRatio
) {
}
