#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ "$1" != --record ]; }; then
  echo 'Usage: harness/run_golden.sh [--record]' >&2
  exit 2
fi
trap 'tail -50 harness/work/load.log harness/work/calculate.log 2>/dev/null >&2' ERR
docker compose -f harness/compose.yaml up -d --wait --wait-timeout 600
docker compose -f harness/compose.yaml exec -T oracle sqlplus -s / as sysdba <<'SQL'
whenever sqlerror exit failure rollback
alter session set container = FREEPDB1;
grant execute on sys.dbms_crypto to system with grant option;
exit
SQL
python3 harness/load.py
docker compose -f harness/compose.yaml cp harness/work/load.sql oracle:/tmp/load.sql
docker compose -f harness/compose.yaml exec -T oracle sqlplus -s 'system/fixture_only@//localhost/FREEPDB1' @/tmp/load.sql > harness/work/load.log
docker compose -f harness/compose.yaml cp harness/calculate.sql oracle:/tmp/calculate.sql
docker compose -f harness/compose.yaml cp harness/coverage.sql oracle:/tmp/coverage.sql
docker compose -f harness/compose.yaml exec -T oracle sqlplus -s 'system/fixture_only@//localhost/FREEPDB1' @/tmp/calculate.sql > harness/work/calculate.log
mkdir -p harness/golden
for file in payroll leave common validation employee security performance notification integration reporting audit; do
  docker compose -f harness/compose.yaml cp "oracle:/tmp/$file.csv" "harness/work/$file.csv"
  if [ "$file" != payroll ] && [ "$file" != leave ]; then
    python3 harness/validate_csv.py "harness/work/$file.csv"
  fi
  if [ "${1:-}" = --record ] && [ "$file" != payroll ] && [ "$file" != leave ]; then
    cp "harness/work/$file.csv" "harness/golden/$file.csv"
    result=RECORDED
  else
    diff -u "harness/golden/$file.csv" "harness/work/$file.csv"
    result=PASS
  fi
  echo "$file: $result ($(($(wc -l < "harness/work/$file.csv") - 1)) rows)"
done
