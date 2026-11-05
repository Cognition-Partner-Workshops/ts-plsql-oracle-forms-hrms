-- Runs after the original payroll/leave CSVs have been spooled.
set termout on
alter session set nls_date_format = 'YYYY-MM-DD"T"HH24:MI:SS';
alter session set nls_sort = 'BINARY';
alter session set nls_comp = 'BINARY';
create table harness_results (
  suite varchar2(30) not null,
  scenario varchar2(100) not null,
  result varchar2(4000) not null
);
create or replace package HRMS.harness_capture as
  procedure value(p_suite varchar2, p_scenario varchar2, p_value varchar2);
  procedure flag(p_suite varchar2, p_scenario varchar2, p_value boolean);
  procedure rows(p_suite varchar2, p_scenario varchar2, p_cursor in out sys_refcursor);
  procedure file_rows(p_scenario varchar2, p_directory varchar2, p_filename varchar2);
end;
/
create or replace package body HRMS.harness_capture as
  procedure value(p_suite varchar2, p_scenario varchar2, p_value varchar2) is
  begin
    insert into harness_results values(p_suite, p_scenario, nvl(p_value, '<null>'));
  end;
  procedure flag(p_suite varchar2, p_scenario varchar2, p_value boolean) is
  begin
    value(p_suite, p_scenario, case when p_value then 'true'
      when not p_value then 'false' else '<null>' end);
  end;
  procedure rows(p_suite varchar2, p_scenario varchar2, p_cursor in out sys_refcursor) is
    v_cursor integer;
    v_columns integer;
    v_desc dbms_sql.desc_tab2;
    v_text varchar2(4000);
    v_number number;
    v_date date;
    v_row json_object_t;
    v_count number := 0;
  begin
    v_cursor := dbms_sql.to_cursor_number(p_cursor);
    dbms_sql.describe_columns2(v_cursor, v_columns, v_desc);
    for i in 1..v_columns loop
      if v_desc(i).col_type = 2 then
        dbms_sql.define_column(v_cursor, i, v_number);
      elsif v_desc(i).col_type = 12 then
        dbms_sql.define_column(v_cursor, i, v_date);
      elsif v_desc(i).col_type in (1, 96) then
        dbms_sql.define_column(v_cursor, i, v_text, 4000);
      else
        raise_application_error(-20990, 'Unsupported capture type: ' || v_desc(i).col_type);
      end if;
    end loop;
    while dbms_sql.fetch_rows(v_cursor) > 0 loop
      v_row := json_object_t();
      for i in 1..v_columns loop
        if v_desc(i).col_type = 2 then
          dbms_sql.column_value(v_cursor, i, v_number);
          if v_number is null then v_row.put_null(v_desc(i).col_name);
          else v_row.put(v_desc(i).col_name, v_number); end if;
        elsif v_desc(i).col_type = 12 then
          dbms_sql.column_value(v_cursor, i, v_date);
          if v_date is null then v_row.put_null(v_desc(i).col_name);
          else v_row.put(v_desc(i).col_name, to_char(v_date, 'YYYY-MM-DD"T"HH24:MI:SS')); end if;
        else
          dbms_sql.column_value(v_cursor, i, v_text);
          if v_text is null then v_row.put_null(v_desc(i).col_name);
          else v_row.put(v_desc(i).col_name, v_text); end if;
        end if;
      end loop;
      value(p_suite, p_scenario, v_row.to_string());
      v_count := v_count + 1;
    end loop;
    dbms_sql.close_cursor(v_cursor);
    value(p_suite, p_scenario || '.row_count', to_char(v_count, 'FM9999990'));
  exception when others then
    if dbms_sql.is_open(v_cursor) then dbms_sql.close_cursor(v_cursor); end if;
    raise;
  end;
  procedure file_rows(p_scenario varchar2, p_directory varchar2, p_filename varchar2) is
    v_file utl_file.file_type;
    v_line varchar2(4000);
    v_count number := 0;
  begin
    v_file := utl_file.fopen(p_directory, p_filename, 'R');
    loop
      begin
        utl_file.get_line(v_file, v_line);
      exception when no_data_found then exit;
      end;
      value('integration', p_scenario, v_line);
      v_count := v_count + 1;
    end loop;
    utl_file.fclose(v_file);
    if v_count = 0 then raise_application_error(-20991, 'Empty integration file'); end if;
    value('integration', p_scenario || '.row_count', to_char(v_count, 'FM9999990'));
  exception when others then
    if utl_file.is_open(v_file) then utl_file.fclose(v_file); end if;
    raise;
  end;
