# HRMS Technical Debt Report

Static review of the Oracle Forms 12c / PL/SQL HRMS estate covering security vulnerabilities, race conditions, performance anti-patterns and validation drift. All evidence is `file:line` in this repository. Nothing was executed against a database; runtime claims are labelled **Observed** (read directly in code), **Documented** (stated in a source comment/README) or **Inferred** (follows from the code but not proven at runtime).

Related reports: [`APPLICATION_INVENTORY.md`](APPLICATION_INVENTORY.md), [`DATA_DICTIONARY.md`](DATA_DICTIONARY.md), [`DEPENDENCY_MAP.md`](DEPENDENCY_MAP.md).

## Severity scale

| Severity | Meaning |
|---|---|
| **Critical** | Exploitable security hole or a defect that corrupts payroll/financial data or breaks a core workflow |
| **High** | Significant security exposure, data-integrity risk under normal concurrency, or compliance failure |
| **Medium** | Incorrect results in edge cases, scalability limits, maintainability risk with business impact |
| **Low** | Hygiene, dead code, misleading documentation |

## Summary

| ID | Category | Severity | Title | Status |
|---|---|---|---|---|
| SEC-01 | Security | Critical | Authentication never verifies the password | Observed |
| SEC-02 | Security | Critical | SQL injection in `PKG_EMPLOYEE.search_employees` | Observed |
| SEC-03 | Security | Critical | Hard-coded AES key for SSN / bank-account encryption | Observed |
| SEC-04 | Security | High | Unsalted MD5 password hashing | Observed |
| SEC-05 | Security | High | No lockout, MFA or rate limiting; cleartext password transport | Documented |
| SEC-06 | Security | High | Authorization enforced only in the UI; packages unprotected | Observed |
| SEC-07 | Security | High | `HRMS_PERFORMANCE` / `HRMS_LEAVE` expose all employees' reviews and balances | Observed |
| SEC-08 | Security | High | `change_password` is a no-op that audits success | Observed |
| SEC-09 | Security | High | Forms derive session id from `GET_APPLICATION_PROPERTY(USERNAME)` | Observed |
| SEC-10 | Security | Medium | Grade-number based permissions (`grade >= 8` = admin) | Observed |
| SEC-11 | Security | Medium | Plaintext PII in flat-file exports; FTP credentials in cleartext | Observed / Documented |
| SEC-12 | Security | Medium | Unauthenticated SMTP on port 25 without TLS, hard-coded host | Observed |
| SEC-13 | Security | Medium | Raw `SQLERRM` shown to end users | Observed |
| SEC-14 | Security | Medium | Absolute (not idle) session timeout; logout without ownership check | Observed |
| RACE-01 | Race condition | Critical | `MAX()+1` employee-number generation | Observed |
| RACE-02 | Race condition | Critical | Payroll partial commits leave half-calculated runs; reruns duplicate lines | Observed |
| RACE-03 | Race condition | High | Leave balance check-then-act without locking (overdraft / overlaps) | Observed |
| RACE-04 | Race condition | High | Monthly accrual not idempotent, commits every 100 rows | Observed |
| RACE-05 | Race condition | High | Notification queue has no claim/lock state - duplicate sends | Observed |
| RACE-06 | Race condition | Medium | Email uniqueness enforced only in a trigger SELECT | Observed |
| RACE-07 | Race condition | Medium | Payroll run state transitions not locked server-side | Observed |
| RACE-08 | Transaction | Medium | Autonomous transactions in audit/notification/history break atomicity | Observed |
| PERF-01 | Performance | High | Row-by-row payroll calculation with interleaved commits | Observed |
| PERF-02 | Performance | High | Nested cursor loops in leave accrual | Observed |
| PERF-03 | Performance | Medium | `CONNECT BY` org hierarchy degrades above ~500 employees | Documented |
| PERF-04 | Performance | Medium | N+1 `POST-QUERY` lookups in Forms | Observed |
| PERF-05 | Performance | Medium | Per-day holiday queries in business-day calculation | Observed |
| PERF-06 | Performance | Medium | Dynamic SQL without binds (hard parse per search) | Observed |
| PERF-07 | Performance | Medium | New SMTP connection per notification | Observed |
| PERF-08 | Performance | Low | Unbatched audit purge; `NOCACHE` sequences; no secondary indexes | Observed |
| PERF-09 | Performance | Medium | Synchronous payroll calculation inside the Forms session | Observed |
| DRIFT-01 | Data/logic drift | Critical | `TRG_EMP_BEFORE_UPDATE` writes non-existent `EMPLOYEE_HISTORY` columns | Observed |
| DRIFT-02 | Data/logic drift | Critical | Leave auto-approval inserts `APPROVED` then calls approve (requires `PENDING`) | Observed |
| DRIFT-03 | Data/logic drift | High | Payroll error handler inserts `ELEMENT_ID = 0` (FK violation) | Observed |
| DRIFT-04 | Data/logic drift | High | Hard-coded 2024 tax brackets; `TAX_BRACKETS` unused; HoH taxed at 0 | Observed |
| DRIFT-05 | Data/logic drift | High | Forms do direct DML, bypassing package business rules | Observed |
| DRIFT-06 | Data/logic drift | High | Leave audit writes `STATUS_CHANGE`, rejected by `AUDIT_LOG` CHECK; failure swallowed | Observed |
| DRIFT-07 | Validation drift | Medium | Four divergent validation layers (Forms, PLL, `PKG_COMMON`/`PKG_VALIDATION`, triggers/DDL) | Observed |
| DRIFT-08 | Data/logic drift | Medium | Leave `AVAILABLE` computed three different ways | Observed |
| DRIFT-09 | Data/logic drift | Medium | Payslip YTD placeholders and inconsistent deduction totals | Observed |
| DRIFT-10 | Data/logic drift | Medium | Rehire blocked by trigger; termination leaves `PENDING` leave balance | Observed |
| DRIFT-11 | Data/logic drift | Medium | Business-day functions disagree on holidays | Observed |
| DRIFT-12 | Data/logic drift | Medium | Configuration duplicated in code instead of `SYSTEM_PARAMETERS` | Observed |
| DRIFT-13 | Data/logic drift | Low | Missing uniqueness constraints and FKs | Observed |
| DRIFT-14 | Data/logic drift | Low | Stubs, dead code and misleading documentation | Observed |

