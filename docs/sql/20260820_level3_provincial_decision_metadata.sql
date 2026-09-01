-- 将既有三级工作流的数据库元数据统一为“省级决策”。
-- 仅修改表和字段注释，不改变字段类型、状态编码或业务数据。

ALTER TABLE w_emergency_dispatch_workflow
    MODIFY COLUMN current_stage TINYINT NULL
        COMMENT '当前阶段：1-现场处置，2-市级专业复核，3-省级决策；办结为空';

ALTER TABLE w_emergency_command_decision
    MODIFY COLUMN decision_id VARCHAR(40) NOT NULL COMMENT '省级决策编号',
    MODIFY COLUMN decision_opinion VARCHAR(500) NULL COMMENT '省级批示或退回意见',
    COMMENT = '路网智能体-三级省级决策与通告表';

SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'w_emergency_command_decision';

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND (
      (TABLE_NAME = 'w_emergency_dispatch_workflow' AND COLUMN_NAME = 'current_stage')
      OR
      (TABLE_NAME = 'w_emergency_command_decision'
          AND COLUMN_NAME IN ('decision_id', 'decision_opinion'))
  )
ORDER BY TABLE_NAME, ORDINAL_POSITION;
