package com.hrms.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.parity.GoldenExport;
import com.hrms.parity.GoldenFormats;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("audit")
@Profile("parity")
public class AuditGoldenExport implements GoldenExport {
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AuditGoldenExport(AuditService audit, JdbcTemplate jdbc, ObjectMapper json) {
        this.audit = audit;
        this.jdbc = jdbc;
        this.json = json;
    }

    public String name() { return "audit"; }

    public List<String> export() throws JsonProcessingException {
        audit.logAction("FIXTURE_RECORD", new BigDecimal("11"), "UPDATE", "FIXTURE_AUDIT", "before", "after");
        List<String> lines = new ArrayList<>();
        lines.add("scenario,result");
        capture(lines, "explicit_action", "SELECT table_name, record_id, action_type, substring(old_values,1,200) AS old_values, "
                + "substring(new_values,1,200) AS new_values, changed_by, changed_date FROM audit_log WHERE table_name = 'FIXTURE_RECORD'");
        capture(lines, "business_actions", "SELECT table_name, action_type, changed_by, changed_date FROM audit_log "
                + "WHERE (table_name IN ('EMPLOYEES', 'REVIEW_CYCLES') AND changed_by LIKE 'FIXTURE%') "
                + "OR table_name IN ('USER_SESSIONS', 'USER_CREDENTIALS')");
        return lines;
    }

    private void capture(List<String> lines, String scenario, String sql) throws JsonProcessingException {
        var rows = jdbc.queryForList(sql);
        for (var row : rows) {
            var values = new LinkedHashMap<String, Object>();
            for (var column : row.entrySet()) {
                Object value = column.getValue();
                if (value instanceof Timestamp timestamp) {
                    value = timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                }
                if ("".equals(value)) value = null;
                values.put(column.getKey().toUpperCase(Locale.ROOT), value);
            }
            lines.add(GoldenFormats.csv(scenario, json.writeValueAsString(values)));
        }
        lines.add(GoldenFormats.csv(scenario + ".row_count", Integer.toString(rows.size())));
    }
}
