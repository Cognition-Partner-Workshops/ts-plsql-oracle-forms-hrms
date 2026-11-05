# Legacy golden harness

Run from the repository root with Docker Compose, Bash and Python 3.9+ (no Python
dependencies). Docker must be running; port 1522 must be free for the container's
loopback-only Oracle listener. Startup pulls `gvenzl/oracle-free:23-slim` and waits
up to 600 seconds for database health.

```sh
harness/run_golden.sh --record  # record ONLY the nine added suites
harness/run_golden.sh           # recreate HRMS from seed, run, compare all 11 CSVs
python3 -m unittest discover -s harness -p 'test_*.py' -v
```

Regenerate PostgreSQL's expected seed counts with
`harness/oracle_table_counts.sh`. This recreates HRMS using the same Oracle
loader, then queries every table **before** payroll/coverage scenarios mutate
the seed. It writes `java-service/src/test/resources/oracle-table-counts.csv`;
review the diff, then run `cd java-service && ./mvnw -B verify` to compare each
table against a fresh PostgreSQL 16.8 Testcontainers database migrated by Flyway.

Every invocation **drops and recreates HRMS** in the disposable Oracle container.
Never point this harness at another database. `--record` always compares, and
never overwrites, `payroll.csv` or `leave.csv`. It fails if either differs or is
missing. Review all new CSV diffs before accepting a re-recording. `RECORDED`
means an observation was saved, not that a comparison passed.

For troubleshooting, inspect `harness/work/load.log` and
`harness/work/calculate.log`. To remove the disposable database entirely:

```sh
docker compose -f harness/compose.yaml down -v
```

