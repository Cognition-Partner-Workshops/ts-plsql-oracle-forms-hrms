package com.hrms;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@SpringBootTest
@Testcontainers
class SchemaMigrationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.8-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TestFactory
    Stream<DynamicTest> oracleSeedCounts() throws Exception {
        List<String[]> expected;
        try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/oracle-table-counts.csv")), StandardCharsets.UTF_8))) {
            assertThat(reader.readLine()).isEqualTo("table_name,row_count");
            expected = reader.lines().map(line -> line.split(",")).toList();
        }
        assertThat(expected).hasSize(30);
        assertThat(jdbcTemplate.queryForList("SELECT upper(tablename) FROM pg_tables "
                + "WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'", String.class))
                .containsExactlyInAnyOrderElementsOf(expected.stream().map(row -> row[0]).toList());
        return expected.stream().map(row -> dynamicTest(row[0], () -> {
            assertThat(row).hasSize(2);
            assertThat(row[0]).matches("[A-Z_]+");
            long oracle = Long.parseLong(row[1]);
            Long actual = jdbcTemplate.queryForObject("SELECT count(*) FROM " + row[0], Long.class);
            System.out.printf("%s | %d | %d | %s%n", row[0], oracle, actual,
                    Objects.equals(actual, oracle) ? "match" : "FAILED");
            assertThat(actual).as("%s count after Flyway vs Oracle load", row[0]).isEqualTo(oracle);
        }));
    }
}
