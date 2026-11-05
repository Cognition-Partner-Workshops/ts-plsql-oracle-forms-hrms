package com.hrms;

import com.hrms.audit.AuditService;
import com.hrms.audit.AuditGoldenExport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.parity.GoldenFormats;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class AuditServiceTests {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.8-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-31T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    private AuditService audit() { return new AuditService(jdbc, CLOCK, transactions); }

    @BeforeEach
    void clearAudit() { jdbc.update("DELETE FROM audit_log"); }

    @Test
    void AUD_001_autonomousWritesCaptureContextSurviveRollbackAndSwallowErrors() {
        AuditService audit = audit();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Integer callerSession = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
            audit.logAction("AUD_TEST", BigDecimal.TEN, "UPDATE", "ACTOR", "before\"\n", "after");
            var row = jdbc.queryForMap("SELECT * FROM audit_log WHERE table_name='AUD_TEST'");
            assertThat(row.get("changed_by")).isEqualTo("ACTOR");
            assertThat(row.get("old_values")).isEqualTo("before\"\n");
            assertThat(row.get("new_values")).isEqualTo("after");
            assertThat(((Timestamp) row.get("changed_date")).toLocalDateTime()).isEqualTo(NOW);
            assertThat(row.get("record_id")).isEqualTo(BigDecimal.TEN);
            assertThat(row.get("action_type")).isEqualTo("UPDATE");
            assertThat(row.get("ip_address")).isEqualTo(jdbc.queryForObject("SELECT inet_client_addr()::text", String.class));
            String session = (String) row.get("session_id");
            assertThat(session).matches("[0-9]+").isNotEqualTo(callerSession.toString());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE pid=?", Long.class, Integer.parseInt(session)))
                    .isEqualTo(1);
            assertThatCode(() -> audit.logAction("AUD_TEST", BigDecimal.TEN, "INVALID", "ACTOR"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> audit.logAction("AUD_TEST", BigDecimal.TEN, "INSERT", null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> audit.logAction(null, BigDecimal.TEN, "INSERT", "ACTOR"))
                    .doesNotThrowAnyException();
            jdbc.update("INSERT INTO audit_log (audit_id,table_name,record_id,action_type,changed_by) "
                    + "VALUES (9901,'CALLER',1,'INSERT','ACTOR')");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class)).isEqualTo(1);
        audit.logAction("DEFAULTS", BigDecimal.ONE, "INSERT");
        var defaults = jdbc.queryForMap("SELECT * FROM audit_log WHERE table_name='DEFAULTS'");
        assertThat(defaults.get("changed_by")).isEqualTo(jdbc.queryForObject("SELECT current_user", String.class));
        assertThat(defaults.get("old_values")).isNull();
        assertThat(defaults.get("new_values")).isNull();
        audit.logAction("EMPTY", BigDecimal.ONE, "INSERT", "ACTOR", "", "");
        assertThat(audit.getChangeHistory("EMPTY", BigDecimal.ONE).get(0).oldValues()).isNull();
        assertThat(audit.getChangeHistory("EMPTY", BigDecimal.ONE).get(0).newValues()).isNull();
        audit.logAction("LARGE", BigDecimal.ONE, "INSERT", "ACTOR", "x".repeat(10000));
        assertThat(audit.getChangeHistory("LARGE", BigDecimal.ONE).get(0).oldValues()).hasSize(10000);
    }

    @Test
    void AUD_002_purgeIsStrictAtCutoffAndCommitsCallerWork() {
        AuditService audit = audit();
        LocalDateTime cutoff = NOW.minusDays(30);
        seed("OLD", BigDecimal.ONE, cutoff.minusSeconds(1));
        seed("BOUNDARY", BigDecimal.ONE, cutoff);
        seed("NEW", BigDecimal.ONE, cutoff.plusSeconds(1));
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            seed("CALLER_COMMITTED", BigDecimal.ONE, NOW);
            audit.purgeOldRecords(new BigDecimal("30"), "IGNORED_ACTOR");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForList("SELECT table_name FROM audit_log ORDER BY table_name", String.class))
                .containsExactly("BOUNDARY", "CALLER_COMMITTED", "NEW");
        audit.purgeOldRecords(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class)).isEqualTo(3);
        jdbc.update("DELETE FROM audit_log");
        seed("FRACTION_OLD", BigDecimal.ONE, NOW.minusHours(12).minusSeconds(1));
        seed("FRACTION_BOUNDARY", BigDecimal.ONE, NOW.minusHours(12));
        audit.purgeOldRecords(new BigDecimal("0.5"));
        assertThat(jdbc.queryForList("SELECT table_name FROM audit_log", String.class)).containsExactly("FRACTION_BOUNDARY");
        jdbc.update("DELETE FROM audit_log");
        seed("DEFAULT_OLD", BigDecimal.ONE, NOW.minusDays(365).minusSeconds(1));
        seed("DEFAULT_BOUNDARY", BigDecimal.ONE, NOW.minusDays(365));
        audit.purgeOldRecords();
        assertThat(jdbc.queryForList("SELECT table_name FROM audit_log", String.class)).containsExactly("DEFAULT_BOUNDARY");
        audit.purgeOldRecords(BigDecimal.ZERO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class)).isZero();
        audit.purgeOldRecords(BigDecimal.ZERO);
        seed("NEGATIVE_KEEP", BigDecimal.ONE, NOW.plusDays(1));
        audit.purgeOldRecords(new BigDecimal("-1"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class)).isEqualTo(1);
    }

    @Test
    void AUD_003_historyFiltersExactTableAndRecordWithInclusiveOptionalBoundsNewestFirst() {
        AuditService audit = audit();
        seed("HISTORY", BigDecimal.ONE, NOW.minusDays(1));
        seed("HISTORY", BigDecimal.ONE, NOW);
        seed("HISTORY", BigDecimal.ONE, NOW.plusDays(1));
        seed("HISTORY", BigDecimal.TEN, NOW);
        seed("OTHER", BigDecimal.ONE, NOW);
        var all = audit.getChangeHistory("HISTORY", BigDecimal.ONE);
        assertThat(all).extracting(AuditService.ChangeHistory::changedDate)
                .containsExactly(NOW.plusDays(1), NOW, NOW.minusDays(1));
        assertThat(all).allSatisfy(row -> {
            assertThat(row.tableName()).isEqualTo("HISTORY");
            assertThat(row.recordId()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(row.actionType()).isEqualTo("UPDATE");
            assertThat(row.oldValues()).isEqualTo("before");
            assertThat(row.newValues()).isEqualTo("after");
            assertThat(row.changedBy()).isEqualTo("ACTOR");
            assertThat(row.ipAddress()).isEqualTo("127.0.0.1");
            assertThat(row.auditId()).isPositive();
        });
        assertThat(audit.getChangeHistory("HISTORY", BigDecimal.ONE, NOW)).extracting(AuditService.ChangeHistory::changedDate)
                .containsExactly(NOW.plusDays(1), NOW);
        assertThat(audit.getChangeHistory("HISTORY", BigDecimal.ONE, null, NOW)).extracting(AuditService.ChangeHistory::changedDate)
                .containsExactly(NOW, NOW.minusDays(1));
        assertThat(audit.getChangeHistory("HISTORY", BigDecimal.ONE, NOW, NOW)).hasSize(1);
        assertThat(audit.getChangeHistory("HISTORY", BigDecimal.ONE, NOW.plusDays(1), NOW)).isEmpty();
        assertThat(audit.getChangeHistory("MISSING", BigDecimal.ONE)).isEmpty();
        assertThat(audit.getChangeHistory("history", BigDecimal.ONE)).isEmpty();
        assertThat(audit.getChangeHistory(null, BigDecimal.ONE)).isEmpty();
        assertThat(audit.getChangeHistory("HISTORY", null)).isEmpty();
    }

    private void seed(String table, BigDecimal recordId, LocalDateTime date) {
        jdbc.update("INSERT INTO audit_log (audit_id,table_name,record_id,action_type,old_values,new_values,changed_by,changed_date,ip_address) "
                + "VALUES (nextval('seq_audit'),?,?,'UPDATE','before','after','ACTOR',?,'127.0.0.1')",
                table, recordId, Timestamp.valueOf(date));
    }

    @Test
    void goldenExportQueriesRealBusinessActionsAndIncludesEmptyCounts() throws Exception {
        AuditService audit = audit();
        AuditGoldenExport export = new AuditGoldenExport(audit, jdbc, json);
        assertThat(export.export()).containsExactly("scenario,result",
                GoldenFormats.csv("explicit_action", "{\"TABLE_NAME\":\"FIXTURE_RECORD\",\"RECORD_ID\":11,\"ACTION_TYPE\":\"UPDATE\","
                        + "\"OLD_VALUES\":\"before\",\"NEW_VALUES\":\"after\",\"CHANGED_BY\":\"FIXTURE_AUDIT\",\"CHANGED_DATE\":\"2024-01-31T12:00:00\"}"),
                GoldenFormats.csv("explicit_action.row_count", "1"),
                GoldenFormats.csv("business_actions.row_count", "0"));
        jdbc.update("DELETE FROM audit_log");
        audit.logAction("EMPLOYEES", BigDecimal.ONE, "INSERT", "FIXTURE_EMP");
        audit.logAction("REVIEW_CYCLES", BigDecimal.ONE, "INSERT", "FIXTURE_PERF");
        audit.logAction("USER_SESSIONS", BigDecimal.ONE, "INSERT", "ANY_USER");
        audit.logAction("USER_CREDENTIALS", BigDecimal.ONE, "UPDATE", "SYSTEM");
        audit.logAction("EMPLOYEES", BigDecimal.ONE, "INSERT", "NON_FIXTURE");
        audit.logAction("OTHER", BigDecimal.ONE, "INSERT", "FIXTURE_OTHER");
        var lines = export.export();
        assertThat(lines).contains(GoldenFormats.csv("business_actions.row_count", "4"));
        assertThat(lines.stream().filter(line -> line.startsWith("\"business_actions\","))).hasSize(4);
        assertThat(lines).noneMatch(line -> line.contains("NON_FIXTURE") || line.contains("FIXTURE_OTHER"));
        assertThat(new AuditGoldenExport(mock(AuditService.class), jdbc, json).name()).isEqualTo("audit");
        jdbc.update("DELETE FROM audit_log");
        assertThat(new AuditGoldenExport(mock(AuditService.class), jdbc, json).export()).containsExactly("scenario,result",
                GoldenFormats.csv("explicit_action.row_count", "0"), GoldenFormats.csv("business_actions.row_count", "0"));
    }

    @Test
    void goldenExportPropagatesQueryErrorsForParityRunnerToReport() {
        JdbcTemplate unavailable = mock(JdbcTemplate.class);
        when(unavailable.queryForList(anyString())).thenThrow(new DataAccessResourceFailureException("unavailable"));
        AuditGoldenExport export = new AuditGoldenExport(mock(AuditService.class), unavailable, json);
        assertThatThrownBy(export::export).isInstanceOf(DataAccessResourceFailureException.class).hasMessage("unavailable");
    }
}