end;
/
declare
  c sys_refcursor;
  v_period number;
  v_run number;
begin
  PKG_REPORTING.headcount_report(c, date '2024-01-31');
  harness_capture.rows('reporting', 'headcount', c);
  PKG_REPORTING.headcount_report(c, date '2024-01-31', 10, 'HQ');
  harness_capture.rows('reporting', 'headcount_hr_hq', c);
  PKG_REPORTING.compensation_summary(c);
  harness_capture.rows('reporting', 'compensation', c);
  PKG_REPORTING.compensation_summary(c, 10, 3);
  harness_capture.rows('reporting', 'compensation_hr_grade3', c);
  PKG_REPORTING.turnover_report(c, date '2023-01-01', date '2024-01-31');
  harness_capture.rows('reporting', 'turnover', c);
  PKG_REPORTING.new_hires_report(c, date '2020-01-01', date '2024-01-31');
  harness_capture.rows('reporting', 'new_hires', c);
  PKG_REPORTING.leave_utilization_report(c, 2024);
  harness_capture.rows('reporting', 'leave_utilization', c);
  select period_id into v_period from pay_periods where period_start_date = date '2024-01-01';
  PKG_REPORTING.payroll_summary_report(c, v_period);
  harness_capture.rows('reporting', 'payroll_summary', c);
  PKG_REPORTING.eeo_compliance_report(c, date '2024-01-31');
  harness_capture.rows('reporting', 'eeo', c);
  PKG_REPORTING.refresh_reporting_tables('FIXTURE_REPORT');
  select run_id into v_run from payroll_runs where period_id = v_period;
  PKG_INTEGRATION.generate_gl_journal(v_run, 'FIXTURE_INTEGRATION');
  harness_capture.file_rows('gl_journal', 'GL_FEED_OUT', 'GL_JOURNAL_' || v_run || '_20240131.dat');
  PKG_INTEGRATION.export_benefits_feed(date '2024-01-31', 'FIXTURE_INTEGRATION');
  harness_capture.file_rows('benefits', 'BENEFITS_FEED_OUT', 'BENEFITS_20240131.txt');
  harness_capture.value('integration', 'missing_status', PKG_INTEGRATION.get_integration_status('MISSING_FIXTURE'));
  PKG_INTEGRATION.sync_org_structure('FIXTURE_INTEGRATION');
end;
/
declare
  c sys_refcursor;
  v_session number;
  v_text varchar2(4000);
