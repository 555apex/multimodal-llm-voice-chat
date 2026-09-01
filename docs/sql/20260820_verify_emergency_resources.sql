-- 应急资源库存与占用一致性只读核验。
SELECT COUNT(*) AS resource_pool_count,
       SUM(total_quantity) AS total_quantity,
       SUM(available_quantity) AS available_quantity,
       SUM(reserved_quantity) AS reserved_quantity,
       SUM(dispatched_quantity) AS dispatched_quantity
FROM w_emergency_resource;

SELECT city_code, city_name, resource_type_code, resource_type_name,
       total_quantity, available_quantity, reserved_quantity,
       dispatched_quantity, minimum_reserve_quantity, lock_version
FROM w_emergency_resource
ORDER BY city_code, resource_type_code;

SELECT COUNT(*) AS invalid_quantity_rows
FROM w_emergency_resource
WHERE total_quantity < 0 OR available_quantity < 0 OR reserved_quantity < 0
   OR dispatched_quantity < 0 OR minimum_reserve_quantity < 0
   OR available_quantity + reserved_quantity + dispatched_quantity > total_quantity;

SELECT allocation_status, COUNT(*) AS allocation_count,
       SUM(allocated_quantity) AS allocation_quantity
FROM w_emergency_resource_allocation
GROUP BY allocation_status
ORDER BY allocation_status;

SELECT COUNT(*) AS events_without_city
FROM w_abnormal_event
WHERE event_city_code IS NULL OR event_city_name IS NULL;
