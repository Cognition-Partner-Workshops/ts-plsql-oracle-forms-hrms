# Oracle Forms Legacy HR System

A representative Oracle Forms 11g/12c legacy Human Resources Management application, designed for modernization analysis and migration workshops. This codebase models a typical enterprise Oracle Forms application with PL/SQL business logic, database schemas, form definitions, and sample data.

## Application Overview

The **HR Management System** (HRMS) is a multi-module Oracle Forms application used by enterprise HR departments to manage:

- **Employee Records** — hire, transfer, terminate, personal details, job history
- **Department & Organization** — department hierarchy, cost centers, reporting lines
- **Payroll Processing** — salary calculations, deductions, tax withholding, pay runs
- **Leave Management** — leave requests, approvals, balance tracking, accrual rules
- **Performance Reviews** — annual review cycles, ratings, goal tracking
- **Reporting** — headcount, compensation analysis, turnover, compliance

The system was originally built in Oracle Forms 6i (circa 2002), upgraded to Forms 11g (2012), and is currently running on Forms 12c with Oracle Database 19c. It serves approximately 200 concurrent users across 3 regional offices.

## Architecture

```
                    +-----------------------+
                    |   Oracle Forms 12c    |
                    |   Application Server  |
                    +-----------+-----------+
                                |
                    +-----------+-----------+
                    |   Oracle WebLogic     |
                    |   12c Server          |
                    +-----------+-----------+
                                |
          +---------------------+---------------------+
          |                     |                      |
+---------+--------+  +---------+--------+  +----------+-------+
| Forms Modules    |  | PL/SQL Packages  |  | Oracle Reports   |
| (.fmb/.fmx)     |  | & Procedures     |  | (.rdf/.rep)      |
| 18 forms         |  | 12 packages      |  | 8 reports        |
+------------------+  +------------------+  +------------------+
          |                     |                      |
          +---------------------+---------------------+
                                |
                    +-----------+-----------+
                    |   Oracle Database     |
                    |   19c (HRMS schema)   |
                    |   42 tables           |
                    |   15 views            |
                    |   200+ triggers       |
                    +-----------------------+
```

## Directory Structure

```
ts-plsql-oracle-forms-legacy-codebase/
|-- forms/
|   |-- xml-exports/          # XML exports of Oracle Forms modules (.fmb -> .xml)
|       |-- HRMS_EMPLOYEE.xml          # Employee maintenance form
|       |-- HRMS_DEPARTMENT.xml        # Department management form
|       |-- HRMS_PAYROLL.xml           # Payroll processing form
|       |-- HRMS_LEAVE.xml             # Leave request and approval form
|       |-- HRMS_PERFORMANCE.xml       # Performance review form
|       |-- HRMS_LOGIN.xml             # Login and authentication form
|       |-- HRMS_MENU.xml              # Main menu navigation form
|       |-- HRMS_REPORTS.xml           # Report parameter and launcher form
|       |-- HRMS_LOV.xml               # Shared List of Values library
|       |-- HRMS_TOOLBAR.xml           # Shared toolbar object library
|-- plsql/
|   |-- packages/             # PL/SQL package specifications and bodies
|   |   |-- PKG_EMPLOYEE.pks / .pkb
|   |   |-- PKG_DEPARTMENT.pks / .pkb
|   |   |-- PKG_PAYROLL.pks / .pkb
|   |   |-- PKG_LEAVE.pks / .pkb
|   |   |-- PKG_PERFORMANCE.pks / .pkb
|   |   |-- PKG_SECURITY.pks / .pkb
|   |   |-- PKG_AUDIT.pks / .pkb
|   |   |-- PKG_NOTIFICATION.pks / .pkb
|   |   |-- PKG_REPORTING.pks / .pkb
|   |   |-- PKG_COMMON.pks / .pkb
|   |   |-- PKG_VALIDATION.pks / .pkb
|   |   |-- PKG_INTEGRATION.pks / .pkb
|   |-- procedures/           # Standalone procedures
|   |-- triggers/             # Database triggers
|   |-- functions/            # Standalone functions
|   |-- types/                # User-defined types
|-- schema/
|   |-- tables/               # CREATE TABLE DDL
|   |-- views/                # CREATE VIEW definitions
|   |-- sequences/            # Sequence definitions
|   |-- indexes/              # Index definitions
|   |-- constraints/          # Named constraints
|-- data/                     # Sample/seed data (INSERT scripts)
|-- config/                   # Forms configuration files
|-- docs/                     # Application documentation
```

## Key Technical Characteristics

### Oracle Forms Specifics
- **Form triggers**: `WHEN-NEW-FORM-INSTANCE`, `WHEN-VALIDATE-ITEM`, `WHEN-BUTTON-PRESSED`, `POST-QUERY`, `PRE-INSERT`, `PRE-UPDATE`
- **LOV (List of Values)**: Record groups with dynamic WHERE clauses
- **Canvas/block architecture**: Multiple data blocks per form, master-detail relationships
- **PLL libraries**: Shared PL/SQL libraries attached to all forms
- **Menu modules (.mmb)**: Role-based menu system with security

### PL/SQL Patterns
- Heavy use of `DBMS_OUTPUT`, `UTL_FILE`, `UTL_MAIL` built-in packages
- Cursor-based processing (row-by-row) for batch operations
- Exception handling with custom error codes (-20000 to -20999)
- Dynamic SQL via `EXECUTE IMMEDIATE` in several procedures
- Global package variables for session state management
- Implicit cursors and `%ROWTYPE` / `%TYPE` declarations

### Database Patterns
- Surrogate keys via sequences + `BEFORE INSERT` triggers
- Soft deletes (`ACTIVE_FLAG CHAR(1) DEFAULT 'Y'`)
- Audit columns on every table (`CREATED_BY`, `CREATED_DATE`, `MODIFIED_BY`, `MODIFIED_DATE`)
- History tables (`_HIST` suffix) for change tracking
- Denormalized reporting tables (refreshed nightly by batch jobs)

### Known Technical Debt
- No unit tests — all testing is manual via Forms
- Business logic split between Forms triggers and database packages (no clear boundary)
- Several packages exceed 3,000 lines
- Hard-coded configuration values in package bodies
- `VARCHAR2(4000)` used as catch-all for text fields
- Mixed naming conventions (some `CAMELCASE`, some `UNDERSCORE_CASE`)
- Dead code from decommissioned modules still present
- Circular package dependencies between `PKG_EMPLOYEE` and `PKG_PAYROLL`

## License

MIT
