-- 养护智能问答项目库、模板和文档快照。脚本可重复执行，不修改既有交通业务表。
CREATE TABLE IF NOT EXISTS w_repair_road (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    project_code VARCHAR(40) NOT NULL COMMENT '稳定养护项目编号',
    route_code VARCHAR(20) NOT NULL COMMENT '来源真实路线编号',
    route_name VARCHAR(200) NOT NULL COMMENT '来源真实路线名称',
    route_section VARCHAR(255) NOT NULL COMMENT '来源真实路段名称',
    facility_type VARCHAR(40) NOT NULL COMMENT '养护对象类别',
    maintenance_type VARCHAR(24) NOT NULL COMMENT '预防养护/修复养护/专项养护/应急养护',
    repair_scale VARCHAR(16) NOT NULL DEFAULT '不适用' COMMENT '大修/中修/小修/不适用',
    disease_description VARCHAR(500) NOT NULL COMMENT '受控生成的主要病害描述',
    recommended_action VARCHAR(500) NOT NULL COMMENT '建议养护措施',
    urgency VARCHAR(16) NOT NULL COMMENT '紧急/较高/一般',
    priority_score INT NOT NULL COMMENT '项目排序分值',
    planned_start_date DATE NOT NULL COMMENT '建议开工日期',
    duration_days INT NOT NULL COMMENT '预计工期天数',
    contractor_name VARCHAR(160) NOT NULL COMMENT '建议施工单位',
    budget_wan DECIMAL(14,2) NOT NULL COMMENT '计划预算万元',
    project_status VARCHAR(24) NOT NULL DEFAULT '待安排' COMMENT '项目状态',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否参与问答',
    generation_batch CHAR(64) NOT NULL COMMENT '来源路段批次摘要',
    data_origin VARCHAR(24) NOT NULL DEFAULT 'RULE_GENERATED' COMMENT '数据来源',
    manual_locked TINYINT NOT NULL DEFAULT 0 COMMENT '人工锁定后定时刷新不覆盖',
    del_flag CHAR(1) NOT NULL DEFAULT 'N' COMMENT '逻辑删除标记',
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_repair_project_code (project_code),
    UNIQUE KEY uk_repair_source_object (route_code, route_section, facility_type),
    KEY idx_repair_active_priority (enabled, del_flag, priority_score),
    CONSTRAINT ck_repair_enabled CHECK (enabled IN (0,1)),
    CONSTRAINT ck_repair_locked CHECK (manual_locked IN (0,1)),
    CONSTRAINT ck_repair_duration CHECK (duration_days > 0),
    CONSTRAINT ck_repair_budget CHECK (budget_wan >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='福建普通国省干线养护工程项目库';

CREATE TABLE IF NOT EXISTS w_maintenance_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_key VARCHAR(64) NOT NULL,
    template_name VARCHAR(160) NOT NULL,
    template_version INT NOT NULL DEFAULT 1,
    template_body LONGTEXT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_maintenance_template_version (template_key, template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='养护计划、方案与报告版本化正文模板';

CREATE TABLE IF NOT EXISTS w_maintenance_document_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id VARCHAR(64) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    report_year INT NOT NULL,
    project_code VARCHAR(40) NULL,
    project_code_key VARCHAR(40) NOT NULL DEFAULT '',
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    content_json LONGTEXT NOT NULL,
    source_batch_id CHAR(64) NOT NULL,
    version_no INT NOT NULL DEFAULT 1,
    data_origin VARCHAR(24) NOT NULL DEFAULT 'RULE_AND_MODEL',
    generated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_maintenance_document_id (document_id),
    UNIQUE KEY uk_maintenance_snapshot (document_type, report_year, project_code_key, source_batch_id, version_no),
    KEY idx_maintenance_document_lookup (document_type, report_year, source_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='养护预安排、方案比选与报告可复现快照';

INSERT INTO w_maintenance_template
(template_key, template_name, template_version, template_body, enabled)
VALUES
('PREPLAN','养护预安排计划模板',1,'按总体安排、项目实施安排、组织实施要求三部分撰写。突出优先级、施工窗口、工期、施工单位、预算和安全质量要求。',1),
('SCHEME_COMPARISON','养护工程方案比选预案模板',1,'对同一项目形成局部处治、综合修复、强化处治三套方案，从适用条件、工期、预算、耐久性、交通影响和风险进行比较，明确推荐方案。',1),
('ANNUAL_STATISTICS','年度养护统计分析报告模板',1,'按年度工作概况、项目结构、投资构成、实施情况、主要问题和下一年度建议撰写。数字必须来自结构化快照。',1),
('DISEASE_DISTRIBUTION','路网病害分布分析报告模板',1,'按分析范围、设施类别分布、路线和路段明细、重点问题、养护建议撰写，不生成没有来源的地理坐标。',1),
('MAJOR_REPAIR_EVALUATION','年度大中修工程考核评估报告模板',1,'按项目概况、计划完成、质量达标、工期控制、预算执行、问题整改和综合评价撰写。',1),
('ROUTINE_SCORE','日常养护量化评分报告模板',1,'按巡查覆盖、整改及时、作业质量、安全管理、资料管理五个维度形成百分制评价和整改建议。',1),
('SERVICE_SATISFACTION','服务站点服务质量满意度评估报告模板',1,'按环境卫生、停车秩序、便民服务、服务态度、信息服务五个维度分析，站点只使用绑定真实路线的匿名样本。',1)
ON DUPLICATE KEY UPDATE
  template_name=VALUES(template_name), template_body=VALUES(template_body), enabled=VALUES(enabled),
  update_time=CURRENT_TIMESTAMP(6);

SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name IN ('w_repair_road','w_maintenance_template','w_maintenance_document_snapshot')
ORDER BY table_name;
