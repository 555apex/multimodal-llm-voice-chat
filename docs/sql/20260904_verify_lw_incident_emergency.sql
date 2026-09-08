-- 只读验收脚本，不修改任何数据。
SELECT COUNT(*) AS total_incidents,
       COUNT(DISTINCT c_no) AS unique_c_no_count,
       SUM(c_type='4' AND status='1' AND completed=0 AND deleted=0) AS pending_emergency_count,
       SUM(c_type<>'4') AS non_emergency_control_count
FROM w_lw_incident;

SELECT event_type,COUNT(*) AS event_count
FROM w_lw_incident
WHERE c_type='4' AND status='1' AND completed=0 AND deleted=0
GROUP BY event_type ORDER BY event_type;

SELECT COUNT(*) AS workflow_count FROM w_emergency_dispatch_workflow;
SELECT COUNT(*) AS dispatch_order_count FROM w_emergency_dispatch_order;
SELECT COUNT(*) AS allocation_count FROM w_emergency_resource_allocation;
SELECT COUNT(*) AS classification_log_count FROM w_emergency_event_classification;
SELECT COUNT(*) AS legacy_event_count,
       SUM(event_status=0) AS legacy_pending_count
FROM w_abnormal_event;

SELECT resource_type_code,COUNT(*) AS city_pool_count,
       SUM(total_quantity) total_quantity,SUM(available_quantity) available_quantity,
       SUM(reserved_quantity) reserved_quantity,SUM(dispatched_quantity) dispatched_quantity
FROM w_emergency_resource GROUP BY resource_type_code ORDER BY resource_type_code;

SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,CHARACTER_SET_NAME,COLLATION_NAME,COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA=DATABASE()
  AND ((TABLE_NAME='w_lw_incident' AND COLUMN_NAME IN ('c_no','c_type','event_type','status'))
    OR (TABLE_NAME IN ('w_emergency_dispatch_order','w_emergency_dispatch_workflow','w_emergency_resource_allocation') AND COLUMN_NAME='event_id')
    OR (TABLE_NAME='w_emergency_dispatch_workflow' AND COLUMN_NAME='terminal_reason'))
ORDER BY TABLE_NAME,ORDINAL_POSITION;

-- 必须返回0；非0说明c_no与下游event_id仍存在字符排序规则不一致，关联查询会报1267。
SELECT COUNT(*) AS incompatible_event_id_collation_count
FROM information_schema.COLUMNS downstream
JOIN information_schema.COLUMNS source_column
  ON source_column.TABLE_SCHEMA=downstream.TABLE_SCHEMA
 AND source_column.TABLE_NAME='w_lw_incident'
 AND source_column.COLUMN_NAME='c_no'
WHERE downstream.TABLE_SCHEMA=DATABASE()
  AND downstream.TABLE_NAME IN (
      'w_emergency_dispatch_order',
      'w_emergency_dispatch_workflow',
      'w_emergency_resource_allocation',
      'w_emergency_event_classification'
  )
  AND downstream.COLUMN_NAME='event_id'
  AND (downstream.CHARACTER_SET_NAME<>source_column.CHARACTER_SET_NAME
       OR downstream.COLLATION_NAME<>source_column.COLLATION_NAME);