Totals: **7 Critical, 15 High, 20 Medium, 3 Low** (45 findings).

---

## 1. Security vulnerabilities

### SEC-01 - Authentication never verifies the password (Critical)
- **Evidence:** `plsql/packages/PKG_SECURITY.pkb:30-84` (`authenticate`). The function looks up an active employee by `EMAIL`, raises `-20301` only if the user is not found (L50), then creates a `USER_SESSIONS` row and returns a session id. `p_password` is never compared with any stored hash, and there is no credential table in the schema (`PKG_SECURITY.pkb:233` audits a non-existent `USER_CREDENTIALS`).
- **Impact:** Anyone who knows or guesses an employee email can log in as that employee, including payroll and HR administrators.
- **Remediation:** Introduce a credential store (or delegate to LDAP/OAM/SSO), verify a salted slow hash in constant time, and fail closed.

### SEC-02 - SQL injection in `search_employees` (Critical)
- **Evidence:** `plsql/packages/PKG_EMPLOYEE.pkb:442` (comment acknowledges), `457-498`: `p_last_name`, `p_first_name`, `p_status`, `p_location_code` are concatenated inside quotes and `p_dept_id` without quotes, then `OPEN p_cursor FOR v_sql` (L498). Package runs with definer rights (`HRMS` owner).
- **Impact:** Arbitrary SQL in the HRMS schema context (read SSN ciphertext, salaries, `USER_SESSIONS`), plus hard parsing for every distinct search (PERF-06).
- **Remediation:** Bind variables (`OPEN ... FOR v_sql USING ...` with a fixed predicate set or `DBMS_SQL`), or static SQL with `(:p IS NULL OR col LIKE :p)`; `DBMS_ASSERT` for any identifier.

### SEC-03 - Hard-coded encryption key (Critical)
- **Evidence:** `plsql/packages/PKG_SECURITY.pkb:7` declares `c_encryption_key RAW(32) := UTL_RAW.CAST_TO_RAW('<redacted>')`, used by `encrypt_ssn` (L179-190) and `decrypt_ssn` (L192-206). The literal is committed to source control (value intentionally not reproduced here). The literal is 30 bytes, shorter than the 32 bytes required for AES-256 (*Inferred:* `DBMS_CRYPTO` will reject or mis-key it).
- **Impact:** Anyone with repository access (or `ALL_SOURCE` read on the schema) can decrypt every `SSN_ENCRYPTED` and, by the same pattern, `ACCOUNT_NUMBER_ENC`. Key cannot be rotated without a code release.
- **Remediation:** Treat the key as compromised and rotate; move to TDE column encryption or a key held in Oracle Wallet / external KMS; restrict `decrypt_ssn` to a dedicated role; re-encrypt existing data.

### SEC-04 - Unsalted MD5 password hashing (High)
- **Evidence:** `PKG_SECURITY.pkb:14-24` - `DBMS_CRYPTO.HASH(..., DBMS_CRYPTO.HASH_MD5)` with no salt.
- **Impact:** Rainbow-table reversible; identical passwords produce identical hashes. Currently moot only because SEC-01 skips verification entirely.
- **Remediation:** PBKDF2/bcrypt/argon2 equivalent (or external IdP); per-user salt; migrate on next login.

