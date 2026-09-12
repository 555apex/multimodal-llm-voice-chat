-- 无资源基线预案发布（2026-09-12）。
-- 保留旧版预案和已有工单快照，为16类事件各发布一个新版本；新版本不限定资源类型或数量。
-- 执行前必须暂停后端的工单生成请求。本脚本不修改事件、工单、通告、资源库存或资源占用历史。
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS publish_resource_unbound_response_plans_20260912;
DELIMITER $$
CREATE PROCEDURE publish_resource_unbound_response_plans_20260912()
BEGIN
  DECLARE marker_count INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    DROP TEMPORARY TABLE IF EXISTS resource_unbound_plan_next;
    RESIGNAL;
  END;

  START TRANSACTION;

  IF (SELECT COUNT(*) FROM w_emergency_dispatch_workflow WHERE workflow_status IN (1, 5)) > 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Generating or revising workflow exists; publication aborted';
  END IF;

  IF (SELECT COUNT(*) FROM w_emergency_response_plan WHERE plan_status = 1) <> 16 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Expected exactly 16 active response plans; publication aborted';
  END IF;

  SELECT COUNT(*) INTO marker_count
  FROM w_emergency_response_plan
  WHERE source_document = 'resource-unbound-20260912';

  IF marker_count NOT IN (0, 16) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Partial resource-unbound publication detected; publication aborted';
  END IF;

  IF marker_count = 0 THEN
    DROP TEMPORARY TABLE IF EXISTS resource_unbound_plan_next;
    CREATE TEMPORARY TABLE resource_unbound_plan_next AS
      SELECT p.id AS previous_id,
             p.plan_id,
             p.event_type,
             p.event_type_name,
             (SELECT MAX(v.plan_version) + 1
              FROM w_emergency_response_plan v
              WHERE v.event_type = p.event_type) AS next_version,
             p.required_facts,
             p.rescue_plan_template,
             CAST(JSON_ARRAY() AS JSON) AS resource_baseline,
             SHA2(CONCAT(CAST(p.required_facts AS CHAR), CHAR(10),
                         p.rescue_plan_template, CHAR(10),
                         CAST(JSON_ARRAY() AS CHAR)), 256) AS content_hash
      FROM w_emergency_response_plan p
      WHERE p.plan_status = 1;

    IF (SELECT COUNT(*) FROM resource_unbound_plan_next) <> 16 THEN
      SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Response plan snapshot validation failed';
    END IF;

    UPDATE w_emergency_response_plan p
    JOIN resource_unbound_plan_next n ON n.previous_id = p.id
    SET p.plan_status = 2,
        p.update_time = CURRENT_TIMESTAMP(6)
    WHERE p.plan_status = 1;

    INSERT INTO w_emergency_response_plan
      (plan_id, event_type, event_type_name, plan_version, plan_status,
       required_facts, rescue_plan_template, resource_baseline, content_hash,
       source_document, activation_time, remarks, create_time, update_time)
    SELECT plan_id, event_type, event_type_name, next_version, 1,
           required_facts, rescue_plan_template, resource_baseline, content_hash,
           'resource-unbound-20260912', CURRENT_TIMESTAMP(6),
           '预案仅约束事件类型、待核实事实和处置正文；资源需求根据事件与人工意见动态提出',
           CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
    FROM resource_unbound_plan_next;
  END IF;

  IF (SELECT COUNT(*)
      FROM w_emergency_response_plan
      WHERE plan_status = 1 AND JSON_LENGTH(resource_baseline) = 0) <> 16 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Active resource-unbound plan validation failed';
  END IF;

  COMMIT;
  DROP TEMPORARY TABLE IF EXISTS resource_unbound_plan_next;
END$$
DELIMITER ;

CALL publish_resource_unbound_response_plans_20260912();
DROP PROCEDURE publish_resource_unbound_response_plans_20260912;

SELECT event_type,
       plan_version,
       plan_status,
       JSON_LENGTH(resource_baseline) AS resource_baseline_count,
       CHAR_LENGTH(rescue_plan_template) AS template_length,
       source_document,
       content_hash
FROM w_emergency_response_plan
WHERE plan_status = 1
ORDER BY event_type;
