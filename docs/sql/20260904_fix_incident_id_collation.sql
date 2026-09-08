-- MySQL 8.0：修复w_lw_incident迁移后事件编号关联字段排序规则不一致。
-- 可重复执行；不修改事件、工单、工作流或资源业务数据。
-- 背景：甲方贴源字段w_lw_incident.c_no使用utf8mb4_general_ci，所有下游event_id必须一致。

ALTER TABLE w_emergency_dispatch_order
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';

ALTER TABLE w_emergency_dispatch_workflow
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';

ALTER TABLE w_emergency_resource_allocation
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';

ALTER TABLE w_emergency_event_classification
    MODIFY COLUMN event_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    NOT NULL COMMENT '关联w_lw_incident.c_no';

-- 结果应为39（以当前Demo数据为准），且不应再出现MySQL 1267错误。
SELECT COUNT(*) AS eligible_emergency_count
FROM w_lw_incident e
LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id=e.c_no
WHERE e.c_type='4' AND e.status='1' AND e.completed=0 AND e.deleted=0
  AND e.event_type IS NOT NULL AND e.event_type<>''
  AND (w.workflow_id IS NULL OR
       (w.current_stage=1 AND w.workflow_status IN (0,1,2,5,6)));
