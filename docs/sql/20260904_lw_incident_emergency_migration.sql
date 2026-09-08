-- MySQL 8.0：w_lw_incident 应急业务迁移。禁止由应用启动自动执行。
-- 执行前：1) 停止Java后端；2) 选中 road_agent schema；3) 确认当前环境是项目Demo库。

DELIMITER $$
DROP PROCEDURE IF EXISTS assert_lw_incident_migration_ready$$
CREATE PROCEDURE assert_lw_incident_migration_ready()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                   WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='w_lw_incident') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='w_lw_incident不存在';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='w_lw_incident'
                     AND COLUMN_NAME='event_type') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='w_lw_incident.event_type不存在';
    END IF;
    IF EXISTS (SELECT c_no FROM w_lw_incident GROUP BY c_no HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='w_lw_incident.c_no存在重复';
    END IF;
END$$
CALL assert_lw_incident_migration_ready()$$
DROP PROCEDURE assert_lw_incident_migration_ready$$
DELIMITER ;

-- 永久清理旧事件产生的业务链路；贴源事件和资源定义本身不删除。
START TRANSACTION;
DELETE FROM w_emergency_resource_allocation;
DELETE FROM w_emergency_dispatch_action_log;
DELETE FROM w_emergency_command_decision;
DELETE FROM w_emergency_professional_review;
DELETE FROM w_emergency_dispatch_workflow;
DELETE FROM w_emergency_dispatch_order;
UPDATE w_emergency_resource
SET available_quantity=total_quantity, reserved_quantity=0, dispatched_quantity=0,
    lock_version=lock_version+1, update_time=CURRENT_TIMESTAMP(6), update_user=NULL;
UPDATE w_abnormal_event
SET event_status=0, no_dispatch_reason=NULL,
    update_time=CURRENT_TIMESTAMP, update_user=NULL;
COMMIT;

DROP TABLE IF EXISTS w_abnormal_event_real;

-- 下游业务表全部以c_no字符串关联，不建指向贴源表的外键。
ALTER TABLE w_emergency_dispatch_order
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';
ALTER TABLE w_emergency_dispatch_workflow
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';
ALTER TABLE w_emergency_resource_allocation
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';

DELIMITER $$
DROP PROCEDURE IF EXISTS apply_lw_incident_emergency_schema$$
CREATE PROCEDURE apply_lw_incident_emergency_schema()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA=DATABASE()
                     AND TABLE_NAME='w_emergency_dispatch_workflow'
                     AND COLUMN_NAME='terminal_reason') THEN
        ALTER TABLE w_emergency_dispatch_workflow
            ADD COLUMN terminal_reason VARCHAR(500) NULL
            COMMENT '无需调度等终止决定原因' AFTER plan_version;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='w_lw_incident'
                     AND INDEX_NAME='idx_lw_incident_emergency_inbox') THEN
        ALTER TABLE w_lw_incident ADD INDEX idx_lw_incident_emergency_inbox
            (c_type,status,completed,deleted,event_type,guard_time,c_no);
    END IF;
END$$
CALL apply_lw_incident_emergency_schema()$$
DROP PROCEDURE apply_lw_incident_emergency_schema$$
DELIMITER ;

ALTER TABLE w_lw_incident
    MODIFY COLUMN c_no VARCHAR(64) NOT NULL COMMENT '甲方事件全局唯一编号，应急工作流业务ID',
    MODIFY COLUMN c_type VARCHAR(16) NULL COMMENT '甲方事件大类：1-建设 2-养护 3-路政 4-应急 5-路网 6-举报 7-其他 8-未知',
    MODIFY COLUMN event_type VARCHAR(10) NULL COMMENT '智能体应急子类：DT01-DT05、ET101-ET110、ET112；非应急事件可为空',
    MODIFY COLUMN status VARCHAR(16) NULL COMMENT '甲方处理状态：1-未完成 2-已完成';

CREATE TABLE IF NOT EXISTS w_emergency_event_classification (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    classification_id VARCHAR(40) NOT NULL COMMENT '分类留痕编号',
    event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
        NOT NULL COMMENT '关联w_lw_incident.c_no',
    attempt_no INT NOT NULL COMMENT '该事件的分类尝试次数',
    classification_method VARCHAR(16) NOT NULL COMMENT 'RULE-规则 MODEL-模型 MANUAL-人工',
    classification_status TINYINT NOT NULL COMMENT '0-成功 1-失败 2-人工更正',
    event_type VARCHAR(10) NULL COMMENT '本次确定的应急子类',
    confidence DECIMAL(5,4) NOT NULL DEFAULT 0 COMMENT '置信度0至1',
    evidence VARCHAR(500) NULL COMMENT '规则命中、模型或人工依据',
    model_name VARCHAR(100) NULL COMMENT '模型分类时使用的模型名称',
    error_message VARCHAR(500) NULL COMMENT '失败原因',
    idempotency_key VARCHAR(100) NULL COMMENT '系统或用户操作幂等键',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '识别或更正时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_classification_id (classification_id),
    UNIQUE KEY uk_event_classification_attempt (event_id,attempt_no),
    UNIQUE KEY uk_event_classification_idempotency (idempotency_key),
    KEY idx_event_classification_latest (event_id,attempt_no,classification_status),
    CONSTRAINT ck_event_classification_confidence CHECK (confidence>=0 AND confidence<=1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-应急事件自动分类与人工更正留痕';

-- 兼容脚本重复执行：若分类表已存在，也确保关联字段与贴源表c_no可直接比较。
ALTER TABLE w_emergency_event_classification
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';

ALTER TABLE w_emergency_dispatch_workflow
    MODIFY COLUMN current_stage TINYINT NULL COMMENT '1-一级现场处置 2-二级市交通应急办复核 3-三级省级决策；办结为空',
    MODIFY COLUMN workflow_status TINYINT NOT NULL COMMENT '0-待生成 1-生成中 2-待一级上报 3-待二级复核 4-待省级决策 5-返工中 6-生成失败 7-已通告 8-无需调度';

SELECT 'migration-ready' AS result,
       (SELECT COUNT(*) FROM w_lw_incident) AS incident_count,
       (SELECT COUNT(*) FROM w_abnormal_event) AS legacy_event_count,
       (SELECT COUNT(*) FROM w_emergency_dispatch_workflow) AS workflow_count;