The reviewed recording used Oracle Free 23 slim image digest
`sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.

## Inputs, ordering, and representation

The clock remains `2024-01-31 12:00:00`, payroll is January 2024, and leave accrual
is January 31. The original calculation and both original spools are unchanged.
`calculate.sql` then includes `coverage.sql`. Reporting and integration run
against the original seed and calculated balances **before** employee mutations.
Security and performance use seeded employee 11 and manager 10. Employee scenarios
transfer/terminate employee 11, add a pending February leave request to observe its
cancellation, and hire one synthetic employee (`fixture.hire@example.invalid`).
No seed files are edited. No email is sent and no external system is contacted.

Each added CSV has `scenario,result` columns. Scalar results use `<null>` for SQL
NULL and `true`/`false` for booleans. Cursor results are JSON objects retaining
column names, numeric types, nulls, and ISO dates. Each cursor capture also records
`<scenario>.row_count`, including empty result sets. CSV quoting preserves commas,
quotes and the benefits feed's fixed-width spaces. Rows are sorted by scenario
and full result with binary collation; duplicate rows are retained. This compares
**multisets**, not the order returned by cursors/files. Integration header/trailer
records are retained, but file record ordering is not proved.

`validate_csv.py` rejects malformed/empty CSVs, invalid JSON objects and mismatched
row counts before recording or comparing. Unexpected SQL errors fail the run;
negative scenarios accept only their specified legacy Oracle error code.
Audit session IDs and client IPs are intentionally excluded as environment-specific.

## Inventory-driven scope

The inventory in [PR #19](https://github.com/Cognition-Partner-Workshops/ts-plsql-oracle-forms-hrms/pull/19)
finds eleven packages; payroll and one leave accrual entry point already have
goldens. The other **nine packages** each now have a directly invoked scenario and
a reviewed output file. This is selected behavior coverage, not full package or
Forms coverage. No rule IDs existed on the harness base when these scenarios were
written; scenario names are descriptive, not invented rule IDs.

| Package / CSV | Recorded behavior | Remaining limits |
| --- | --- | --- |
| COMMON / common | Inclusive weekdays, weekend skipping, fiscal-year boundary and quarter, phone/name/SSN masking, missing parameter | Other formatting/config writes and log failure paths |
| VALIDATION / validation | Date ranges/nulls, email/phone/employee-number checks, fixed-date future/weekend tests, all ten grades at/below minimum and at/above maximum, required fields | Holiday/location combinations, all invalid-input combinations |
| EMPLOYEE / employee | Transfer department/job, typed trigger + package history, termination status/salary/pending-leave effects, direct-reactivation rejection, normalized hire/default location/salary | Rehire/promotion/search, concurrency, delete/insert error paths; seeded employee 11 has no pay elements, so deactivation of a populated pay-element set remains unproven |
| SECURITY / security | Real DBMS_CRYPTO hash call, missing user, session creation/exact timeout/expiry/logout, three permission checks for every seeded employee, short-password rejection, password-change audit, encryption error and decrypt fallback | Real password verification/persistence is absent in the legacy model; encryption success remains failed, not proved |
| PERFORMANCE / performance | Cycle open/close, self assessment, rating labels at 1/1.49/1.5/2.49/2.5/3.49/3.5/4.49/4.5/5, invalid rating rejection, acknowledgement/team cursor, goal 50/100%, rating distribution | Bulk review generation, multi-employee rating distribution, other invalid transitions |
| NOTIFICATION / notification | Employee email lookup, queued content/status, cancellation, retries below/at limit, employee/performance notification effects including duplicates | SMTP delivery, actual transport failures and rate limiting; retry states are explicitly arranged test inputs |
| INTEGRATION / integration | Actual UTL_FILE GL journal and fixed-width benefits output from seed, missing integration status, sync/refresh log observations | Time/attendance import, external financial/vendor/LDAP acceptance; sync/refresh are legacy logging-only placeholders, not integrations |
| REPORTING / reporting | All seven report cursors: headcount, compensation, turnover, new hires, leave utilization, payroll summary, EEO; headcount department/location and compensation department/grade filters | All optional-filter combinations, post-maintenance report state, actual denormalized table refresh |
| AUDIT / audit | Explicit before/after action plus employee, session, password and cycle audit effects with frozen dates | Purge/change-history cursor, full audit payloads on all DML, rollback/failure behavior |

## Fixture repairs required before capture

* `DBMS_CRYPTO` exists in Oracle Free 23; the old failure was a privilege issue,
  not a missing implementation. The runner grants SYSTEM execute **with grant
  option** inside the disposable `FREEPDB1` using container-local SYSDBA, and the
  loader grants HRMS execute. No security body is stubbed/replaced. All package
  errors and invalid objects, including SECURITY, now fail loading.
* `TRG_EMP_BEFORE_UPDATE` is no longer omitted. Its history inserts now use the
  checked-in typed schema: `HIST_ID`, `EFFECTIVE_DATE`, `CREATED_BY`, typed old/new
  department/job fields; statuses go in `COMMENTS`. Department changes use the
  permitted `TRANSFER` change type. Job changes use `STATUS_CHANGE` plus
  `REASON_CODE=JOB_CHANGE` rather than assuming every job change is a promotion.
  Existing status-transition rejection and modification columns are preserved.
* The salary sequence is advanced past seeded salary IDs before scenarios run;
  otherwise a hire hits seed salary ID 1 and misleadingly reports a duplicate
  employee number. No existing salary rows or payroll amounts are changed.
* Oracle directory objects point only to `/tmp` **inside the disposable container**
  for the GL and benefits files. The existing seed-alias and SQLERRM loader
  adaptations are unchanged.

## Observed legacy defects — not repaired or rounded up

* `PKG_SECURITY.authenticate` accepts an active email without checking the password;
  both synthetic password values create sessions. `change_password` only validates
  complexity and writes an audit row, not credentials.
* `encrypt_ssn` fails with **ORA-28234** (the hard-coded AES256 key is too short).
  That failure is recorded. `decrypt_ssn` returns `***DECRYPT_ERROR***` for invalid
  ciphertext. Successful SSN encryption/decryption remains **failed/unproven**.
* Hiring creates no HIRE history row: the autonomous history transaction cannot
  reference the uncommitted new employee and swallows the error. The autonomous
  welcome notification cannot see the new employee's email, so its queued recipient
  email is NULL. Both observed outputs remain explicit in the goldens.
* Transfer emits package history **and** trigger history; repeated manager-rating
  submissions emit repeated completion notifications. Duplicates are not deduplicated.
* Rating labels use the supplied unrounded rating, but `OVERALL_RATING` stores one
  decimal place: input 4.49 is stored as 4.5 with `Exceeds Expectations`, while input
  4.5 is stored as 4.5 with `Exceptional`. The goldens preserve this discrepancy.

These files characterize the executable legacy fixture, including its defects;
they do not approve those defects for a redesigned authentication or encryption
service. No Java implementation is supplied by this ticket, so Java-to-legacy
parity remains untested.
