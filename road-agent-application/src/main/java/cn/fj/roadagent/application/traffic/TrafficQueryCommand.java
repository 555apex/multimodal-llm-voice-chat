package cn.fj.roadagent.application.traffic;

public record TrafficQueryCommand(
        String areaCode,
        String roadName,
        String direction,
        String traceId
) {
}
