package com.hrms.parity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("parity")
public class ParityRunner implements ApplicationRunner, ExitCodeGenerator {
    private static final List<String> ORDER = List.of("payroll", "leave", "common", "validation",
            "employee", "security", "performance", "notification", "integration", "reporting", "audit");
    private final Map<String, GoldenExport> exporters;
    private final Path goldenDir;
    private final Path workDir;
    private int exitCode;

    public ParityRunner(List<GoldenExport> exporters, @Value("${parity.golden-dir}") Path goldenDir,
            @Value("${parity.work-dir}") Path workDir) {
        this.exporters = exporters.stream().collect(Collectors.toMap(GoldenExport::name, Function.identity()));
        this.goldenDir = goldenDir;
        this.workDir = workDir;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Files.createDirectories(workDir);
        var table = new StringBuilder("| File | Result | Rows expected | Rows matched | Mismatches | First diffs |\n"
                + "| --- | --- | --- | --- | --- | --- |\n");
        List<Path> files;
        try (var paths = Files.list(goldenDir)) {
            files = paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".csv"))
                    .sorted(Comparator.comparingInt((Path p) -> rank(name(p))).thenComparing(ParityRunner::name))
                    .toList();
        }
        for (Path file : files) {
            String name = name(file);
            var expected = Files.readAllLines(file);
            int rows = Math.max(0, expected.size() - 1);
            if (!exporters.containsKey(name)) {
                System.out.println(name + ": NOT PORTED");
                table.append("| ").append(name).append(".csv | not ported | ").append(rows).append(" | - | - | - |\n");
                continue;
            }
            GoldenComparator.Comparison result;
            try {
                var actual = exporters.get(name).export();
                Files.write(workDir.resolve(name + ".csv"), actual);
                result = GoldenComparator.compare(expected, actual, name.equals("payroll") || name.equals("leave"));
            } catch (Exception e) {
                result = new GoldenComparator.Comparison(rows, 0, Math.max(1, rows),
                        List.of("-export / +" + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
            if (result.passed()) {
                System.out.println(name + ": PASS (" + rows + " rows)");
            } else {
                exitCode = 1;
                System.out.println(name + ": FAIL (" + result.matched() + "/" + rows
                        + " rows matched, " + result.mismatches() + " mismatches)");
            }
            table.append("| ").append(name).append(".csv | ").append(result.passed() ? "passed" : "failed")
                    .append(" | ").append(rows).append(" | ").append(result.matched()).append(" | ")
                    .append(result.mismatches()).append(" | ")
                    .append(result.diffs().stream().map(ParityRunner::snippet).collect(Collectors.joining("; ")))
                    .append(" |\n");
        }
        Files.writeString(workDir.resolve("java-parity.md"), table);
        System.out.print(table);
    }

    private static String snippet(String value) {
        return "`" + value.replace("|", "&#124;").replace("`", "&#96;")
                .replace("\n", "\\n").replace("\r", "\\r") + "`";
    }

    private static String name(Path file) {
        String filename = file.getFileName().toString();
        return filename.substring(0, filename.length() - 4);
    }

    private static int rank(String name) {
        int index = ORDER.indexOf(name);
        return index < 0 ? ORDER.size() : index;
    }

    @Override
    public int getExitCode() { return exitCode; }
}
