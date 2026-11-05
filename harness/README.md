# Legacy golden harness

Run from the repository root with Docker Compose, Bash and Python 3.9+ (no Python
dependencies). Docker must be running; port 1522 must be free for the container's
loopback-only Oracle listener. Startup pulls `gvenzl/oracle-free:23-slim` and waits
up to 600 seconds for database health.

```sh
harness/run_golden.sh --record  # re-record every golden except payroll and leave
harness/run_golden.sh           # recreate HRMS from seed, run, compare all 11 CSVs
python3 -m unittest discover -s harness -p 'test_*.py' -v
```

Every invocation **drops and recreates HRMS** in the disposable Oracle container.
Never point this harness at another database. `--record` always compares, and
never overwrites, `payroll.csv` or `leave.csv`. It fails if either differs or is
missing. Review all new CSV diffs before accepting a re-recording.

## Output

Each golden prints `name: PASS (n rows)` or, with `--record`, `name: RECORDED (n rows)`.
`RECORDED` means an observation was saved, not that a comparison passed. A mismatch
prints a `diff -u` of `harness/golden/<name>.csv` against `harness/work/<name>.csv`
and the script exits nonzero; `validate_csv.py` also fails the run on a malformed or
empty CSV before it is recorded or compared.

Golden files other than `payroll.csv` and `leave.csv` have `scenario,result` columns.
Scalar results use `<null>` for SQL NULL and `true`/`false` for booleans; cursor
results are JSON objects, and each cursor also records `<scenario>.row_count`.
Rows are sorted by scenario and full result with binary collation, duplicates kept.

For troubleshooting, inspect `harness/work/load.log` and
`harness/work/calculate.log`. To remove the disposable database entirely:

```sh
docker compose -f harness/compose.yaml down -v
```

The reviewed recording used Oracle Free 23 slim image digest
`sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.

## PostgreSQL seed counts

```sh
harness/oracle_table_counts.sh            # writes java-service/src/test/resources/oracle-table-counts.csv
cd java-service && ./mvnw -B verify       # compares each table with Flyway-migrated PostgreSQL 16.8
```

The counts script uses the same disposable Oracle container and loader, and
counts every table right after load. Review the CSV diff before committing it.

## Java parity runner

Run `harness/run_java_parity.sh` from the repository root with Docker and Java 17+
available. It builds the Java service and uses only a disposable PostgreSQL
16.8 container on loopback port 55432 (which must be free), removed on exit.
The parity profile cleans and migrates that database before running all exports
against the same seed and a fixed UTC clock at `2024-01-31T12:00:00Z`.

Each golden prints `name: PASS (n rows)`, `name: NOT PORTED`, or
`name: FAIL (matched/expected rows matched, k mismatches)`. Failures exit nonzero;
not-ported suites alone exit zero and are never reported as passed.
Exports and `java-parity.md` are written to `harness/work/java`.
The profile properties `parity.golden-dir` and `parity.work-dir` override paths
when invoking the jar directly. Never point the parity profile at another DB.

To plug in a port, implement `com.hrms.parity.GoldenExport` as a Spring bean:
`name()` returns the golden basename and `export()` returns exact CSV lines,
including the header. Inject `Clock` for frozen time and use `GoldenFormats`
for two-decimal HALF_UP numbers and always-quoted CSV fields. Payroll and leave
must preserve legacy ordering; coverage rows compare as multisets with duplicates.
