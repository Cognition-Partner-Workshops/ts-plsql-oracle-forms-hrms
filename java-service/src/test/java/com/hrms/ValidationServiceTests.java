package com.hrms;

import com.hrms.common.CommonService;
import com.hrms.validation.ValidationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class ValidationServiceTests {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.8-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CommonService common;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-31T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime TODAY = LocalDateTime.of(2024, 1, 31, 0, 0);

    private ValidationService validation() { return new ValidationService(jdbc, common, CLOCK); }
    private BigDecimal number(String value) { return new BigDecimal(value); }

    @Test
    void VAL_001_dateRangesRequireBothEndpointsAndCompareFullDates() {
        ValidationService validation = validation();
        assertThat(validation.validateDateRange(TODAY, TODAY)).isTrue();
        assertThat(validation.validateDateRange(TODAY, TODAY.plusDays(1))).isTrue();
        assertThat(validation.validateDateRange(TODAY.plusDays(1), TODAY)).isFalse();
        assertThat(validation.validateDateRange(TODAY.plusHours(1), TODAY)).isFalse();
        assertThat(validation.validateDateRange(null, TODAY)).isFalse();
        assertThat(validation.validateDateRange(TODAY, null)).isFalse();
        assertThat(validation.validateDateRange(null, null)).isFalse();
    }

    @Test
    void VAL_002_salaryRequiresCurrentInclusiveGradeBoundsAndExactMessages() {
        ValidationService validation = validation();
        BigDecimal grade = number("1");
        assertThat(validation.validateSalaryForGrade(null, grade)).isEqualTo("Salary and grade are required");
        assertThat(validation.validateSalaryForGrade(number("40000"), null)).isEqualTo("Salary and grade are required");
        assertThat(validation.validateSalaryForGrade(null, null)).isEqualTo("Salary and grade are required");
        assertThat(validation.validateSalaryForGrade(number("35000"), grade)).isNull();
        assertThat(validation.validateSalaryForGrade(number("55000"), grade)).isNull();
        assertThat(validation.validateSalaryForGrade(number("40000"), grade)).isNull();
        assertThat(validation.validateSalaryForGrade(number("34999"), grade))
                .isEqualTo("Salary $34,999.00 is below minimum for grade Entry Level ($35,000.00)");
        assertThat(validation.validateSalaryForGrade(number("55001"), grade))
                .isEqualTo("Salary $55,001.00 exceeds maximum for grade Entry Level ($55,000.00)");
        assertThat(validation.validateSalaryForGrade(number("-1"), grade))
                .isEqualTo("Salary -$1.00 is below minimum for grade Entry Level ($35,000.00)");
        assertThat(validation.validateSalaryForGrade(number("40000"), number("-1"))).isEqualTo("Invalid grade ID: -1");
        jdbc.update("UPDATE job_grades SET max_salary=56000, active_flag='N' WHERE grade_id=1");
        assertThat(validation.validateSalaryForGrade(number("55001"), grade)).isNull();
        assertThat(validation.validateSalaryForGrade(number("56001"), grade))
                .isEqualTo("Salary $56,001.00 exceeds maximum for grade Entry Level ($56,000.00)");
    }

    @Test
    void VAL_003_emailAndPhoneDelegateToCommonIncludingNullAndErrors() {
        ValidationService validation = validation();
        assertThat(validation.validateEmailFormat("fixture@example.invalid")).isTrue();
        assertThat(validation.validateEmailFormat("fixture")).isFalse();
        assertThat(validation.validateEmailFormat(null)).isNull();
        assertThat(validation.validatePhoneFormat("212-555-1000")).isTrue();
        assertThat(validation.validatePhoneFormat("92125551000")).isTrue();
        assertThat(validation.validatePhoneFormat("123")).isFalse();
        assertThat(validation.validatePhoneFormat(null)).isNull();
        assertThatThrownBy(() -> validation.validatePhoneFormat("1".repeat(21))).hasMessageContaining("ORA-06502");
        CommonService delegate = mock(CommonService.class);
        when(delegate.isValidEmail("delegated")).thenReturn(true);
        when(delegate.isValidPhone("delegated")).thenReturn(true);
        ValidationService forwarding = new ValidationService(jdbc, delegate, CLOCK);
        assertThat(forwarding.validateEmailFormat("delegated")).isTrue();
        assertThat(forwarding.validatePhoneFormat("delegated")).isTrue();
        verify(delegate).isValidEmail("delegated");
        verify(delegate).isValidPhone("delegated");
    }

    @Test
    void VAL_004_employeeNumberRequiresExactUppercasePrefixAndSixDigits() {
        ValidationService validation = validation();
        assertThat(validation.validateEmpNumberFormat("EMP-000011")).isTrue();
        assertThat(validation.validateEmpNumberFormat("EMP-000000")).isTrue();
        for (String invalid : new String[]{"EMP-11", "EMP-1234567", "emp-123456", "EMP-12345a", " EMP-123456", "EMP-123456\n"}) {
            assertThat(validation.validateEmpNumberFormat(invalid)).as(invalid).isFalse();
        }
        assertThat(validation.validateEmpNumberFormat(null)).isNull();
        assertThat(validation.validateEmpNumberFormat("")).isNull();
    }

    @Test
    void VAL_005_futureDatesTruncateAndBusinessDaysRespectActiveLocationHolidays() {
        ValidationService validation = validation();
        assertThat(validation.isFutureDate(TODAY.minusSeconds(1))).isFalse();
        assertThat(validation.isFutureDate(TODAY.plusHours(23))).isFalse();
        assertThat(validation.isFutureDate(TODAY.plusDays(1))).isTrue();
        assertThat(validation.isFutureDate(null)).isNull();
        assertThat(validation.isBusinessDay(LocalDateTime.of(2024, 1, 27, 10, 0), "HQ")).isFalse();
        assertThat(validation.isBusinessDay(LocalDateTime.of(2024, 1, 28, 10, 0), "HQ")).isFalse();
        assertThat(validation.isBusinessDay(TODAY, "HQ")).isTrue();
        LocalDateTime globalHoliday = LocalDateTime.of(2024, 1, 1, 18, 0);
        assertThat(validation.isBusinessDay(globalHoliday, "HQ")).isFalse();
        assertThat(validation.isBusinessDay(globalHoliday)).isFalse();
        jdbc.update("INSERT INTO holidays (holiday_id,holiday_date,holiday_name,location_code,active_flag,created_by) VALUES "
                + "(9901,'2024-02-01','Local','HQ','Y','TEST'), "
                + "(9902,'2024-02-02','Inactive',NULL,'N','TEST'), "
                + "(9903,'2024-02-05 12:00:00','Non-midnight',NULL,'Y','TEST')");
        LocalDateTime localHoliday = TODAY.plusDays(1).plusHours(14);
        assertThat(validation.isBusinessDay(localHoliday, "HQ")).isFalse();
        assertThat(validation.isBusinessDay(localHoliday, "OTHER")).isTrue();
        assertThat(validation.isBusinessDay(localHoliday)).isTrue();
        assertThat(validation.isBusinessDay(localHoliday, "")).isTrue();
        assertThat(validation.isBusinessDay(TODAY.plusDays(2), "HQ")).isTrue();
        assertThat(validation.isBusinessDay(TODAY.plusDays(5), "HQ")).isTrue();
        assertThat(validation.isBusinessDay(null, "HQ")).isTrue();
    }

    @Test
    void VAL_006_requiredFieldsOnlyCheckEmployeesInLegacyOrder() {
        ValidationService validation = validation();
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("11"))).isNull();
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("-1"))).isEqualTo("Record not found");
        assertThat(validation.validateRequiredFields("EMPLOYEES", null)).isEqualTo("Record not found");
        assertThat(validation.validateRequiredFields("JOB_GRADES", number("-1"))).isNull();
        assertThat(validation.validateRequiredFields("employees", number("-1"))).isNull();
        assertThat(validation.validateRequiredFields(null, null)).isNull();
        // The migrated table forbids missing required fields; a temporary shadow exercises the defensive branches.
        jdbc.execute("CREATE TEMP TABLE employees (emp_id numeric, first_name text, last_name text, hire_date timestamp, dept_id numeric, job_id numeric) ON COMMIT DROP");
        jdbc.update("INSERT INTO employees (emp_id) VALUES (9901)");
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("9901"))).isEqualTo("First Name is required");
        jdbc.update("UPDATE employees SET first_name='Fixture'");
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("9901"))).isEqualTo("Last Name is required");
        jdbc.update("UPDATE employees SET last_name='Employee'");
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("9901"))).isEqualTo("Hire Date is required");
        jdbc.update("UPDATE employees SET hire_date='2024-01-31'");
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("9901"))).isEqualTo("Department is required");
        jdbc.update("UPDATE employees SET dept_id=-1");
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("9901"))).isEqualTo("Job Title is required");
        jdbc.update("UPDATE employees SET job_id=-1");
        assertThat(validation.validateRequiredFields("EMPLOYEES", number("9901"))).isNull();
    }

    @Test
    void VLL_001_serverEmailRuleWinsOverPll() {
        assertThat(validation().validateEmailFormat("a@b.c")).isFalse();
        assertThat(validation().validateEmailFormat("user@mail.company.com")).isTrue();
        assertThat(validation().validateEmailFormat("a b@c.co")).isFalse();
        assertThat(validation().validateEmailFormat(null)).isNull();
    }

    @Test
    void VLL_002_serverPhoneNormalizationWinsOverPll() {
        assertThat(validation().validatePhoneFormat("123456789a")).isFalse();
        assertThat(validation().validatePhoneFormat("abc2125551000xyz")).isTrue();
        assertThat(validation().validatePhoneFormat("92125551000")).isTrue();
        assertThat(validation().validatePhoneFormat(null)).isNull();
    }

    @Test
    void VLL_003_commonSsnRuleWinsWithoutInventingValidationWrapper() {
        assertThat(common.isValidSsn("000-00-0000")).isTrue();
        assertThat(common.isValidSsn("abc123-45-6789xyz")).isTrue();
        assertThat(common.isValidSsn("abcdefghi")).isNull();
        assertThat(common.isValidSsn(null)).isNull();
    }

    @Test
    void VLL_004_serverFuturePredicateIsOppositeOfPllNotFuture() {
        assertThat(validation().isFutureDate(TODAY)).isFalse();
        assertThat(validation().isFutureDate(TODAY.plusHours(23))).isFalse();
        assertThat(validation().isFutureDate(TODAY.plusDays(1))).isTrue();
        assertThat(validation().isFutureDate(null)).isNull();
    }

    @Test
    void VLL_005_serverSalaryRequiredAndCurrentBoundsWinOverPll() {
        ValidationService validation = validation();
        assertThat(validation.validateSalaryForGrade(null, number("1"))).isEqualTo("Salary and grade are required");
        assertThat(validation.validateSalaryForGrade(number("40000"), null)).isEqualTo("Salary and grade are required");
        assertThat(validation.validateSalaryForGrade(number("55001"), number("1")))
                .isEqualTo("Salary $55,001.00 exceeds maximum for grade Entry Level ($55,000.00)");
        jdbc.update("UPDATE job_grades SET max_salary=56000 WHERE grade_id=1");
        assertThat(validation.validateSalaryForGrade(number("55001"), number("1"))).isNull();
    }
}