begin
  harness_capture.value('security', 'hash_fixture', PKG_SECURITY.hash_password('fixture'));
  v_session := PKG_SECURITY.authenticate('DAVID.MARTINEZ@COMPANY.COM', 'fixture', '127.0.0.1');
  harness_capture.flag('security', 'new_session', PKG_SECURITY.is_session_valid(v_session));
  update user_sessions set login_time = sysdate - 30/1440 where session_id = v_session;
  harness_capture.flag('security', 'exactly_30_minutes', PKG_SECURITY.is_session_valid(v_session));
  update user_sessions set login_time = sysdate - 31/1440 where session_id = v_session;
  harness_capture.flag('security', 'after_30_minutes', PKG_SECURITY.is_session_valid(v_session));
  open c for select emp_id, username, session_status, login_time, logout_time from user_sessions;
  harness_capture.rows('security', 'expired_session', c);
  v_session := PKG_SECURITY.authenticate('david.martinez@company.com', 'different_fixture');
  PKG_SECURITY.logout(v_session);
  harness_capture.flag('security', 'logged_out', PKG_SECURITY.is_session_valid(v_session));
  harness_capture.flag('security', 'missing_session', PKG_SECURITY.is_session_valid(-1));
  for e in (select emp_id from employees order by emp_id) loop
    harness_capture.flag('security', 'emp_' || e.emp_id || '.payroll_view', PKG_SECURITY.has_permission(e.emp_id, 'PAYROLL', 'VIEW'));
    harness_capture.flag('security', 'emp_' || e.emp_id || '.payroll_approve', PKG_SECURITY.has_permission(e.emp_id, 'PAYROLL', 'APPROVE'));
    harness_capture.flag('security', 'emp_' || e.emp_id || '.leave_create', PKG_SECURITY.has_permission(e.emp_id, 'LEAVE', 'CREATE'));
  end loop;
  begin
    v_session := PKG_SECURITY.authenticate('absent@company.com', 'fixture');
    raise_application_error(-20992, 'Missing username unexpectedly accepted');
  exception when others then
    if sqlcode != -20301 then raise; end if;
    harness_capture.value('security', 'missing_user_error', to_char(sqlcode));
  end;
  begin
    PKG_SECURITY.change_password(11, 'fixture', 'short');
    raise_application_error(-20992, 'Short password unexpectedly accepted');
  exception when others then
    if sqlcode != -20310 then raise; end if;
    harness_capture.value('security', 'short_password_error', to_char(sqlcode));
  end;
  PKG_SECURITY.change_password(11, 'fixture', 'Fixture123');
  begin
    v_text := PKG_SECURITY.encrypt_ssn('000-00-0000');
    harness_capture.value('security', 'encrypt_synthetic_ssn', v_text);
  exception when others then
    if sqlcode != -28234 then raise; end if;
    harness_capture.value('security', 'encrypt_synthetic_ssn_error', to_char(sqlcode));
  end;
  harness_capture.value('security', 'decrypt_invalid', PKG_SECURITY.decrypt_ssn('00'));
end;
/
declare
  c sys_refcursor;
  v_cycle number;
  v_review number;
  v_goal number;
  v_rating number;
begin
  v_cycle := PKG_PERFORMANCE.create_review_cycle('Fixture 2024', 2024, date '2024-01-01', date '2024-12-31', p_user => 'FIXTURE_PERF');
  PKG_PERFORMANCE.open_review_cycle(v_cycle, 'FIXTURE_PERF');
  v_review := PKG_PERFORMANCE.create_review(v_cycle, 11, 10, 'FIXTURE_PERF');
  PKG_PERFORMANCE.submit_self_assessment(v_review, 'Fixture self assessment', 'FIXTURE_PERF');
  open c for select emp_id, status, dbms_lob.substr(self_assessment, 100) self_assessment from performance_reviews;
  harness_capture.rows('performance', 'self_assessment', c);
  for i in 1..10 loop
    v_rating := case i when 1 then 1 when 2 then 1.49 when 3 then 1.5 when 4 then 2.49
      when 5 then 2.5 when 6 then 3.49 when 7 then 3.5 when 8 then 4.49 when 9 then 4.5 else 5 end;
    PKG_PERFORMANCE.submit_manager_review(v_review, v_rating, 'Fixture manager assessment', p_user => 'FIXTURE_PERF');
    open c for select overall_rating, rating_label, status from performance_reviews where review_id = v_review;
    harness_capture.rows('performance', 'rating_' || to_char(v_rating, 'FM0.00'), c);
  end loop;
  begin
    PKG_PERFORMANCE.submit_manager_review(v_review, 5.01, 'Invalid', p_user => 'FIXTURE_PERF');
    raise_application_error(-20992, 'Invalid rating unexpectedly accepted');
  exception when others then
    if sqlcode != -20403 then raise; end if;
    harness_capture.value('performance', 'invalid_rating_error', to_char(sqlcode));
  end;
  PKG_PERFORMANCE.acknowledge_review(v_review, 'Fixture acknowledgement', 'FIXTURE_PERF');
  v_goal := PKG_PERFORMANCE.add_goal(v_review, 11, 'Fixture goal', p_weight_pct => 100, p_target_date => date '2024-01-31', p_user => 'FIXTURE_PERF');
  PKG_PERFORMANCE.update_goal_progress(v_goal, 50, p_user => 'FIXTURE_PERF');
  open c for select emp_id, goal_title, progress_pct, status from performance_goals;
  harness_capture.rows('performance', 'goal_half', c);
  PKG_PERFORMANCE.update_goal_progress(v_goal, 100, p_user => 'FIXTURE_PERF');
  open c for select emp_id, goal_title, progress_pct, status from performance_goals;
  harness_capture.rows('performance', 'goal_complete', c);
  PKG_PERFORMANCE.get_team_reviews(c, 10, v_cycle);
  harness_capture.rows('performance', 'team_reviews', c);
  c := PKG_PERFORMANCE.get_rating_distribution(v_cycle);
  harness_capture.rows('performance', 'rating_distribution', c);
  PKG_PERFORMANCE.close_review_cycle(v_cycle, 'FIXTURE_PERF');
  open c for select cycle_name, cycle_year, status from review_cycles;
  harness_capture.rows('performance', 'closed_cycle', c);
