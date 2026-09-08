-- 历史方案草稿，未采用：不属于当前版本迁移，协作者启动项目不要执行。
-- 当前城市OD七日统计只读取w_transport_hubs与w_region_code，不依赖本表。
-- 本脚本包含CREATE与INSERT ... ON DUPLICATE KEY UPDATE，可能修改已有数据。
-- 如需研究旧方案，请由数据库负责人确认后仅在隔离实验库使用。
-- 旧方案：路线与福建九市无方向城市对的固定映射表。
-- 数据首次由w_highway_network.start_place/end_place与w_region_code确定。
-- 仅保留起终点均属于福建九市且两端城市不同的跨市路线。

CREATE TABLE IF NOT EXISTS w_route_city_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    route_code VARCHAR(20) NOT NULL COMMENT '路线编号；一条路线全表唯一',
    route_name VARCHAR(100) NOT NULL COMMENT '路线名称，以w_highway_network为准',
    city_a_name VARCHAR(20) NOT NULL COMMENT '城市A名称；无方向含义，固定存行政区划代码较小的城市',
    city_a_region_code CHAR(6) NOT NULL COMMENT '城市A行政区划代码，固定为城市对中较小代码',
    city_b_name VARCHAR(20) NOT NULL COMMENT '城市B名称；无方向含义，固定存行政区划代码较大的城市',
    city_b_region_code CHAR(6) NOT NULL COMMENT '城市B行政区划代码，固定为城市对中较大代码',
    del_flag CHAR(1) NOT NULL DEFAULT 'N' COMMENT '逻辑删除标记：N-有效，Y-停用',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_route_city_mapping_route (route_code),
    KEY idx_route_city_mapping_pair (city_a_region_code, city_b_region_code, del_flag),
    KEY idx_route_city_mapping_city_a (city_a_region_code, del_flag),
    KEY idx_route_city_mapping_city_b (city_b_region_code, del_flag),
    CONSTRAINT ck_route_city_mapping_pair CHECK (
        city_a_region_code < city_b_region_code
    ),
    CONSTRAINT ck_route_city_mapping_flag CHECK (
        del_flag IN ('N', 'Y')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='需求1-7福建九市跨市路线与无方向城市对固定映射';

-- 根据当前活动路网及九市代码生成或刷新固定映射。
INSERT INTO w_route_city_mapping (
    route_code,
    route_name,
    city_a_name,
    city_a_region_code,
    city_b_name,
    city_b_region_code,
    del_flag
)
SELECT
    TRIM(n.route_code) AS route_code,
    TRIM(n.route_name) AS route_name,
    CASE WHEN start_region.code < end_region.code THEN start_region.name ELSE end_region.name END AS city_a_name,
    LEAST(start_region.code, end_region.code) AS city_a_region_code,
    CASE WHEN start_region.code < end_region.code THEN end_region.name ELSE start_region.name END AS city_b_name,
    GREATEST(start_region.code, end_region.code) AS city_b_region_code,
    'N' AS del_flag
FROM w_highway_network n
JOIN w_region_code start_region
  ON start_region.name = TRIM(n.start_place)
 AND start_region.code IN ('350100','350200','350300','350400','350500','350600','350700','350800','350900')
 AND (start_region.del_flag IS NULL OR start_region.del_flag IN ('N','0'))
JOIN w_region_code end_region
  ON end_region.name = TRIM(n.end_place)
 AND end_region.code IN ('350100','350200','350300','350400','350500','350600','350700','350800','350900')
 AND (end_region.del_flag IS NULL OR end_region.del_flag IN ('N','0'))
WHERE (n.del_flag IS NULL OR n.del_flag IN ('N','0'))
  AND n.route_code IS NOT NULL
  AND TRIM(n.route_code) <> ''
  AND n.route_name IS NOT NULL
  AND TRIM(n.route_name) <> ''
  AND start_region.code <> end_region.code
ORDER BY n.route_code
ON DUPLICATE KEY UPDATE
    route_name = VALUES(route_name),
    city_a_name = VALUES(city_a_name),
    city_a_region_code = VALUES(city_a_region_code),
    city_b_name = VALUES(city_b_name),
    city_b_region_code = VALUES(city_b_region_code),
    del_flag = 'N',
    update_time = CURRENT_TIMESTAMP(6);
