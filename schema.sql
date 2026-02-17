-- ========================================================================
-- schema.sql - SOURCE OF TRUTH FOR DOMAIN MODELS
-- ========================================================================
-- This SQL file is the single source of truth for all domain models.
-- DO NOT manually edit models.yaml - it is auto-generated from this file.
--
-- To update models:
-- 1. Modify this file (schema.sql)
-- 2. Run: make parse-schema
-- 3. Run: make generate
--
-- Generated files (git-ignored):
-- - models.yaml (intermediate YAML representation)
-- - src/main/java-generated/*.java (Java model classes)
-- ========================================================================

-- appget domain
CREATE TABLE roles (
    name VARCHAR(100) NOT NULL
);

CREATE TABLE employees (
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    role_id VARCHAR(100) NOT NULL,
    country_of_origin VARCHAR(100)
);

CREATE TABLE vendors (
    name VARCHAR(100) NOT NULL,
    location_id INT NOT NULL,
    FOREIGN KEY (location_id) REFERENCES locations(locationId)
);

-- hr domain
CREATE TABLE departments (
    id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    budget DECIMAL(15, 2) NOT NULL
);

CREATE TABLE salaries (
    employee_id VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    years_of_service INT
);

-- finance domain
CREATE TABLE invoices (
    invoice_number VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    issue_date DATE NOT NULL
);

-- geo location
CREATE TABLE locations (
    locationId VARCHAR(50) NOT NULL,
    locationName VARCHAR(100) NOT NULL,
    longitude FLOAT NOT NULL,
    latitude FLOAT NOT NULL
);