### SEC-05 - No lockout, MFA, rate limiting; cleartext transport (High)
- **Evidence:** `forms/xml-exports/HRMS_LOGIN.xml:11-13` (documented limitations); no failed-attempt counter in `PKG_SECURITY.authenticate` or `USER_SESSIONS`; README "known issues". `SYSTEM_PARAMETERS.PASSWORD_MIN_LENGTH` is seeded but never read.
- **Impact:** Unlimited online guessing (combined with SEC-01, guessing is not even needed).
- **Remediation:** Front Forms with SSO (OAM/SAML) providing MFA and lockout; enforce TLS on WebLogic; implement attempt counters if local auth is kept.

### SEC-06 - Authorization only in the UI (High)
- **Evidence:** Permissions are checked by Forms to hide/disable items (`HRMS_MENU.xml:26-34,75,115`, `HRMS_EMPLOYEE.xml:45`, `HRMS_PAYROLL.xml:34,137`). No package procedure calls `PKG_SECURITY.has_permission` (e.g. `PKG_PAYROLL.approve_payroll`, `PKG_EMPLOYEE.terminate_employee`, `PKG_SECURITY.decrypt_ssn`). Packages accept `p_user` as a free-text parameter for audit attribution.
- **Impact:** Any DB session able to execute the packages (SQL*Plus, report tools, a crafted form) can approve payroll, terminate employees or decrypt SSNs and attribute it to any user.
- **Remediation:** Enforce `has_permission` (or VPD/RLS policies) inside every state-changing package entry point; derive the acting user from a trusted context (`SYS_CONTEXT`), never from a parameter.

### SEC-07 - Unfiltered access to reviews and balances (High)
- **Evidence:** `HRMS_PERFORMANCE.xml:58` (`PERFORMANCE_REVIEWS` block, update allowed) and `:96` (`PERFORMANCE_GOALS`, insert/update) have no `DEFAULT_WHERE` by employee/manager and no permission check. `HRMS_LEAVE.xml:175-176` (`LEAVE_BALANCE` block) has no employee filter.
- **Impact:** Any logged-in employee can read and change other employees' performance ratings and view all leave balances.
- **Remediation:** Row filtering by `:GLOBAL.current_emp_id`/manager chain, enforced server-side via VPD or package-based views; route writes through `PKG_PERFORMANCE`.

### SEC-08 - `change_password` is a no-op (High)
- **Evidence:** `PKG_SECURITY.pkb:211-237` - does not verify the old password, persists nothing, then logs `UPDATE` on `USER_CREDENTIALS` (L233). Menu item `MI_CHANGE_PWD` points to an undefined window (`HRMS_MENU.xml`).
- **Impact:** Users believe passwords are changed; audit trail records a change that did not happen.
- **Remediation:** Implement with credential store (see SEC-01) or remove in favor of IdP.

### SEC-09 - Session id derived from DB username (High)
- **Evidence:** `HRMS_EMPLOYEE.xml:34`, `HRMS_PAYROLL.xml:27`, `HRMS_LEAVE.xml:26`, `HRMS_PERFORMANCE.xml:24` pass `TO_NUMBER(GET_APPLICATION_PROPERTY(USERNAME))` to `PKG_SECURITY.is_session_valid`, while `HRMS_LOGIN` stores the real id in `:GLOBAL.session_id` and `HRMS_COMMON_LIB.check_session` uses that global.
- **Impact:** *Inferred:* with a non-numeric DB account `TO_NUMBER` raises `ORA-01722` and every module fails to open; if a numeric DB username is used, all users share one "session id", so session validation is meaningless.
- **Remediation:** Use `HRMS_COMMON_LIB.check_session` / `:GLOBAL.session_id` consistently.

### SEC-10 - Grade-number based permissions (Medium)
- **Evidence:** `PKG_SECURITY.pkb:131-174` - `IF v_grade_id >= 8` grants everything (L153); `VIEW` granted at `>= 5` (L157); otherwise module/action defaults. `GRADE_ID` is a surrogate key, not a rank.
- **Impact:** Re-keying or adding grades silently changes privileges; no role model, no segregation of duties (the payroll preparer can approve: `PKG_PAYROLL.approve_payroll` does not compare approver to creator).
- **Remediation:** Explicit role/permission tables; SoD rule for payroll approval.

### SEC-11 - Plaintext PII exports and cleartext integration credentials (Medium)
- **Evidence:** `PKG_INTEGRATION.pkb:90-150` writes DOB, gender, marital status and dependents to a fixed-width file via `UTL_FILE` (`BENEFITS_FEED_OUT`); `PKG_PAYROLL.generate_pay_register` (`PKG_PAYROLL.pkb:846-870`) writes salaries/taxes to CSV; `PKG_INTEGRATION.pks:12` documents FTP credentials stored in cleartext in `SYSTEM_PARAMETERS`.
- **Remediation:** Encrypt files at rest (PGP), SFTP with wallet-held credentials, restrict directory object grants.

### SEC-12 - Insecure SMTP (Medium)
- **Evidence:** `PKG_NOTIFICATION.pkb:7-8` hard-codes `smtp.internal.company.com:25`; `UTL_SMTP.OPEN_CONNECTION` (L90) without `STARTTLS`/auth; recipients and subjects come from table data without header sanitization.
- **Remediation:** TLS + authenticated relay via wallet; read host from `SYSTEM_PARAMETERS`; strip CR/LF from header fields.

