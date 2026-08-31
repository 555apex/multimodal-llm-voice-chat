-- MySQL 8.0：福建九市数据库应急资源调度结构迁移。
-- 可重复核验执行，不由应用启动自动触发。

DELIMITER $$

DROP PROCEDURE IF EXISTS apply_emergency_resource_dispatch_schema$$
CREATE PROCEDURE apply_emergency_resource_dispatch_schema()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_abnormal_event'
          AND COLUMN_NAME = 'event_city_code'
    ) THEN
        ALTER TABLE w_abnormal_event
            ADD COLUMN event_city_code CHAR(6) NULL COMMENT '事件调度城市行政区划编码' AFTER description;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_abnormal_event'
          AND COLUMN_NAME = 'event_city_name'
    ) THEN
        ALTER TABLE w_abnormal_event
            ADD COLUMN event_city_name VARCHAR(20) NULL COMMENT '事件调度城市名称' AFTER event_city_code;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_abnormal_event'
          AND INDEX_NAME = 'idx_abnormal_event_city'
    ) THEN
        ALTER TABLE w_abnormal_event ADD INDEX idx_abnormal_event_city (event_city_code, event_status);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_dispatch_order'
          AND COLUMN_NAME = 'resource_requirements'
    ) THEN
        ALTER TABLE w_emergency_dispatch_order
            ADD COLUMN resource_requirements JSON NULL COMMENT '模型在数据库白名单内提出的资源需求' AFTER event_snapshot;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_dispatch_order'
          AND COLUMN_NAME = 'resource_shortages'
    ) THEN
        ALTER TABLE w_emergency_dispatch_order
            ADD COLUMN resource_shortages JSON NULL COMMENT '全省匹配后仍存在的资源缺口' AFTER resource_list;
    END IF;
    UPDATE w_emergency_dispatch_order
    SET resource_requirements = COALESCE(resource_requirements, JSON_ARRAY()),
        resource_shortages = COALESCE(resource_shortages, JSON_ARRAY());
    ALTER TABLE w_emergency_dispatch_order
        MODIFY COLUMN resource_requirements JSON NOT NULL COMMENT '模型在数据库白名单内提出的资源需求',
        MODIFY COLUMN resource_shortages JSON NOT NULL COMMENT '全省匹配后仍存在的资源缺口',
        MODIFY COLUMN resource_list JSON NOT NULL COMMENT '经过库存校验并完成软占用的实际资源快照';
END$$

