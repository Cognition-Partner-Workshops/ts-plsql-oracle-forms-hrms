package com.hrms;

import com.hrms.parity.GoldenFormats;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenFormatsTests {
    @Test
    void numbersUseHalfUpAndKeepLeadingZero() {
        assertThat(GoldenFormats.number(new BigDecimal("-0.50"))).isEqualTo("-0.50");
        assertThat(GoldenFormats.number(new BigDecimal("37500"))).isEqualTo("37500.00");
        assertThat(GoldenFormats.number(new BigDecimal("1.005"))).isEqualTo("1.01");
        assertThat(GoldenFormats.number(new BigDecimal("-1.005"))).isEqualTo("-1.01");
        assertThat(GoldenFormats.number(BigDecimal.ZERO)).isEqualTo("0.00");
        assertThat(GoldenFormats.number(new BigDecimal("0.004"))).isEqualTo("0.00");
        assertThatThrownBy(() -> GoldenFormats.number(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void coverageCsvAlwaysQuotesAndPreservesText() {
        assertThat(GoldenFormats.csv("name", "Employee, Fixture")).isEqualTo("\"name\",\"Employee, Fixture\"");
        assertThat(GoldenFormats.csv("a\"b", "  x  ", null, "", "a\nb"))
                .isEqualTo("\"a\"\"b\",\"  x  \",\"<null>\",\"\",\"a\nb\"");
        assertThat(GoldenFormats.csv()).isEmpty();
    }
}