### SEC-13 - Raw database errors shown to users (Medium)
- **Evidence:** `forms/libraries/HRMS_COMMON_LIB.pll.sql:21-33` (`MESSAGE(... SQLERRM)`), displayed twice.
- **Impact:** Leaks table/constraint names and internals; aids SEC-02 exploitation.
- **Remediation:** Show a correlation id; log detail server-side via `PKG_COMMON.log_error`.

### SEC-14 - Session handling weaknesses (Medium)
- **Evidence:** `PKG_SECURITY.pkb:8` fixed 30-min timeout from login (`is_session_valid` L98-133) - not idle-based and ignores `SYSTEM_PARAMETERS.SESSION_TIMEOUT_MIN`; the function performs DML (cannot be used in SQL, side effects on read); `logout` (L85-96) ends any session id passed without verifying ownership.
- **Remediation:** Sliding idle timeout from parameter; separate validate (pure) from touch (procedure); bind session to user/context.

## 2. Race conditions and transaction defects

### RACE-01 - `MAX()+1` employee numbers (Critical)
- **Evidence:** `plsql/packages/PKG_EMPLOYEE.pkb:37` (comment), `43-48`:
  ```sql
  SELECT NVL(MAX(TO_NUMBER(SUBSTR(EMP_NUMBER, 5))), 0) + 1 INTO v_max_num
  FROM EMPLOYEES WHERE EMP_NUMBER LIKE c_emp_number_prefix || '-%';
  ```
  Called from `create_employee` and from `HRMS_EMPLOYEE` `PRE-INSERT` (`HRMS_EMPLOYEE.xml:323`). `SEQ_EMP_NUMBER` exists (`schema/sequences/hrms_sequences.sql:21`) but is unused.
- **Impact:** Two concurrent hires get the same number; the second fails on `UK_EMP_NUMBER` at commit (lost data entry in Forms). Full scan of `EMPLOYEES` per hire.
- **Remediation:** `'EMP-' || LPAD(SEQ_EMP_NUMBER.NEXTVAL, 6, '0')`.

### RACE-02 - Payroll partial commits and non-idempotent reruns (Critical)
- **Evidence:** `PKG_PAYROLL.pkb:295` (comment), loop over active employees with `COMMIT` every 50 (L323-325) and final `COMMIT` (L346); no delete of existing `PAYROLL_DETAILS` before recalculation and no unique key on (`RUN_ID`,`EMP_ID`,`ELEMENT_ID`). Run status set to `CALCULATING` is not reset on failure.
- **Impact:** A failure at employee 120 leaves 100 employees committed, run stuck in `CALCULATING`; clicking "Calculate" again (`HRMS_PAYROLL.xml`) duplicates lines -> double pay in register/GL feed (`PKG_INTEGRATION.generate_gl_journal` sums details without a dedup guard).
- **Remediation:** Single transaction per run (or per-employee savepoints with a restartable "delete-then-insert for unprocessed employees" design); lock the run row `FOR UPDATE`, require `PENDING`/`ERROR` state; add unique key.

### RACE-03 - Leave check-then-act (High)
- **Evidence:** `PKG_LEAVE.pkb:67-207` - `check_leave_overlap` (L45-62) and the balance check (L147-154) run before `INSERT` and the `PENDING` update (L176-182) without `SELECT ... FOR UPDATE` on `LEAVE_BALANCES`. Balance update can match zero rows (no balance row for the year) without raising.
- **Impact:** Two simultaneous submissions both pass, overdrawing balance or creating overlapping requests; requests can be created with no balance row at all.
- **Remediation:** Lock the balance row first (`FOR UPDATE`), re-check, then insert; raise if `SQL%ROWCOUNT = 0`.

### RACE-04 - Non-idempotent monthly accrual (High)
- **Evidence:** `PKG_LEAVE.pkb:459-552` - accrues for all active employees x leave types, `COMMIT` every 100 (L543); `LEAVE_ACCRUAL_LOG` has no uniqueness per employee/type/period and `RUN_ID` is never set.
- **Impact:** A rerun after partial failure (or double scheduler fire) accrues twice for already-processed employees.
- **Remediation:** Record an accrual period key with a unique constraint; skip already-accrued rows; set-based `MERGE`.

### RACE-05 - Notification double-send (High)
- **Evidence:** `PKG_NOTIFICATION.pkb:70-137` - cursor `WHERE STATUS = 'PENDING'` (L82) with no `FOR UPDATE SKIP LOCKED` and no `PROCESSING` state (`CHK` allows only PENDING/SENT/FAILED/CANCELLED).
- **Impact:** Overlapping 5-minute scheduler runs send the same email twice.
- **Remediation:** `FOR UPDATE SKIP LOCKED` or claim rows via `UPDATE ... SET STATUS='PROCESSING' ... RETURNING`; consider Oracle AQ.

