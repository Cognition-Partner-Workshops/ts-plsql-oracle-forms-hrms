# HRMS Application Inventory

Catalog of every physically present artifact in the Oracle Forms 12c / PL/SQL HRMS estate, with layer classification. Companion reports: [`DATA_DICTIONARY.md`](DATA_DICTIONARY.md), [`DEPENDENCY_MAP.md`](DEPENDENCY_MAP.md), [`TECHNICAL_DEBT_REPORT.md`](TECHNICAL_DEBT_REPORT.md).

## 1. Layer model

| Layer | Code | Contents in repo |
|---|---|---|
| L1 Presentation - Forms | `PRES-FORM` | 6 Forms XML exports (`forms/xml-exports/`) |
| L1 Presentation - Menu | `PRES-MENU` | 1 menu source (`forms/menus/HRMS_MENU.mmb.sql`) + `MENU_MAIN` embedded in `HRMS_MENU.xml` |
| L2 Client logic - PLL | `CLIENT-LIB` | 2 PL/SQL libraries (`forms/libraries/`) |
| L3 Server business logic | `BIZ-PKG` | 7 domain packages: EMPLOYEE, PAYROLL, LEAVE, PERFORMANCE, REPORTING, INTEGRATION, NOTIFICATION |
| L3 Server shared services | `SVC-PKG` | 4 cross-cutting packages: COMMON, AUDIT, SECURITY, VALIDATION |
| L4 Data rules - triggers | `DB-TRG` | 6 row-level triggers (`plsql/triggers/`) |
| L5 Data access - views | `DB-VIEW` | 6 views (`schema/views/hrms_views.sql`) |
| L6 Data - tables/sequences | `DB-TABLE` / `DB-SEQ` | 30 tables, 29 sequences |
| L7 Data - seed | `DB-SEED` | 2 seed scripts (`data/seed/`) |

## 2. Physical vs. documented (README reconciliation)

| Item | README claim | In repo | Status |
|---|---|---|---|
| Forms modules | 18 forms incl. `HRMS_DEPARTMENT`, `HRMS_REPORTS`, `HRMS_LOV`, `HRMS_TOOLBAR` | 6 XML exports | **12 missing**. `HRMS_REPORTS` and `HRMS_ADMIN` are opened by `HRMS_MENU` but absent; `HRMS_TOOLBAR` canvas is referenced by `HRMS_COMMON_LIB` but absent. |
| PL/SQL packages | 12 incl. `PKG_DEPARTMENT` | 11 (spec+body each) | `PKG_DEPARTMENT` missing. |
| Tables | 42 | 30 | 12 not in source (also `RPT_*` reporting tables referenced in `PKG_REPORTING` comments, `USER_CREDENTIALS` concept in `PKG_SECURITY`). |
| Views | 15 | 6 | 9 missing. |
| Triggers | 200+ | 6 | Forms triggers are counted separately below (39 in XML); DB triggers far below claim. |
| `procedures/`, `functions/`, `types/`, `indexes/`, `constraints/`, `reports/`, `config/` dirs | Listed | Absent | No standalone procedures/functions/types, no secondary indexes, no Oracle Reports `.rdf`. |

Embedded Forms metadata also overstates contents: e.g. `HRMS_EMPLOYEE.xml` header claims 5 blocks / 8 LOVs but defines 2 blocks / 4 LOVs; `HRMS_LEAVE.xml` claims 5 blocks but defines 3; `HRMS_PAYROLL.xml` claims 4 blocks but defines 2.

## 3. Forms XML exports (`PRES-FORM`)

