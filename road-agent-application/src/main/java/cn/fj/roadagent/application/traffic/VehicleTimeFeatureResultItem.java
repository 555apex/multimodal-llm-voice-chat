package cn.fj.roadagent.application.traffic;

public record VehicleTimeFeatureResultItem(
        String vehicleType,
        String vehicleTypeName,
        String peakHour,
        long peakVolume,
        double morningPeakRatio,
        double eveningPeakRatio,
        String characteristic
) {
}
