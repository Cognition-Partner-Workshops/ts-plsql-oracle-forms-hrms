CREATE FUNCTION oracle_months_between(later timestamp, earlier timestamp)
RETURNS numeric LANGUAGE sql IMMUTABLE STRICT AS $$
SELECT ((EXTRACT(YEAR FROM later) - EXTRACT(YEAR FROM earlier)) * 12
       + EXTRACT(MONTH FROM later) - EXTRACT(MONTH FROM earlier))
       + CASE WHEN EXTRACT(DAY FROM later) = EXTRACT(DAY FROM earlier)
              OR (later::date = (date_trunc('month', later) + interval '1 month - 1 day')::date
                  AND earlier::date = (date_trunc('month', earlier) + interval '1 month - 1 day')::date)
         THEN 0
         ELSE (EXTRACT(DAY FROM later) - EXTRACT(DAY FROM earlier)
               + EXTRACT(EPOCH FROM (later::time - earlier::time)) / 86400) / 31 END
$$;

-- ============================================================================
-- HRMS Database Views
-- Used by Oracle Reports (.rdf), Forms LOVs, and external reporting tools
-- ============================================================================

-- -----------------------------------------------------------------------
-- VW_ACTIVE_EMPLOYEES
-- Commonly used view for active employee lookups
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_ACTIVE_EMPLOYEES AS
SELECT e.EMP_ID, e.EMP_NUMBER, e.FIRST_NAME, e.LAST_NAME,
       e.FIRST_NAME || ' ' || e.LAST_NAME AS FULL_NAME,
       e.EMAIL, e.PHONE_WORK, e.PHONE_MOBILE,
       e.HIRE_DATE,
       TRUNC(oracle_months_between(LOCALTIMESTAMP(0), e.HIRE_DATE) / 12, 1) AS TENURE_YEARS,
       e.EMPLOYMENT_TYPE, e.EMPLOYMENT_STATUS,
       e.DEPT_ID, d.DEPT_NAME, d.DEPT_CODE, d.COST_CENTER,
       e.JOB_ID, j.JOB_TITLE, j.JOB_CODE,
       g.GRADE_ID, g.GRADE_NAME,
       e.MANAGER_EMP_ID,
       COALESCE(m.FIRST_NAME, '') || ' ' || COALESCE(m.LAST_NAME, '') AS MANAGER_NAME,
       e.LOCATION_CODE,
       l.LOCATION_NAME, l.CITY, l.STATE_PROVINCE, l.COUNTRY_CODE,
       sr.BASE_SALARY AS CURRENT_SALARY,
       sr.CURRENCY_CODE, sr.PAY_FREQUENCY
FROM EMPLOYEES e
JOIN DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID
JOIN JOB_TITLES j ON e.JOB_ID = j.JOB_ID
JOIN JOB_GRADES g ON j.GRADE_ID = g.GRADE_ID
LEFT JOIN EMPLOYEES m ON e.MANAGER_EMP_ID = m.EMP_ID
LEFT JOIN LOCATIONS l ON e.LOCATION_CODE = l.LOCATION_CODE
LEFT JOIN SALARY_RECORDS sr ON e.EMP_ID = sr.EMP_ID
    AND sr.ACTIVE_FLAG = 'Y'
    AND sr.EFFECTIVE_DATE <= LOCALTIMESTAMP(0)
    AND (sr.END_DATE IS NULL OR sr.END_DATE > LOCALTIMESTAMP(0))
WHERE e.EMPLOYMENT_STATUS = 'ACTIVE'
AND e.ACTIVE_FLAG = 'Y';

COMMENT ON VIEW VW_ACTIVE_EMPLOYEES IS
    'Denormalized view of active employees with department, job, manager, location, and salary';

