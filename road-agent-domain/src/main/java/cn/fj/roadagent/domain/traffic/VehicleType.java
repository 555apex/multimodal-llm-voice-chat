package cn.fj.roadagent.domain.traffic;

/** 协作者车型分析结果中的三类车辆。 */
public enum VehicleType {
    CAR("car", "小型客车"),
    BUS("bus", "中型客车"),
    TRUCK("truck", "大型货车");

    private final String databaseKey;
    private final String displayName;

    VehicleType(String databaseKey, String displayName) {
        this.databaseKey = databaseKey;
        this.displayName = displayName;
    }

    public String databaseKey() {
        return databaseKey;
    }

    public String displayName() {
        return displayName;
    }
}