### RACE-06 - Email uniqueness in trigger only (Medium)
- **Evidence:** `plsql/triggers/trg_employees.sql:12-55` counts `EMPLOYEES` with the same email (raises -20501/-20502); no unique constraint on `EMPLOYEES.EMAIL` (`schema/tables/01_core_tables.sql:98-145`).
- **Impact:** Concurrent inserts both pass; email is the login identifier, so `HRMS_LOGIN` (`ROWNUM = 1`, `HRMS_LOGIN.xml:90`) and `authenticate` may resolve to different employees. Same-table SELECT in a row trigger is also mutating-table-prone for multi-row inserts.
- **Remediation:** Unique (function-based on `UPPER(EMAIL)`) index; drop the trigger check.

### RACE-07 - Payroll state transitions not locked (Medium)
- **Evidence:** `HRMS_PAYROLL.xml:137+` checks run status client-side before calling `approve_payroll`/`calculate_payroll`; `PKG_PAYROLL.approve_payroll` correctly locks the run (`FOR UPDATE`) and requires `CALCULATED`, but `calculate_payroll` (`PKG_PAYROLL.pkb:285-287`) neither locks nor checks the current status before setting `CALCULATING`, and `create_payroll_run` performs no period-status or duplicate-run check (multiple `REGULAR` runs per period, non-`OPEN` periods).
- **Impact:** Two users (or a user and a scheduler job) can calculate the same run concurrently or recalculate an `APPROVED` run; duplicate regular runs can be paid twice.
- **Remediation:** Server-side state machine with row lock and allowed-transition table; unique `REGULAR` run per period.

### RACE-08 - Autonomous transactions break atomicity (Medium)
- **Evidence:** `PKG_AUDIT.pkb:14,26`, `PKG_COMMON.pkb:16,28,46,57`, `PKG_NOTIFICATION.pkb:27,56`, `PKG_EMPLOYEE.pkb:155,170` (history logging).
- **Impact:** Audit/history rows and notifications are committed even when the business transaction rolls back (e.g. "employee terminated" email sent for a termination that failed). `PKG_AUDIT.log_action` swallows its own errors, so audit loss is silent (see DRIFT-06).
- **Remediation:** Keep history and notifications in the caller's transaction; reserve autonomous transactions for error logging only.

## 3. Performance anti-patterns

### PERF-01 - Row-by-row payroll (High)
- **Evidence:** `PKG_PAYROLL.pkb:295` (`-- BUG: Cursor loop - should use BULK COLLECT + FORALL`), `calculate_employee_pay` issues several single-row SELECTs and 5-10 single-row INSERTs per employee (L406-537), plus `NOCACHE` sequence calls.
- **Impact:** Linear context-switch cost; README cites multi-hour runs. Combined with RACE-02 a long run widens the failure window.
- **Remediation:** Set-based `INSERT ... SELECT` per element type, or `BULK COLLECT`/`FORALL` in batches inside one transaction; cache sequences.

### PERF-02 - Nested cursor accrual (High)
- **Evidence:** `PKG_LEAVE.pkb:459-552` - outer loop employees, inner loop leave types, per-row tenure/cap lookups and `UPDATE`.
- **Remediation:** Single `MERGE` into `LEAVE_BALANCES` joined to `LEAVE_TYPES`, plus one `INSERT ... SELECT` into the log.

### PERF-03 - Hierarchical query scaling (Medium)
- **Evidence:** `schema/views/hrms_views.sql:44-60` (`CONNECT BY PRIOR EMP_ID = MANAGER_EMP_ID` at L56) with header warning about >500 employees; `PKG_EMPLOYEE.get_org_chart` repeats it. No index on `EMPLOYEES.MANAGER_EMP_ID`.
- **Remediation:** Index `MANAGER_EMP_ID`; start-with filter; materialized closure table refreshed on change.

### PERF-04 - N+1 POST-QUERY lookups (Medium)
- **Evidence:** `HRMS_EMPLOYEE.xml:345` (3 lookups/row: department, job, manager), `HRMS_LEAVE.xml:94`, `HRMS_PERFORMANCE.xml:79`.
- **Remediation:** Base blocks on a joined view (e.g. `VW_ACTIVE_EMPLOYEES`) or use `FROM` clause query data sources.

### PERF-05 - Per-day holiday query (Medium)
- **Evidence:** `PKG_LEAVE.pkb:12-40` loops each date and runs `SELECT COUNT(*) FROM HOLIDAYS` per weekday; also `PKG_VALIDATION.is_business_day` (`PKG_VALIDATION.pkb:78-97`).
- **Remediation:** One query with a generated date range and anti-join to `HOLIDAYS`.

### PERF-06 - Hard parsing in dynamic search (Medium)
- **Evidence:** `PKG_EMPLOYEE.pkb:457-498` (literals instead of binds).
- **Remediation:** Same fix as SEC-02.

