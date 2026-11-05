package com.hrms;

import com.hrms.parity.GoldenComparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GoldenComparatorTests {
    @Test
    void orderedRowsMustKeepTheirPositions() {
        var expected = List.of("header", "a", "b");
        assertThat(GoldenComparator.compare(expected, expected, true).passed()).isTrue();
        var result = GoldenComparator.compare(expected, List.of("header", "b", "a"), true);
        assertThat(result.matched()).isZero();
        assertThat(result.mismatches()).isEqualTo(2);
    }

    @Test
    void multisetsIgnoreOrderButKeepDuplicates() {
        var expected = List.of("scenario,result", "a", "a", "b");
        assertThat(GoldenComparator.compare(expected, List.of("scenario,result", "b", "a", "a"), false).passed()).isTrue();
        var result = GoldenComparator.compare(expected, List.of("scenario,result", "b", "a", "b"), false);
        assertThat(result.matched()).isEqualTo(2);
        assertThat(result.mismatches()).isEqualTo(1);
    }

    @Test
    void headerMismatchNeverPassesEvenWhenAllRowsMatch() {
        var result = GoldenComparator.compare(List.of("header", "a"), List.of("wrong", "a"), false);
        assertThat(result.passed()).isFalse();
        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.diffs()).containsExactly("-header / +wrong");
    }

    @Test
    void extraAndMissingRowsAreMismatches() {
        for (boolean ordered : List.of(true, false)) {
            var extra = GoldenComparator.compare(List.of("h", "a"), List.of("h", "a", "b"), ordered);
            assertThat(extra.matched()).isEqualTo(1);
            assertThat(extra.mismatches()).isEqualTo(1);
            var missing = GoldenComparator.compare(List.of("h", "a", "b"), List.of("h", "a"), ordered);
            assertThat(missing.expected()).isEqualTo(2);
            assertThat(missing.matched()).isEqualTo(1);
            assertThat(missing.mismatches()).isEqualTo(1);
        }
    }

    @Test
    void emptyInputsNeedAHeaderAndDiffsAreLimited() {
        assertThat(GoldenComparator.compare(List.of("h"), List.of("h"), false).passed()).isTrue();
        assertThat(GoldenComparator.compare(List.of(), List.of(), false).passed()).isFalse();
        assertThat(GoldenComparator.compare(List.of("h"), List.of(), true).passed()).isFalse();
        var result = GoldenComparator.compare(List.of("h", "1", "2", "3", "4"), List.of("h"), true);
        assertThat(result.mismatches()).isEqualTo(4);
        assertThat(result.diffs()).hasSize(3);
    }
}