end;
/
declare
  c sys_refcursor;
begin
  PKG_NOTIFICATION.send_notification(11, p_subject => 'Fixture cancel', p_body => 'Fixture body', p_user => 'FIXTURE_NOTIF');
  PKG_NOTIFICATION.send_notification(11, p_subject => 'Fixture retry', p_body => 'Fixture body', p_user => 'FIXTURE_NOTIF');
  PKG_NOTIFICATION.send_notification(11, p_subject => 'Fixture exhausted', p_body => 'Fixture body', p_user => 'FIXTURE_NOTIF');
  for n in (select notification_id from notification_queue where subject = 'Fixture cancel') loop
    PKG_NOTIFICATION.cancel_notification(n.notification_id, 'FIXTURE_NOTIF');
  end loop;
  update notification_queue set status = 'FAILED', retry_count = 2, error_message = 'fixture failure' where subject = 'Fixture retry';
  update notification_queue set status = 'FAILED', retry_count = 3, error_message = 'fixture failure' where subject = 'Fixture exhausted';
  PKG_NOTIFICATION.retry_failed(3, 'FIXTURE_NOTIF');
  open c for select recipient_emp_id, recipient_email, subject, notification_type, status, priority,
    retry_count, error_message, dbms_lob.substr(body, 200) body from notification_queue where created_by = 'FIXTURE_NOTIF';
  harness_capture.rows('notification', 'retry_and_cancel', c);
  open c for select recipient_emp_id, recipient_email, subject, status from notification_queue where created_by = 'FIXTURE_PERF';
  harness_capture.rows('notification', 'performance_notifications', c);
end;
/
declare
  c sys_refcursor;
  v_emp number;
