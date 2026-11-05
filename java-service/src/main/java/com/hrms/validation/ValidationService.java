package com.hrms.validation;

import com.hrms.common.CommonService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
    private final JdbcTemplate jdbc;
    private final CommonService common;
    private final Clock clock;

    public ValidationService(JdbcTemplate jdbc, CommonService common, Clock clock) {
        this.jdbc = jdbc;
        this.common = common;
        this.clock = clock;
    }

    public boolean validateDateRange(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && !end.isBefore(start);
    }

    public String validateSalaryForGrade(BigDecimal salary, BigDecimal gradeId) {
        if (salary == null || gradeId == null) return "Salary and grade are required";
        var grades = jdbc.query("SELECT min_salary, max_salary, grade_name FROM job_grades WHERE grade_id = ?",
                (rs, row) -> new Grade(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getString(3)), gradeId);
        if (grades.isEmpty()) return "Invalid grade ID: " + gradeId.stripTrailingZeros().toPlainString();
        Grade grade = grades.get(0);
        if (salary.compareTo(grade.min()) < 0) {
            return "Salary " + dollars(salary) + " is below minimum for grade " + grade.name()
                    + " (" + dollars(grade.min()) + ")";
        }
        if (salary.compareTo(grade.max()) > 0) {
            return "Salary " + dollars(salary) + " exceeds maximum for grade " + grade.name()
                    + " (" + dollars(grade.max()) + ")";
        }
        return null;
    }

    private record Grade(BigDecimal min, BigDecimal max, String name) { }

    private String dollars(BigDecimal amount) {
        String formatted = common.formatCurrency(amount.abs());
        if (formatted.indexOf('#') >= 0) return "################";
        return (amount.signum() < 0 ? "-" : "") + formatted;
    }

    public Boolean validateEmailFormat(String email) { return common.isValidEmail(email); }
    public Boolean validatePhoneFormat(String phone) { return common.isValidPhone(phone); }

    public Boolean validateEmpNumberFormat(String number) {
        return number == null || number.isEmpty() ? null : number.matches("EMP-[0-9]{6}");
    }

    public Boolean isFutureDate(LocalDateTime date) {
        return date == null ? null : date.toLocalDate().isAfter(LocalDate.now(clock));
    }

    public boolean isBusinessDay(LocalDateTime date) { return isBusinessDay(date, null); }

    /** VAL-005: A null date falls through to a zero holiday count and returns true. */
    public boolean isBusinessDay(LocalDateTime date, String locationCode) {
        if (date != null && date.getDayOfWeek().getValue() >= 6) return false;
        Long holidays = jdbc.queryForObject("SELECT count(*) FROM holidays WHERE holiday_date = ? "
                + "AND active_flag = 'Y' AND (location_code IS NULL OR location_code = ?)", Long.class,
                date == null ? null : Timestamp.valueOf(date.toLocalDate().atStartOfDay()),
                locationCode == null || locationCode.isEmpty() ? null : locationCode);
        return holidays == 0;
    }

    /** VAL-006: Legacy completeness checks only EMPLOYEES and never reference eligibility. */
    public String validateRequiredFields(String tableName, BigDecimal recordId) {
        if (!"EMPLOYEES".equals(tableName)) return null;
        var records = jdbc.queryForList("SELECT first_name, last_name, hire_date, dept_id, job_id FROM employees WHERE emp_id = ?", recordId);
        if (records.isEmpty()) return "Record not found";
        var record = records.get(0);
        String[] fields = {"first_name", "last_name", "hire_date", "dept_id", "job_id"};
        String[] messages = {"First Name is required", "Last Name is required", "Hire Date is required",
                "Department is required", "Job Title is required"};
        for (int i = 0; i < fields.length; i++) {
            Object value = record.get(fields[i]);
            if (value == null || "".equals(value)) return messages[i];
        }
        return null;
    }
}
