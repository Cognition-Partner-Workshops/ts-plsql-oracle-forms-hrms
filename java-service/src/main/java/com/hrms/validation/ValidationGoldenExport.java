package com.hrms.validation;

import com.hrms.parity.GoldenExport;
import com.hrms.parity.GoldenFormats;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("validation")
@Profile("parity")
public class ValidationGoldenExport implements GoldenExport {
    private final ValidationService validation;
    private final JdbcTemplate jdbc;

    public ValidationGoldenExport(ValidationService validation, JdbcTemplate jdbc) {
        this.validation = validation;
        this.jdbc = jdbc;
    }

    public String name() { return "validation"; }

    public List<String> export() {
        List<String> lines = new ArrayList<>();
        lines.add("scenario,result");
        LocalDateTime today = LocalDateTime.of(2024, 1, 31, 0, 0);
        add(lines, "same_day_range", validation.validateDateRange(today, today));
        add(lines, "reversed_range", validation.validateDateRange(today.plusDays(1), today));
        add(lines, "null_range", validation.validateDateRange(null, today));
        add(lines, "email_valid", validation.validateEmailFormat("fixture@example.invalid"));
        add(lines, "email_invalid", validation.validateEmailFormat("fixture"));
        add(lines, "phone_valid", validation.validatePhoneFormat("212-555-1000"));
        add(lines, "emp_number_valid", validation.validateEmpNumberFormat("EMP-000011"));
        add(lines, "emp_number_invalid", validation.validateEmpNumberFormat("EMP-11"));
        add(lines, "today_not_future", validation.isFutureDate(today));
        add(lines, "tomorrow_future", validation.isFutureDate(today.plusDays(1)));
        add(lines, "weekend", validation.isBusinessDay(LocalDateTime.of(2024, 1, 27, 0, 0), "HQ"));
        add(lines, "weekday", validation.isBusinessDay(today, "HQ"));
        add(lines, "salary_required", validation.validateSalaryForGrade(null, new BigDecimal("3")));
        add(lines, "invalid_grade", validation.validateSalaryForGrade(new BigDecimal("60000"), new BigDecimal("-1")));
        jdbc.query("SELECT grade_id, min_salary, max_salary FROM job_grades ORDER BY grade_id", rs -> {
            BigDecimal id = rs.getBigDecimal("grade_id");
            BigDecimal min = rs.getBigDecimal("min_salary");
            BigDecimal max = rs.getBigDecimal("max_salary");
            String prefix = "grade_" + id.toPlainString();
            add(lines, prefix + ".below", validation.validateSalaryForGrade(min.subtract(BigDecimal.ONE), id));
            add(lines, prefix + ".min", validation.validateSalaryForGrade(min, id));
            add(lines, prefix + ".max", validation.validateSalaryForGrade(max, id));
            add(lines, prefix + ".above", validation.validateSalaryForGrade(max.add(BigDecimal.ONE), id));
        });
        add(lines, "required_existing", validation.validateRequiredFields("EMPLOYEES", new BigDecimal("11")));
        add(lines, "required_missing", validation.validateRequiredFields("EMPLOYEES", new BigDecimal("-1")));
        return lines;
    }

    private void add(List<String> lines, String scenario, Object value) {
        lines.add(GoldenFormats.csv(scenario, value == null ? null : value.toString()));
    }
}
