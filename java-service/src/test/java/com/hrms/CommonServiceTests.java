package com.hrms;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.hrms.common.CommonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class CommonServiceTests {
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
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-01-31T12:00:00Z"), ZoneOffset.UTC);

    private CommonService common() { return new CommonService(jdbc, CLOCK, transactions); }

    @Test
    void COM_001_parametersUseGroupAndCodeAndInvalidConversionsReturnNull() {
        CommonService common = common();
        jdbc.update("INSERT INTO system_parameters (param_id,param_group,param_code,param_value,created_by) "
                + "VALUES (9901,'COM_TEST','VALUE','12.50','TEST'), (9902,'COM_OTHER','VALUE','2024-02-29','TEST'), "
                + "(9903,'COM_TEST','LOCKED','fixed','TEST')");
        jdbc.update("UPDATE system_parameters SET editable_flag = 'N' WHERE param_id = 9903");
        assertThat(common.getParam("COM_TEST", "VALUE")).isEqualTo("12.50");
        assertThat(common.getParamNumber("COM_TEST", "VALUE")).isEqualByComparingTo("12.50");
        assertThat(common.getParamDate("COM_OTHER", "VALUE")).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(common.getParam("MISSING", "VALUE")).isNull();
        assertThat(common.getParamNumber("MISSING", "VALUE")).isNull();
        assertThat(common.getParamDate("MISSING", "VALUE")).isNull();
        assertThat(common.getParam(null, "VALUE")).isNull();
        common.setParam("COM_TEST", "VALUE", "invalid", "TEST_EDITOR");
        assertThat(common.getParamNumber("COM_TEST", "VALUE")).isNull();
        assertThat(common.getParamDate("COM_TEST", "VALUE")).isNull();
        common.setParam("COM_TEST", "VALUE", "2024-02-30");
        assertThat(common.getParamDate("COM_TEST", "VALUE")).isNull();
        assertThat(jdbc.queryForObject("SELECT modified_date FROM system_parameters WHERE param_id=9901", LocalDateTime.class))
                .isEqualTo(LocalDateTime.now(CLOCK));
        assertThatThrownBy(() -> common.setParam("COM_TEST", "LOCKED", "new"))
                .hasMessage("ORA-20900: Parameter not found or not editable: COM_TEST.LOCKED");
        assertThatThrownBy(() -> common.setParam("MISSING", "VALUE", "new")).hasMessageContaining("ORA-20900");
    }

    @Test
    void COM_002_businessDaysIncludeEndpointsAndSkipWeekendsOnly() {
        CommonService common = common();
        LocalDate friday = LocalDate.of(2024, 1, 26);
        assertThat(common.businessDaysBetween(friday, friday.plusDays(3))).isEqualTo(2);
        assertThat(common.businessDaysBetween(friday, friday)).isEqualTo(1);
        assertThat(common.businessDaysBetween(friday.plusDays(1), friday.plusDays(2))).isZero();
        assertThat(common.businessDaysBetween(friday.plusDays(1), friday)).isZero();
        assertThat(common.businessDaysBetween((LocalDate) null, friday)).isZero();
        assertThat(common.businessDaysBetween(friday, null)).isZero();
        assertThat(common.businessDaysBetween(friday.atTime(23, 59), friday.atStartOfDay())).isEqualTo(1);
        LocalDate holiday = LocalDate.of(2024, 1, 1);
        assertThat(common.businessDaysBetween(holiday, holiday)).isEqualTo(1);
        assertThat(common.addBusinessDays(holiday.minusDays(3), BigDecimal.ONE)).isEqualTo(holiday);
        assertThat(common.addBusinessDays(friday, BigDecimal.ONE)).isEqualTo(friday.plusDays(3));
        assertThat(common.addBusinessDays(friday, new BigDecimal("1.2"))).isEqualTo(friday.plusDays(4));
        assertThat(common.addBusinessDays(friday, BigDecimal.TEN.negate())).isEqualTo(friday);
        assertThat(common.addBusinessDays(friday.plusDays(1), BigDecimal.ZERO)).isEqualTo(friday.plusDays(1));
        assertThat(common.addBusinessDays(friday.atTime(12, 30), null)).isEqualTo(friday);
        assertThat(common.addBusinessDays((LocalDate) null, BigDecimal.ONE)).isNull();
    }

    @Test
    void COM_003_fiscalYearTurnsInOctoberAndQuartersFollowFiscalCalendar() {
        CommonService common = common();
        for (int month = 1; month <= 12; month++) {
            LocalDate date = LocalDate.of(2024, month, 1);
            assertThat(common.getFiscalYear(date)).isEqualTo(month >= 10 ? 2025 : 2024);
            assertThat(common.getFiscalQuarter(date)).isEqualTo(month >= 10 ? 1 : (month - 1) / 3 + 2);
        }
        assertThat(common.getFiscalYear(LocalDate.of(2024, 9, 30))).isEqualTo(2024);
        assertThat(common.getFiscalYear()).isEqualTo(2024);
        assertThat(common.getFiscalQuarter()).isEqualTo(2);
        assertThat(common.getFiscalYear(null)).isNull();
        assertThat(common.getFiscalQuarter(null)).isNull();
    }

    @Test
    void COM_004_ssnMaskUsesLastFourCharactersWithoutNormalizing() {
        CommonService common = common();
        assertThat(common.formatSsnMasked(null)).isEqualTo("***-**-****");
        assertThat(common.formatSsnMasked("")).isEqualTo("***-**-****");
        assertThat(common.formatSsnMasked("123")).isEqualTo("***-**-****");
        assertThat(common.formatSsnMasked("abcd")).isEqualTo("***-**-abcd");
        assertThat(common.formatSsnMasked("000-00-1234")).isEqualTo("***-**-1234");
        assertThat(common.formatSsnMasked("1234-56")).isEqualTo("***-**-4-56");
    }

    @Test
    void COM_005_emailUsesAnchoredRegexAndAlphabeticSuffix() {
        CommonService common = common();
        assertThat(common.isValidEmail("Fixture+tag@example.invalid")).isTrue();
        assertThat(common.isValidEmail("a..b@-example..co.uk")).isTrue();
        for (String email : new String[]{"a@b.c", "a@b.12", " a@b.co", "a@b.co ", "a@b.co\n", "a@b", "a b@c.co"}) {
            assertThat(common.isValidEmail(email)).as(email).isFalse();
        }
        assertThat(common.isValidEmail(null)).isNull();
        assertThat(common.isValidEmail("")).isNull();
    }

    @Test
    void COM_006_phoneStripsNonDigitsAndAcceptsAnyElevenDigitPrefix() {
        CommonService common = common();
        assertThat(common.isValidPhone("(212) 555-1000")).isTrue();
        assertThat(common.isValidPhone("9-212-555-1000")).isTrue();
        assertThat(common.isValidPhone("abc2125551000xyz")).isTrue();
        assertThat(common.isValidPhone("123456789")).isFalse();
        assertThat(common.isValidPhone("123456789012")).isFalse();
        assertThat(common.isValidPhone(null)).isNull();
        assertThat(common.isValidPhone("")).isNull();
        assertThat(common.isValidPhone("abc")).isNull();
        assertThat(common.formatPhone("212-555-1000")).isEqualTo("(212) 555-1000");
        assertThat(common.formatPhone("12125551000")).isEqualTo("+1 (212) 555-1000");
        assertThat(common.formatPhone("92125551000")).isEqualTo("92125551000");
        assertThat(common.formatPhone(null)).isNull();
        assertThatThrownBy(() -> common.isValidPhone("1".repeat(21))).hasMessageContaining("ORA-06502");
        assertThatThrownBy(() -> common.formatPhone("1".repeat(21))).hasMessageContaining("ORA-06502");
    }

    @Test
    void COM_007_ssnChecksNineDigitsButNotNonzeroGroups() {
        CommonService common = common();
        assertThat(common.isValidSsn("000-00-0000")).isTrue();
        assertThat(common.isValidSsn("abc123-45-6789xyz")).isTrue();
        assertThat(common.isValidSsn("12345678")).isFalse();
        assertThat(common.isValidSsn("1234567890")).isFalse();
        assertThat(common.isValidSsn(null)).isNull();
        assertThat(common.isValidSsn("")).isNull();
        assertThat(common.isValidSsn("abc")).isNull();
    }

    @Test
    void formattingPreservesLegacyDefaultsAndNullConcatenation() {
        CommonService common = common();
        assertThat(common.formatName("fIXTURE", "o'NEILL", "LF")).isEqualTo("O'Neill, Fixture");
        assertThat(common.formatName(null, null)).isEqualTo(" ");
        assertThat(common.formatName("a", "b", null)).isEqualTo("A B");
        assertThat(common.formatCurrency(new BigDecimal("1234.565"))).isEqualTo("$1,234.57");
        assertThat(common.formatCurrency(new BigDecimal("-1"), "EUR")).isEqualTo("€-1.00");
        assertThat(common.formatCurrency(BigDecimal.ZERO, "GBP")).isEqualTo("£0.00");
        assertThat(common.formatCurrency(BigDecimal.ONE, "CAD")).isEqualTo("CAD 1.00");
        assertThat(common.formatCurrency(null)).isEqualTo("$");
    }

    @Test
    void loggingCommitsAutonomouslyAndSwallowsFailures() {
        CommonService common = common();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            common.logError("COM_TEST", "error", "a\"b", "TEST");
            common.logInfo("COM_TEST", "info", "a\"b", "TEST");
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT new_values FROM audit_log WHERE table_name='ERROR_LOG'", String.class))
                .isEqualTo("{\"package\":\"COM_TEST\",\"procedure\":\"error\",\"message\":\"a\\\"b\"}");
        assertThat(jdbc.queryForObject("SELECT new_values FROM audit_log WHERE table_name='INFO_LOG'", String.class))
                .isEqualTo("{\"package\":\"COM_TEST\",\"procedure\":\"info\",\"message\":\"a\"b\"}");
        assertThat(jdbc.queryForObject("SELECT changed_date FROM audit_log WHERE table_name='ERROR_LOG'", LocalDateTime.class))
                .isEqualTo(LocalDateTime.now(CLOCK));
        common.logInfo(null, null, null);
        common.logError("COM_TEST", "long", "x".repeat(3001));
        common.logInfo("COM_TEST", "fail", "message", null);
        common.logError("COM_TEST", "fail", "message", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE table_name IN ('ERROR_LOG','INFO_LOG')", Integer.class))
                .isEqualTo(4);
    }
}
