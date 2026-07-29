-- MySQL 8.0
-- 数据库应急调度结构。脚本可重复执行；已存在的字段和索引不会重复创建。

DELIMITER $$

DROP PROCEDURE IF EXISTS apply_roadagent_emergency_dispatch_schema$$
CREATE PROCEDURE apply_roadagent_emergency_dispatch_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'w_abnormal_event'
          AND COLUMN_NAME = 'no_dispatch_reason'
    ) THEN
        ALTER TABLE w_abnormal_event
            ADD COLUMN no_dispatch_reason VARCHAR(500) NULL
            COMMENT '人工确认无需生成调度工单的原因'
            AFTER event_status;
    END IF;

    ALTER TABLE w_abnormal_event
        MODIFY COLUMN event_status TINYINT NOT NULL DEFAULT 0
        COMMENT '事件处置状态：0-待处理，1-工单审批通过，2-无需调度';

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'w_abnormal_event'
          AND CONSTRAINT_TYPE = 'PRIMARY KEY'
    ) THEN
        ALTER TABLE w_abnormal_event
            ADD PRIMARY KEY (id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'w_abnormal_event'
          AND INDEX_NAME = 'uk_abnormal_event_custom_id'
    ) THEN
        ALTER TABLE w_abnormal_event
            ADD UNIQUE INDEX uk_abnormal_event_custom_id (custom_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'w_abnormal_event'
          AND INDEX_NAME = 'idx_abnormal_event_pending'
    ) THEN
        ALTER TABLE w_abnormal_event
            ADD INDEX idx_abnormal_event_pending (
                event_status, del_flag, occurrence_time, id
            );
    END IF;
END$$

CALL apply_roadagent_emergency_dispatch_schema()$$
DROP PROCEDURE apply_roadagent_emergency_dispatch_schema$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS w_emergency_dispatch_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    plan_id VARCHAR(40) NOT NULL COMMENT '稳定工单编号，同一事件各版本共用',
    event_id BIGINT NOT NULL COMMENT 'w_abnormal_event.id',
    version INT NOT NULL COMMENT '工单版本，从1开始',
    resource_list JSON NOT NULL COMMENT '模型生成的建议救援资源清单',
    rescue_plan TEXT NULL COMMENT '模型生成的救援方案描述',
    order_status TINYINT NOT NULL COMMENT '0-生成中，1-待审批，2-已驳回，3-已通过，4-生成失败',
    rejection_reason VARCHAR(500) NULL COMMENT '该版本的人工驳回意见',
    error_message VARCHAR(500) NULL COMMENT '模型生成失败原因',
    model_name VARCHAR(100) NULL COMMENT '生成该版本的模型名称',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
    create_user BIGINT NULL COMMENT '创建人，未接入身份时为空',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后更新时间',
    update_user BIGINT NULL COMMENT '最后修改人，未接入身份时为空',
    approved_time DATETIME NULL COMMENT '审批通过时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dispatch_plan_version (plan_id, version),
    UNIQUE KEY uk_dispatch_event_version (event_id, version),
    KEY idx_dispatch_event_latest (event_id, version, order_status)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-应急调度工单版本表';
