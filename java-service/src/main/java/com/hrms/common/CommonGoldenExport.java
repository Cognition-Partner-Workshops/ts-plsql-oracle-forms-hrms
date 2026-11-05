package com.hrms.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.hrms.parity.GoldenExport;
import com.hrms.parity.GoldenFormats;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("common")
@Profile("parity")
public class CommonGoldenExport implements GoldenExport {
    private final CommonService common;

    public CommonGoldenExport(CommonService common) { this.common = common; }
    public String name() { return "common"; }

    public List<String> export() {
        LocalDate friday = LocalDate.of(2024, 1, 26);
        return List.of("scenario,result",
                GoldenFormats.csv("weekdays_inclusive", String.valueOf(common.businessDaysBetween(friday, LocalDate.of(2024, 1, 29)))),
                GoldenFormats.csv("add_business_days", common.addBusinessDays(friday, BigDecimal.ONE).toString()),
                GoldenFormats.csv("fy_september", common.getFiscalYear(LocalDate.of(2024, 9, 30)).toString()),
                GoldenFormats.csv("fy_october", common.getFiscalYear(LocalDate.of(2024, 10, 1)).toString()),
                GoldenFormats.csv("quarter_january", common.getFiscalQuarter(LocalDate.of(2024, 1, 31)).toString()),
                GoldenFormats.csv("phone", common.formatPhone("212-555-1000")),
                GoldenFormats.csv("masked_synthetic_ssn", common.formatSsnMasked("000000000")),
                GoldenFormats.csv("name", common.formatName("Fixture", "Employee", "LF")),
                GoldenFormats.csv("missing_param", common.getParam("FIXTURE", "MISSING")));
    }
}
