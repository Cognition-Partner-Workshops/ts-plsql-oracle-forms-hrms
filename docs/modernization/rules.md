# HRMS business-rule catalogue

Source baseline: the inventory on this branch. Sections follow its dependency-first port waves (within a wave independent items are ordered as below; salary audit precedes payroll). This is a static catalogue of implemented behavior, not proof of runtime coverage or approval to preserve defects. Package sections cover spec/body as one module; XML triggers belong to their form. REPORTS and ADMIN are additional missing-source sections, not additions to the 34 inventoried items.

IDs are immutable module prefixes plus three digits. Use the Java-safe spelling with `_` replacing `-` for a test method (for example `PAY_001`); do not renumber existing IDs when adding rules. Each table row is one rule, sometimes a cohesive decision with multiple branches. Source paths are repository-relative; citations identify the start of the relevant expression/block, not just a declaration. No ID is assigned to missing source or pure allocation/fixture mechanics.

Columns: **Rule** = stable ID and observed statement; **Source** = file:line; **Lives** = form trigger / PLL library / package / DB trigger / menu / config (schema/seed/view SQL is config); **Kind** = H hard-coded, D data-driven, M mixed; **Change?** = plausible business change, not a commitment. All change assessments and other inferred intent are explicitly labelled **Hypothesis:**. Numeric parameters stored in tables are D even when their initial values are literal seed SQL. Constraints and SQL formulas are H/M, not evidence of a configurable policy engine.

## Counts per module

**152 rules across 34 inventoried items**, plus two explicitly missing screens (zero cataloguable rules).

| Module | Rules |
| --- | ---: |
| Core tables | 3 |
| Sequences | 0 |
| Payroll tables | 3 |
| Leave tables | 2 |
| Performance and system tables | 3 |
| Views | 6 |
| Reference seed | 4 |
| Employee seed | 0 |
| PKG_COMMON | 7 |
| PKG_AUDIT | 3 |
| HRMS_VALIDATION_LIB | 5 |
| TRG_EMP_BEFORE_INSERT | 3 |
| TRG_EMP_BEFORE_UPDATE | 3 |
| TRG_EMP_INSTEAD_OF_DELETE | 1 |
| PKG_VALIDATION | 6 |
| PKG_NOTIFICATION | 5 |
| TRG_SALARY_AUDIT | 1 |
| PKG_PAYROLL | 19 |
| PKG_INTEGRATION | 4 |
| PKG_REPORTING | 6 |
| TRG_LEAVE_REQUEST_AUDIT | 1 |
| TRG_DEPARTMENT_AUDIT | 1 |
| PKG_EMPLOYEE | 10 |
| PKG_LEAVE | 13 |
| PKG_PERFORMANCE | 7 |
| PKG_SECURITY | 6 |
| HRMS_COMMON_LIB | 4 |
| HRMS_EMPLOYEE | 7 |
| HRMS_LEAVE | 4 |
| HRMS_PAYROLL | 4 |
| HRMS_PERFORMANCE | 3 |
| HRMS_MENU (screen) | 3 |
| HRMS_MENU (menu source) | 2 |
| HRMS_LOGIN | 3 |
| REPORTS / ADMIN | 0 / 0 (missing) |

## Core tables

Wave 0a.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| CORE-001 — Grade maximum salary must be at least its minimum. | `schema/tables/01_core_tables.sql:71` | config | H | Hypothesis: no — basic interval consistency. |
| CORE-002 — Employment status is ACTIVE, ON_LEAVE, SUSPENDED or TERMINATED; type FULL_TIME, PART_TIME, CONTRACT or INTERN; gender M, F or O. | `schema/tables/01_core_tables.sql:140` | config | H | Hypothesis: yes — employment classifications and demographic options evolve. |
| CORE-003 — Dependent relationship is SPOUSE, CHILD, PARENT, DOMESTIC_PARTNER or OTHER. | `schema/tables/01_core_tables.sql:198` | config | H | Hypothesis: yes — benefits eligibility categories evolve. |

## Sequences

Wave 0a. No real business rules: Oracle identity allocation definitions, not eligibility or calculation policy (`schema/sequences/hrms_sequences.sql:1`). Zero rules; sequence start/cache choices are persistence mechanics.

## Payroll tables

Wave 0b.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| PDT-001 — Salary frequency is WEEKLY, BIWEEKLY, SEMIMONTHLY or MONTHLY; basis ANNUAL or HOURLY. | `schema/tables/02_payroll_tables.sql:30` | config | H | Hypothesis: yes — compensation offerings change. |
| PDT-002 — Pay element types are EARNING, DEDUCTION, TAX, BENEFIT, REIMBURSEMENT; calculation types FLAT, PERCENTAGE, HOURS, FORMULA. | `schema/tables/02_payroll_tables.sql:57` | config | H | Hypothesis: yes — new compensation elements may need new types. |
| PDT-003 — Period statuses OPEN/PROCESSING/CLOSED/REVERSED; run types REGULAR/SUPPLEMENTAL/BONUS/FINAL and statuses PENDING/CALCULATING/CALCULATED/APPROVED/PAID/REVERSED/ERROR. | `schema/tables/02_payroll_tables.sql:101`, `schema/tables/02_payroll_tables.sql:129` | config | H | Hypothesis: yes — payroll control workflows may change. |

## Leave tables

Wave 0b.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| LDT-001 — Accrual frequency admits MONTHLY, BIWEEKLY, ANNUAL or null. | `schema/tables/03_leave_tables.sql:31` | config | H | Hypothesis: yes — accrual schedules are policy. |
| LDT-002 — Leave status admits PENDING/APPROVED/REJECTED/CANCELLED/TAKEN; end must be on/after start; half-day period AM/PM or null. | `schema/tables/03_leave_tables.sql:88` | config | H | Hypothesis: yes — leave workflows may change, though ordered dates remain necessary. |

## Performance and system tables

Wave 0b.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| SDT-001 — Cycle statuses DRAFT/OPEN/IN_PROGRESS/CALIBRATION/CLOSED; review statuses NOT_STARTED/SELF_REVIEW/MANAGER_REVIEW/MEETING_SCHEDULED/COMPLETED/ACKNOWLEDGED; overall rating 1.0–5.0. | `schema/tables/04_performance_tables.sql:25`, `schema/tables/04_performance_tables.sql:57` | config | H | Hypothesis: yes — review process and scales change. |
| SDT-002 — Goal statuses NOT_STARTED/IN_PROGRESS/COMPLETED/DEFERRED/CANCELLED; categories BUSINESS/DEVELOPMENT/LEADERSHIP/INNOVATION/COMPLIANCE. | `schema/tables/04_performance_tables.sql:85` | config | H | Hypothesis: yes — goal taxonomy changes. |
| SDT-003 — Notification statuses PENDING/SENT/FAILED/CANCELLED and channels EMAIL/IN_APP/SMS are admitted. | `schema/tables/04_performance_tables.sql:146` | config | H | Hypothesis: yes — delivery channels and lifecycle evolve. |

## Views