begin
  PKG_EMPLOYEE.transfer_employee(11, 20, p_new_job_id => 50, p_effective_date => date '2024-01-31', p_user => 'FIXTURE_EMP');
  open c for select emp_id, dept_id, job_id, location_code, employment_status, modified_by, modified_date from employees where emp_id = 11;
  harness_capture.rows('employee', 'transferred', c);
  insert into leave_requests(request_id, emp_id, leave_type_id, start_date, end_date, total_days, status, created_by)
    values(SEQ_LEAVE_REQUEST.nextval, 11, 1, date '2024-02-01', date '2024-02-02', 2, 'PENDING', 'FIXTURE_EMP');
  PKG_EMPLOYEE.terminate_employee(11, date '2024-01-31', 'VOLUNTARY', p_user => 'FIXTURE_EMP');
  open c for select emp_id, employment_status, active_flag, termination_date, termination_reason from employees where emp_id = 11;
  harness_capture.rows('employee', 'terminated', c);
  open c for select emp_id, base_salary, active_flag, end_date from salary_records where emp_id = 11;
  harness_capture.rows('employee', 'terminated_salary', c);
  open c for select emp_id, element_id, active_flag, end_date from employee_pay_elements where emp_id = 11;
  harness_capture.rows('employee', 'terminated_pay_elements', c);
  open c for select emp_id, status, cancel_reason, cancelled_date from leave_requests where emp_id = 11;
  harness_capture.rows('employee', 'terminated_leave', c);
  begin
    update employees set employment_status = 'ACTIVE' where emp_id = 11;
    raise_application_error(-20992, 'Direct reactivation unexpectedly accepted');
  exception when others then
    if sqlcode != -20503 then raise; end if;
    harness_capture.value('employee', 'reactivation_error', to_char(sqlcode));
  end;
  open c for select emp_id, change_type, effective_date, old_dept_id, new_dept_id, old_job_id, new_job_id,
    reason_code, comments, created_by from employee_history where emp_id = 11;
  harness_capture.rows('employee', 'history', c);
  v_emp := PKG_EMPLOYEE.create_employee(' fixture ', ' hire ', date '2024-01-31', 10, 53, 10,
    p_base_salary => 60000, p_email => ' fixture.hire@example.invalid ', p_user => 'FIXTURE_EMP');
  commit;
  open c for select emp_number, first_name, last_name, email, hire_date, dept_id, job_id, location_code,
    employment_status, active_flag from employees where emp_id = v_emp;
  harness_capture.rows('employee', 'hired', c);
  open c for select emp_id, base_salary, effective_date, active_flag from salary_records where emp_id = v_emp;
  harness_capture.rows('employee', 'hired_salary', c);
  open c for select change_type, effective_date from employee_history where emp_id = v_emp;
  harness_capture.rows('employee', 'hired_history', c);
  open c for select recipient_emp_id, recipient_email, subject, status from notification_queue where created_by = 'FIXTURE_EMP';
  harness_capture.rows('notification', 'employee_notifications', c);
end;
/
declare
  c sys_refcursor;
