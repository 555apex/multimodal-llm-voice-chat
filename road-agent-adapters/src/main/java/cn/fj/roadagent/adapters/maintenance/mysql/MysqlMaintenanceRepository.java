package cn.fj.roadagent.adapters.maintenance.mysql;

import cn.fj.roadagent.application.maintenance.MaintenanceDocument;
import cn.fj.roadagent.application.maintenance.MaintenanceProject;
import cn.fj.roadagent.application.port.MaintenanceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class MysqlMaintenanceRepository implements MaintenanceRepository {
    private static final String[] FACILITIES = {"路面", "路基", "排水设施", "边坡防护", "交通安全设施", "桥涵构造物"};
    private static final String[] TYPES = {"预防养护", "修复养护", "专项养护", "应急养护"};
    private static final String[] DISEASES = {"表面裂缝与局部松散", "路肩局部沉陷", "排水构造物淤积", "防护构造局部损伤", "标志标线磨损", "构造物接缝局部老化"};
    private static final String[] ACTIONS = {"开展局部封缝并恢复表面功能", "整修路肩并复核路基稳定性", "清疏排水系统并修复破损部位", "修补防护构造并加强巡查", "更新标志标线及必要防护设施", "修复接缝并实施构造物专项检查"};
    private static final String[] CONTRACTORS = {"福建省公路养护中心直属作业队", "福州路网养护工程有限公司", "闽路养护工程联合体", "福建交建养护工程有限公司"};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;

    public MysqlMaintenanceRepository(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mapper = mapper;
    }

    @Override
    public void refreshProjects() {
        transaction.executeWithoutResult(status -> {
            Boolean locked = jdbc.queryForObject("SELECT GET_LOCK('roadagent_maintenance_refresh', 3)", Boolean.class);
            if (!Boolean.TRUE.equals(locked)) return;
            try {
                List<Segment> segments = jdbc.query("""
                        SELECT rout_code, rout_name, rout_section
                        FROM w_congestion_detection_result
                        WHERE (del_flag IS NULL OR del_flag IN ('N','0'))
                          AND (rout_code LIKE 'G%' OR rout_code LIKE 'S%')
                        ORDER BY SHA2(CONCAT(rout_code, '|', rout_section, '|roadagent-maintenance-v1'), 256)
                        LIMIT 15
                        """, (rs, row) -> new Segment(rs.getString(1), rs.getString(2), rs.getString(3)));
                if (segments.isEmpty()) return;
                String batch = fingerprint(segments);
                for (Segment segment : segments) upsert(segment, batch);
                jdbc.update("""
                        UPDATE w_repair_road SET enabled=0, update_time=CURRENT_TIMESTAMP(6)
                        WHERE data_origin='RULE_GENERATED' AND manual_locked=0 AND generation_batch<>?
                        """, batch);
            } finally {
                jdbc.queryForObject("SELECT RELEASE_LOCK('roadagent_maintenance_refresh')", Object.class);
            }
        });
    }

    private void upsert(Segment segment, String batch) {
        int seed = Math.floorMod((segment.code + "|" + segment.section).hashCode(), 10_000);
        int index = seed % FACILITIES.length;
        String type = TYPES[seed % TYPES.length];
        String scale = "修复养护".equals(type) ? new String[]{"大修", "中修", "小修"}[seed % 3] : "不适用";
        String urgency = seed % 7 == 0 ? "紧急" : seed % 3 == 0 ? "较高" : "一般";
        int priority = "紧急".equals(urgency) ? 90 + seed % 10 : "较高".equals(urgency) ? 70 + seed % 15 : 50 + seed % 20;
        LocalDate start = LocalDate.now().plusDays(7L + seed % 120);
        int duration = 3 + seed % 18;
        BigDecimal budget = BigDecimal.valueOf(35 + seed % 365).setScale(2);
        String projectCode = "MR-" + sha256(segment.code + "|" + segment.section).substring(0, 12).toUpperCase(Locale.ROOT);
        // Existing automatic projects retain their first generated construction window. This keeps a
        // source-stable project list from drifting one day forward on every scheduled refresh.
        jdbc.update("""
                INSERT INTO w_repair_road
                (project_code, route_code, route_name, route_section, facility_type, maintenance_type,
                 repair_scale, disease_description, recommended_action, urgency, priority_score,
                 planned_start_date, duration_days, contractor_name, budget_wan, project_status,
                 enabled, generation_batch, data_origin, manual_locked, del_flag)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'待安排',1,?,'RULE_GENERATED',0,'N')
                ON DUPLICATE KEY UPDATE
                  route_name=IF(manual_locked=1,route_name,VALUES(route_name)),
                  facility_type=IF(manual_locked=1,facility_type,VALUES(facility_type)),
                  maintenance_type=IF(manual_locked=1,maintenance_type,VALUES(maintenance_type)),
                  repair_scale=IF(manual_locked=1,repair_scale,VALUES(repair_scale)),
                  disease_description=IF(manual_locked=1,disease_description,VALUES(disease_description)),
                  recommended_action=IF(manual_locked=1,recommended_action,VALUES(recommended_action)),
                  urgency=IF(manual_locked=1,urgency,VALUES(urgency)),
                  priority_score=IF(manual_locked=1,priority_score,VALUES(priority_score)),
                  planned_start_date=planned_start_date,
                  duration_days=IF(manual_locked=1,duration_days,VALUES(duration_days)),
                  contractor_name=IF(manual_locked=1,contractor_name,VALUES(contractor_name)),
                  budget_wan=IF(manual_locked=1,budget_wan,VALUES(budget_wan)),
                  enabled=1, generation_batch=VALUES(generation_batch), update_time=CURRENT_TIMESTAMP(6)
                """, projectCode, segment.code, segment.name, segment.section, FACILITIES[index], type,
                scale, DISEASES[index], ACTIONS[index], urgency, priority, start, duration,
                CONTRACTORS[seed % CONTRACTORS.length], budget, batch);
    }

    @Override
    public List<MaintenanceProject> findActiveProjects() {
        return jdbc.query("""
                SELECT project_code,route_code,route_name,route_section,facility_type,maintenance_type,
                       repair_scale,disease_description,recommended_action,urgency,priority_score,
                       planned_start_date,duration_days,contractor_name,budget_wan
                FROM w_repair_road
                WHERE enabled=1 AND (del_flag IS NULL OR del_flag IN ('N','0'))
                ORDER BY priority_score DESC, route_code, route_section
                """, (rs, row) -> new MaintenanceProject(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                rs.getInt(11), rs.getDate(12).toLocalDate(), rs.getInt(13), rs.getString(14), rs.getBigDecimal(15)
        ));
    }

    @Override
    public Optional<MaintenanceProject> findProject(String message) {
        String normalized = message == null ? "" : message.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        return findActiveProjects().stream().filter(project -> normalized.contains(project.projectCode().toUpperCase(Locale.ROOT))
                || normalized.contains(project.routeCode().toUpperCase(Locale.ROOT))
                || normalized.contains(project.routeSection().replaceAll("\\s+", "").toUpperCase(Locale.ROOT))).findFirst();
    }

    @Override public String currentBatchId() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(generation_batch),'EMPTY') FROM w_repair_road WHERE enabled=1", String.class);
    }

    @Override public String loadTemplate(String templateKey) {
        List<String> values = jdbc.query("SELECT template_body FROM w_maintenance_template WHERE template_key=? AND enabled=1 ORDER BY template_version DESC LIMIT 1",
                (rs, row) -> rs.getString(1), templateKey);
        return values.isEmpty() ? "" : values.get(0);
    }

    @Override
    public Optional<MaintenanceDocument> findSnapshot(String type, int year, String projectCode, String batchId) {
        List<String> json = jdbc.query("""
                SELECT content_json FROM w_maintenance_document_snapshot
                WHERE document_type=? AND report_year=? AND project_code_key=? AND source_batch_id=?
                ORDER BY version_no DESC LIMIT 1
                """, (rs, row) -> rs.getString(1), type, year, projectCode == null ? "" : projectCode, batchId);
        return json.stream().findFirst().map(this::readDocument);
    }

    @Override
    public MaintenanceDocument saveSnapshot(MaintenanceDocument document) {
        try {
            jdbc.update("""
                    INSERT INTO w_maintenance_document_snapshot
                    (document_id,document_type,report_year,project_code,project_code_key,title,summary,
                     content_json,source_batch_id,version_no,data_origin,generated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?, 'RULE_AND_MODEL', ?)
                    """, document.documentId(), document.documentType(), document.reportYear(), document.projectCode(),
                    document.projectCode() == null ? "" : document.projectCode(), document.title(), document.summary(),
                    mapper.writeValueAsString(document), document.sourceBatchId(), document.version(),
                    java.sql.Timestamp.from(document.generatedAt()));
            return document;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("养护文档序列化失败", exception);
        }
    }

    @Override
    public Optional<MaintenanceDocument> findDocument(String documentId) {
        List<String> json = jdbc.query("SELECT content_json FROM w_maintenance_document_snapshot WHERE document_id=? LIMIT 1",
                (rs, row) -> rs.getString(1), documentId);
        return json.stream().findFirst().map(this::readDocument);
    }

    private MaintenanceDocument readDocument(String json) {
        try { return mapper.readValue(json, MaintenanceDocument.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("养护文档读取失败", exception); }
    }

    private String fingerprint(List<Segment> segments) {
        return sha256(segments.stream().map(s -> s.code + "|" + s.section).sorted().reduce("", (a, b) -> a + "\n" + b));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record Segment(String code, String name, String section) {}
}