### PERF-07 - SMTP connection per message (Medium)
- **Evidence:** `PKG_NOTIFICATION.pkb:90-91` inside the per-row loop.
- **Remediation:** Open once per batch; or `UTL_MAIL`/AQ-based relay.

### PERF-08 - Housekeeping and physical design (Low)
- **Evidence:** `PKG_AUDIT.pkb:33-47` single unbatched `DELETE FROM AUDIT_LOG`; 28 of 29 sequences `NOCACHE` (`schema/sequences/hrms_sequences.sql`); no secondary indexes anywhere in `schema/` (FK columns such as `EMPLOYEES.DEPT_ID`, `LEAVE_REQUESTS.EMP_ID`, `PAYROLL_DETAILS.EMP_ID` unindexed -> full scans and FK lock escalation on parent deletes/updates).
- **Remediation:** Partition `AUDIT_LOG` by month and drop partitions; `CACHE 20+`; index FK and frequent filter columns.

### PERF-09 - Synchronous payroll in Forms (Medium)
- **Evidence:** `HRMS_PAYROLL.xml` `BTN_CALCULATE` calls `PKG_PAYROLL.calculate_payroll` directly.
- **Impact:** Blocks the Forms session for the whole run; WebLogic/Forms timeouts can kill the session mid-run (feeds RACE-02).
- **Remediation:** Submit via `DBMS_SCHEDULER` and poll status.

## 4. Validation and logic drift

### DRIFT-01 - Employee history trigger vs table (Critical)
- **Evidence:** `plsql/triggers/trg_employees.sql:78-109` inserts `HISTORY_ID, EMP_ID, CHANGE_TYPE, CHANGE_DATE, OLD_VALUE, NEW_VALUE, CHANGED_BY, CHANGE_REASON`; table `EMPLOYEE_HISTORY` (`schema/tables/01_core_tables.sql:152-180`) defines `HIST_ID, EFFECTIVE_DATE, OLD_/NEW_DEPT_ID, ... REASON_CODE, COMMENTS, CREATED_BY, CREATED_DATE`. Trigger also uses `CHANGE_TYPE` values (`DEPARTMENT_CHANGE`, `JOB_CHANGE`) not in `CHK_CHANGE_TYPE` (L173-176).
- **Impact:** *Inferred:* the trigger is created `INVALID` (`ORA-00904`); with an invalid BEFORE UPDATE trigger, **every UPDATE on `EMPLOYEES` fails** with `ORA-04098` - transfers, promotions, terminations and Forms edits all break.
- **Remediation:** Rewrite the trigger against the real columns (or remove it, since `PKG_EMPLOYEE.log_history` already writes history) and add a CI compile check.

### DRIFT-02 - Leave auto-approval defect (Critical)
- **Evidence:** `PKG_LEAVE.pkb:170` inserts `STATUS = 'APPROVED'` when `REQUIRES_APPROVAL = 'N'`, then L200-202 calls `approve_leave_request`, which (L212-263, check at L225) requires `STATUS = 'PENDING'` and raises otherwise. Seeded `JURY` and `BEREAVE` leave types have `REQUIRES_APPROVAL = 'N'` (`data/seed/01_reference_data.sql`).
- **Impact:** Every jury-duty/bereavement request errors and rolls back; if it did not, the `PENDING` balance moved at L176-182 would never be converted to `USED`.
- **Remediation:** Insert as `PENDING` and let `approve_leave_request` transition, or post directly to `USED` without calling approve.

### DRIFT-03 - Payroll error row FK violation (High)
- **Evidence:** `PKG_PAYROLL.pkb:310-320` inserts into `PAYROLL_DETAILS` with `ELEMENT_ID = 0` in the exception handler; `FK_PD_ELEMENT` -> `PAY_ELEMENTS`, which has no element 0 (seed IDs 1, 100-103, 200-205).
- **Impact:** The error handler itself raises, aborting the whole run on the first employee error (after previous partial commits - RACE-02).
- **Remediation:** Dedicated `PAYROLL_ERRORS` table or a seeded `ERROR` element.

### DRIFT-04 - Hard-coded tax logic (High)
- **Evidence:** `PKG_PAYROLL.pkb:605` and `644` (comments), brackets and standard deductions as constants; branches only for `SINGLE`/`MARRIED_SEPARATE` (L645) and `MARRIED_JOINT` (L661) - `HEAD_OF_HOUSEHOLD` (allowed by `EMPLOYEE_TAX_INFO` CHECK) falls through to 0 federal tax. `TAX_BRACKETS` table (`02_payroll_tables.sql:159`) is never read; allowances/extra withholding partially ignored; state tax flat-rate.
- **Impact:** Under-withholding (compliance exposure); annual code change required for tax-year updates.
- **Remediation:** Data-driven calculation from `TAX_BRACKETS` keyed by tax year/jurisdiction/filing status, with unit tests per status.

