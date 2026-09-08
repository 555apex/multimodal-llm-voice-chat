-- 只读验收：应返回33条种子记录，其中2026正式数据13条、未来法定日期14条、模拟活动6条。
SELECT data_status, event_type, COUNT(*) AS row_count
FROM w_festival_data
WHERE del_flag IN ('N','0')
GROUP BY data_status, event_type
ORDER BY data_status, event_type;

SELECT event_code, event_name, event_type, impact_start_time, impact_end_time,
       scope_type, region_code, affected_route_codes, data_status, enabled
FROM w_festival_data
WHERE del_flag IN ('N','0')
ORDER BY impact_start_time, event_code;

-- 应返回0：启用记录的时间、范围与JSON均合法。
SELECT COUNT(*) AS invalid_row_count
FROM w_festival_data
WHERE enabled = 1
  AND del_flag IN ('N','0')
  AND (
      event_end_time < event_start_time
      OR impact_start_time > event_start_time
      OR impact_end_time < event_end_time
      OR (scope_type = 'CITY' AND region_code IS NULL)
      OR (scope_type = 'ROUTE' AND (affected_route_codes IS NULL OR JSON_LENGTH(affected_route_codes) = 0))
  );
