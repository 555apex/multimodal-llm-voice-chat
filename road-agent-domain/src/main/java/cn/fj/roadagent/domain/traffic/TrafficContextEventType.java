package cn.fj.roadagent.domain.traffic;

/** 可参与路况原因提示的节假日或重大活动类型。 */
public enum TrafficContextEventType {
    HOLIDAY,
    ADJUSTED_WORKDAY,
    CONCERT,
    SPORTS_EVENT,
    OTHER_EVENT;

    public boolean holidayRelated() {
        return this == HOLIDAY || this == ADJUSTED_WORKDAY;
    }
}