| Module | File (lines) | Domain | Libraries | Blocks (data source) | LOVs / record groups | Key triggers | Server calls |
|---|---|---|---|---|---|---|---|
| `HRMS_LOGIN` | `forms/xml-exports/HRMS_LOGIN.xml` (130) | Security | none | `LOGIN` (control) | - | WNFI, `BTN_LOGIN` WBP, `KEY-NEXT-ITEM` | `PKG_SECURITY.authenticate`; direct `SELECT` on `EMPLOYEES`; `OPEN_FORM('HRMS_MENU')` |
| `HRMS_MENU` | `forms/xml-exports/HRMS_MENU.xml` (175) | Navigation shell (MDI) | `HRMS_COMMON_LIB` | `MENU_CONTROL` (control) | - | WNFI, 6 button WBPs; `MENU_MAIN` with 9 items | `PKG_SECURITY.has_permission`, `PKG_SECURITY.logout`; opens EMPLOYEE, PAYROLL, LEAVE, PERFORMANCE, REPORTS*, ADMIN* (*missing) |
| `HRMS_EMPLOYEE` | `forms/xml-exports/HRMS_EMPLOYEE.xml` (538) | Employee | `HRMS_COMMON_LIB`, `HRMS_VALIDATION_LIB` | `EMPLOYEE` (DML on `EMPLOYEES`), `SALARY` (query `SALARY_RECORDS`) | `LOV_DEPARTMENTS`/`RG_DEPARTMENTS`, `LOV_JOB_TITLES`/`RG_JOB_TITLES`, `LOV_MANAGERS`/`RG_MANAGERS`, `LOV_LOCATIONS`/`RG_LOCATIONS` | WNFI, ON-ERROR, KEY-EXIT, PRE-INSERT, PRE-UPDATE, POST-QUERY, WHEN-VALIDATE-ITEM | `PKG_SECURITY.is_session_valid/has_permission`, `PKG_EMPLOYEE.generate_emp_number`, `PKG_VALIDATION.validate_email_format`, `SEQ_EMPLOYEE` |
| `HRMS_LEAVE` | `forms/xml-exports/HRMS_LEAVE.xml` (219) | Leave | `HRMS_COMMON_LIB` | `LEAVE_REQUEST` (query `LEAVE_REQUESTS`), `NEW_REQUEST` (control), `LEAVE_BALANCE` (query `LEAVE_BALANCES`) | `RG_LEAVE_TYPES` | WNFI, 2 WBPs, POST-QUERY | `PKG_SECURITY.is_session_valid`, `PKG_LEAVE.submit_leave_request`, `PKG_LEAVE.cancel_leave_request` |
| `HRMS_PAYROLL` | `forms/xml-exports/HRMS_PAYROLL.xml` (166) | Payroll | `HRMS_COMMON_LIB` | `PAY_PERIOD` (`PAY_PERIODS`), `PAYROLL_RUN` (`PAYROLL_RUNS`) | - | WNFI, 3 WBPs | `PKG_SECURITY.is_session_valid/has_permission`, `PKG_PAYROLL.create_payroll_run/calculate_payroll/approve_payroll` |
| `HRMS_PERFORMANCE` | `forms/xml-exports/HRMS_PERFORMANCE.xml` (131) | Performance | `HRMS_COMMON_LIB` | `REVIEW_CYCLE` (`REVIEW_CYCLES`), `PERFORMANCE_REVIEW` (update `PERFORMANCE_REVIEWS`), `PERFORMANCE_GOAL` (insert/update `PERFORMANCE_GOALS`) | - | WNFI, POST-QUERY | `PKG_SECURITY.is_session_valid` only - **no `PKG_PERFORMANCE` call** |

Forms-level trigger count (XML): LOGIN 3, MENU 7, EMPLOYEE 7, LEAVE 4, PAYROLL 4, PERFORMANCE 2 (plus menu item commands).

## 4. Menu module (`PRES-MENU`)

| Artifact | File | Contents |
|---|---|---|
| `HRMS_MENU.mmb` source | `forms/menus/HRMS_MENU.mmb.sql` (60, comment-only tree) | File / Edit / Query / Navigate / Modules / Admin / Help. Modules open `HRMS_EMPLOYEE`, `HRMS_PAYROLL`, `HRMS_LEAVE`, `HRMS_PERFORMANCE`, `HRMS_REPORTS`, `HRMS_ADMIN`. "Cancel Query" is mapped to `EXIT_FORM`. |
| `MENU_MAIN` | embedded in `HRMS_MENU.xml` | `MI_LOGOUT`, `MI_EMPLOYEES`, `MI_PAYROLL`, `MI_LEAVE`, `MI_PERFORMANCE`, `MI_REPORTS`, `MI_ADMIN`, `MI_CHANGE_PWD` (window `WIN_CHANGE_PWD` undefined), `MI_ABOUT` |

