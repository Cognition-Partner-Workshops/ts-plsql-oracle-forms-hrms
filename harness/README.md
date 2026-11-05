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