begin
  harness_capture.value('common', 'weekdays_inclusive', PKG_COMMON.business_days_between(date '2024-01-26', date '2024-01-29'));
  harness_capture.value('common', 'add_business_days', to_char(PKG_COMMON.add_business_days(date '2024-01-26', 1), 'YYYY-MM-DD'));
  harness_capture.value('common', 'fy_september', PKG_COMMON.get_fiscal_year(date '2024-09-30'));
  harness_capture.value('common', 'fy_october', PKG_COMMON.get_fiscal_year(date '2024-10-01'));
  harness_capture.value('common', 'quarter_january', PKG_COMMON.get_fiscal_quarter(date '2024-01-31'));
  harness_capture.value('common', 'phone', PKG_COMMON.format_phone('212-555-1000'));
  harness_capture.value('common', 'masked_synthetic_ssn', PKG_COMMON.format_ssn_masked('000000000'));
  harness_capture.value('common', 'name', PKG_COMMON.format_name('Fixture', 'Employee', 'LF'));
  harness_capture.value('common', 'missing_param', PKG_COMMON.get_param('FIXTURE', 'MISSING'));
  harness_capture.flag('validation', 'same_day_range', PKG_VALIDATION.validate_date_range(date '2024-01-31', date '2024-01-31'));
  harness_capture.flag('validation', 'reversed_range', PKG_VALIDATION.validate_date_range(date '2024-02-01', date '2024-01-31'));
  harness_capture.flag('validation', 'null_range', PKG_VALIDATION.validate_date_range(null, date '2024-01-31'));
  harness_capture.flag('validation', 'email_valid', PKG_VALIDATION.validate_email_format('fixture@example.invalid'));
  harness_capture.flag('validation', 'email_invalid', PKG_VALIDATION.validate_email_format('fixture'));
  harness_capture.flag('validation', 'phone_valid', PKG_VALIDATION.validate_phone_format('212-555-1000'));
  harness_capture.flag('validation', 'emp_number_valid', PKG_VALIDATION.validate_emp_number_format('EMP-000011'));
  harness_capture.flag('validation', 'emp_number_invalid', PKG_VALIDATION.validate_emp_number_format('EMP-11'));
  harness_capture.flag('validation', 'today_not_future', PKG_VALIDATION.is_future_date(date '2024-01-31'));
  harness_capture.flag('validation', 'tomorrow_future', PKG_VALIDATION.is_future_date(date '2024-02-01'));
  harness_capture.flag('validation', 'weekend', PKG_VALIDATION.is_business_day(date '2024-01-27', 'HQ'));
  harness_capture.flag('validation', 'weekday', PKG_VALIDATION.is_business_day(date '2024-01-31', 'HQ'));
  harness_capture.value('validation', 'salary_required', PKG_VALIDATION.validate_salary_for_grade(null, 3));
  harness_capture.value('validation', 'invalid_grade', PKG_VALIDATION.validate_salary_for_grade(60000, -1));
  for g in (select grade_id, min_salary, max_salary from job_grades order by grade_id) loop
    harness_capture.value('validation', 'grade_' || g.grade_id || '.below', PKG_VALIDATION.validate_salary_for_grade(g.min_salary - 1, g.grade_id));
    harness_capture.value('validation', 'grade_' || g.grade_id || '.min', PKG_VALIDATION.validate_salary_for_grade(g.min_salary, g.grade_id));
    harness_capture.value('validation', 'grade_' || g.grade_id || '.max', PKG_VALIDATION.validate_salary_for_grade(g.max_salary, g.grade_id));
    harness_capture.value('validation', 'grade_' || g.grade_id || '.above', PKG_VALIDATION.validate_salary_for_grade(g.max_salary + 1, g.grade_id));
  end loop;
  harness_capture.value('validation', 'required_existing', PKG_VALIDATION.validate_required_fields('EMPLOYEES', 11));
  harness_capture.value('validation', 'required_missing', PKG_VALIDATION.validate_required_fields('EMPLOYEES', -1));
  PKG_AUDIT.log_action('FIXTURE_RECORD', 11, 'UPDATE', 'FIXTURE_AUDIT', 'before', 'after');
  open c for select table_name, record_id, action_type, dbms_lob.substr(old_values, 200) old_values,
    dbms_lob.substr(new_values, 200) new_values, changed_by, changed_date from audit_log where table_name = 'FIXTURE_RECORD';
  harness_capture.rows('audit', 'explicit_action', c);
  open c for select table_name, action_type, changed_by, changed_date from audit_log
    where (table_name in ('EMPLOYEES', 'REVIEW_CYCLES') and changed_by like 'FIXTURE%')
      or table_name in ('USER_SESSIONS', 'USER_CREDENTIALS');
  harness_capture.rows('audit', 'business_actions', c);
  open c for select table_name, dbms_lob.substr(new_values, 1000) new_values, changed_by from audit_log
    where changed_by in ('FIXTURE_INTEGRATION', 'FIXTURE_REPORT');
  harness_capture.rows('integration', 'integration_logs', c);
end;
/
set termout off
spool /tmp/common.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'common' order by scenario, result;
spool off
spool /tmp/validation.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'validation' order by scenario, result;
spool off
spool /tmp/employee.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'employee' order by scenario, result;
spool off
spool /tmp/security.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'security' order by scenario, result;
spool off
spool /tmp/performance.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'performance' order by scenario, result;
spool off
spool /tmp/notification.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'notification' order by scenario, result;
spool off
spool /tmp/integration.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'integration' order by scenario, result;
spool off
spool /tmp/reporting.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'reporting' order by scenario, result;
spool off
spool /tmp/audit.csv
prompt scenario,result
select '"' || scenario || '","' || replace(result, '"', '""') || '"' from harness_results where suite = 'audit' order by scenario, result;
spool off
