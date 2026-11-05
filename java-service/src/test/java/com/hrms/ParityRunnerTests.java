package com.hrms;

import com.hrms.parity.GoldenExport;
import com.hrms.parity.ParityConfiguration;
import com.hrms.parity.ParityRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class ParityRunnerTests {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.8-alpine");

    @TempDir
    Path directory;

    @Test
    void runsFreshDatabaseAndReportsPassFailMissingAndException(CapturedOutput output) throws Exception {
        Path golden = Files.createDirectory(directory.resolve("golden"));
        Path work = directory.resolve("work");
        Files.write(golden.resolve("payroll.csv"), List.of("header", "right"));
        Files.write(golden.resolve("common.csv"), List.of("scenario,result", "a", "b", "a"));
        Files.write(golden.resolve("leave.csv"), List.of("header", "not ported"));
        Files.write(golden.resolve("error.csv"), List.of("header", "expected"));
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("create table must_be_cleaned(id int)");
        }
        var app = new SpringApplication(Exports.class);
        var context = app.run("--spring.profiles.active=parity",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--parity.golden-dir=" + golden, "--parity.work-dir=" + work);
        assertThat(context.getBean(Clock.class).instant()).isEqualTo(Instant.parse("2024-01-31T12:00:00Z"));
        assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                "select to_regclass('must_be_cleaned')::text", String.class)).isNull();
        assertThat(SpringApplication.exit(context)).isEqualTo(1);
        assertThat(output.getOut()).containsSubsequence("payroll: FAIL (0/1 rows matched, 1 mismatches)",
                "leave: NOT PORTED", "common: PASS (3 rows)", "error: FAIL (0/1 rows matched, 1 mismatches)");
        String table = Files.readString(work.resolve("java-parity.md"));
        assertThat(output.getOut()).contains(table);
        assertThat(table).contains("| common.csv | passed | 3 | 3 | 0 |", "| leave.csv | not ported | 1 | - | - | - |",
                "| payroll.csv | failed | 1 | 0 | 1 | `-right / +wrong&#124;value` |",
                "IllegalStateException: deliberate");
        assertThat(Files.readAllLines(work.resolve("common.csv"))).containsExactly("scenario,result", "b", "a", "a");
    }

    @TestConfiguration
    @EnableAutoConfiguration
    @Import({ParityConfiguration.class, ParityRunner.class})
    static class Exports {
        @Bean
        GoldenExport commonExport() {
            return exporter("common", List.of("scenario,result", "b", "a", "a"));
        }

        @Bean
        GoldenExport wrongPayrollExport() {
            return exporter("payroll", List.of("header", "wrong|value"));
        }

        @Bean
        GoldenExport throwingExport() {
            return new GoldenExport() {
                public String name() { return "error"; }
                public List<String> export() { throw new IllegalStateException("deliberate"); }
            };
        }

        private GoldenExport exporter(String name, List<String> lines) {
            return new GoldenExport() {
                public String name() { return name; }
                public List<String> export() { return lines; }
            };
        }
    }
}