-- -----------------------------------------------------------------------
-- VW_ORG_HIERARCHY
-- Hierarchical org chart using CONNECT BY
-- WARNING: Performance degrades significantly with >500 employees
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_ORG_HIERARCHY AS
WITH RECURSIVE org AS (
    SELECT e.*, 1 AS org_level,
           ' > ' || FIRST_NAME || ' ' || LAST_NAME AS org_path,
           ARRAY[EMP_ID::numeric] AS visited,
           ARRAY[LAST_NAME || ':' || EMP_ID::text] AS sort_path
    FROM EMPLOYEES e WHERE MANAGER_EMP_ID IS NULL
    UNION ALL
    SELECT e.*, o.org_level + 1,
           o.org_path || ' > ' || e.FIRST_NAME || ' ' || e.LAST_NAME,
           o.visited || e.EMP_ID,
           o.sort_path || (e.LAST_NAME || ':' || e.EMP_ID::text)
    FROM EMPLOYEES e JOIN org o ON e.MANAGER_EMP_ID = o.EMP_ID
    WHERE NOT e.EMP_ID = ANY(o.visited)
)
SELECT EMP_ID, EMP_NUMBER, FIRST_NAME || ' ' || LAST_NAME AS EMP_NAME,
       MANAGER_EMP_ID, DEPT_ID, org_level, org_path,
       CASE WHEN EXISTS (SELECT 1 FROM EMPLOYEES c WHERE c.MANAGER_EMP_ID = org.EMP_ID)
            THEN 0 ELSE 1 END AS is_leaf
FROM org WHERE EMPLOYMENT_STATUS = 'ACTIVE'
ORDER BY sort_path;

-- -----------------------------------------------------------------------
-- VW_EMPLOYEE_COMPENSATION
-- Current compensation details with compa-ratio calculation
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_EMPLOYEE_COMPENSATION AS
SELECT e.EMP_ID, e.EMP_NUMBER,
       e.FIRST_NAME || ' ' || e.LAST_NAME AS EMP_NAME,
       d.DEPT_NAME, j.JOB_TITLE, g.GRADE_NAME,
       sr.BASE_SALARY,
       g.MIN_SALARY AS GRADE_MIN,
       g.MAX_SALARY AS GRADE_MAX,
       (g.MIN_SALARY + g.MAX_SALARY) / 2 AS GRADE_MIDPOINT,
       ROUND(sr.BASE_SALARY / ((g.MIN_SALARY + g.MAX_SALARY) / 2) * 100, 1) AS COMPA_RATIO,
       sr.EFFECTIVE_DATE AS SALARY_EFFECTIVE_DATE,
       sr.CHANGE_REASON AS LAST_CHANGE_REASON,
       sr.CHANGE_PCT AS LAST_CHANGE_PCT
FROM EMPLOYEES e
JOIN DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID
JOIN JOB_TITLES j ON e.JOB_ID = j.JOB_ID
JOIN JOB_GRADES g ON j.GRADE_ID = g.GRADE_ID
JOIN SALARY_RECORDS sr ON e.EMP_ID = sr.EMP_ID AND sr.ACTIVE_FLAG = 'Y'
WHERE e.EMPLOYMENT_STATUS = 'ACTIVE';

-- -----------------------------------------------------------------------
-- VW_LEAVE_SUMMARY
-- Current year leave balances with utilization
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_LEAVE_SUMMARY AS
SELECT e.EMP_ID, e.EMP_NUMBER,
       e.FIRST_NAME || ' ' || e.LAST_NAME AS EMP_NAME,
       d.DEPT_NAME,
       lt.LEAVE_TYPE_NAME,
       lb.OPENING_BALANCE,
       lb.ACCRUED,
       lb.USED,
       lb.ADJUSTMENT,
       lb.PENDING,
       lb.OPENING_BALANCE + lb.ACCRUED - lb.USED + lb.ADJUSTMENT AS AVAILABLE,
       ROUND(lb.USED * 100 / NULLIF(lb.OPENING_BALANCE + lb.ACCRUED, 0), 1) AS UTILIZATION_PCT
FROM LEAVE_BALANCES lb
JOIN EMPLOYEES e ON lb.EMP_ID = e.EMP_ID
JOIN DEPARTMENTS d ON e.DEPT_ID = d.DEPT_ID
JOIN LEAVE_TYPES lt ON lb.LEAVE_TYPE_ID = lt.LEAVE_TYPE_ID
WHERE lb.CALENDAR_YEAR = EXTRACT(YEAR FROM LOCALTIMESTAMP(0))
AND e.EMPLOYMENT_STATUS = 'ACTIVE';

