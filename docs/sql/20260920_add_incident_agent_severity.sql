-- c_level is an upstream event-level code with an independently maintained 0-4 vocabulary.
-- Keep it untouched and store the RoadAgent assessment in a dedicated nullable field.
SET @agent_severity_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'w_lw_incident'
      AND column_name = 'agent_severity'
);
SET @agent_severity_ddl = IF(@agent_severity_exists = 0,
    'ALTER TABLE w_lw_incident ADD COLUMN agent_severity VARCHAR(32) NULL COMMENT ''智能体事件严重等级：GENERAL/LARGER/MAJOR/ESPECIALLY_MAJOR'' AFTER c_level',
    'SELECT ''agent_severity already exists'' AS migration_status');
PREPARE agent_severity_stmt FROM @agent_severity_ddl;
EXECUTE agent_severity_stmt;
DEALLOCATE PREPARE agent_severity_stmt;
