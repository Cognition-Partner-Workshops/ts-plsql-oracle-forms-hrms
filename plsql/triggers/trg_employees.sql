-- ============================================================================
-- Database Triggers for EMPLOYEES table
-- These triggers enforce business rules at the database level,
-- duplicating logic that also exists in PKG_EMPLOYEE and Forms triggers.
-- This is a common anti-pattern in legacy Oracle Forms applications.
-- ============================================================================

-- -----------------------------------------------------------------------
-- TRG_EMP_BEFORE_INSERT
-- Sets audit columns and validates required fields before insert
-- -----------------------------------------------------------------------
CREATE OR REPLACE TRIGGER HRMS.TRG_EMP_BEFORE_INSERT
BEFORE INSERT ON HRMS.EMPLOYEES
FOR EACH ROW
BEGIN
    -- Set audit columns
    IF :NEW.CREATED_BY IS NULL THEN
        :NEW.CREATED_BY := USER;
    END IF;
    IF :NEW.CREATED_DATE IS NULL THEN
        :NEW.CREATED_DATE := SYSDATE;
    END IF;

    -- Default active flag
    IF :NEW.ACTIVE_FLAG IS NULL THEN
        :NEW.ACTIVE_FLAG := 'Y';
    END IF;

    -- Default employment status
    IF :NEW.EMPLOYMENT_STATUS IS NULL THEN
        :NEW.EMPLOYMENT_STATUS := 'ACTIVE';
    END IF;

    -- Validate hire date not too far in the future
    IF :NEW.HIRE_DATE > SYSDATE + 180 THEN
        RAISE_APPLICATION_ERROR(-20501,
            'Hire date cannot be more than 180 days in the future');
    END IF;

    -- Validate email uniqueness (also enforced by unique constraint, but
    -- this trigger provides a better error message)
    DECLARE
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
        FROM EMPLOYEES
        WHERE UPPER(EMAIL) = UPPER(:NEW.EMAIL)
        AND ACTIVE_FLAG = 'Y';

        IF v_count > 0 THEN
            RAISE_APPLICATION_ERROR(-20502,
                'Email address already in use: ' || :NEW.EMAIL);
        END IF;
    END;
END TRG_EMP_BEFORE_INSERT;
/

-- -----------------------------------------------------------------------
-- TRG_EMP_BEFORE_UPDATE
-- Sets modification audit columns and validates state transitions
-- -----------------------------------------------------------------------
CREATE OR REPLACE TRIGGER HRMS.TRG_EMP_BEFORE_UPDATE
BEFORE UPDATE ON HRMS.EMPLOYEES
FOR EACH ROW
BEGIN
    :NEW.MODIFIED_BY := NVL(:NEW.MODIFIED_BY, USER);
    :NEW.MODIFIED_DATE := SYSDATE;

    -- Prevent reactivation of terminated employees via direct UPDATE
    -- (should go through PKG_EMPLOYEE.rehire_employee instead)
    IF :OLD.EMPLOYMENT_STATUS = 'TERMINATED' AND :NEW.EMPLOYMENT_STATUS = 'ACTIVE' THEN
        RAISE_APPLICATION_ERROR(-20503,
            'Cannot directly reactivate a terminated employee. Use the rehire process.');
    END IF;

    -- Log status changes to history
    IF :OLD.EMPLOYMENT_STATUS != :NEW.EMPLOYMENT_STATUS THEN
        INSERT INTO EMPLOYEE_HISTORY (
            HIST_ID, EMP_ID, CHANGE_TYPE, EFFECTIVE_DATE,
            COMMENTS, CREATED_BY, REASON_CODE
        ) VALUES (
            SEQ_EMP_HISTORY.NEXTVAL, :NEW.EMP_ID, 'STATUS_CHANGE', SYSDATE,
            :OLD.EMPLOYMENT_STATUS || ' -> ' || :NEW.EMPLOYMENT_STATUS,
            NVL(:NEW.MODIFIED_BY, USER), 'STATUS_UPDATE'
        );
    END IF;

    -- Log department transfers
    IF NVL(:OLD.DEPT_ID, -1) != NVL(:NEW.DEPT_ID, -1) THEN
        INSERT INTO EMPLOYEE_HISTORY (
            HIST_ID, EMP_ID, CHANGE_TYPE, EFFECTIVE_DATE,
            OLD_DEPT_ID, NEW_DEPT_ID, CREATED_BY, COMMENTS
        ) VALUES (
            SEQ_EMP_HISTORY.NEXTVAL, :NEW.EMP_ID, 'TRANSFER', SYSDATE,
            :OLD.DEPT_ID, :NEW.DEPT_ID,
            NVL(:NEW.MODIFIED_BY, USER), 'Department transfer'
        );
    END IF;

    -- Log job changes
    IF NVL(:OLD.JOB_ID, -1) != NVL(:NEW.JOB_ID, -1) THEN
        INSERT INTO EMPLOYEE_HISTORY (
            HIST_ID, EMP_ID, CHANGE_TYPE, EFFECTIVE_DATE,
            OLD_JOB_ID, NEW_JOB_ID, CREATED_BY, REASON_CODE, COMMENTS
        ) VALUES (
            SEQ_EMP_HISTORY.NEXTVAL, :NEW.EMP_ID, 'STATUS_CHANGE', SYSDATE,
            :OLD.JOB_ID, :NEW.JOB_ID,
            NVL(:NEW.MODIFIED_BY, USER), 'JOB_CHANGE', 'Job title change'
        );
    END IF;
END TRG_EMP_BEFORE_UPDATE;
/

-- -----------------------------------------------------------------------
-- TRG_EMP_AFTER_DELETE
-- Soft delete: instead of actual deletion, marks record as inactive
-- NOTE: This trigger converts DELETE into an UPDATE, which is confusing
-- and a known maintenance issue
-- -----------------------------------------------------------------------
CREATE OR REPLACE TRIGGER HRMS.TRG_EMP_INSTEAD_OF_DELETE
BEFORE DELETE ON HRMS.EMPLOYEES
FOR EACH ROW
BEGIN
    -- Convert delete to soft delete
    -- BUG: This actually prevents deletion, but Forms expects DELETE to succeed.
    -- Workaround in Forms: set ACTIVE_FLAG = 'N' then CLEAR_RECORD instead of DELETE_RECORD.
    RAISE_APPLICATION_ERROR(-20504,
        'Direct deletion not allowed. Use termination process or set ACTIVE_FLAG to N.');
END TRG_EMP_INSTEAD_OF_DELETE;
/
