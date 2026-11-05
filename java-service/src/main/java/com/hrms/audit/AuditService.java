package com.hrms.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate autonomous;

    public AuditService(JdbcTemplate jdbc, Clock clock, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.clock = clock;
        autonomous = new TransactionTemplate(transactions);
        autonomous.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void logAction(String table, BigDecimal recordId, String action) {
        log(table, recordId, action, null, null, null, true);
    }

    public void logAction(String table, BigDecimal recordId, String action, String user) {
        logAction(table, recordId, action, user, null, null);
    }

    public void logAction(String table, BigDecimal recordId, String action, String user, String oldValues) {
        logAction(table, recordId, action, user, oldValues, null);
    }

    public void logAction(String table, BigDecimal recordId, String action, String user, String oldValues, String newValues) {
        log(table, recordId, action, user, oldValues, newValues, false);
    }

    /** AUD-001: Legacy audit failures are silently discarded, never propagated. */
    private void log(String table, BigDecimal recordId, String action, String user,
            String oldValues, String newValues, boolean defaultUser) {
        try {
            autonomous.executeWithoutResult(status -> jdbc.update("INSERT INTO audit_log "
                    + "(audit_id, table_name, record_id, action_type, old_values, new_values, changed_by, changed_date, ip_address, session_id) "
                    + "VALUES (nextval('seq_audit'), ?, ?, ?, ?, ?, " + (defaultUser ? "current_user" : "?")
                    + ", ?, inet_client_addr()::text, pg_backend_pid()::text)",
                    defaultUser
                            ? new Object[]{emptyToNull(table), recordId, emptyToNull(action), emptyToNull(oldValues), emptyToNull(newValues), Timestamp.valueOf(now())}
                            : new Object[]{emptyToNull(table), recordId, emptyToNull(action), emptyToNull(oldValues), emptyToNull(newValues), emptyToNull(user), Timestamp.valueOf(now())}));
        } catch (RuntimeException ignored) {
        }
    }

    public void purgeOldRecords() { purgeOldRecords(new BigDecimal("365"), null); }
    public void purgeOldRecords(BigDecimal daysToKeep) { purgeOldRecords(daysToKeep, null); }

    /** AUD-002: Like the package COMMIT, purge also commits any pending caller work. */
    public void purgeOldRecords(BigDecimal daysToKeep, String user) {
        LocalDateTime cutoff = daysToKeep == null ? null : now().minusSeconds(daysToKeep
                .multiply(new BigDecimal("86400")).setScale(0, RoundingMode.HALF_UP).longValueExact());
        Integer deleted = jdbc.execute((ConnectionCallback<Integer>) connection -> {
            int count;
            try (var statement = connection.prepareStatement("DELETE FROM audit_log WHERE changed_date < ?")) {
                statement.setTimestamp(1, cutoff == null ? null : Timestamp.valueOf(cutoff));
                count = statement.executeUpdate();
            }
            if (!connection.getAutoCommit()) connection.commit();
            return count;
        });
        System.out.println("Purged " + deleted + " audit records older than "
                + (daysToKeep == null ? "" : daysToKeep.stripTrailingZeros().toPlainString()) + " days");
    }

    public record ChangeHistory(BigDecimal auditId, String tableName, BigDecimal recordId, String actionType,
            String oldValues, String newValues, String changedBy, LocalDateTime changedDate, String ipAddress) { }

    public List<ChangeHistory> getChangeHistory(String table, BigDecimal recordId) {
        return getChangeHistory(table, recordId, null, null);
    }

    public List<ChangeHistory> getChangeHistory(String table, BigDecimal recordId, LocalDateTime from) {
        return getChangeHistory(table, recordId, from, null);
    }

    public List<ChangeHistory> getChangeHistory(String table, BigDecimal recordId, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT audit_id, table_name, record_id, action_type, old_values, new_values, changed_by, changed_date, ip_address "
                + "FROM audit_log WHERE table_name = ? AND record_id = ?";
        var args = new java.util.ArrayList<Object>();
        args.add(emptyToNull(table));
        args.add(recordId);
        if (from != null) { sql += " AND changed_date >= ?"; args.add(Timestamp.valueOf(from)); }
        if (to != null) { sql += " AND changed_date <= ?"; args.add(Timestamp.valueOf(to)); }
        return jdbc.query(sql + " ORDER BY changed_date DESC", (rs, row) -> new ChangeHistory(
                rs.getBigDecimal("audit_id"), rs.getString("table_name"), rs.getBigDecimal("record_id"),
                rs.getString("action_type"), rs.getString("old_values"), rs.getString("new_values"),
                rs.getString("changed_by"), rs.getTimestamp("changed_date").toLocalDateTime(), rs.getString("ip_address")), args.toArray());
    }

    private LocalDateTime now() { return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS); }
    private String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
}
