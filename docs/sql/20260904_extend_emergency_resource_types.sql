-- 为16类应急事件补齐资源适用关系，并新墖九市4类Demo资源。
-- 数量只用于流程测试，不代表真实保障能力。

UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','DT05','ET101','ET102','ET105','ET106','ET108','ET109','ET110','ET112') WHERE resource_type_code='TRAFFIC_CONTROL_TEAM';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','DT05','ET103','ET109','ET110') WHERE resource_type_code='ROAD_RESCUE_TEAM';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('ET101','ET104','ET105','ET106','ET107','ET109') WHERE resource_type_code='TOW_TRUCK';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','DT05','ET101','ET102','ET103','ET104','ET105','ET106','ET107','ET108','ET109','ET110','ET112') WHERE resource_type_code='WARNING_EQUIPMENT';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','ET110') WHERE resource_type_code='EXCAVATOR';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','DT05','ET103','ET109','ET110','ET112') WHERE resource_type_code='LOADER';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','DT05','ET103','ET110') WHERE resource_type_code='DUMP_TRUCK';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT05') WHERE resource_type_code='DRAINAGE_PUMP';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','DT05','ET101','ET102','ET106','ET108','ET110','ET112') WHERE resource_type_code='MOBILE_LIGHTING';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','DT05','ET102','ET106') WHERE resource_type_code='AMBULANCE';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04','ET102','ET106') WHERE resource_type_code='FIRE_RESCUE_TEAM';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT01','DT02','DT03','DT04') WHERE resource_type_code='GEOLOGICAL_TEAM';
UPDATE w_emergency_resource SET applicable_event_types=JSON_ARRAY('DT04','ET106','ET109') WHERE resource_type_code='CRANE';

DROP TEMPORARY TABLE IF EXISTS tmp_emergency_city;
CREATE TEMPORARY TABLE tmp_emergency_city(city_code CHAR(6) PRIMARY KEY,city_name VARCHAR(20),tier TINYINT);
INSERT INTO tmp_emergency_city VALUES
('350100','福州',3),('350200','厦门',3),('350300','莆田',2),('350400','三明',1),
('350500','泉州',3),('350600','漳州',2),('350700','南平',1),('350800','龙岩',1),('350900','宁德',1);

DROP TEMPORARY TABLE IF EXISTS tmp_new_resource_type;
CREATE TEMPORARY TABLE tmp_new_resource_type(
 type_code VARCHAR(40) PRIMARY KEY,type_name VARCHAR(50),unit VARCHAR(20),capability VARCHAR(500),types JSON);
INSERT INTO tmp_new_resource_type VALUES
('ROAD_PATROL_TEAM','道路巡查队伍','组','事件核查、动态巡查、交通异常发现与报送',JSON_ARRAY('ET101','ET102','ET103','ET104','ET105','ET106','ET107','ET108','ET109','ET112')),
('EQUIPMENT_REPAIR_TEAM','机电设备抢修队伍','组','隧道、收费、监控和交通机电设备故障抢修',JSON_ARRAY('ET104','ET107')),
('SNOW_REMOVAL_VEHICLE','除雪作业车辆','辆','道路推雪、扫雪和结冰路面应急作业',JSON_ARRAY('ET112')),
('DEICING_MATERIAL','融雪防滑物资','吨','融雪剂和防滑料的应急投放',JSON_ARRAY('ET112'));

INSERT IGNORE INTO w_emergency_resource
(resource_id,resource_type_code,resource_type_name,resource_name,city_code,city_name,unit,
 capability,applicable_event_types,total_quantity,available_quantity,reserved_quantity,
 dispatched_quantity,minimum_reserve_quantity,resource_status,lock_version,del_flag,create_time,update_time)
SELECT CONCAT('ER-',c.city_code,'-',t.type_code),t.type_code,t.type_name,
       CONCAT(c.city_name,t.type_name),c.city_code,c.city_name,t.unit,t.capability,t.types,
       CASE t.type_code
         WHEN 'ROAD_PATROL_TEAM' THEN CASE c.tier WHEN 3 THEN 8 WHEN 2 THEN 6 ELSE 4 END
         WHEN 'EQUIPMENT_REPAIR_TEAM' THEN CASE c.tier WHEN 3 THEN 5 WHEN 2 THEN 4 ELSE 3 END
         WHEN 'SNOW_REMOVAL_VEHICLE' THEN CASE c.tier WHEN 3 THEN 6 WHEN 2 THEN 4 ELSE 3 END
         ELSE CASE c.tier WHEN 3 THEN 20 WHEN 2 THEN 14 ELSE 10 END END,
       CASE t.type_code
         WHEN 'ROAD_PATROL_TEAM' THEN CASE c.tier WHEN 3 THEN 8 WHEN 2 THEN 6 ELSE 4 END
         WHEN 'EQUIPMENT_REPAIR_TEAM' THEN CASE c.tier WHEN 3 THEN 5 WHEN 2 THEN 4 ELSE 3 END
         WHEN 'SNOW_REMOVAL_VEHICLE' THEN CASE c.tier WHEN 3 THEN 6 WHEN 2 THEN 4 ELSE 3 END
         ELSE CASE c.tier WHEN 3 THEN 20 WHEN 2 THEN 14 ELSE 10 END END,
       0,0,CASE WHEN t.type_code='DEICING_MATERIAL' THEN 3 ELSE 1 END,0,0,'N',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
FROM tmp_emergency_city c CROSS JOIN tmp_new_resource_type t;

DROP TEMPORARY TABLE tmp_new_resource_type;
DROP TEMPORARY TABLE tmp_emergency_city;

SELECT resource_type_code,COUNT(*) AS city_pool_count
FROM w_emergency_resource GROUP BY resource_type_code ORDER BY resource_type_code;
