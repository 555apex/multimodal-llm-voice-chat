package cn.fj.roadagent.domain.dispatch;

/** 异常事件的最终处置状态，对应 w_abnormal_event.event_status。 */
public enum AbnormalEventStatus {
    PENDING(0),
    DISPATCH_APPROVED(1),
    NO_DISPATCH_REQUIRED(2);

    private final int databaseValue;

    AbnormalEventStatus(int databaseValue) {
        this.databaseValue = databaseValue;
    }

    public int databaseValue() {
        return databaseValue;
    }

    public static AbnormalEventStatus fromDatabaseValue(int value) {
        for (AbnormalEventStatus status : values()) {
            if (status.databaseValue == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的异常事件状态：" + value);
    }
}