## 5. PL/SQL libraries (`CLIENT-LIB`)

| Library | File (lines) | Attached by | Program units | Server dependencies |
|---|---|---|---|---|
| `HRMS_COMMON_LIB` | `forms/libraries/HRMS_COMMON_LIB.pll.sql` (151) | MENU, EMPLOYEE, LEAVE, PAYROLL, PERFORMANCE | `handle_error`, `toolbar_save/clear/query/first/prev/next/last/insert/delete/exit`, `format_date`, `format_datetime`, `get_current_user`, `get_session_id`, `check_session`, `refresh_lov` (17 units) | `PKG_COMMON.log_error`, `PKG_SECURITY.is_session_valid` (header wrongly says "Dependencies: None") |
| `HRMS_VALIDATION_LIB` | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql` (135) | EMPLOYEE | `validate_email`, `validate_phone`, `validate_ssn`, `validate_date_not_future`, `validate_salary_range` (5 units) | `JOB_GRADES` (direct SELECT). None of its units is invoked by any checked-in form trigger. |

## 6. PL/SQL packages (`BIZ-PKG` / `SVC-PKG`)

All in `plsql/packages/` as `.pks` (spec) + `.pkb` (body).

| Package | Layer | Spec / body lines | Public interface (summary) | Tables touched | Declared deps (spec) | Actual calls (body) |
|---|---|---|---|---|---|---|
| `PKG_COMMON` | SVC | 121 / 283 | `log_error`, `log_info`, `get_param(_number/_date)`, `set_param`, `business_days_between`, `add_business_days`, `get_fiscal_year/quarter`, `format_phone/ssn_masked/currency/name`, `is_valid_email/phone/ssn` | `AUDIT_LOG`, `SYSTEM_PARAMETERS` | none | none |
| `PKG_AUDIT` | SVC | 32 / 72 | `log_action` (autonomous), `purge_old_records`, `get_change_history` | `AUDIT_LOG` | none | none |
| `PKG_SECURITY` | SVC | 63 / 237 | `hash_password`, `authenticate`, `logout`, `is_session_valid`, `has_permission`, `encrypt_ssn`, `decrypt_ssn`, `change_password` | `EMPLOYEES`, `USER_SESSIONS`, `JOB_TITLES` | COMMON, AUDIT | AUDIT, **EMPLOYEE** (`set_session_context`) |
| `PKG_VALIDATION` | SVC | 47 / 125 | `validate_date_range`, `validate_salary_for_grade`, `validate_email_format`, `validate_phone_format`, `validate_emp_number_format`, `is_future_date`, `is_business_day`, `validate_required_fields` | `JOB_GRADES`, `HOLIDAYS`, `EMPLOYEES` | COMMON | COMMON. Called only by `HRMS_EMPLOYEE` (email). |
| `PKG_EMPLOYEE` | BIZ | 192 / 966 | `generate_emp_number`, `create_employee`, `update_employee`, `search_employees`, `transfer_employee`, `promote_employee`, `terminate_employee`, `rehire_employee`, `get_org_chart`, `get_headcount_by_dept`, `validate_manager`, `is_active`, `set_session_context`; package globals `g_current_user/emp_id/dept_id`, `g_debug_mode` | `EMPLOYEES`, `EMPLOYEE_HISTORY`, `SALARY_RECORDS`, `JOB_TITLES`, `JOB_GRADES`, `DEPARTMENTS`, `LEAVE_REQUESTS`, `EMPLOYEE_PAY_ELEMENTS` | COMMON, AUDIT, NOTIFICATION, PAYROLL | COMMON, AUDIT, NOTIFICATION, PAYROLL (`create_salary_record`) |
| `PKG_PAYROLL` | BIZ | 164 / 897 | `create_salary_record`, `get_current_salary`, `get_salary_as_of`, `create_pay_periods`, `create_payroll_run`, `calculate_payroll`, `calculate_employee_pay`, `calculate_federal_tax`, `calculate_state_tax`, `get_ytd_earnings`, `approve_payroll`, `reverse_payroll`, `close_pay_period`, `get_payslip`, `generate_pay_register` | `SALARY_RECORDS`, `PAY_PERIODS`, `PAYROLL_RUNS`, `PAYROLL_DETAILS`, `EMPLOYEES`, `EMPLOYEE_TAX_INFO`, `EMPLOYEE_PAY_ELEMENTS`, `PAY_ELEMENTS`, `DEPARTMENTS`; `UTL_FILE` dir `PAYROLL_OUTPUT` | EMPLOYEE, COMMON, AUDIT, NOTIFICATION | COMMON, AUDIT only |
| `PKG_LEAVE` | BIZ | 128 / 673 | `submit/approve/reject/cancel_leave_request`, `get_leave_balance`, `adjust_leave_balance`, `initialize_balances`, `run_monthly_accrual`, `process_carryover`, `expire_carryover`, `get_pending_requests`, `get_team_calendar`, `calculate_business_days`, `check_leave_overlap` | `LEAVE_REQUESTS`, `LEAVE_BALANCES`, `LEAVE_TYPES`, `LEAVE_ACCRUAL_LOG`, `HOLIDAYS`, `EMPLOYEES` | EMPLOYEE, COMMON, AUDIT, NOTIFICATION | AUDIT, NOTIFICATION |
| `PKG_PERFORMANCE` | BIZ | 97 / 320 | `create/open/close_review_cycle`, `create_review`, `submit_self_assessment`, `submit_manager_review`, `acknowledge_review`, `add_goal`, `update_goal_progress`, `get_team_reviews`, `get_rating_distribution`, `generate_reviews_for_cycle` | `REVIEW_CYCLES`, `PERFORMANCE_REVIEWS`, `PERFORMANCE_GOALS`, `EMPLOYEES`, `JOB_TITLES`, `DEPARTMENTS` | EMPLOYEE, COMMON, AUDIT, NOTIFICATION | AUDIT, NOTIFICATION |
| `PKG_NOTIFICATION` | BIZ (infra) | 42 / 177 | `send_notification` (autonomous), `process_queue` (`UTL_SMTP`), `retry_failed`, `cancel_notification` | `NOTIFICATION_QUEUE`, `EMPLOYEES` | - | COMMON |
| `PKG_REPORTING` | BIZ | 63 / 207 | `headcount_report`, `compensation_summary`, `turnover_report`, `new_hires_report`, `leave_utilization_report`, `payroll_summary_report`, `eeo_compliance_report`, `refresh_reporting_tables` (stub) | `EMPLOYEES`, `DEPARTMENTS`, `LOCATIONS`, `JOB_TITLES`, `JOB_GRADES`, `SALARY_RECORDS`, `LEAVE_BALANCES`, `LEAVE_TYPES`, `PAYROLL_DETAILS`, `PAYROLL_RUNS` | EMPLOYEE, PAYROLL, COMMON | COMMON |
| `PKG_INTEGRATION` | BIZ (integration) | 50 / 213 | `generate_gl_journal`, `export_benefits_feed`, `import_time_attendance` (stub), `sync_org_structure` (stub), `get_integration_status` | `PAYROLL_DETAILS`, `PAYROLL_RUNS`, `PAY_PERIODS`, `EMPLOYEES`, `DEPARTMENTS`, `PAY_ELEMENTS`, `EMPLOYEE_DEPENDENTS`; `UTL_FILE` dirs `GL_FEED_OUT`, `BENEFITS_FEED_OUT`, `TIME_ATTENDANCE_IN` | COMMON, PAYROLL, EMPLOYEE | COMMON |

Callers by channel: Forms call `PKG_SECURITY`, `PKG_EMPLOYEE`, `PKG_VALIDATION`, `PKG_LEAVE`, `PKG_PAYROLL`, `PKG_COMMON` (via PLL). `PKG_PERFORMANCE`, `PKG_REPORTING`, `PKG_INTEGRATION`, `PKG_NOTIFICATION.process_queue`, `PKG_LEAVE` batch procs and `PKG_AUDIT.purge_old_records` have **no checked-in caller** (documented as `DBMS_SCHEDULER` jobs / missing forms; no job definitions are in source).

## 7. Database triggers (`DB-TRG`)

| Trigger | File:line | Timing / event / table | Purpose | Calls / writes |
|---|---|---|---|---|
| `TRG_SALARY_AUDIT` | `plsql/triggers/trg_audit.sql:10` | AFTER INSERT/UPDATE/DELETE ON `SALARY_RECORDS`, row | Salary audit | `PKG_AUDIT.log_action` -> `AUDIT_LOG` |
| `TRG_LEAVE_REQUEST_AUDIT` | `plsql/triggers/trg_audit.sql:47` | AFTER UPDATE OF STATUS ON `LEAVE_REQUESTS`, row | Leave status audit | `PKG_AUDIT.log_action` (`STATUS_CHANGE` - rejected by CHECK) |
| `TRG_DEPARTMENT_AUDIT` | `plsql/triggers/trg_audit.sql:66` | AFTER INSERT/UPDATE/DELETE ON `DEPARTMENTS`, row | Department audit | `PKG_AUDIT.log_action` |
| `TRG_EMP_BEFORE_INSERT` | `plsql/triggers/trg_employees.sql:12` | BEFORE INSERT ON `EMPLOYEES`, row | Defaults, email-uniqueness check (-20501/-20502) | SELECT `EMPLOYEES` |
| `TRG_EMP_BEFORE_UPDATE` | `plsql/triggers/trg_employees.sql:62` | BEFORE UPDATE ON `EMPLOYEES`, row | Blocks TERMINATED->ACTIVE; writes history on dept/job/status change | INSERT `EMPLOYEE_HISTORY` (column mismatch) |
| `TRG_EMP_INSTEAD_OF_DELETE` | `plsql/triggers/trg_employees.sql:120` | BEFORE DELETE ON `EMPLOYEES` (comment says AFTER; name says INSTEAD OF) | Prevents hard delete (-20504) | - |

## 8. Views (`DB-VIEW`)

| View | File:line | Domain | Base tables |
|---|---|---|---|
| `VW_ACTIVE_EMPLOYEES` | `schema/views/hrms_views.sql:10` | Employee | EMPLOYEES, DEPARTMENTS, JOB_TITLES, JOB_GRADES, LOCATIONS, SALARY_RECORDS |
| `VW_ORG_HIERARCHY` | `schema/views/hrms_views.sql:47` | Organization | EMPLOYEES |
| `VW_EMPLOYEE_COMPENSATION` | `schema/views/hrms_views.sql:63` | Compensation | EMPLOYEES, JOB_TITLES, JOB_GRADES, SALARY_RECORDS, DEPARTMENTS |
| `VW_LEAVE_SUMMARY` | `schema/views/hrms_views.sql:86` | Leave | LEAVE_BALANCES, LEAVE_TYPES, EMPLOYEES |
| `VW_PAYROLL_LATEST` | `schema/views/hrms_views.sql:109` | Payroll | PAYROLL_DETAILS, PAYROLL_RUNS, PAY_PERIODS, EMPLOYEES |
| `VW_PENDING_APPROVALS` | `schema/views/hrms_views.sql:135` | Workflow | LEAVE_REQUESTS, PERFORMANCE_REVIEWS, EMPLOYEES, LEAVE_TYPES |

No package, form or library in the repo selects from any view; they serve external/reporting consumers only.

## 9. Tables (`DB-TABLE`)

Full column-level detail is in [`DATA_DICTIONARY.md`](DATA_DICTIONARY.md).

| # | Table | File:line | Domain | Seeded |
|---|---|---|---|---|
| 1 | `DEPARTMENTS` | `schema/tables/01_core_tables.sql:10` | Organization | 10 rows |
| 2 | `LOCATIONS` | `01_core_tables.sql:35` | Organization | 3 |
| 3 | `JOB_GRADES` | `01_core_tables.sql:57` | Organization | 10 |
| 4 | `JOB_TITLES` | `01_core_tables.sql:77` | Organization | 26 |
| 5 | `EMPLOYEES` | `01_core_tables.sql:98` | Employee | 24 |
| 6 | `EMPLOYEE_HISTORY` | `01_core_tables.sql:152` | Employee | - |
| 7 | `EMPLOYEE_DEPENDENTS` | `01_core_tables.sql:182` | Employee | - |
| 8 | `EMERGENCY_CONTACTS` | `01_core_tables.sql:204` | Employee | - |
| 9 | `SALARY_RECORDS` | `schema/tables/02_payroll_tables.sql:10` | Payroll | 23 |
| 10 | `PAY_ELEMENTS` | `02_payroll_tables.sql:37` | Payroll | 11 |
| 11 | `EMPLOYEE_PAY_ELEMENTS` | `02_payroll_tables.sql:64` | Payroll | - |
| 12 | `PAY_PERIODS` | `02_payroll_tables.sql:86` | Payroll | - |
| 13 | `PAYROLL_RUNS` | `02_payroll_tables.sql:107` | Payroll | - |
| 14 | `PAYROLL_DETAILS` | `02_payroll_tables.sql:136` | Payroll | - |
| 15 | `TAX_BRACKETS` | `02_payroll_tables.sql:159` | Payroll (unused) | - |
| 16 | `EMPLOYEE_TAX_INFO` | `02_payroll_tables.sql:178` | Payroll | - |
| 17 | `EMPLOYEE_BANK_ACCOUNTS` | `02_payroll_tables.sql:203` | Payroll | - |
| 18 | `LEAVE_TYPES` | `schema/tables/03_leave_tables.sql:10` | Leave | 6 |
| 19 | `LEAVE_BALANCES` | `03_leave_tables.sql:37` | Leave | - |
| 20 | `LEAVE_REQUESTS` | `03_leave_tables.sql:63` | Leave | - |
| 21 | `LEAVE_ACCRUAL_LOG` | `03_leave_tables.sql:96` | Leave | - |
| 22 | `HOLIDAYS` | `03_leave_tables.sql:114` | Leave | 10 |
| 23 | `REVIEW_CYCLES` | `schema/tables/04_performance_tables.sql:10` | Performance | - |
| 24 | `PERFORMANCE_REVIEWS` | `04_performance_tables.sql:31` | Performance | - |
| 25 | `PERFORMANCE_GOALS` | `04_performance_tables.sql:64` | Performance | - |
| 26 | `AUDIT_LOG` | `04_performance_tables.sql:92` | Platform | - |
| 27 | `SYSTEM_PARAMETERS` | `04_performance_tables.sql:110` | Platform | 10 |
| 28 | `NOTIFICATION_QUEUE` | `04_performance_tables.sql:129` | Platform | - |
| 29 | `USER_SESSIONS` | `04_performance_tables.sql:153` | Platform | - |
| 30 | `LOOKUP_VALUES` | `04_performance_tables.sql:170` | Platform | - |

## 10. Sequences (`DB-SEQ`) and seed data (`DB-SEED`)

- `schema/sequences/hrms_sequences.sql`: 29 sequences (listed in `DATA_DICTIONARY.md` section 8). `SEQ_EMP_NUMBER` is unused.
- `data/seed/01_reference_data.sql` (203 lines): DEPARTMENTS 10, LOCATIONS 3, JOB_GRADES 10, JOB_TITLES 26, LEAVE_TYPES 6, PAY_ELEMENTS 11, HOLIDAYS 10, SYSTEM_PARAMETERS 10.
- `data/seed/02_employee_data.sql` (172 lines): EMPLOYEES 24 (header says 25), SALARY_RECORDS 23.

## 11. External runtime dependencies

| Dependency | Used by | Purpose |
|---|---|---|
| `DBMS_CRYPTO` | `PKG_SECURITY` | MD5 hashing, AES SSN encryption |
| `UTL_FILE` (dirs `PAYROLL_OUTPUT`, `GL_FEED_OUT`, `BENEFITS_FEED_OUT`, `TIME_ATTENDANCE_IN`) | `PKG_PAYROLL`, `PKG_INTEGRATION` | Flat-file exports/imports |
| `UTL_SMTP` / `UTL_TCP` | `PKG_NOTIFICATION` | Email delivery to `smtp.internal.company.com:25` |
| `DBMS_SCHEDULER` (documented, no job DDL in repo) | payroll batch, monthly accrual, notification queue (5 min), GL/benefits feeds | Batch orchestration |
| `DBMS_OUTPUT` | `PKG_EMPLOYEE`, `PKG_LEAVE`, `PKG_PERFORMANCE` | Debug/progress output |
| Oracle Forms built-ins (`OPEN_FORM`, `POPULATE_GROUP`, `:GLOBAL.*`) | All forms/PLLs | Client runtime |
