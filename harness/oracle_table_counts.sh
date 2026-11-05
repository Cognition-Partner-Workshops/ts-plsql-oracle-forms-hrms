#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
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
python3 - <<'PY'
from pathlib import Path
import re
import subprocess

tables = sorted(name for path in Path('schema/tables').glob('*.sql')
                for name in re.findall(r'CREATE TABLE HRMS\.(\w+)', path.read_text()))
sql = 'whenever sqlerror exit failure rollback\nset heading off feedback off pagesize 0 linesize 200 trimspool on\n'
sql += '\n'.join(f"select '{name},' || count(*) from HRMS.{name};" for name in tables)
sql += '\nexit\n'
result = subprocess.run(['docker', 'compose', '-f', 'harness/compose.yaml',
                         'exec', '-T', 'oracle', 'sqlplus', '-s',
                         'system/fixture_only@//localhost/FREEPDB1'],
                        input=sql, text=True, capture_output=True, check=True)
rows = [line.strip() for line in result.stdout.splitlines() if line.strip()]
if len(rows) != len(tables) or any(not re.fullmatch(r'[A-Z_]+,\d+', row) for row in rows):
    raise RuntimeError(f'Unexpected Oracle output: {result.stdout}')
target = Path('java-service/src/test/resources/oracle-table-counts.csv')
target.write_text('table_name,row_count\n' + '\n'.join(rows) + '\n')
print(target.read_text(), end='')
PY