-- -----------------------------------------------------------------------
-- VW_PAYROLL_LATEST
-- Latest payroll run details per employee
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_PAYROLL_LATEST AS
SELECT pd.EMP_ID, e.EMP_NUMBER,
       e.FIRST_NAME || ' ' || e.LAST_NAME AS EMP_NAME,
       pp.PERIOD_NAME,
       SUM(CASE WHEN pd.ELEMENT_TYPE = 'EARNING' THEN pd.AMOUNT ELSE 0 END) AS GROSS_PAY,
       SUM(CASE WHEN pd.ELEMENT_TYPE = 'TAX' THEN ABS(pd.AMOUNT) ELSE 0 END) AS TOTAL_TAXES,
       SUM(CASE WHEN pd.ELEMENT_TYPE IN ('DEDUCTION','BENEFIT') THEN ABS(pd.AMOUNT) ELSE 0 END) AS TOTAL_DEDUCTIONS,
       SUM(pd.AMOUNT) AS NET_PAY
FROM PAYROLL_DETAILS pd
JOIN EMPLOYEES e ON pd.EMP_ID = e.EMP_ID
JOIN PAYROLL_RUNS pr ON pd.RUN_ID = pr.RUN_ID
JOIN PAY_PERIODS pp ON pr.PERIOD_ID = pp.PERIOD_ID
WHERE pr.RUN_ID = (
    SELECT MAX(pr2.RUN_ID)
    FROM PAYROLL_RUNS pr2
    WHERE pr2.STATUS = 'APPROVED'
)
AND pd.STATUS != 'ERROR'
GROUP BY pd.EMP_ID, e.EMP_NUMBER,
         e.FIRST_NAME || ' ' || e.LAST_NAME,
         pp.PERIOD_NAME;

-- -----------------------------------------------------------------------
-- VW_PENDING_APPROVALS
-- Unified view of items pending approval across modules
-- -----------------------------------------------------------------------
CREATE OR REPLACE VIEW VW_PENDING_APPROVALS AS
SELECT 'LEAVE' AS APPROVAL_TYPE,
       lr.REQUEST_ID AS ITEM_ID,
       lr.APPROVER_EMP_ID AS APPROVER_ID,
       e.FIRST_NAME || ' ' || e.LAST_NAME AS REQUESTOR_NAME,
       lt.LEAVE_TYPE_NAME AS ITEM_DESCRIPTION,
       lr.CREATED_DATE AS REQUEST_DATE,
       lr.TOTAL_DAYS || ' day(s) ' ||
           TO_CHAR(lr.START_DATE, 'MM/DD') || '-' || TO_CHAR(lr.END_DATE, 'MM/DD') AS DETAILS
FROM LEAVE_REQUESTS lr
JOIN EMPLOYEES e ON lr.EMP_ID = e.EMP_ID
JOIN LEAVE_TYPES lt ON lr.LEAVE_TYPE_ID = lt.LEAVE_TYPE_ID
WHERE lr.STATUS = 'PENDING'
UNION ALL
SELECT 'PERFORMANCE' AS APPROVAL_TYPE,
       pr.REVIEW_ID AS ITEM_ID,
       pr.REVIEWER_EMP_ID AS APPROVER_ID,
       e.FIRST_NAME || ' ' || e.LAST_NAME AS REQUESTOR_NAME,
       'Performance Review - ' || rc.CYCLE_NAME AS ITEM_DESCRIPTION,
       pr.CREATED_DATE AS REQUEST_DATE,
       pr.STATUS AS DETAILS
FROM PERFORMANCE_REVIEWS pr
JOIN EMPLOYEES e ON pr.EMP_ID = e.EMP_ID
JOIN REVIEW_CYCLES rc ON pr.CYCLE_ID = rc.CYCLE_ID
WHERE pr.STATUS = 'MANAGER_REVIEW';