### DRIFT-05 - Forms bypass package business rules (High)
- **Evidence:** `HRMS_EMPLOYEE` inserts/updates `EMPLOYEES` directly (block on `HRMS.EMPLOYEES`, `HRMS_EMPLOYEE.xml:111`) instead of `PKG_EMPLOYEE.create_employee/update_employee` - no salary record, history, audit, notification or `validate_manager` loop check. `HRMS_PERFORMANCE` updates `PERFORMANCE_REVIEWS` and inserts `PERFORMANCE_GOALS` directly (no sequence for `GOAL_ID` -> *Inferred* insert fails on NOT NULL PK), bypassing `PKG_PERFORMANCE` status transitions, rating labels and audit.
- **Remediation:** Base DML blocks on procedures (`ON-INSERT`/`ON-UPDATE` calling packages) or transactional APIs; revoke direct table DML from the Forms runtime user.

### DRIFT-06 - Silent audit loss (High)
- **Evidence:** `plsql/triggers/trg_audit.sql:47-64` passes `'STATUS_CHANGE'`; `AUDIT_LOG.CHK_AUDIT_ACTION` (`04_performance_tables.sql:104`) allows only `INSERT/UPDATE/DELETE`; `PKG_AUDIT.log_action` (`PKG_AUDIT.pkb:6-31`) swallows exceptions.
- **Impact:** No leave-status audit trail is ever recorded, and nobody is told. Compliance gap for leave approvals.
- **Remediation:** Use `UPDATE` with old/new status in the values, or extend the CHECK; make audit failures visible (log to alert table / raise).

### DRIFT-07 - Divergent validation layers (Medium)

| Rule | Forms / PLL (`HRMS_VALIDATION_LIB`) | `PKG_COMMON` / `PKG_VALIDATION` | DB (DDL / trigger) |
|---|---|---|---|
| Email | `INSTR` '@' then '.' (`HRMS_VALIDATION_LIB.pll.sql:21-40`); NULL valid | `REGEXP_LIKE` (`PKG_COMMON.pkb:265-268`) via `PKG_VALIDATION.validate_email_format` (`PKG_VALIDATION.pkb:50-55`) - this is what `HRMS_EMPLOYEE.xml:377` actually calls | Trigger: uniqueness only; no format CHECK |
| Phone | digit-count rule (`pll.sql:47-62`) | different regex (`PKG_COMMON.pkb:270-275`) | none |
| SSN | format + area-number checks (`pll.sql:69-89`) | regex only (`PKG_COMMON.pkb:277-279`) | stored encrypted; no check |
| Salary vs grade | NULL -> valid, short messages (`pll.sql:108-134`); header claims caching that isn't implemented | NULL -> error "required" (`PKG_VALIDATION.pkb:17-48`) | none - `PKG_PAYROLL.create_salary_record` doesn't call either |
| Hire date | `<= SYSDATE + 90` in `HRMS_EMPLOYEE.xml:383` | `validate_date_not_future` (PLL) forbids any future date; `PKG_EMPLOYEE.create_employee` has no limit | none |
| Required fields | Forms item `Required` | `PKG_VALIDATION.validate_required_fields` (L99-121) - never called | NOT NULL |

- **Impact:** Records accepted by one channel are rejected by another (e.g. batch/API vs Forms); `HRMS_VALIDATION_LIB` and most of `PKG_VALIDATION` are dead code.
- **Remediation:** One server-side validation API used by packages (authoritative) plus DB CHECK constraints for invariants; Forms call the same API.

### DRIFT-08 - Three leave-availability formulas (Medium)
- **Evidence:** Virtual column `AVAILABLE = OPENING_BALANCE + ACCRUED - USED + ADJUSTMENT - PENDING` (`03_leave_tables.sql:47`); `VW_LEAVE_SUMMARY` omits `- PENDING` (`hrms_views.sql:96`); `PKG_REPORTING.leave_utilization_report` (`PKG_REPORTING.pkb:123-147`) and `PKG_LEAVE.process_carryover` (L558-603) recompute without pending.
- **Impact:** Employees/HR see higher balances in reports than the system enforces; carry-over includes days already requested.
- **Remediation:** Always read the virtual column.

### DRIFT-09 - Payslip and register inconsistencies (Medium)
- **Evidence:** `PKG_PAYROLL.pkb:784-785` returns `0 AS YTD_GROSS`, `0 AS YTD_NET` (placeholders) although `get_ytd_earnings` exists; run totals exclude `BENEFIT` from deductions (L336-339) while payslip/register include it (L777-778, 858); element IDs 100-103 hard-coded (L780-783, 854-857; `PKG_REPORTING.pkb:157-160`).
- **Impact:** Payslip YTD always zero (statutory payslip content), run totals disagree with register/GL.
- **Remediation:** Use `get_ytd_earnings`; single definition of deductions; look up elements by `ELEMENT_CODE`.

