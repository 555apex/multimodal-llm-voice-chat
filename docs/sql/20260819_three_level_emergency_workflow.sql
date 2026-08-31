-- MySQL 8.0
-- 三级应急调度工作流结构迁移。可重复核验执行，不由应用启动自动触发。
-- 执行前必须先选择 road_agent schema。

DELIMITER $$

DROP PROCEDURE IF EXISTS apply_three_level_emergency_workflow_schema$$
CREATE PROCEDURE apply_three_level_emergency_workflow_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'w_emergency_dispatch_order'
          AND COLUMN_NAME = 'event_snapshot'
    ) THEN
        ALTER TABLE w_emergency_dispatch_order
            ADD COLUMN event_snapshot JSON NULL
            COMMENT '生成本版本时冻结的异常事件快照'
            AFTER version;
    END IF;

    ALTER TABLE w_abnormal_event
        MODIFY COLUMN event_status TINYINT NOT NULL DEFAULT 0
        COMMENT '事件最终状态：0-三级流程处理中，1-最终批准并通告，2-无需调度';

    ALTER TABLE w_emergency_dispatch_order
        MODIFY COLUMN order_status TINYINT NOT NULL
        COMMENT '方案版本状态：0-生成中，1-三级审核中，2-已退回，3-最终采用，4-生成失败';
END$$

CALL apply_three_level_emergency_workflow_schema()$$
DROP PROCEDURE apply_three_level_emergency_workflow_schema$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS w_emergency_dispatch_workflow (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    workflow_id VARCHAR(40) NOT NULL COMMENT '稳定工作流编号',
    event_id BIGINT NOT NULL COMMENT 'w_abnormal_event.id',
    current_stage TINYINT NULL COMMENT '当前阶段：1-现场处置，2-市级专业复核，3-省级决策；办结为空',
    workflow_status TINYINT NOT NULL COMMENT '0-待生成，1-生成中，2-待一级上报，3-待二级复核，4-待三级决策，5-返工中，6-生成失败，7-已通告，8-无需调度',
    plan_id VARCHAR(40) NULL COMMENT '当前稳定工单编号',
    plan_version INT NOT NULL DEFAULT 0 COMMENT '当前方案版本',
    lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    stage_entered_at DATETIME(6) NOT NULL COMMENT '进入当前状态时间',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_emergency_workflow_id (workflow_id),
    UNIQUE KEY uk_emergency_workflow_event (event_id),
    UNIQUE KEY uk_emergency_workflow_plan (plan_id),
    KEY idx_emergency_workflow_inbox (current_stage, workflow_status, stage_entered_at, event_id),
    KEY idx_emergency_workflow_history (workflow_status, update_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-三级应急工作流主表';

CREATE TABLE IF NOT EXISTS w_emergency_professional_review (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    review_id VARCHAR(40) NOT NULL COMMENT '专业复核编号',
    workflow_id VARCHAR(40) NOT NULL COMMENT '工作流编号',
    plan_id VARCHAR(40) NOT NULL COMMENT '被审核工单编号',
    plan_version INT NOT NULL COMMENT '被审核方案版本',
    review_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待复核，1-通过，2-退回',
    event_severity VARCHAR(32) NULL COMMENT '人工初判事件等级',
    resource_feasibility VARCHAR(32) NULL COMMENT '资源建议可行性',
    impact_assessment TEXT NULL COMMENT '影响研判',
    coordination_requirements TEXT NULL COMMENT '协同要求',
    review_opinion VARCHAR(500) NULL COMMENT '专业审核或退回意见',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_professional_review_id (review_id),
    UNIQUE KEY uk_professional_review_version (workflow_id, plan_id, plan_version),
    KEY idx_professional_review_pending (workflow_id, review_status, id),
    CONSTRAINT fk_professional_review_workflow
        FOREIGN KEY (workflow_id)
        REFERENCES w_emergency_dispatch_workflow (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-二级市交通应急办专业复核表';

CREATE TABLE IF NOT EXISTS w_emergency_command_decision (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    decision_id VARCHAR(40) NOT NULL COMMENT '省级决策编号',
    workflow_id VARCHAR(40) NOT NULL COMMENT '工作流编号',
    review_id VARCHAR(40) NOT NULL COMMENT '对应的已通过专业复核编号',
    plan_id VARCHAR(40) NOT NULL COMMENT '被决策工单编号',
    plan_version INT NOT NULL COMMENT '被决策方案版本',
    decision_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-待决策，1-退回，2-已通告',
    decision_opinion VARCHAR(500) NULL COMMENT '省级批示或退回意见',
    notice_snapshot JSON NULL COMMENT '最终批准时冻结的只读通告快照',
    published_time DATETIME(6) NULL COMMENT '正式通告时间',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_command_decision_id (decision_id),
    UNIQUE KEY uk_command_decision_version (workflow_id, plan_id, plan_version),
    KEY idx_command_decision_pending (workflow_id, decision_status, id),
    CONSTRAINT fk_command_decision_workflow
        FOREIGN KEY (workflow_id)
        REFERENCES w_emergency_dispatch_workflow (workflow_id),
    CONSTRAINT fk_command_decision_review
        FOREIGN KEY (review_id)
        REFERENCES w_emergency_professional_review (review_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-三级省级决策与通告表';

CREATE TABLE IF NOT EXISTS w_emergency_dispatch_action_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    action_id VARCHAR(40) NOT NULL COMMENT '动作编号',
    workflow_id VARCHAR(40) NOT NULL COMMENT '工作流编号',
    action_type VARCHAR(40) NOT NULL COMMENT '动作类型',
    from_stage TINYINT NULL COMMENT '变更前阶段',
    to_stage TINYINT NULL COMMENT '变更后阶段',
    from_status TINYINT NULL COMMENT '变更前工作流状态',
    to_status TINYINT NOT NULL COMMENT '变更后工作流状态',
    plan_id VARCHAR(40) NULL COMMENT '动作关联工单编号',
    plan_version INT NOT NULL DEFAULT 0 COMMENT '动作关联方案版本',
    action_comment VARCHAR(500) NULL COMMENT '审核、退回或处置意见',
    detail_json JSON NULL COMMENT '动作的扩展结构化快照',
    idempotency_key VARCHAR(100) NOT NULL COMMENT '客户端或系统幂等键',
    actor_stage TINYINT NULL COMMENT 'Demo操作席位阶段，不代表实名身份',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_emergency_action_id (action_id),
    UNIQUE KEY uk_emergency_action_idempotency (workflow_id, idempotency_key),
    KEY idx_emergency_action_timeline (workflow_id, create_time, id),
    CONSTRAINT fk_emergency_action_workflow
        FOREIGN KEY (workflow_id)
        REFERENCES w_emergency_dispatch_workflow (workflow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-三级应急工作流动作流水';

-- 核验结构；以下查询应分别返回1、1、1、1。
SELECT COUNT(*) AS workflow_table_ready
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_dispatch_workflow';
SELECT COUNT(*) AS professional_review_table_ready
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_professional_review';
SELECT COUNT(*) AS command_decision_table_ready
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_command_decision';
SELECT COUNT(*) AS action_log_table_ready
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_dispatch_action_log';
