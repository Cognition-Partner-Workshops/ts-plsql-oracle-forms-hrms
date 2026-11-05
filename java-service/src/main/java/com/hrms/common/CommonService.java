package com.hrms.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CommonService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate autonomous;

    public CommonService(JdbcTemplate jdbc, Clock clock, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.clock = clock;
        autonomous = new TransactionTemplate(transactions);
        autonomous.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private String currentUser() {
        return jdbc.queryForObject("SELECT current_user", String.class);
    }

    public void logError(String pkg, String procedure, String message) {
        logError(pkg, procedure, message, currentUser());
    }

    public void logError(String pkg, String procedure, String message, String user) {
        log(true, pkg, procedure, message, user);
    }

    public void logInfo(String pkg, String procedure, String message) {
        logInfo(pkg, procedure, message, currentUser());
    }

    public void logInfo(String pkg, String procedure, String message, String user) {
        log(false, pkg, procedure, message, user);
    }

    private void log(boolean error, String pkg, String procedure, String message, String user) {
        String text = text(message);
        text = text.substring(0, Math.min(text.length(), 3000));
        if (error) text = text.replace("\"", "\\\"");
        String payload = "{\"package\":\"" + text(pkg) + "\",\"procedure\":\"" + text(procedure)
                + "\",\"message\":\"" + text + "\"}";
        try {
            autonomous.executeWithoutResult(status -> jdbc.update("INSERT INTO audit_log "
                    + "(audit_id, table_name, record_id, action_type, new_values, changed_by, changed_date) "
                    + "VALUES (nextval('seq_audit'), ?, 0, 'INSERT', ?, ?, ?)",
                    error ? "ERROR_LOG" : "INFO_LOG", payload, user, Timestamp.valueOf(LocalDateTime.now(clock))));
        } catch (RuntimeException failure) {
            if (error) System.out.println("ERROR LOG FAILED: " + text(pkg) + "." + text(procedure) + ": " + text(message));
        }
    }

    public String getParam(String group, String code) {
        var values = jdbc.queryForList("SELECT param_value FROM system_parameters WHERE param_group = ? AND param_code = ?",
                String.class, emptyToNull(group), emptyToNull(code));
        return values.isEmpty() ? null : emptyToNull(values.get(0));
    }

    public BigDecimal getParamNumber(String group, String code) {
        String value = getParam(group, code);
        try { return value == null ? null : new BigDecimal(value.trim()); }
        catch (NumberFormatException invalid) { return null; }
    }

    public LocalDate getParamDate(String group, String code) {
        try {
            String value = getParam(group, code);
            return value == null ? null : LocalDate.parse(value);
        } catch (RuntimeException invalid) { return null; }
    }

    public void setParam(String group, String code, String value) {
        setParam(group, code, value, currentUser());
    }

    public void setParam(String group, String code, String value, String user) {
        int count = jdbc.update("UPDATE system_parameters SET param_value = ?, modified_by = ?, modified_date = ? "
                + "WHERE param_group = ? AND param_code = ? AND editable_flag = 'Y'",
                emptyToNull(value), emptyToNull(user), Timestamp.valueOf(LocalDateTime.now(clock)), group, code);
        if (count == 0) throw new IllegalArgumentException("ORA-20900: Parameter not found or not editable: "
                + text(group) + "." + text(code));
    }

    /** COM-002: Legacy business days ignore holidays. */
    public int businessDaysBetween(LocalDate start, LocalDate end) {
        int count = 0;
        if (start == null || end == null) return count;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (weekday(date)) count++;
        }
        return count;
    }

    public int businessDaysBetween(LocalDateTime start, LocalDateTime end) {
        return businessDaysBetween(start == null ? null : start.toLocalDate(), end == null ? null : end.toLocalDate());
    }

    /** COM-002: Negative/null counts leave the truncated date unchanged; holidays are ignored. */
    public LocalDate addBusinessDays(LocalDate date, BigDecimal days) {
        if (date == null || days == null) return date;
        for (int added = 0; BigDecimal.valueOf(added).compareTo(days) < 0;) {
            date = date.plusDays(1);
            if (weekday(date)) added++;
        }
        return date;
    }

    public LocalDate addBusinessDays(LocalDateTime date, BigDecimal days) {
        return addBusinessDays(date == null ? null : date.toLocalDate(), days);
    }

    private boolean weekday(LocalDate date) { return date.getDayOfWeek().getValue() < 6; }

    public Integer getFiscalYear() { return getFiscalYear(LocalDate.now(clock)); }
    public Integer getFiscalYear(LocalDate date) {
        return date == null ? null : date.getYear() + (date.getMonthValue() >= 10 ? 1 : 0);
    }
    public Integer getFiscalQuarter() { return getFiscalQuarter(LocalDate.now(clock)); }
    public Integer getFiscalQuarter(LocalDate date) {
        return date == null ? null : ((date.getMonthValue() + 2) % 12) / 3 + 1;
    }

    public String formatPhone(String phone) {
        String digits = phoneDigits(phone);
        if (digits == null) return emptyToNull(phone);
        if (digits.length() == 10) return "(" + digits.substring(0, 3) + ") " + digits.substring(3, 6) + "-" + digits.substring(6);
        if (digits.length() == 11 && digits.startsWith("1")) return "+1 " + formatPhone(digits.substring(1));
        return emptyToNull(phone);
    }

    public String formatSsnMasked(String ssn) {
        return ssn == null || ssn.length() < 4 ? "***-**-****" : "***-**-" + ssn.substring(ssn.length() - 4);
    }

    public String formatCurrency(BigDecimal amount) { return formatCurrency(amount, "USD"); }
    public String formatCurrency(BigDecimal amount, String currency) {
        String prefix = "USD".equals(currency) ? "$" : "EUR".equals(currency) ? "€" : "GBP".equals(currency) ? "£" : text(currency) + " ";
        if (amount == null) return prefix;
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        if (rounded.abs().compareTo(new BigDecimal("1000000000")) >= 0) return prefix + "###############";
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return prefix + format.format(rounded);
    }

    public String formatName(String first, String last) { return formatName(first, last, "FL"); }
    public String formatName(String first, String last, String format) {
        return "LF".equals(format) ? initcap(last) + ", " + initcap(first) : initcap(first) + " " + initcap(last);
    }

    private String initcap(String value) {
        StringBuilder result = new StringBuilder();
        boolean start = true;
        for (char c : text(value).toCharArray()) {
            result.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = !Character.isLetterOrDigit(c);
        }
        return result.toString();
    }

    public Boolean isValidEmail(String email) {
        return emptyToNull(email) == null ? null : Pattern.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", email);
    }

    /** COM-006: Legacy validation accepts any 11-digit prefix. */
    public Boolean isValidPhone(String phone) {
        String value = phoneDigits(phone);
        return value == null ? null : value.length() >= 10 && value.length() <= 11;
    }

    /** COM-007: Legacy validation accepts all-zero SSNs. */
    public Boolean isValidSsn(String ssn) {
        String value = digits(ssn);
        return value == null ? null : value.matches("[0-9]{9}");
    }

    private String digits(String value) { return value == null ? null : emptyToNull(value.replaceAll("[^0-9]", "")); }
    private String phoneDigits(String value) {
        String result = digits(value);
        if (result != null && result.length() > 20) throw new IllegalArgumentException("ORA-06502: character string buffer too small");
        return result;
    }
    private static String text(String value) { return value == null ? "" : value; }
    private static String emptyToNull(String value) { return value == null || value.isEmpty() ? null : value; }
}