Wave 0c. These are query contracts with real selection/calculation rules, not just aliases.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| VW-001 — Active-employee view requires status ACTIVE and flag Y; salary must be active, effective now, with end null or after now. | `schema/views/hrms_views.sql:32` | config | M | Hypothesis: yes — effective-date boundary conventions can change. |
| VW-002 — Organization hierarchy starts with managerless employees, follows manager links and filters ACTIVE status. | `schema/views/hrms_views.sql:44` | config | M | Hypothesis: yes — inclusion of vacancies/inactive employees may change. |
| VW-003 — Compa-ratio is salary / grade midpoint ×100 rounded to one decimal, using active salary records and ACTIVE employees. | `schema/views/hrms_views.sql:64` | config | M | Hypothesis: yes — compensation reporting definitions change. |
| VW-004 — Current-year leave available is opening + accrued − used + adjustment (pending is not subtracted); utilization is used / (opening + accrued) ×100 with zero denominator null. | `schema/views/hrms_views.sql:96` | config | M | Hypothesis: yes — reservation/reporting definitions may change. |
| VW-005 — Latest payroll means the globally maximum APPROVED run ID, not one latest run per employee; exclude ERROR details and sum signed amounts for net. | `schema/views/hrms_views.sql:109` | config | M | Hypothesis: yes — report scope may need employee-specific latest pay. |
| VW-006 — Pending approvals union PENDING leave with MANAGER_REVIEW performance reviews, assigning leave approver/review reviewer. | `schema/views/hrms_views.sql:133` | config | M | Hypothesis: yes — approval queues evolve. |

## Reference seed

Wave 0d. Configuration values below are real policy inputs, unlike synthetic employee fixtures. Seeded parameters do not prove their consumers read them.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| REF-001 — PTO accrues 1.25 days/month, maximum 20, carryover maximum 5 with expiry 3; SICK accrues 0.833/month, maximum 10, carryover 10 without expiry; both require approval and zero minimum tenure. | `data/seed/01_reference_data.sql:101` | config | D | Hypothesis: yes — leave benefit rates change. |
| REF-002 — COMP requires 90-day tenure, FMLA 365; neither accrues; JURY requires no approval and zero tenure. | `data/seed/01_reference_data.sql:107` | config | D | Hypothesis: yes — leave eligibility and approval policy change. |
| REF-003 — Initial employee 401(k) default is 6%; medical/dental/vision/life/HSA flat defaults are 250/45/15/25/150; pretax flags are stored per element. | `data/seed/01_reference_data.sql:137` | config | D | Hypothesis: yes — benefit offerings and contributions change. |
| REF-004 — Holidays are dated active rows, initially ten global US holidays for 2024. | `data/seed/01_reference_data.sql:158` | config | D | Hypothesis: yes — calendars change each year/location. |

## Employee seed

Wave 0d. No additional normative business rules: synthetic employee inserts and salary/pay-element fixtures (`data/seed/02_employee_data.sql:1`, `data/seed/02_employee_data.sql:100`). Zero rules. Values characterize this dataset, not universal salary/eligibility limits. Hypothesis: these fixtures should remain separate from production migration data.

## PKG_COMMON

Wave 1.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| COM-001 — Parameter lookup uses group/code; missing returns null; invalid numeric/date conversions return null. | `plsql/packages/PKG_COMMON.pkb:66`, `plsql/packages/PKG_COMMON.pkb:83` | package | D | Hypothesis: yes — missing-configuration handling may become fail-fast. |
| COM-002 — Business-day count includes both endpoints, excludes Saturday/Sunday but does not query holidays; adding business days also skips weekends only. | `plsql/packages/PKG_COMMON.pkb:132`, `plsql/packages/PKG_COMMON.pkb:151` | package | H | Hypothesis: yes — holiday treatment is business policy. |
| COM-003 — Fiscal year turns in October; Oct–Dec is Q1, Jan–Mar Q2, Apr–Jun Q3, Jul–Sep Q4. | `plsql/packages/PKG_COMMON.pkb:170` | package | H | Hypothesis: yes — accounting calendar can change. |
| COM-004 — SSN display exposes only last four; null or fewer than four characters becomes fully masked. | `plsql/packages/PKG_COMMON.pkb:220` | package | H | Hypothesis: yes — privacy/display policy may tighten. |
| COM-005 — Email uses anchored local-part/domain regex with alphabetic final suffix of at least two characters. | `plsql/packages/PKG_COMMON.pkb:265` | package | H | Hypothesis: yes — accepted addresses may expand. |
| COM-006 — Phone strips all non-digits and accepts 10 or 11 digits without a country-prefix check. | `plsql/packages/PKG_COMMON.pkb:270` | package | H | Hypothesis: yes — international support changes validation. |
| COM-007 — SSN strips non-digits and checks exactly nine digits, not nonzero groups. | `plsql/packages/PKG_COMMON.pkb:277` | package | H | Hypothesis: yes — identity validation may be strengthened. |

## PKG_AUDIT

Wave 1.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| AUD-001 — Audit writes commit autonomously, recording actor, old/new values, time, IP/session; errors are swallowed. | `plsql/packages/PKG_AUDIT.pkb:6` | package | H | Hypothesis: yes — compliance may require failure propagation/transaction coupling. |
| AUD-002 — Purge deletes records strictly older than now minus requested retention days. | `plsql/packages/PKG_AUDIT.pkb:33` | package | M | Hypothesis: yes — retention requirements change. |
| AUD-003 — History filters table/record and optional date bounds, returning newest changes first. | `plsql/packages/PKG_AUDIT.pkb:49` | package | M | Hypothesis: no — basic retrieval contract, not a policy threshold. |

## HRMS_VALIDATION_LIB

