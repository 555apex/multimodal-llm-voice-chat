-- 应急预案版本表、工单预案快照和资源城市坐标迁移。
-- 执行前请停止后端；本脚本不删除事件、工单或库存数据。

CREATE TABLE IF NOT EXISTS w_emergency_response_plan (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库代理主键，不暴露给业务核心',
    plan_id VARCHAR(40) NOT NULL COMMENT '稳定预案ID，同一事件类型的多个版本共用',
    event_type VARCHAR(10) NOT NULL COMMENT '应急事件类型编码：DT01-DT05、ET101-ET110、ET112',
    event_type_name VARCHAR(50) NOT NULL COMMENT '应急事件类型名称',
    plan_version INT NOT NULL COMMENT '预案版本号，从1开始递增',
    plan_status TINYINT NOT NULL DEFAULT 0 COMMENT '预案状态：0=草稿，1=已发布，2=已退役',
    required_facts JSON NOT NULL COMMENT '模板生成前应核实的现场事实项',
    rescue_plan_template LONGTEXT NOT NULL COMMENT '可填充的救援方案固定模板，【】内为受控变量',
    resource_baseline JSON NOT NULL COMMENT '预案资源基线，BASE为必选，CONDITIONAL为条件增配',
    content_hash CHAR(64) NOT NULL COMMENT '必需事实、模板和资源基线的SHA-256摘要',
    source_document VARCHAR(255) NULL COMMENT '该版本来源文档或审批依据',
    activation_time DATETIME(6) NULL COMMENT '预案发布生效时间',
    remarks VARCHAR(500) NULL COMMENT '版本维护备注',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    create_user VARCHAR(64) NULL COMMENT '创建人，Demo可为空',
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最后更新时间',
    update_user VARCHAR(64) NULL COMMENT '最后更新人，Demo可为空',
    active_event_type VARCHAR(10) GENERATED ALWAYS AS
        (CASE WHEN plan_status = 1 THEN event_type ELSE NULL END) STORED
        COMMENT '用于保证每类事件最多一个已发布版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_response_plan_id_version (plan_id, plan_version),
    UNIQUE KEY uk_response_plan_event_version (event_type, plan_version),
    UNIQUE KEY uk_response_plan_one_active (active_event_type),
    KEY idx_response_plan_query (event_type, plan_status, plan_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='福建公路应急事件版本化处置预案';

DROP PROCEDURE IF EXISTS migrate_emergency_plan_columns;
DELIMITER $$
CREATE PROCEDURE migrate_emergency_plan_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'w_emergency_dispatch_order'
          AND column_name = 'response_plan_id'
    ) THEN
        ALTER TABLE w_emergency_dispatch_order
            ADD COLUMN response_plan_id VARCHAR(40) NULL COMMENT '生成工单所使用的预案ID' AFTER rescue_plan,
            ADD COLUMN response_plan_version INT NULL COMMENT '生成工单所使用的预案版本' AFTER response_plan_id,
            ADD COLUMN response_plan_snapshot JSON NULL COMMENT '生成时冻结的预案全量快照' AFTER response_plan_version,
            ADD KEY idx_dispatch_response_plan (response_plan_id, response_plan_version);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'w_emergency_resource'
          AND column_name = 'longitude'
    ) THEN
        ALTER TABLE w_emergency_resource
            ADD COLUMN longitude DECIMAL(10,6) NULL COMMENT '资源所属地市中心经度（WGS84）' AFTER city_name,
            ADD COLUMN latitude DECIMAL(9,6) NULL COMMENT '资源所属地市中心纬度（WGS84）' AFTER longitude;
    END IF;
END$$
DELIMITER ;
CALL migrate_emergency_plan_columns();
DROP PROCEDURE migrate_emergency_plan_columns;

-- 九市中心点是Demo级调度排序坐标，不代表仓库、队伍或车辆的真实位置。
UPDATE w_emergency_resource SET longitude=119.296500, latitude=26.074500 WHERE city_code='350100';
UPDATE w_emergency_resource SET longitude=118.089400, latitude=24.479800 WHERE city_code='350200';
UPDATE w_emergency_resource SET longitude=119.007700, latitude=25.454100 WHERE city_code='350300';
UPDATE w_emergency_resource SET longitude=117.638900, latitude=26.263400 WHERE city_code='350400';
UPDATE w_emergency_resource SET longitude=118.675700, latitude=24.874100 WHERE city_code='350500';
UPDATE w_emergency_resource SET longitude=117.647100, latitude=24.513000 WHERE city_code='350600';
UPDATE w_emergency_resource SET longitude=118.177700, latitude=26.641800 WHERE city_code='350700';
UPDATE w_emergency_resource SET longitude=117.017500, latitude=25.075100 WHERE city_code='350800';
UPDATE w_emergency_resource SET longitude=119.548200, latitude=26.665600 WHERE city_code='350900';
