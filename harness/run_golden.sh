#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ "$1" != --record ]; }; then
  echo 'Usage: harness/run_golden.sh [--record]' >&2
  exit 2
fi
trap 'tail -50 harness/work/load.log harness/work/calculate.log 2>/dev/null >&2' ERR
docker compose -f harness/compose.yaml up -d --wait --wait-timeout 600
python3 harness/load.py
docker compose -f harness/compose.yaml cp harness/work/load.sql oracle:/tmp/load.sql
docker compose -f harness/compose.yaml exec -T oracle sqlplus -s 'system/fixture_only@//localhost/FREEPDB1' @/tmp/load.sql > harness/work/load.log
docker compose -f harness/compose.yaml cp harness/calculate.sql oracle:/tmp/calculate.sql
docker compose -f harness/compose.yaml exec -T oracle sqlplus -s 'system/fixture_only@//localhost/FREEPDB1' @/tmp/calculate.sql > harness/work/calculate.log
mkdir -p harness/golden
for file in payroll leave; do
  docker compose -f harness/compose.yaml cp "oracle:/tmp/$file.csv" "harness/work/$file.csv"
  if [ "${1:-}" = --record ]; then
    cp "harness/work/$file.csv" "harness/golden/$file.csv"
  else
    diff -u "harness/golden/$file.csv" "harness/work/$file.csv"
  fi
  echo "$file: PASS ($(($(wc -l < "harness/work/$file.csv") - 1)) rows)"
done
