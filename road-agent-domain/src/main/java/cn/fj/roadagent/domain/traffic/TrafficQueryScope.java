package cn.fj.roadagent.domain.traffic;

/** 交通查询范围
 * 具体：由模型识别用户文本获得，最终必须经过Java白名单校验。
 * */
public enum TrafficQueryScope {
    ROAD,   // 具体路查询
    AREA_ALL,   // 区域全体查询
    AREA_MAJOR; // 区域主要干道查询

    public boolean isArea() {
        return this != ROAD;
    }

    /** 高德道路等级具有包含关系：4包含主要道路及更高等级，5再包含一般道路。 */
    public int amapRoadLevel() {
        return this == AREA_MAJOR ? 4 : 5;
    }
}
