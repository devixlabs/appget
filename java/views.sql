-- ========================================================================
-- views.sql - SQL VIEWS FOR COMPOSITE MODELS
-- ========================================================================
-- Views combine data from multiple tables into composite read models.
-- These are parsed by SQLSchemaParser alongside schema.sql to generate
-- view model classes in the view/ subpackage.
--
-- To update view models:
-- 1. Modify this file (views.sql)
-- 2. Run: make parse-schema
-- 3. Run: make generate
-- ========================================================================

-- appget domain: Employee with Salary details
CREATE VIEW employee_salary_view AS
SELECT
    e.name AS employee_name,
    e.age AS employee_age,
    e.role_id AS role_id,
    s.amount AS salary_amount,
    s.years_of_service AS years_of_service
FROM employees e
JOIN salaries s ON e.name = s.employee_id;

-- hr domain: Department with budget summary
CREATE VIEW department_budget_view AS
SELECT
    d.id AS department_id,
    d.name AS department_name,
    d.budget AS department_budget
FROM departments d;
