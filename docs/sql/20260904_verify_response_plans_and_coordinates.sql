-- 只读核验：预期 active_plan_count=16，其余异常查询均返0行。

SELECT COUNT(*) AS active_plan_count
FROM w_emergency_response_plan
WHERE plan_status = 1;

SELECT event_type, COUNT(*) AS active_versions
FROM w_emergency_response_plan
WHERE plan_status = 1
GROUP BY event_type
HAVING COUNT(*) <> 1;

SELECT plan_id, event_type, plan_version
FROM w_emergency_response_plan
WHERE plan_status = 1
  AND (JSON_LENGTH(required_facts) = 0
       OR JSON_LENGTH(resource_baseline) = 0
       OR rescue_plan_template NOT LIKE '%【事件编号】%'
       OR CHAR_LENGTH(content_hash) <> 64);

SELECT DISTINCT p.event_type, baseline.resource_type_code
FROM w_emergency_response_plan p,
JSON_TABLE(
    p.resource_baseline,
    '$[*]' COLUMNS(resource_type_code VARCHAR(40) PATH '$.resourceTypeCode')
) baseline
LEFT JOIN w_emergency_resource r
  ON r.resource_type_code = baseline.resource_type_code
 AND r.resource_status = 0
 AND (r.del_flag IS NULL OR r.del_flag IN ('N','0'))
WHERE p.plan_status = 1
  AND r.resource_id IS NULL;

SELECT resource_id, city_code, city_name
FROM w_emergency_resource
WHERE resource_status = 0
  AND (del_flag IS NULL OR del_flag IN ('N','0'))
  AND (longitude IS NULL OR latitude IS NULL);

SELECT city_code, COUNT(DISTINCT CONCAT(longitude, ',', latitude)) AS coordinate_versions
FROM w_emergency_resource
WHERE resource_status = 0
  AND (del_flag IS NULL OR del_flag IN ('N','0'))
GROUP BY city_code
HAVING COUNT(DISTINCT CONCAT(longitude, ',', latitude)) <> 1;

SELECT city_code, city_name, MIN(longitude) AS longitude, MIN(latitude) AS latitude,
       COUNT(*) AS resource_pool_count
FROM w_emergency_resource
WHERE resource_status = 0
  AND (del_flag IS NULL OR del_flag IN ('N','0'))
GROUP BY city_code, city_name
ORDER BY city_code;
