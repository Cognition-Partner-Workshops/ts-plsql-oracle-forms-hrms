package com.hrms.parity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class GoldenFormats {
    private GoldenFormats() { }

    public static String number(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String csv(String... fields) {
        return Arrays.stream(fields)
                .map(value -> "\"" + (value == null ? "<null>" : value).replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(","));
    }
}
