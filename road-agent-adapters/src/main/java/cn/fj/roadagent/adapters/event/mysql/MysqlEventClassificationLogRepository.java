package cn.fj.roadagent.adapters.event.mysql;

import cn.fj.roadagent.application.port.EventClassificationLogPort;
import cn.fj.roadagent.domain.dispatch.EventClassificationAttempt;
import cn.fj.roadagent.domain.dispatch.EventClassificationStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class MysqlEventClassificationLogRepository implements EventClassificationLogPort {
    private final JdbcTemplate jdbcTemplate;

    public MysqlEventClassificationLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int nextAttemptNumber(String eventId) {
        Integer value = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(attempt_no), 0) + 1
                FROM w_emergency_event_classification WHERE event_id = ?
                """, Integer.class, eventId);
        return value == null ? 1 : value;
    }

    @Override
    public boolean insert(EventClassificationAttempt attempt) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO w_emergency_event_classification (
                        classification_id, event_id, attempt_no, classification_method,
                        classification_status, event_type, confidence, evidence, model_name,
                        error_message, idempotency_key, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, attempt.classificationId(), attempt.eventId(), attempt.attemptNumber(),
                    attempt.method().name(), status(attempt.status()), attempt.eventType(),
                    attempt.confidence(), attempt.evidence(), attempt.modelName(),
                    attempt.errorMessage(), attempt.idempotencyKey(),
                    Timestamp.from(attempt.createdAt())) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public long countLatestFailuresForPendingEvents() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM w_lw_incident e
                WHERE e.c_type = '4' AND e.status = '1' AND e.completed = 0 AND e.deleted = 0
                  AND (e.event_type IS NULL OR e.event_type = '')
                  AND EXISTS (
                    SELECT 1 FROM w_emergency_event_classification c
                    WHERE c.event_id = e.c_no AND c.classification_status = 1
                      AND c.attempt_no = (
                        SELECT MAX(c2.attempt_no) FROM w_emergency_event_classification c2
                        WHERE c2.event_id = e.c_no
                      )
                  )
                """, Long.class);
        return count == null ? 0 : count;
    }

    private int status(EventClassificationStatus status) {
        return switch (status) {
            case SUCCEEDED -> 0;
            case FAILED -> 1;
            case MANUALLY_CORRECTED -> 2;
        };
    }
}
