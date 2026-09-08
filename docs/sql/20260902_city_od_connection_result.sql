-- 历史方案草稿，未采用：不属于当前版本迁移，协作者启动项目不要执行。
-- 当前城市OD七日统计只读取w_transport_hubs与w_region_code，不依赖本表。
-- 本脚本包含CREATE与INSERT ... ON DUPLICATE KEY UPDATE，可能修改已有数据。
-- 如需研究旧方案，请由数据库负责人确认后仅在隔离实验库使用。
-- 旧方案：福建九市城市间OD联系分析结果表（精简版）。
-- 旧方案不区分行驶方向，city_a/city_b仅表示一个无方向城市对。
-- 同一城市对固定将行政区划代码较小的城市存入city_a，避免同一城市对重复存储。

CREATE TABLE IF NOT EXISTS w_city_od_connection_result (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    city_a_name VARCHAR(20) NOT NULL COMMENT '城市A名称，如福州市；无起点或终点方向含义',
    city_a_region_code CHAR(6) NOT NULL COMMENT '城市A行政区划代码；固定存城市对中较小的行政区划代码',
    city_b_name VARCHAR(20) NOT NULL COMMENT '城市B名称，如厦门市；无起点或终点方向含义',
    city_b_region_code CHAR(6) NOT NULL COMMENT '城市B行政区划代码；固定存城市对中较大的行政区划代码',
    route_code VARCHAR(20) NOT NULL COMMENT '连接两城市的国省道路线编号，如G324',
    route_name VARCHAR(100) NOT NULL COMMENT '路线名称，如福州—昆明',
    city_a_avg_daily_flow DECIMAL(14,2) NOT NULL COMMENT '城市A在该路线相关卡口的平均日流量（辆/日）',
    city_b_avg_daily_flow DECIMAL(14,2) NOT NULL COMMENT '城市B在该路线相关卡口的平均日流量（辆/日）',
    connection_flow DECIMAL(14,2) NOT NULL COMMENT '该路线的城市间OD联系量基础值（辆/日），取两城市路线日均流量的较小值',
    car_connection_flow DECIMAL(14,2) NULL COMMENT '小型客车OD联系量基础值（辆/日）',
    bus_connection_flow DECIMAL(14,2) NULL COMMENT '中型客车OD联系量基础值（辆/日）',
    truck_connection_flow DECIMAL(14,2) NULL COMMENT '大型货车OD联系量基础值（辆/日）',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据创建时间，用于判断数据新旧',

    PRIMARY KEY (id),
    UNIQUE KEY uk_city_od_pair_route (
        city_a_region_code, city_b_region_code, route_code
    ),
    KEY idx_city_od_city_a (city_a_region_code, create_time),
    KEY idx_city_od_city_b (city_b_region_code, create_time),
    KEY idx_city_od_route (route_code, create_time),

    CONSTRAINT ck_city_od_city_pair_v2 CHECK (city_a_region_code < city_b_region_code),
    CONSTRAINT ck_city_od_nonnegative_v2 CHECK (
        city_a_avg_daily_flow >= 0
        AND city_b_avg_daily_flow >= 0
        AND connection_flow >= 0
        AND (car_connection_flow IS NULL OR car_connection_flow >= 0)
        AND (bus_connection_flow IS NULL OR bus_connection_flow >= 0)
        AND (truck_connection_flow IS NULL OR truck_connection_flow >= 0)
    ),
    CONSTRAINT ck_city_od_connection_limit_v2 CHECK (
        connection_flow <= city_a_avg_daily_flow
        AND connection_flow <= city_b_avg_daily_flow
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='需求1-7福建九市无方向城市间OD联系分析结果';

-- 示例：数值仅用于说明字段口径，协作者开始维护正式数据时可以覆盖或删除。
INSERT INTO w_city_od_connection_result (
    city_a_name,
    city_a_region_code,
    city_b_name,
    city_b_region_code,
    route_code,
    route_name,
    city_a_avg_daily_flow,
    city_b_avg_daily_flow,
    connection_flow,
    car_connection_flow,
    bus_connection_flow,
    truck_connection_flow,
    create_time
) VALUES (
    '福州市',
    '350100',
    '厦门市',
    '350200',
    'G324',
    '福州—昆明',
    12500.00,
    11800.00,
    11800.00,
    8260.00,
    1180.00,
    2360.00,
    '2026-09-02 10:00:00.000000'
) ON DUPLICATE KEY UPDATE
    city_a_name = VALUES(city_a_name),
    city_b_name = VALUES(city_b_name),
    route_name = VALUES(route_name),
    city_a_avg_daily_flow = VALUES(city_a_avg_daily_flow),
    city_b_avg_daily_flow = VALUES(city_b_avg_daily_flow),
    connection_flow = VALUES(connection_flow),
    car_connection_flow = VALUES(car_connection_flow),
    bus_connection_flow = VALUES(bus_connection_flow),
    truck_connection_flow = VALUES(truck_connection_flow),
    create_time = VALUES(create_time);
