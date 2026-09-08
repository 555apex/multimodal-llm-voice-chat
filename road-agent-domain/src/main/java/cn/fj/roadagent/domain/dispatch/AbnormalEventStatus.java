package cn.fj.roadagent.domain.dispatch;

/** 旧版事件终态兼容枚举；w_lw_incident迁移后不再直接映射贴源表字段。 */
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
