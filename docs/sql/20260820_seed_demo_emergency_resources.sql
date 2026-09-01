-- 福建九市虚构Demo资源。仅插入不存在的resource_id，不覆盖人工修改过的库存。
-- 共108个城市级聚合资源池，不代表真实应急保障能力。

DROP TEMPORARY TABLE IF EXISTS tmp_demo_resource_city;
CREATE TEMPORARY TABLE tmp_demo_resource_city (
    city_code CHAR(6) PRIMARY KEY,
    city_name VARCHAR(20) NOT NULL,
    capacity_tier TINYINT NOT NULL
);
INSERT INTO tmp_demo_resource_city VALUES
('350100','福州',3),('350200','厦门',3),('350300','莆田',2),
('350400','三明',1),('350500','泉州',3),('350600','漳州',2),
('350700','南平',1),('350800','龙岩',1),('350900','宁德',1);

DROP TEMPORARY TABLE IF EXISTS tmp_demo_resource_type;
CREATE TEMPORARY TABLE tmp_demo_resource_type (
    type_code VARCHAR(40) PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    capability VARCHAR(500) NOT NULL,
    applicable_event_types JSON NOT NULL,
    specialist_group VARCHAR(20) NULL
);
INSERT INTO tmp_demo_resource_type VALUES
('TRAFFIC_CONTROL_TEAM','交通管制队伍','组','现场警戒、交通管制、分流疏导',JSON_ARRAY('ET101','DT01'),NULL),
('ROAD_RESCUE_TEAM','公路抢险队伍','组','道路抢通、边坡和路基应急处置',JSON_ARRAY('ET101','DT01'),NULL),
('TOW_TRUCK','清障拖车','辆','事故车辆和故障车辆拖移清障',JSON_ARRAY('ET101'),NULL),
('WARNING_EQUIPMENT','交通警示设施','套','锥桶、标志牌、临时隔离和警示',JSON_ARRAY('ET101','DT01'),NULL),
('EXCAVATOR','挖掘机','台','土石方开挖、塌方清理和便道修筑',JSON_ARRAY('DT01'),NULL),
('LOADER','装载机','台','散落物、土石和障碍物装运清理',JSON_ARRAY('ET101','DT01'),NULL),
('DUMP_TRUCK','自卸运输车','辆','土石方、散落物和抢通物料运输',JSON_ARRAY('DT01'),NULL),
('DRAINAGE_PUMP','移动排水泵组','套','道路积水、边沟和涵洞应急排水',JSON_ARRAY('ET101','DT01'),NULL),
('MOBILE_LIGHTING','移动照明设备','套','夜间救援和连续作业照明',JSON_ARRAY('ET101','DT01'),NULL),
('AMBULANCE','医疗救护车','辆','事故伤员现场救治和转运',JSON_ARRAY('ET101'),NULL),
('FIRE_RESCUE_TEAM','消防救援队伍','组','事故破拆、人员搜救和隧道救援',JSON_ARRAY('ET101'),NULL),
('GEOLOGICAL_TEAM','地质技术组','组','边坡稳定性、落石和地质灾害风险研判',JSON_ARRAY('DT01'),'GEO'),
('CRANE','重型汽车起重机','台','重型车辆、构件和大型障碍物吊移',JSON_ARRAY('ET101','DT01'),'CRANE');

INSERT IGNORE INTO w_emergency_resource (
    resource_id, resource_type_code, resource_type_name, resource_name,
    city_code, city_name, unit, capability, applicable_event_types,
    total_quantity, available_quantity, reserved_quantity, dispatched_quantity,
    minimum_reserve_quantity, resource_status, lock_version, del_flag,
    create_time, update_time
)
SELECT
    CONCAT('ER-', city.city_code, '-', resource_type.type_code),
    resource_type.type_code, resource_type.type_name,
    CONCAT(city.city_name, '市', resource_type.type_name, '资源池'),
    city.city_code, city.city_name, resource_type.unit, resource_type.capability,
    resource_type.applicable_event_types,
    CASE
        WHEN resource_type.type_code = 'WARNING_EQUIPMENT' THEN
            CASE city.capacity_tier WHEN 3 THEN 24 WHEN 2 THEN 16 ELSE 12 END
        WHEN resource_type.type_code IN ('DUMP_TRUCK','TOW_TRUCK','AMBULANCE') THEN
            CASE city.capacity_tier WHEN 3 THEN 8 WHEN 2 THEN 6 ELSE 4 END
        WHEN resource_type.specialist_group IS NOT NULL THEN
            CASE city.capacity_tier WHEN 3 THEN 3 WHEN 2 THEN 2 ELSE 1 END
        ELSE CASE city.capacity_tier WHEN 3 THEN 6 WHEN 2 THEN 4 ELSE 3 END
    END AS total_quantity,
    CASE
        WHEN resource_type.type_code = 'WARNING_EQUIPMENT' THEN
            CASE city.capacity_tier WHEN 3 THEN 24 WHEN 2 THEN 16 ELSE 12 END
        WHEN resource_type.type_code IN ('DUMP_TRUCK','TOW_TRUCK','AMBULANCE') THEN
            CASE city.capacity_tier WHEN 3 THEN 8 WHEN 2 THEN 6 ELSE 4 END
        WHEN resource_type.specialist_group IS NOT NULL THEN
            CASE city.capacity_tier WHEN 3 THEN 3 WHEN 2 THEN 2 ELSE 1 END
        ELSE CASE city.capacity_tier WHEN 3 THEN 6 WHEN 2 THEN 4 ELSE 3 END
    END AS available_quantity,
    0, 0,
    CASE WHEN resource_type.type_code = 'WARNING_EQUIPMENT' THEN 3 ELSE 1 END,
    0, 0, 'N', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM tmp_demo_resource_city city
CROSS JOIN tmp_demo_resource_type resource_type
WHERE resource_type.specialist_group IS NULL
   OR (resource_type.specialist_group = 'GEO'
       AND city.city_code IN ('350100','350400','350700','350800','350900'))
   OR (resource_type.specialist_group = 'CRANE'
       AND city.city_code IN ('350100','350200','350500','350600'));

DROP TEMPORARY TABLE tmp_demo_resource_type;
DROP TEMPORARY TABLE tmp_demo_resource_city;

SELECT COUNT(*) AS demo_resource_pool_count FROM w_emergency_resource;
SELECT city_name, COUNT(*) AS resource_type_count,
       SUM(total_quantity) AS total_resource_quantity
FROM w_emergency_resource
WHERE resource_status = 0 AND (del_flag IS NULL OR del_flag IN ('N','0'))
GROUP BY city_code, city_name
ORDER BY city_code;
