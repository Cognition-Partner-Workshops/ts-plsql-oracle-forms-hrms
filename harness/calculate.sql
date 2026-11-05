set define off echo off feedback off serveroutput off
whenever sqlerror exit failure rollback
alter session set current_schema = HRMS;
alter session set nls_numeric_characters = '.,';
alter session set nls_date_language = 'AMERICAN';
declare
  v_run_id number;
  v_period_id number;
  errors number;
  employee_total number;
  calculated_total number;
begin
  PKG_PAYROLL.create_pay_periods(2024, 'MONTHLY', 'FIXTURE');
  select period_id into v_period_id from pay_periods
    where period_start_date = date '2024-01-01';
  v_run_id := PKG_PAYROLL.create_payroll_run(v_period_id, 'REGULAR', 'FIXTURE');
  PKG_PAYROLL.calculate_payroll(v_run_id, 'FIXTURE');
  select error_count into errors from payroll_runs where run_id = v_run_id;
  if errors > 0 then raise_application_error(-20998, 'Payroll employee errors'); end if;
  select count(*) into employee_total from employees
    where employment_status = 'ACTIVE' and active_flag = 'Y';
  select count(distinct emp_id) into calculated_total from payroll_details where run_id = v_run_id;
  if employee_total = 0 or calculated_total != employee_total then
    raise_application_error(-20997, 'Payroll must cover every seeded active employee');
  end if;
  PKG_LEAVE.run_monthly_accrual(date '2024-01-31', 'FIXTURE');
  select count(distinct emp_id) into calculated_total from leave_balances where calendar_year = 2024;
  if calculated_total != employee_total then
    raise_application_error(-20996, 'Leave must cover every seeded active employee');
  end if;
end;
/
set heading off pagesize 0 linesize 32767 trimspool on termout off
spool /tmp/payroll.csv
prompt employee_id,element_id,element_type,amount,status
select emp_id || ',' || element_id || ',' || element_type || ',' ||
       to_char(amount, 'FM999999990.00') || ',' || status
  from payroll_details order by emp_id, element_id, detail_id;
spool off
spool /tmp/leave.csv
prompt employee_id,leave_type_id,calendar_year,opening_balance,accrued,used,adjustment
select emp_id || ',' || leave_type_id || ',' || calendar_year || ',' ||
       to_char(opening_balance, 'FM999990.00') || ',' ||
       to_char(accrued, 'FM999990.00') || ',' ||
       to_char(used, 'FM999990.00') || ',' ||
       to_char(adjustment, 'FM999990.00')
  from leave_balances order by emp_id, leave_type_id, calendar_year;
spool off
exit