### DRIFT-10 - Lifecycle conflicts (Medium)
- **Evidence:** `trg_employees.sql:71-75` raises on `TERMINATED -> ACTIVE`, which is exactly what `PKG_EMPLOYEE.rehire_employee` does; `PKG_EMPLOYEE.terminate_employee` (`PKG_EMPLOYEE.pkb:669-682`) sets `PENDING` leave requests to `CANCELLED` by direct UPDATE without releasing `LEAVE_BALANCES.PENDING`, and TODOs (`PKG_EMPLOYEE.pkb:737-739`) for final pay, access revocation and COBRA are unimplemented.
- **Remediation:** Allow rehire via a flagged path; route cancellations through `PKG_LEAVE.cancel_leave_request`; implement offboarding steps (at minimum revoke sessions).

### DRIFT-11 - Business-day disagreement (Medium)
- **Evidence:** `PKG_COMMON.business_days_between` / `add_business_days` (`PKG_COMMON.pkb:132-168`) ignore holidays; `PKG_LEAVE.calculate_business_days` (`PKG_LEAVE.pkb:12-40`) and `PKG_VALIDATION.is_business_day` include them (global + location).
- **Remediation:** One holiday-aware calendar function.

### DRIFT-12 - Configuration in code (Medium)
- **Evidence:** SMTP host/port (`PKG_NOTIFICATION.pkb:7-8`) vs seeded `SMTP_HOST`/`FROM_ADDRESS`; session timeout constant (`PKG_SECURITY.pkb:8`) vs `SESSION_TIMEOUT_MIN`; fiscal-year start hard-coded in `PKG_COMMON.get_fiscal_year` (`PKG_COMMON.pkb:170-198`) vs `FISCAL_YEAR_START`; `PASSWORD_MIN_LENGTH` unused.
- **Remediation:** Read via `PKG_COMMON.get_param`, with caching.

### DRIFT-13 - Missing constraints (Low)
- **Evidence:** No unique key on `EMPLOYEES.EMAIL`, `PERFORMANCE_REVIEWS(CYCLE_ID, EMP_ID)` (`generate_reviews_for_cycle`, `PKG_PERFORMANCE.pkb:289-317`, relies on `DUP_VAL_ON_INDEX` that cannot fire), `PAYROLL_DETAILS(RUN_ID, EMP_ID, ELEMENT_ID)`, one active `SALARY_RECORDS` per employee; no FKs for `DEPARTMENTS.PARENT_DEPT_ID/MANAGER_EMP_ID/LOCATION_CODE`, `HOLIDAYS.LOCATION_CODE`, `SALARY_RECORDS.APPROVED_BY`; `CHK_ACCRUAL_FREQ` / `CHK_HALF_DAY` include a meaningless `NULL` in `IN (...)`.
- **Remediation:** Add constraints after data clean-up (see `DATA_DICTIONARY.md`).

### DRIFT-14 - Stubs, dead code, misleading docs (Low)
- **Evidence:** `PKG_INTEGRATION.import_time_attendance` (TODO at `PKG_INTEGRATION.pkb:170`) and `sync_org_structure` (placeholder, L196-203); `PKG_REPORTING.refresh_reporting_tables` (L196-205) is a stub over non-existent `RPT_*` tables; unused `TAX_BRACKETS`, `EMPLOYEE_BANK_ACCOUNTS`, `LOOKUP_VALUES`, `EMERGENCY_CONTACTS` and 5 sequences; `HRMS_VALIDATION_LIB` never called; `TRG_EMP_INSTEAD_OF_DELETE` is actually `BEFORE DELETE`; Forms headers overstate blocks/LOVs; README counts (18 forms / 42 tables / 15 views / 200+ triggers) do not match the repository (see `APPLICATION_INVENTORY.md` section 2); `HRMS_MENU` opens missing `HRMS_REPORTS`/`HRMS_ADMIN`; "Cancel Query" mapped to `EXIT_FORM` (`forms/menus/HRMS_MENU.mmb.sql`); `KEY-NEXT-ITEM` uses `DO_KEY('WHEN-BUTTON-PRESSED')` (`HRMS_LOGIN.xml:111-112`), which is not a valid key trigger name.
- **Remediation:** Remove or implement; correct documentation.

## 5. Recommended remediation sequence

1. **Immediate (security containment):** SEC-01, SEC-03 (rotate key), SEC-02, SEC-06/SEC-07 (server-side authorization), SEC-09.
2. **Data integrity:** DRIFT-01 (trigger compile), DRIFT-02, DRIFT-03, RACE-01, RACE-02/RACE-07 (payroll state machine + idempotency), RACE-03, RACE-04, DRIFT-06.
3. **Compliance / correctness:** DRIFT-04 (tax tables), DRIFT-09 (YTD), DRIFT-05 (route Forms DML through packages), DRIFT-10.
4. **Scalability:** PERF-01, PERF-02, PERF-09, PERF-04, PERF-08 indexes.
5. **Consolidation:** DRIFT-07/08/11/12 into single server-side services; remove dead code (DRIFT-14).

Add a CI step that compiles all PL/SQL into a scratch schema and fails on `INVALID` objects - it would have caught DRIFT-01, DRIFT-03 and SEC-03 key-length issues automatically.