Wave 1. Attached to EMPLOYEE; attachment alone does not show invocation of every function. See drift below: comments are not a substitute for implementation.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| VLL-001 — Null email passes; otherwise first @ must be internal and a following first dot cannot immediately follow @ or be last. Extra dots/subdomains are not rejected by this code. | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:25` | PLL library | H | Hypothesis: yes — email acceptance may be reconciled. |
| VLL-002 — Null phone passes; TRANSLATE removes listed punctuation and checks remaining length 10/11, not that all remaining characters are digits. | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:50` | PLL library | H | Hypothesis: yes — international/strict validation changes. |
| VLL-003 — Null SSN passes; strip hyphens, require length nine and reject 000/00/0000 groups (no general digit regex). | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:72` | PLL library | H | Hypothesis: yes — identity policy may be unified. |
| VLL-004 — Null dates pass not-future validation; otherwise compare truncated dates with today. | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:96` | PLL library | H | Hypothesis: yes — allowable advance dates are contextual. |
| VLL-005 — Null salary or grade passes; otherwise query current JOB_GRADES bounds, accept inclusive range, return an error for unknown grade. There is no cache in the implementation. | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:116` | PLL library | M | Hypothesis: yes — pay-range enforcement changes. |

## TRG_EMP_BEFORE_INSERT

Wave 1.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| EBI-001 — Missing creator/date default to USER/now; missing active flag/status default Y/ACTIVE. | `plsql/triggers/trg_employees.sql:17` | DB trigger | H | Hypothesis: yes — onboarding default state may change. |
| EBI-002 — Reject hire date more than 180 days ahead. | `plsql/triggers/trg_employees.sql:35` | DB trigger | H | Hypothesis: yes — advance hiring window is policy. |
| EBI-003 — Reject case-insensitive duplicate email among active-flag Y rows. The row trigger queries its own table; runtime safety is not established here. | `plsql/triggers/trg_employees.sql:45` | DB trigger | M | Hypothesis: yes — uniqueness scope can change. |

## TRG_EMP_BEFORE_UPDATE

Wave 1. History inserts name columns incompatible with checked-in DDL (`schema/tables/01_core_tables.sql:152`); these are source rules, not a claim of successful execution.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| EBU-001 — Set modification actor to supplied actor or USER, modification time to now. | `plsql/triggers/trg_employees.sql:66` | DB trigger | H | Hypothesis: no — basic attribution requirement. |
| EBU-002 — Reject TERMINATED → ACTIVE updates, with no package-call exemption in the predicate. | `plsql/triggers/trg_employees.sql:71` | DB trigger | H | Hypothesis: yes — must reconcile with rehire workflow. |
| EBU-003 — Attempt history entries for status, department and job changes; department/job null comparison uses −1 sentinel. | `plsql/triggers/trg_employees.sql:77`, `plsql/triggers/trg_employees.sql:89`, `plsql/triggers/trg_employees.sql:101` | DB trigger | H | Hypothesis: yes — tracked changes/representation may evolve. |

## TRG_EMP_INSTEAD_OF_DELETE

Wave 1.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| EBD-001 — BEFORE DELETE always raises −20504; does not perform an UPDATE/soft-delete despite its name/comments. | `plsql/triggers/trg_employees.sql:120` | DB trigger | H | Hypothesis: yes — retention/deletion policy can change. |

## PKG_VALIDATION

Wave 2.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| VAL-001 — Date ranges require nonnull endpoints and end ≥ start. | `plsql/packages/PKG_VALIDATION.pkb:11` | package | H | Hypothesis: no — basic interval consistency. |
| VAL-002 — Salary and grade required; salary must be within inclusive current grade bounds; unknown grade returns error text including ID. | `plsql/packages/PKG_VALIDATION.pkb:25` | package | M | Hypothesis: yes — pay-range enforcement is policy. |
| VAL-003 — Email/phone validation delegates to COMMON regex/digit-length rules, not PLL. | `plsql/packages/PKG_VALIDATION.pkb:50`, `plsql/packages/PKG_VALIDATION.pkb:57` | package | H | Hypothesis: yes — accepted formats evolve. |
| VAL-004 — Employee number must match EMP- followed by six digits. | `plsql/packages/PKG_VALIDATION.pkb:68` | package | H | Hypothesis: yes — identifier format may change. |
| VAL-005 — Future-date predicate uses truncated dates; business day excludes weekends and active global/matching-location holidays. | `plsql/packages/PKG_VALIDATION.pkb:75`, `plsql/packages/PKG_VALIDATION.pkb:85` | package | M | Hypothesis: yes — location calendars change. |
| VAL-006 — Required-field check handles EMPLOYEES only: first/last name, hire date, department, job; missing record returns error; other table names return null. | `plsql/packages/PKG_VALIDATION.pkb:106` | package | H | Hypothesis: yes — completeness criteria change. |

## PKG_NOTIFICATION

Wave 2.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| NOT-001 — Explicit recipient email wins, else resolve employee email; queue PENDING autonomously, default EMAIL/priority 5; failures roll back queue write and log without rethrow. | `plsql/packages/PKG_NOTIFICATION.pkb:16` | package | M | Hypothesis: yes — delivery guarantees/priority policy may change. |
| NOT-002 — Process PENDING EMAIL rows with email present, lowest priority number then oldest creation first, batch default 50. | `plsql/packages/PKG_NOTIFICATION.pkb:71` | package | M | Hypothesis: yes — throughput and priority policy change. |
| NOT-003 — Successful SMTP marks SENT/time; failure marks FAILED, records error and increments retry count. | `plsql/packages/PKG_NOTIFICATION.pkb:109` | package | H | Hypothesis: yes — failure workflow may change. |
| NOT-004 — Retry resets FAILED rows below requested retry limit (default 3) to PENDING; cancellation only affects PENDING. | `plsql/packages/PKG_NOTIFICATION.pkb:148`, `plsql/packages/PKG_NOTIFICATION.pkb:170` | package | M | Hypothesis: yes — retry limits and cancellation policy evolve. |
| NOT-005 — SMTP uses literal host, port 25 and from address/name rather than seeded SYSTEM_PARAMETERS. | `plsql/packages/PKG_NOTIFICATION.pkb:7` | package | H | Hypothesis: yes — mail routing/sender identity changes. |

## TRG_SALARY_AUDIT

Wave 2, before payroll salary-write behavior.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| TSA-001 — Every salary insert/update/delete calls AUDIT with action, salary ID and JSON snapshots; update snapshots contain salary/active flag; actor is NEW.MODIFIED_BY or USER. | `plsql/triggers/trg_audit.sql:10` | DB trigger | H | Hypothesis: yes — audited fields/attribution policy evolve. |

## PKG_PAYROLL

Wave 2. **Payroll owner decision (provided requirement, not inferred from source): tax/wage-cap logic becomes business-maintained dated rate tables.** This catalogue records legacy outcomes; it does not endorse carrying 2024 constants forward. Existing TAX_BRACKETS DDL (`schema/tables/02_payroll_tables.sql:154`) is not evidence that the calculation reads it. Characterize legacy boundaries, then test approved dated-rate changes.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| PAY-001 — Salary creation rejects nonpositive base pay, ends earlier-effective active salaries at new effective date minus one day and deactivates them, then inserts ANNUAL replacement salary. Same/later-effective active rows are not ended. | `plsql/packages/PKG_PAYROLL.pkb:30` | package | M | Hypothesis: yes — salary overlap/effective-date policy may evolve. |
| PAY-002 — Current salary requires active flag, effective ≤ now and end null or strictly after now. As-of salary ignores active flag and includes end date (end ≥ requested date). Both take latest effective record; missing returns zero. | `plsql/packages/PKG_PAYROLL.pkb:63`, `plsql/packages/PKG_PAYROLL.pkb:86` | package | M | Hypothesis: yes — boundary/retro-pay rules evolve. |
| PAY-003 — MONTHLY periods span calendar months, pay month-end shifted to Friday for Saturday/Sunday; BIWEEKLY starts 13 days before first Friday and pays five days after 14-day period end. Other frequencies insert no periods. | `plsql/packages/PKG_PAYROLL.pkb:120`, `plsql/packages/PKG_PAYROLL.pkb:148` | package | H | Hypothesis: yes — pay calendars change. |
| PAY-004 — Already CLOSED periods cannot be closed again; CLOSED periods cannot get a new payroll run. | `plsql/packages/PKG_PAYROLL.pkb:198`, `plsql/packages/PKG_PAYROLL.pkb:245` | package | H | Hypothesis: yes — reopening/correction workflows evolve. |
| PAY-005 — Calculate all status ACTIVE/flag Y employees; individual errors are counted/logged; final run is ERROR if any error else CALCULATED. | `plsql/packages/PKG_PAYROLL.pkb:294`, `plsql/packages/PKG_PAYROLL.pkb:331` | package | M | Hypothesis: yes — inclusion and partial-run acceptance may change. |
| PAY-006 — Annual salary as of period end divided by 52/26/24/12 for WEEKLY/BIWEEKLY/SEMIMONTHLY/MONTHLY (else 12), rounded to cents; zero salary fails. No hourly-hours calculation here. | `plsql/packages/PKG_PAYROLL.pkb:384` | package | H | Hypothesis: yes — hourly/proration rules may be introduced. |
| PAY-007 — Missing active tax-year W-4 defaults SINGLE, zero allowances/additional federal withholding, no state; taxable federal income is gross without pretax deduction subtraction. | `plsql/packages/PKG_PAYROLL.pkb:416` | package | M | Hypothesis: yes — default withholding and pretax treatment may change. |
| PAY-008 — Federal annual taxable = period gross × periods − standard deduction − allowances ×4300; married-joint standard deduction 29200, all other statuses 14600 (`c_standard_deduction_single/married`). Nonpositive taxable returns zero before extra withholding. | `plsql/packages/PKG_PAYROLL.pkb:12`, `plsql/packages/PKG_PAYROLL.pkb:619` | package | H | Hypothesis: yes — annual tax law changes; dated tables required by owner. |
| PAY-009 — 2024 SINGLE/MARRIED_SEPARATE annual brackets: upper bounds 11600/47150/100525/191950/243725/609350, marginal rates 10/12/22/24/32/35%, then 37%; cumulative bases 0/1160/5426/17168.50/39110.50/55678.50/183647.25. | `plsql/packages/PKG_PAYROLL.pkb:643` | package | H | Hypothesis: yes — annual tax law changes; dated tables required. |
| PAY-010 — 2024 MARRIED_JOINT bounds 23200/94300/201050/383900/487450/731200, same marginal rates, bases 0/2320/10852/34337/78221/111357/196669.50. No HEAD_OF_HOUSEHOLD branch: initialized annual tax stays zero. | `plsql/packages/PKG_PAYROLL.pkb:614`, `plsql/packages/PKG_PAYROLL.pkb:661` | package | H | Hypothesis: yes — tax schedules/status support change. |
| PAY-011 — Round annual federal tax / periods to cents, then add NVL(additional withholding,0), except early zero-tax return above. | `plsql/packages/PKG_PAYROLL.pkb:683` | package | H | Hypothesis: yes — statutory withholding treatment can change. |
| PAY-012 — Flat state rates CA 7.25%, NY 6.85%, TX/FL/WA 0%, IL 4.95%, PA 3.07%, OH 4%, NJ 6.37%, MA 5%, else 5%; gross × rate rounded to cents; filing status/allowances unused. | `plsql/packages/PKG_PAYROLL.pkb:703` | package | H | Hypothesis: yes — tax jurisdiction/rates change; dated tables required. |
| PAY-013 — `c_ss_wage_base_2024` is 168600; Social Security is 6.2% of lesser of gross and remaining cap, rounded to cents; zero once YTD ≥ cap. | `plsql/packages/PKG_PAYROLL.pkb:7`, `plsql/packages/PKG_PAYROLL.pkb:729` | package | H | Hypothesis: yes — annual wage caps/rates change; dated tables required. |
| PAY-014 — Medicare base is gross ×1.45% rounded; add 0.9% on current-period earnings crossing/above YTD threshold 200000, separately rounded. | `plsql/packages/PKG_PAYROLL.pkb:9`, `plsql/packages/PKG_PAYROLL.pkb:747` | package | H | Hypothesis: yes — tax thresholds/rates change; dated tables required. |
| PAY-015 — Active employee DEDUCTION/BENEFIT elements effective by period end and not ended before period start use override first, else employee flat/percentage falling back to element default, else employee amount or zero; insert positive results as negative amounts. PRETAX_FLAG is selected but not applied to federal taxable. | `plsql/packages/PKG_PAYROLL.pkb:503` | package | M | Hypothesis: yes — benefit/deduction formulas evolve. |
| PAY-016 — Approval requires CALCULATED run and marks only run APPROVED with approver/time (no detail status update or audit call there); reversal sets run/details REVERSED and audits without a status eligibility check. | `plsql/packages/PKG_PAYROLL.pkb:555`, `plsql/packages/PKG_PAYROLL.pkb:583` | package | H | Hypothesis: yes — financial controls may tighten. |
| PAY-017 — Payslip grosses sum EARNING; deduction total includes DEDUCTION/TAX/BENEFIT absolute amounts; tax buckets use element IDs 100–103. | `plsql/packages/PKG_PAYROLL.pkb:776` | package | H | Hypothesis: yes — pay element mappings/reporting evolve. |
| PAY-018 — YTD earnings sum EARNING details with CALCULATED detail status and period start in requested year; no run-status filter or exclusion of current run. | `plsql/packages/PKG_PAYROLL.pkb:802` | package | M | Hypothesis: yes — YTD inclusion/year allocation can change. |
| PAY-019 — Run summary net subtracts absolute DEDUCTION/TAX but not BENEFIT, whereas payslip deduction total includes BENEFIT; register tax columns also use fixed element IDs. | `plsql/packages/PKG_PAYROLL.pkb:338`, `plsql/packages/PKG_PAYROLL.pkb:853` | package | H | Hypothesis: yes — inconsistent financial totals need owner reconciliation. |

## PKG_INTEGRATION

Wave 2.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| INT-001 — GL export groups non-ERROR run details by cost center/account/type/period, skips unmapped accounts; earnings debit and all other types credit absolute totals. | `plsql/packages/PKG_INTEGRATION.pkb:35` | package | M | Hypothesis: yes — accounting mappings and posting conventions change. |
| INT-002 — Benefits feed includes ACTIVE employees, left-joining active dependents; writes fixed-width employee/dependent demographic data, not contribution selections. | `plsql/packages/PKG_INTEGRATION.pkb:90` | package | M | Hypothesis: yes — provider feed contracts change. |
| INT-003 — Time import reads nonempty non-comment lines but contains no implemented parsing/time-record insertion; sync_org_structure only logs. Do not invent hours/overtime rules from these stubs. | `plsql/packages/PKG_INTEGRATION.pkb:153`, `plsql/packages/PKG_INTEGRATION.pkb:196` | package | H | Hypothesis: yes — actual integration behavior needs definition. |
| INT-004 — Integration status comes from INTEGRATION / name_STATUS parameter; file directories are fixed GL_FEED_OUT, BENEFITS_FEED_OUT, TIME_ATTENDANCE_IN. | `plsql/packages/PKG_INTEGRATION.pkb:7`, `plsql/packages/PKG_INTEGRATION.pkb:205` | package | M | Hypothesis: yes — integration enablement/routes change. |

## PKG_REPORTING

Wave 2; this package is not evidence that missing REPORTS screen rules are known.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| RPT-001 — Headcount groups ACTIVE employees by department, counting employment types and M/F gender buckets. | `plsql/packages/PKG_REPORTING.pkb:6` | package | M | Hypothesis: yes — workforce metrics/categories evolve. |
| RPT-002 — Compensation summary uses ACTIVE employees and active salary records, aggregating by department/grade. | `plsql/packages/PKG_REPORTING.pkb:36` | package | M | Hypothesis: yes — compensation reporting scope changes. |
| RPT-003 — Turnover percentage uses terminations in date range divided by hires through period end ×100, null denominator protection, rounded to one decimal; VOLUNTARY versus other reason buckets. | `plsql/packages/PKG_REPORTING.pkb:65` | package | M | Hypothesis: yes — turnover denominator/definitions may change. |
| RPT-004 — New hires filter hire date inclusively between supplied bounds and optional department. | `plsql/packages/PKG_REPORTING.pkb:98` | package | M | Hypothesis: no — basic requested-date report contract. |
| RPT-005 — Leave utilization aggregates balances for requested year, with optional department filter. | `plsql/packages/PKG_REPORTING.pkb:123` | package | M | Hypothesis: yes — utilization metrics evolve. |
| RPT-006 — Payroll summary filters requested period, uses fixed tax element IDs 100–103 and includes deductions/benefits in deduction totals; EEO report includes ACTIVE employees grouped by EEO category/department and gender. | `plsql/packages/PKG_REPORTING.pkb:148`, `plsql/packages/PKG_REPORTING.pkb:174` | package | M | Hypothesis: yes — regulatory reporting and mappings change. |

## TRG_LEAVE_REQUEST_AUDIT

Wave 2, before domain leave status writes.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| TLA-001 — Every AFTER UPDATE OF STATUS logs STATUS_CHANGE, even if unchanged, with old/new status snapshots and actor NEW.MODIFIED_BY or USER. AUDIT_LOG constraint admits only INSERT/UPDATE/DELETE, so this action conflicts and AUDIT swallows the insert error. | `plsql/triggers/trg_audit.sql:47`, `schema/tables/04_performance_tables.sql:104`, `plsql/packages/PKG_AUDIT.pkb:28` | DB trigger | H | Hypothesis: yes — audit action vocabulary needs reconciliation. |

## TRG_DEPARTMENT_AUDIT

Wave 2.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| TDA-001 — Department insert/update/delete logs action, department ID and DB USER to AUDIT, with no old/new snapshots supplied. | `plsql/triggers/trg_audit.sql:71` | DB trigger | H | Hypothesis: yes — organization audit scope changes. |

## PKG_EMPLOYEE

Wave 3.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| EMP-001 — Employee number uses prefix and max numeric suffix +1 padded to six digits; on error uses employee sequence fallback. | `plsql/packages/PKG_EMPLOYEE.pkb:39` | package | H | Hypothesis: yes — identifier format/allocation may change. |
| EMP-002 — Department must be active; optional manager must be active and manager-chain traversal must not reach this employee. | `plsql/packages/PKG_EMPLOYEE.pkb:70`, `plsql/packages/PKG_EMPLOYEE.pkb:88` | package | M | Hypothesis: yes — supervisory eligibility may change. |
| EMP-003 — Hiring requires first/last name, valid department/manager and active job; out-of-grade base salary only warns in debug rather than rejects. Missing location is resolved from department. | `plsql/packages/PKG_EMPLOYEE.pkb:203`, `plsql/packages/PKG_EMPLOYEE.pkb:221` | package | M | Hypothesis: yes — hiring/pay exception policy evolves. |
| EMP-004 — Hiring creates optional salary, history/audit, and manager notification; employee update uses nonnull supplied values, retaining existing values for null inputs. | `plsql/packages/PKG_EMPLOYEE.pkb:272`, `plsql/packages/PKG_EMPLOYEE.pkb:348` | package | M | Hypothesis: yes — onboarding side effects and field-clearing semantics may change. |
| EMP-005 — Transfer requires current ACTIVE status, validates new department/optional manager, updates assignment and logs history/audit. | `plsql/packages/PKG_EMPLOYEE.pkb:504` | package | M | Hypothesis: yes — transfer eligibility/approval evolves. |
| EMP-006 — Promotion changes job and creates new salary effective on requested date, recording previous/new job and pay in history. | `plsql/packages/PKG_EMPLOYEE.pkb:581` | package | M | Hypothesis: yes — promotion approvals/effective dates change. |
| EMP-007 — Termination rejects already terminated; auto-cancels PENDING leave, sets TERMINATED/flag N, ends active salaries and employee pay elements on termination date. No final-pay calculation is implemented here. | `plsql/packages/PKG_EMPLOYEE.pkb:662`, `plsql/packages/PKG_EMPLOYEE.pkb:695` | package | H | Hypothesis: yes — offboarding settlement/cancellation rules change. |
| EMP-008 — Rehire validates department, sets ACTIVE/Y, replaces hire date/assignment, clears termination data and creates REHIRE salary/history; does not require prior TERMINATED status. This UPDATE conflicts with EBU-002 if that trigger is installed. | `plsql/packages/PKG_EMPLOYEE.pkb:759` | package | M | Hypothesis: yes — rehire eligibility and trigger conflict need owner resolution. |
| EMP-009 — Direct reports/org chart select ACTIVE employees; headcount also requires current ACTIVE status and honors hire/termination dates as of requested date. Tenure is months between hire and termination-or-now /12 rounded to one decimal (missing employee returns null). | `plsql/packages/PKG_EMPLOYEE.pkb:798`, `plsql/packages/PKG_EMPLOYEE.pkb:845`, `plsql/packages/PKG_EMPLOYEE.pkb:865` | package | M | Hypothesis: yes — workforce metric definitions evolve. |
| EMP-010 — Employee validation reports missing names/hire date and inconsistent ACTIVE status with non-Y active flag. | `plsql/packages/PKG_EMPLOYEE.pkb:904` | package | H | Hypothesis: yes — completeness rules expand. |

## PKG_LEAVE

Wave 3. Accrual rates are read from LEAVE_TYPES, not payroll constants; initial PTO/SICK values are REF-001.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| LEV-001 — Business days include endpoints, excluding weekends and active global or location holidays. Overlap considers existing PENDING/APPROVED requests and optional excluded request ID. | `plsql/packages/PKG_LEAVE.pkb:12`, `plsql/packages/PKG_LEAVE.pkb:45` | package | M | Hypothesis: yes — calendars/reservation scope change. |
| LEV-002 — Submission requires active employee and active leave type and satisfies type minimum tenure measured at now. | `plsql/packages/PKG_LEAVE.pkb:85`, `plsql/packages/PKG_LEAVE.pkb:107` | package | M | Hypothesis: yes — eligibility/tenure change. |
| LEV-003 — Start ≤ end; retroactive start may be at most five days before today. | `plsql/packages/PKG_LEAVE.pkb:116` | package | H | Hypothesis: yes — retroactive request window is policy. |
| LEV-004 — Half-day flag assigns 0.5 days; otherwise use business-day count; reject ≤0 days or overlapping requests. | `plsql/packages/PKG_LEAVE.pkb:129` | package | H | Hypothesis: yes — partial-day booking policy evolves. |
| LEV-005 — Accruing leave requires sufficient available balance; submission reserves pending days, notifies manager when approval needed, auto-approves types not requiring approval. | `plsql/packages/PKG_LEAVE.pkb:147`, `plsql/packages/PKG_LEAVE.pkb:180` | package | M | Hypothesis: yes — overdraft/approval policy evolves. |
| LEV-006 — Only PENDING can approve/reject; approval moves days from pending to used and rejection releases pending, with employee notification/audit. | `plsql/packages/PKG_LEAVE.pkb:225`, `plsql/packages/PKG_LEAVE.pkb:281` | package | H | Hypothesis: yes — workflow and consumption timing change. |
| LEV-007 — Only PENDING/APPROVED can cancel; release pending for former, subtract used for latter. | `plsql/packages/PKG_LEAVE.pkb:331` | package | H | Hypothesis: yes — cancellation restrictions may change. |
| LEV-008 — Available balance = opening + accrued − used + adjustment − pending; missing balance returns zero. | `plsql/packages/PKG_LEAVE.pkb:369` | package | H | Hypothesis: yes — reservation treatment can change. |
| LEV-009 — Adjustment adds requested amount, initializing missing balance; initialization creates zero balances for active leave types. | `plsql/packages/PKG_LEAVE.pkb:392`, `plsql/packages/PKG_LEAVE.pkb:428` | package | M | Hypothesis: yes — opening-entitlement/adjustment controls may change. |
| LEV-010 — Monthly accrual selects ACTIVE/Y employees and active accruing MONTHLY types; tenure evaluated on accrual date. | `plsql/packages/PKG_LEAVE.pkb:470` | package | M | Hypothesis: yes — eligible population/frequency changes. |
| LEV-011 — Accrue type ACCRUAL_RATE, limited to remaining MAX_BALANCE (floor zero), updating or initializing yearly balance and logging positive accrual. No already-accrued-month exclusion in the loop. | `plsql/packages/PKG_LEAVE.pkb:496` | package | D | Hypothesis: yes — rate/caps and duplicate-run controls change. |
| LEV-012 — Year carryover uses positive remaining opening + accrued − used + adjustment, capped at type CARRYOVER_MAX; creates next-year opening/carryover and optional month-based expiry. | `plsql/packages/PKG_LEAVE.pkb:568` | package | M | Hypothesis: yes — carryover entitlements/expiry change. |
| LEV-013 — Expired positive carryover is deducted from adjustment and cleared once expiry date ≤ today. | `plsql/packages/PKG_LEAVE.pkb:614` | package | M | Hypothesis: yes — expiry/forfeiture policy changes. |

## PKG_PERFORMANCE

Wave 3.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| PER-001 — New cycle is DRAFT; opening requires DRAFT or errors; closing sets CLOSED without equivalent prior-state predicate. | `plsql/packages/PKG_PERFORMANCE.pkb:6`, `plsql/packages/PKG_PERFORMANCE.pkb:39`, `plsql/packages/PKG_PERFORMANCE.pkb:56` | package | H | Hypothesis: yes — review governance evolves. |
| PER-002 — New review is NOT_STARTED; submitting self assessment only from NOT_STARTED/SELF_REVIEW changes to MANAGER_REVIEW and notifies reviewer. | `plsql/packages/PKG_PERFORMANCE.pkb:65`, `plsql/packages/PKG_PERFORMANCE.pkb:103` | package | H | Hypothesis: yes — self-review workflow evolves. |
| PER-003 — Completion rejects ratings outside 1–5, sets COMPLETED and maps ≥4.5/3.5/2.5/1.5/else to Exceptional/Exceeds Expectations/Meets Expectations/Needs Improvement/Unsatisfactory; no previous-state guard in completion UPDATE. | `plsql/packages/PKG_PERFORMANCE.pkb:142` | package | H | Hypothesis: yes — rating scales and completion controls change. |
| PER-004 — Acknowledge updates only COMPLETED reviews to ACKNOWLEDGED with employee comments/time. | `plsql/packages/PKG_PERFORMANCE.pkb:184` | package | H | Hypothesis: yes — acknowledgement process evolves. |
| PER-005 — Goals start NOT_STARTED/zero progress; progress update uses supplied status, else COMPLETED at ≥100 or IN_PROGRESS above zero. | `plsql/packages/PKG_PERFORMANCE.pkb:204`, `plsql/packages/PKG_PERFORMANCE.pkb:238` | package | H | Hypothesis: yes — goal lifecycle changes. |
| PER-006 — Rating distribution includes only nonnull ratings in cycle and optional department, grouped by label. | `plsql/packages/PKG_PERFORMANCE.pkb:269` | package | M | Hypothesis: yes — calibration reporting changes. |
| PER-007 — Bulk review creation selects ACTIVE employees with nonnull manager, uses manager as reviewer and ignores duplicate-key failures. | `plsql/packages/PKG_PERFORMANCE.pkb:290` | package | M | Hypothesis: yes — review assignment eligibility changes. |

## PKG_SECURITY

Wave 4. Observed defects below are not security requirements for the replacement.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| SEC-001 — Authenticate identifies ACTIVE employee by case-insensitive email; duplicate matches choose minimum EMP_ID. No password comparison executes before creating session/context/audit. | `plsql/packages/PKG_SECURITY.pkb:42`, `plsql/packages/PKG_SECURITY.pkb:65` | package | M | Hypothesis: yes — actual credential verification must be defined. |
| SEC-002 — Session expires strictly more than 30 minutes after LOGIN_TIME (not inactivity); absent/non-ACTIVE sessions invalid; logout closes session. | `plsql/packages/PKG_SECURITY.pkb:8`, `plsql/packages/PKG_SECURITY.pkb:86`, `plsql/packages/PKG_SECURITY.pkb:106` | package | H | Hypothesis: yes — session lifetime/security policy evolves. |
| SEC-003 — Grade ID ≥8 grants everything; grade ID ≥5 grants VIEW; all grades can LEAVE CREATE/VIEW and EMPLOYEE VIEW; otherwise deny, including absent employee. No row/department ownership predicate enforces comments about own records. | `plsql/packages/PKG_SECURITY.pkb:144` | package | H | Hypothesis: yes — authorization policy requires explicit roles/ownership. |
| SEC-004 — Password-change procedure checks length ≥8, uppercase and digit only, but does not verify old password or persist replacement. | `plsql/packages/PKG_SECURITY.pkb:208` | package | H | Hypothesis: yes — credential lifecycle needs complete implementation. |
| SEC-005 — Hash helper uses MD5, separate from authenticate; not evidence of credential verification. | `plsql/packages/PKG_SECURITY.pkb:14` | package | H | Hypothesis: yes — credential protection standards change. |
| SEC-006 — SSN encryption/decryption uses AES256 CBC PKCS5 with source-defined key; decryption errors return masked error marker. Key value intentionally not reproduced here. | `plsql/packages/PKG_SECURITY.pkb:179`, `plsql/packages/PKG_SECURITY.pkb:195` | package | H | Hypothesis: yes — key management/privacy requirements change. |

## HRMS_COMMON_LIB

Wave 5.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| CLL-001 — Shared error handler tries logging without recursive propagation, displays error then raises FORM_TRIGGER_FAILURE. | `forms/libraries/HRMS_COMMON_LIB.pll.sql:16` | PLL library | H | Hypothesis: no — basic error-surfacing contract, presentation may differ. |
| CLL-002 — Clear/exit ask about pending commit; save commits form; query button switches enter-query/execute-query according to mode. | `forms/libraries/HRMS_COMMON_LIB.pll.sql:44`, `forms/libraries/HRMS_COMMON_LIB.pll.sql:93` | PLL library | H | Hypothesis: yes — unsaved-edit interaction policy changes. |
| CLL-003 — Dates display MM/DD/YYYY and datetimes add 24-hour HH24:MI:SS. | `forms/libraries/HRMS_COMMON_LIB.pll.sql:101` | PLL library | H | Hypothesis: yes — localization policy changes. |
| CLL-004 — Current user falls back from global to DB USER; missing/non-numeric session ID returns null; missing/invalid session blocks operation. | `forms/libraries/HRMS_COMMON_LIB.pll.sql:114` | PLL library | M | Hypothesis: yes — identity/session transport changes. |

## HRMS_EMPLOYEE

Wave 6. Direct Forms table DML is not equivalent to PKG_EMPLOYEE orchestration.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| EFM-001 — Require valid session; lacking EMPLOYEE EDIT disables insert/update/delete; default query status ACTIVE and active-flag Y employees. | `forms/xml-exports/HRMS_EMPLOYEE.xml:35`, `forms/xml-exports/HRMS_EMPLOYEE.xml:45` | form trigger | M | Hypothesis: yes — access/scope policy changes. |
| EFM-002 — Changed-form exit prompts save/discard/cancel; cancellation raises form failure. | `forms/xml-exports/HRMS_EMPLOYEE.xml:93` | form trigger | H | Hypothesis: yes — edit interaction policy changes. |
| EFM-003 — Nonnull email delegates to server email validator; reject hire dates more than 90 days ahead. | `forms/xml-exports/HRMS_EMPLOYEE.xml:375` | form trigger | H | Hypothesis: yes — hiring horizon and formats evolve. |
| EFM-004 — Department/job item validation requires active reference rows; LOVs also show active departments/jobs. | `forms/xml-exports/HRMS_EMPLOYEE.xml:388`, `forms/xml-exports/HRMS_EMPLOYEE.xml:400`, `forms/xml-exports/HRMS_EMPLOYEE.xml:465` | form trigger | M | Hypothesis: yes — assignment eligibility changes. |
| EFM-005 — Insert allocates employee ID, generates number via package, sets ACTIVE/Y and creator/time; update stamps modifier/time; these are direct form DML hooks. | `forms/xml-exports/HRMS_EMPLOYEE.xml:323`, `forms/xml-exports/HRMS_EMPLOYEE.xml:336` | form trigger | H | Hypothesis: yes — onboarding defaults change. |
| EFM-006 — Marital-status dropdown offers SINGLE/MARRIED/DIVORCED/WIDOWED; gender offers M/F/O. | `forms/xml-exports/HRMS_EMPLOYEE.xml:158`, `forms/xml-exports/HRMS_EMPLOYEE.xml:169` | config | H | Hypothesis: yes — demographic classifications evolve. |
| EFM-007 — Employee-linked salary block is read-only (no insert/update/delete), queried by employee ID. | `forms/xml-exports/HRMS_EMPLOYEE.xml:431`, `forms/xml-exports/HRMS_EMPLOYEE.xml:453` | config | H | Hypothesis: yes — compensation editing responsibilities change. |

## HRMS_LEAVE

Wave 6.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| LFM-001 — Validate session using numeric application USERNAME; default leave query restricts to global current employee. | `forms/xml-exports/HRMS_LEAVE.xml:25`, `forms/xml-exports/HRMS_LEAVE.xml:35` | form trigger | M | Hypothesis: yes — session transport/visibility policy changes. |
| LFM-002 — Cancel requires PENDING/APPROVED and confirmation before package cancellation. | `forms/xml-exports/HRMS_LEAVE.xml:78` | form trigger | H | Hypothesis: yes — cancellation UX/policy changes. |
| LFM-003 — New request requires leave type, start and end before calling submit_leave_request. | `forms/xml-exports/HRMS_LEAVE.xml:139` | form trigger | H | Hypothesis: no — minimum request completeness. |
| LFM-004 — Submission passes global employee ID and half-day flag (default N), but not half-day period, to package; clears/requeries after success. Screen has no separate business-day/overlap calculation. | `forms/xml-exports/HRMS_LEAVE.xml:152` | form trigger | M | Hypothesis: yes — submission flow may evolve. |

## HRMS_PAYROLL

Wave 6.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| PFM-001 — Require valid session and PAYROLL VIEW permission; initial period query limits OPEN/PROCESSING periods. | `forms/xml-exports/HRMS_PAYROLL.xml:28`, `forms/xml-exports/HRMS_PAYROLL.xml:43` | form trigger | M | Hypothesis: yes — authorization and visible history evolve. |
| PFM-002 — Run creation calls package with selected period and REGULAR type. | `forms/xml-exports/HRMS_PAYROLL.xml:98` | form trigger | H | Hypothesis: yes — supplemental/bonus initiation may be added. |
| PFM-003 — Calculate button only allows PENDING runs, then invokes calculate_payroll. Package calculate itself has no matching prior-status check. | `forms/xml-exports/HRMS_PAYROLL.xml:114` | form trigger | H | Hypothesis: yes — recalculation controls evolve. |
| PFM-004 — Approve requires PAYROLL APPROVE permission before package approval. | `forms/xml-exports/HRMS_PAYROLL.xml:137` | form trigger | M | Hypothesis: yes — financial approval authority evolves. |

## HRMS_PERFORMANCE

Wave 6. No package workflow calls appear in this export.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| XFM-001 — Require valid session using numeric application USERNAME; initial cycles only OPEN/DRAFT, newest year first. | `forms/xml-exports/HRMS_PERFORMANCE.xml:23`, `forms/xml-exports/HRMS_PERFORMANCE.xml:33` | form trigger | M | Hypothesis: yes — visibility/session contract changes. |
| XFM-002 — Cycles read-only; reviews update-only; goals insert/update but not delete; relations constrain cycle→review→goal. Review ratings/assessments are direct table edits, not submit/complete package workflow. | `forms/xml-exports/HRMS_PERFORMANCE.xml:40`, `forms/xml-exports/HRMS_PERFORMANCE.xml:57`, `forms/xml-exports/HRMS_PERFORMANCE.xml:97` | config | H | Hypothesis: yes — review editing controls evolve. |
| XFM-003 — Goal dropdown offers BUSINESS/DEVELOPMENT/LEADERSHIP only, unlike wider database category constraint. | `forms/xml-exports/HRMS_PERFORMANCE.xml:106` | config | H | Hypothesis: yes — goal taxonomy may expand. |

## HRMS_MENU (screen)

Wave 6.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| MFM-001 — Disable PAYROLL/ADMIN/REPORTS menu entries when corresponding VIEW permission absent. This is UI enablement, not proof of endpoint authorization. | `forms/xml-exports/HRMS_MENU.xml:26` | form trigger | M | Hypothesis: yes — module access rules evolve. |
| MFM-002 — Navigation opens employee/payroll/leave/performance/reports/admin forms in ACTIVATE/SESSION mode. | `forms/xml-exports/HRMS_MENU.xml:153` | menu | H | Hypothesis: yes — navigation destinations change. |
| MFM-003 — Exit logs out global session and exits form; security session state is explicitly closed. | `forms/xml-exports/HRMS_MENU.xml:130` | form trigger | H | Hypothesis: no — explicit logout remains required. |

## HRMS_MENU (menu source)

Wave 6. Entire file is a comment-only representation, not executable PL/SQL. Rules below are described menu contracts, not observed execution; actual XML navigation is above.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| MNU-001 — Described File/Edit/Query/Navigate actions cover save/record edits/query/navigation/exit using Forms built-ins. | `forms/menus/HRMS_MENU.mmb.sql:11`, `forms/menus/HRMS_MENU.mmb.sql:24` | menu | H | Hypothesis: yes — client interaction contract changes. |
| MNU-002 — Described module launches and security-based visibility are part of menu representation; do not treat comment references as executable package calls. | `forms/menus/HRMS_MENU.mmb.sql:40` | menu | H | Hypothesis: yes — navigation/authorization contract evolves. |

## REPORTS

**MISSING — no source in repo; rules cannot be catalogued**. HRMS_MENU references HRMS_REPORTS in button trigger and menu command (`forms/xml-exports/HRMS_MENU.xml:119`, `forms/xml-exports/HRMS_MENU.xml:161`). Zero rules; PKG_REPORTING is separately catalogued and does not establish this screen's behavior.

## ADMIN

**MISSING — no source in repo; rules cannot be catalogued**. HRMS_MENU references HRMS_ADMIN (`forms/xml-exports/HRMS_MENU.xml:165`), with permission-based entry disabling (`forms/xml-exports/HRMS_MENU.xml:30`). Zero rules; no administrative workflow can be inferred from that reference.

## HRMS_LOGIN

Wave 7.

| Rule | Source | Lives | Kind | Change? |
| --- | --- | --- | --- | --- |
| LOG-001 — Require nonnull username/password before authenticate; pass client host to package. | `forms/xml-exports/HRMS_LOGIN.xml:69` | form trigger | H | Hypothesis: yes — identity methods may change. |
| LOG-002 — Successful authentication stores session/user globals, selects first ACTIVE employee with case-insensitive email for current_emp_id, opens HRMS_MENU. ROWNUM=1 need not choose package's minimum duplicate ID. | `forms/xml-exports/HRMS_LOGIN.xml:81` | form trigger | M | Hypothesis: yes — identity uniqueness/session contract changes. |
| LOG-003 — Any login exception displays generic invalid-credentials message, clears password, refocuses password and fails trigger. | `forms/xml-exports/HRMS_LOGIN.xml:95` | form trigger | H | Hypothesis: yes — failure/recovery policy may change. |

## Validation drift

Static differences, not a claim that every PLL function is called by a screen. EMPLOYEE attaches PLL (`forms/xml-exports/HRMS_EMPLOYEE.xml:23`) but its email trigger explicitly calls PKG_VALIDATION. No PLL invocation should be invented from attachment alone. PKG_VALIDATION delegates email/phone to COMMON, so both the dispatch and implementation are cited below. Items without a server counterpart are explicitly identified, not asserted equivalent.

| Item | Client/form source | Server comparison source | Difference |
| --- | --- | --- | --- |
| Email acceptance and misleading comment | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:25`, `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:34` | `plsql/packages/PKG_VALIDATION.pkb:54`, `plsql/packages/PKG_COMMON.pkb:267` | PLL accepts null and uses first @/dot positions; e.g. `a@b.c` passes PLL but fails server two-letter suffix regex. Despite PLL comment, `user@mail.company.com` passes both: no extra-dot rejection exists. Server enforces allowed characters; PLL does not. |
| Form email bypass | `forms/xml-exports/HRMS_EMPLOYEE.xml:376` | `plsql/packages/PKG_VALIDATION.pkb:54`, `plsql/packages/PKG_COMMON.pkb:267` | Form skips email validation entirely for null; otherwise directly uses server rule, not PLL. Null acceptance at form is therefore explicit guard behavior. |
| Phone normalization | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:50`, `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:55` | `plsql/packages/PKG_VALIDATION.pkb:61`, `plsql/packages/PKG_COMMON.pkb:272` | PLL removes only TRANSLATE-listed punctuation and accepts null; server removes all nondigits then checks digit length. `123456789a` can pass PLL length check but leaves nine server digits. Neither checks an 11-digit number begins with 1. |
| SSN groups/characters and missing central wrapper | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:72`, `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:83` | `plsql/packages/PKG_VALIDATION.pks:1`, `plsql/packages/PKG_COMMON.pkb:279` | PKG_VALIDATION exposes no SSN validator; COMMON is nearest counterpart. PLL rejects zero groups and strips only hyphens; COMMON strips any nondigit and permits zero groups when nine digits. PLL length-only logic can admit nine letters; COMMON cannot. |
| Salary nulls, messages, not caching | `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:116`, `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:122` | `plsql/packages/PKG_VALIDATION.pkb:25`, `plsql/packages/PKG_VALIDATION.pkb:29` | PLL null salary/grade succeeds; server returns required error. Both query current JOB_GRADES and use inclusive limits, so alleged stale PLL cache is not implemented. Server includes grade name/ID and decimal salary in errors; PLL uses short messages. |
| Hire-date horizons and opposite date predicates | `forms/xml-exports/HRMS_EMPLOYEE.xml:383`, `forms/libraries/HRMS_VALIDATION_LIB.pll.sql:98` | `plsql/packages/PKG_VALIDATION.pkb:75`, `plsql/triggers/trg_employees.sql:35` | Form permits 90 days ahead, DB insert trigger 180. PLL not-future permits today/null; server is_future_date answers the opposite question using truncation (null comparison is unknown), not a hire-date 90/180-day policy. |
| Reference eligibility not in required-field validation | `forms/xml-exports/HRMS_EMPLOYEE.xml:388`, `forms/xml-exports/HRMS_EMPLOYEE.xml:400` | `plsql/packages/PKG_VALIDATION.pkb:114` | Form requires active existing department/job; centralized required-field function checks only nonnull IDs, not existence/active flags. |
| Leave request completeness versus date-range rule | `forms/xml-exports/HRMS_LEAVE.xml:139` | `plsql/packages/PKG_VALIDATION.pkb:11`, `plsql/packages/PKG_LEAVE.pkb:116` | Form requires type/start/end but does not call centralized date-range validator; package LEAVE separately checks ordering/retroactivity. Central date-range function additionally returns false for missing endpoints. This is duplicated coverage across different dispatch paths, not proof of a shared validator call. |

Hypothesis: reconcile these differences with business owners before assigning replacement Java tests; preserving one layer indiscriminately would silently select a policy. The documented payroll dated-table decision is already explicit; other policy resolutions remain open.
