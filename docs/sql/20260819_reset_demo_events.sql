-- 仅用于当前Demo数据库的永久重置，不创建备份，禁止在正式环境执行。
-- 重置范围：脚本开始执行时w_abnormal_event中实际存在的全部事件，不依赖固定条数。

DELIMITER $$

DROP PROCEDURE IF EXISTS assert_demo_events_exist$$
CREATE PROCEDURE assert_demo_events_exist()
BEGIN
    DECLARE event_count BIGINT DEFAULT 0;
    SELECT COUNT(*) INTO event_count FROM w_abnormal_event;
    IF event_count = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '安全校验失败：w_abnormal_event没有可重置的事件';
    END IF;
END$$

CALL assert_demo_events_exist()$$
DROP PROCEDURE assert_demo_events_exist$$

DELIMITER ;

DROP TEMPORARY TABLE IF EXISTS tmp_reset_demo_event_ids;
CREATE TEMPORARY TABLE tmp_reset_demo_event_ids (
    event_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

INSERT INTO tmp_reset_demo_event_ids (event_id)
SELECT id FROM w_abnormal_event;

SELECT event_status, COUNT(*) AS event_count
FROM w_abnormal_event
GROUP BY event_status
ORDER BY event_status;

SELECT COUNT(*) AS reset_event_count
FROM tmp_reset_demo_event_ids;

START TRANSACTION;

DELETE resource_allocation
FROM w_emergency_resource_allocation resource_allocation
JOIN tmp_reset_demo_event_ids target
  ON target.event_id = resource_allocation.event_id;

DELETE action_log
FROM w_emergency_dispatch_action_log action_log
JOIN w_emergency_dispatch_workflow workflow
  ON workflow.workflow_id = action_log.workflow_id
JOIN tmp_reset_demo_event_ids target
  ON target.event_id = workflow.event_id;

DELETE decision_record
FROM w_emergency_command_decision decision_record
JOIN w_emergency_dispatch_workflow workflow
  ON workflow.workflow_id = decision_record.workflow_id
JOIN tmp_reset_demo_event_ids target
  ON target.event_id = workflow.event_id;

DELETE review_record
FROM w_emergency_professional_review review_record
JOIN w_emergency_dispatch_workflow workflow
  ON workflow.workflow_id = review_record.workflow_id
JOIN tmp_reset_demo_event_ids target
  ON target.event_id = workflow.event_id;

DELETE dispatch_order
FROM w_emergency_dispatch_order dispatch_order
JOIN tmp_reset_demo_event_ids target
  ON target.event_id = dispatch_order.event_id;

DELETE workflow
FROM w_emergency_dispatch_workflow workflow
JOIN tmp_reset_demo_event_ids target
  ON target.event_id = workflow.event_id;

UPDATE w_abnormal_event event_record
JOIN tmp_reset_demo_event_ids target
  ON target.event_id = event_record.id
SET event_record.event_status = 0,
    event_record.no_dispatch_reason = NULL,
    event_record.update_time = CURRENT_TIMESTAMP,
    event_record.update_user = NULL;

UPDATE w_emergency_resource
SET available_quantity = CASE WHEN resource_status = 0 THEN total_quantity ELSE 0 END,
    reserved_quantity = 0,
    dispatched_quantity = 0,
    lock_version = lock_version + 1,
    update_time = CURRENT_TIMESTAMP(6),
    update_user = NULL;

COMMIT;

DROP TEMPORARY TABLE tmp_reset_demo_event_ids;

SELECT event_status, COUNT(*) AS event_count
FROM w_abnormal_event
GROUP BY event_status
ORDER BY event_status;
SELECT COUNT(*) AS remaining_dispatch_order_count
FROM w_emergency_dispatch_order;
SELECT COUNT(*) AS remaining_workflow_count
FROM w_emergency_dispatch_workflow;
SELECT COUNT(*) AS remaining_resource_allocation_count
FROM w_emergency_resource_allocation;
