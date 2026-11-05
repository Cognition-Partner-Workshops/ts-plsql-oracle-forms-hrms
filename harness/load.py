#!/usr/bin/env python3
"""Generate a disposable Oracle Free loader without editing estate files."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
WORK = ROOT / 'harness/work'
WORK.mkdir(exist_ok=True)
parts = ["""set define off echo off feedback off serveroutput on
whenever sqlerror exit failure rollback
alter system set fixed_date = '2024-01-31-12:00:00';
begin
  execute immediate 'drop user HRMS cascade';
exception when others then if sqlcode != -1918 then raise; end if;
end;
/
create user HRMS identified by fixture_only quota unlimited on users;
grant create session, create table, create sequence, create procedure, create view, create trigger to HRMS;
alter session set current_schema = HRMS;
alter session set nls_date_language = 'AMERICAN';
alter session set nls_numeric_characters = '.,';
"""]
for group in ['schema/tables/*.sql', 'schema/sequences/*.sql',
              'data/seed/*.sql', 'plsql/packages/*.pks', 'plsql/packages/*.pkb',
              'schema/views/*.sql', 'plsql/triggers/*.sql']:
    for path in sorted(ROOT.glob(group)):
        source = path.read_text()
        if path.name == '01_reference_data.sql':
            # Seed column aliases disagree with the checked-in table definitions.
            source = source.replace(', PHONE,', ', PHONE_NUMBER,')
            source = source.replace(', GRADE_LEVEL,', ', GRADE_CODE,')
            source = source.replace(', DESCRIPTION,', ', PARAM_DESCRIPTION,')
        source = re.sub(r';[ \t]+--[^\n]*', ';', source)
        if path.name == 'trg_employees.sql':
            # This unused update trigger targets an incompatible history schema.
            source = re.sub(r'CREATE OR REPLACE TRIGGER HRMS.TRG_EMP_BEFORE_UPDATE\b.*?^/\s*$',
                            'prompt Omitted TRG_EMP_BEFORE_UPDATE: history columns differ from schema.',
                            source, flags=re.S | re.M)
        # SQLERRM is a PL/SQL function, not callable inside a SQL statement.
        if path.name in {'PKG_PAYROLL.pkb', 'PKG_NOTIFICATION.pkb'}:
            source = source.replace('AS\n', 'AS\n    g_error_message VARCHAR2(4000);\n', 1)
            source = source.replace('WHEN OTHERS THEN', 'WHEN OTHERS THEN\n                    g_error_message := SQLERRM;')
            source = source.replace('SUBSTR(SQLERRM, 1, 4000)', 'SUBSTR(g_error_message, 1, 4000)')
        parts.append(f'prompt Loading {path.relative_to(ROOT)}\n' + source)
parts.append("""
begin dbms_utility.compile_schema('HRMS', false); end;
/
set linesize 240 pagesize 1000
select name, type, line, position, text from all_errors where owner = 'HRMS' order by name, sequence;
declare n number;
begin
  select count(*) into n from all_errors where owner='HRMS'
    and name != 'PKG_SECURITY';
  if n > 0 then raise_application_error(-20999, 'Loaded objects have compilation errors'); end if;
end;
/
prompt PKG_SECURITY requires DBMS_CRYPTO unavailable in this slim fixture; payroll and leave do not call it.
exit
""")
(WORK / 'load.sql').write_text('\n'.join(parts))