CALL apply_emergency_resource_dispatch_schema()$$
DROP PROCEDURE apply_emergency_resource_dispatch_schema$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS w_emergency_resource (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    resource_id VARCHAR(40) NOT NULL COMMENT '稳定资源池编号',
    resource_type_code VARCHAR(40) NOT NULL COMMENT '模型可选择的资源类型编码',
    resource_type_name VARCHAR(50) NOT NULL COMMENT '资源类型名称',
    resource_name VARCHAR(100) NOT NULL COMMENT '城市级资源池名称',
    city_code CHAR(6) NOT NULL COMMENT '福建地级市行政区划编码',
    city_name VARCHAR(20) NOT NULL COMMENT '资源所在城市',
    unit VARCHAR(20) NOT NULL COMMENT '计量单位',
    capability VARCHAR(500) NOT NULL COMMENT '能力说明',
    applicable_event_types JSON NOT NULL COMMENT '适用事件类型编码数组',
    total_quantity INT NOT NULL COMMENT '资源总量',
    available_quantity INT NOT NULL COMMENT '当前可用数量',
    reserved_quantity INT NOT NULL DEFAULT 0 COMMENT '方案审批期间软占用数量',
    dispatched_quantity INT NOT NULL DEFAULT 0 COMMENT '三级批准后已调度数量',
    minimum_reserve_quantity INT NOT NULL DEFAULT 0 COMMENT '跨市支援后最低保有量',
    resource_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-正常，1-维护，2-停用',
    lock_version BIGINT NOT NULL DEFAULT 0 COMMENT '库存乐观锁版本',
    del_flag CHAR(1) NULL DEFAULT 'N' COMMENT '逻辑删除标记',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    create_user BIGINT NULL,
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_user BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emergency_resource_id (resource_id),
    KEY idx_emergency_resource_match (resource_type_code, city_code, resource_status, del_flag),
    KEY idx_emergency_resource_city (city_code, resource_status, resource_type_code),
    CONSTRAINT ck_emergency_resource_quantities CHECK (
        total_quantity >= 0 AND available_quantity >= 0
        AND reserved_quantity >= 0 AND dispatched_quantity >= 0
        AND minimum_reserve_quantity >= 0
        AND available_quantity + reserved_quantity + dispatched_quantity <= total_quantity
        AND minimum_reserve_quantity <= total_quantity
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-福建九市城市级应急资源库存';

CREATE TABLE IF NOT EXISTS w_emergency_resource_allocation (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '内部主键',
    allocation_id VARCHAR(40) NOT NULL COMMENT '稳定资源占用编号',
    workflow_id VARCHAR(40) NOT NULL COMMENT '三级工作流编号',
    event_id BIGINT NOT NULL COMMENT '异常事件ID',
    plan_id VARCHAR(40) NOT NULL COMMENT '调度方案编号',
    plan_version INT NOT NULL COMMENT '调度方案版本',
    resource_id VARCHAR(40) NOT NULL COMMENT '资源池编号',
    resource_type_code VARCHAR(40) NOT NULL COMMENT '资源类型编码快照',
    resource_type_name VARCHAR(50) NOT NULL COMMENT '资源类型名称快照',
    resource_name VARCHAR(100) NOT NULL COMMENT '资源池名称快照',
    source_city_code CHAR(6) NOT NULL COMMENT '来源城市编码快照',
    source_city_name VARCHAR(20) NOT NULL COMMENT '来源城市名称快照',
    allocated_quantity INT NOT NULL COMMENT '实际占用数量',
    unit VARCHAR(20) NOT NULL COMMENT '单位快照',
    purpose VARCHAR(300) NOT NULL COMMENT '本次调度用途',
    estimated_distance_km DECIMAL(8,1) NOT NULL COMMENT '城市中心直线估算距离',
    dispatch_scope VARCHAR(20) NOT NULL COMMENT 'LOCAL或CROSS_CITY',
    allocation_status TINYINT NOT NULL DEFAULT 0 COMMENT '0-预留，1-已调度，2-已释放',
    reserved_time DATETIME(6) NOT NULL,
    dispatched_time DATETIME(6) NULL,
    released_time DATETIME(6) NULL,
    release_reason VARCHAR(500) NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_allocation_id (allocation_id),
    UNIQUE KEY uk_resource_allocation_plan_resource (plan_id, plan_version, resource_id),
    KEY idx_resource_allocation_workflow (workflow_id, allocation_status, id),
    KEY idx_resource_allocation_resource (resource_id, allocation_status, id),
    CONSTRAINT fk_resource_allocation_workflow FOREIGN KEY (workflow_id)
        REFERENCES w_emergency_dispatch_workflow (workflow_id),
    CONSTRAINT fk_resource_allocation_resource FOREIGN KEY (resource_id)
        REFERENCES w_emergency_resource (resource_id),
    CONSTRAINT ck_resource_allocation_quantity CHECK (allocated_quantity > 0),
    CONSTRAINT ck_resource_allocation_distance CHECK (estimated_distance_km >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='路网智能体-方案版本资源软占用与调度流水';

-- 将当前Demo事件映射到福建九市。福清、闽侯、平潭均归入福州调度范围。
UPDATE w_abnormal_event
SET event_city_code = CASE
        WHEN description LIKE '%厦门%' THEN '350200'
        WHEN description LIKE '%莆田%' THEN '350300'
        WHEN description LIKE '%三明%' THEN '350400'
        WHEN description LIKE '%泉州%' THEN '350500'
        WHEN description LIKE '%漳州%' THEN '350600'
        WHEN description LIKE '%南平%' OR description LIKE '%武夷山%' THEN '350700'
        WHEN description LIKE '%龙岩%' THEN '350800'
        WHEN description LIKE '%宁德%' THEN '350900'
        WHEN description LIKE '%福州%' OR description LIKE '%福清%'
          OR description LIKE '%闽侯%' OR description LIKE '%平潭%' THEN '350100'
        ELSE event_city_code
    END,
    event_city_name = CASE
        WHEN description LIKE '%厦门%' THEN '厦门'
        WHEN description LIKE '%莆田%' THEN '莆田'
        WHEN description LIKE '%三明%' THEN '三明'
        WHEN description LIKE '%泉州%' THEN '泉州'
        WHEN description LIKE '%漳州%' THEN '漳州'
        WHEN description LIKE '%南平%' OR description LIKE '%武夷山%' THEN '南平'
        WHEN description LIKE '%龙岩%' THEN '龙岩'
        WHEN description LIKE '%宁德%' THEN '宁德'
        WHEN description LIKE '%福州%' OR description LIKE '%福清%'
          OR description LIKE '%闽侯%' OR description LIKE '%平潭%' THEN '福州'
        ELSE event_city_name
    END
WHERE event_city_code IS NULL OR event_city_name IS NULL;

SELECT COUNT(*) AS resource_table_ready
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_resource';
SELECT COUNT(*) AS allocation_table_ready
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'w_emergency_resource_allocation';
SELECT COUNT(*) AS events_without_city
FROM w_abnormal_event
WHERE event_city_code IS NULL OR event_city_name IS NULL;
