# Legacy Oracle golden harness

From the repository root, with Docker Engine and Docker Compose available:

```sh
docker pull gvenzl/oracle-free:23-slim
docker compose -f harness/compose.yaml down -v
docker ps -a # confirm mc1105-hrms-oracle-1 is gone before each clean run
time harness/run_golden.sh
docker compose -f harness/compose.yaml down -v
```

The script loads the schema, seeds and packages, calculates January 2024 monthly
payroll and leave accrual as of 2024-01-31, then runs `diff -u` against the
checked-in golden CSVs (92 payroll rows, 138 leave rows, excluding headers).
Do not use `--record` when verifying. Generated SQL, SQL*Plus logs and CSVs are
in ignored `harness/work/`. Each run recreates HRMS; use only this disposable DB.

## Requirements and observed resources

- Image: `gvenzl/oracle-free:23-slim` (Oracle Free 23), tested on Linux amd64;
  observed digest `sha256:6d61d267a3b978c24c5ac1790e62e927416a0aec446bd86e4b3a1527562757bd`.
  The tag is mutable.
- Tested with Docker 29.9.2, Compose 5.6.1, 8 CPUs and 31 GiB host RAM.
  Oracle used about 2.07 GiB RAM after loading; Compose configures 1 GiB shared
  memory. These are observations, not measured minimum requirements.
- Image size: 2.84 GB; populated container writable layer: 3.17 GB.
  Host free disk fell from about 110 GiB to 105 GiB during the run; allow headroom.
- Port mapping: host `127.0.0.1:1522` to container `1521`; keep host port 1522 free.
  SQL*Plus runs inside the container against `FREEPDB1`.
- Host dependencies: Bash, Python 3 (tested 3.12.16), standard `diff`/`wc` tools.
  `load.py` uses only Python's standard library (`pathlib`, `re`); no pip packages
  or host Oracle client are needed.

## Verification observations

Two clean-container runs, each preceded by `down -v` and an empty `docker ps -a`:

| Run | Cold start to first successful health check | Script total | Payroll | Leave |
| --- | --- | --- | --- | --- |
| 1 | 10.225 s | 15.564 s | PASS, 92 rows | PASS, 138 rows |
| 2 | 10.169 s | 14.324 s | PASS, 92 rows | PASS, 138 rows |

Initial image pull: 13.144 s, measured separately. Script totals exclude pull
and teardown. Both diffs were empty in both runs; no flakiness across 2 runs.
No harness fixes or golden changes were needed. The loader intentionally reports
`PKG_SECURITY` compilation errors for unavailable `DBMS_CRYPTO` and omits the
incompatible employee update trigger; these existing fixture limitations do not
fail the payroll/leave checks. This is not verification of those omitted paths.

