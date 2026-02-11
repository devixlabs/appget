# appget.dev/java - Code Generation from Database Schema

**Write your database schema in SQL and business rules in Gherkin. Automatically generate Java models, validation logic, and REST API endpoints.**

> Production-ready code generation system: Gherkin-first business rules, schema-first protobuf design with SQL views, descriptor-based rule evaluation, compound business rules (AND/OR), metadata-aware authorization, blocking/informational rule enforcement, and 171 comprehensive unit tests.

---

## What Is This Project?

**appget.dev/java** is a code generator that converts your database schema into a complete Java backend with:

1. **Protobuf Models** - Auto-generated from `schema.sql` via `.proto` files (e.g., Employee, Department, Salary as protobuf messages)
2. **gRPC Service Stubs** - 5 services across 3 domains with full CRUD
3. **REST API Server** - Spring Boot server with CRUD endpoints for all your models
4. **Business Rules** - Define rules in Gherkin `.feature` files, embedded in `.proto` as custom options, enforced automatically
5. **Authorization** - Rules can check headers/metadata to enforce access control
6. **Type Safety** - Everything generated from same schema source = perfect type alignment

**Think of it like**: Gherkin rules + SQL schema → Protocol Buffers → gRPC + REST + Business Rules, all from human-friendly sources of truth.

### The Complete Pipeline

```
YOU WRITE THIS:                SYSTEM GENERATES THIS:
┌──────────────────────┐
│  features/*.feature  │       ┌──────────────────────────────┐
│  (Business rules)    │──┐    │  specs.yaml (intermediate)   │
└──────────────────────┘  ├──▶ │  (YAML rules + metadata)     │
┌──────────────────────┐  │    └──────────────────────────────┘
│  metadata.yaml       │──┘            │
│  (Context POJOs)     │               │
└──────────────────────┘               │
                                       ▼
┌──────────────────┐         ┌──────────────────────────────┐
│  schema.sql      │         │  Java Domain Models          │
│  (Your DB)       │────────▶│  (Employee, Department, etc) │
└──────────────────┘         └──────────────────────────────┘
                                     │
┌──────────────────┐                 │
│  views.sql       │─────────────────┘
│  (Read models)   │         ┌──────────────────────────────┐
└──────────────────┘────────▶│  REST API Specification      │
                             │  (OpenAPI 3.0)               │
                                     │
                            ┌────────┘
                            ▼
                     ┌──────────────────────────────┐
                     │  Spring Boot REST Server     │
                     │  - Controllers (endpoints)   │
                     │  - Services (rules + logic)  │
                     │  - Repositories (storage)    │
                     └──────────────────────────────┘
```

---

## File Organization: What You Edit vs What's Generated

### YOU EDIT THESE (Source Files - Commit to Git)

| File | Purpose | Edit When |
|------|---------|-----------|
| **features/*.feature** | Business rules in Gherkin (BDD) | Adding or changing validation logic |
| **metadata.yaml** | Context POJO definitions (sso, roles, user) | Adding new authorization categories |
| **schema.sql** | Database table definitions | Adding new models or tables |
| **views.sql** | SQL composite views (read models) | Creating read-optimized views |

### SYSTEM GENERATES THESE (Don't Edit - Git-Ignored)

| File/Directory | What It Is | Generated From |
|---|---|---|
| **specs.yaml** | Intermediate rules + metadata YAML | features/*.feature + metadata.yaml |
| **models.yaml** | Intermediate YAML representation | schema.sql + views.sql |
| **openapi.yaml** | REST API contract specification | models.yaml |
| **src/main/java-generated/** | All Java models, views, specs | models.yaml + specs.yaml |
| **generated-server/** | Complete Spring Boot REST server | models.yaml + specs.yaml |

---

## Getting Started: 5-Minute Quickstart

### The Fastest Path to a Working System

```bash
# 1. Generate everything from existing files (already in repo)
make all
# This runs: clean → generate (features-to-specs + proto + specs) → test → build
# Time: ~5 seconds
# Result: All 171 tests pass ✓

# 2. Run the rule engine demo
make run
# See rules evaluated on sample Employee object
# Output shows which rules passed/failed

# 3. View test results
open build/reports/tests/test/index.html
# Detailed test report in your browser
```

**That's it!** You now have a working system. The models, rules, and specifications are already defined in the repo.

---

## Full Walkthrough: Starting from Scratch

### Step 1: Edit Your Database Schema (`schema.sql`)

This is where you define all your models. Edit `schema.sql`:

```sql
-- Your company's domain (appget is the default)
CREATE TABLE employees (
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    role_id VARCHAR(100) NOT NULL
);

-- HR domain models
CREATE TABLE departments (
    id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    budget DECIMAL(15, 2) NOT NULL
);

-- Finance domain models
CREATE TABLE invoices (
    id VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status VARCHAR(50) NOT NULL
);
```

**Domains auto-map based on table names:**
- `employees` → `dev.appget.model.Employee`
- `departments` → `dev.appget.hr.model.Department`
- `invoices` → `dev.appget.finance.model.Invoice`

### Step 2: Define Read Models (`views.sql` - Optional)

Create composite views for reporting/read-optimization:

```sql
CREATE VIEW employee_salary_view AS
SELECT
    e.name AS employee_name,
    e.age AS employee_age,
    s.amount AS salary_amount
FROM employees e
JOIN salaries s ON e.name = s.employee_id;
```

Views generate Java view classes automatically. System resolves column types from source tables.

### Step 3: Parse Your Schema → Generate Intermediate YAML

```bash
make parse-schema
# INPUT:  schema.sql + views.sql
# OUTPUT: models.yaml (with all fields, types, domains)
```

**What happens**: SQLSchemaParser reads your SQL, parses tables/views, maps types to Java, generates models.yaml.

### Step 4: Generate Protobuf Models and Views

```bash
make generate-proto
# INPUT:  schema.sql + views.sql + specs.yaml (for rule embedding)
# OUTPUT: .proto files → protoc → build/generated/
#         ├── dev/appget/model/Employee.java (protobuf)
#         ├── dev/appget/hr/model/Department.java (protobuf)
#         ├── dev/appget/finance/model/Invoice.java (protobuf)
#         ├── dev/appget/view/EmployeeSalaryView.java (protobuf)
#         └── gRPC service stubs (5 services)
```

All models are protobuf `MessageOrBuilder` classes with Builder pattern.

### Step 5: Define Business Rules (`features/*.feature`)

This is where you write authorization and validation logic using human-friendly Gherkin syntax.

Edit `features/appget.feature`:

```gherkin
@domain:appget
Feature: Appget Domain Business Rules

  @target:Employee @blocking @rule:EmployeeAgeCheck
  Scenario: Employee must be over 18
    When age is greater than 18
    Then status is "APPROVED"
    But otherwise status is "REJECTED"

  @target:Employee @rule:SeniorManagerCheck
  Scenario: Senior manager must be 30+ and a Manager
    When all conditions are met:
      | field   | operator | value   |
      | age     | >=       | 30      |
      | role_id | ==       | Manager |
    Then status is "SENIOR_MANAGER"
    But otherwise status is "NOT_SENIOR_MANAGER"

  @view @target:EmployeeSalaryView @rule:HighEarnerCheck
  Scenario: High earner salary threshold
    When salary_amount is greater than 100000
    Then status is "HIGH_EARNER"
    But otherwise status is "STANDARD_EARNER"

  @target:Employee @blocking @rule:AuthenticatedApproval
  Scenario: Authenticated employee approval with role level
    Given sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    And roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 3     |
    When age is at least 25
    Then status is "APPROVED_WITH_AUTH"
    But otherwise status is "DENIED"
```

**Gherkin Tags**:
- `@domain:appget` - Domain assignment (feature-level)
- `@target:Employee` - Target model/view name
- `@rule:EmployeeAgeCheck` - Rule name
- `@blocking` - Rule causes 422 rejection when unsatisfied
- `@view` - Target is a view (not a model)

**Operator Phrases** (natural language):
- `equals` → `==`, `does not equal` → `!=`
- `is greater than` → `>`, `is less than` → `<`
- `is at least` → `>=`, `is at most` → `<=`

**Rule Flow**:
1. Check `Given ... context requires:` metadata (if present) → if fails, return `else` status
2. Check `When` conditions on model/view fields → return `then` or `else` status

The system converts `.feature` files + `metadata.yaml` into `specs.yaml` automatically during the build.

### Step 6: Generate Specifications + Metadata Classes

```bash
make generate-specs
# INPUT:  specs.yaml + models.yaml
# OUTPUT: src/main/java-generated/
#         ├── dev/appget/specification/generated/ (Specification classes)
#         └── dev/appget/specification/context/ (Metadata POJOs)
```

### Step 7: Build Everything + Run Tests

```bash
make all
# Runs full pipeline:
#   1. clean (remove old builds)
#   2. features-to-specs (.feature + metadata → specs.yaml)
#   3. generate (proto + specs + registry + openapi)
#   4. test (run 171 tests)
#   5. build (compile & package)
#
# Result: ✓ All 171 tests passing
```

### Step 8: Run the Demo Rule Engine

```bash
make run
# Descriptor-driven evaluation: discovers all models with rules
# Shows which rules passed/failed with their status values
#
# Output:
# --- Rule Engine Evaluation (Descriptor-Based) ---
#
# Model: Employee (4 rule(s))
#   Rule: EmployeeAgeCheck              | Result: APPROVED
#   Rule: EmployeeRoleCheck             | Result: REJECTED
#   Rule: SeniorManagerCheck            | Result: NOT_SENIOR_MANAGER
#   Rule: AuthenticatedApproval         | Result: DENIED
#
# Model: Salary (1 rule(s))
#   Rule: SalaryAmountCheck             | Result: STANDARD
```

---

## When to Use What Command

### Development Workflow

```
You edit:              Command:                    Output:
──────────────────────────────────────────────────────────────
features/*.feature ──→ make features-to-specs ──→ specs.yaml
metadata.yaml     ──→ (included in features-to-specs)

schema.sql  ──→  make parse-schema  ──→  models.yaml
views.sql   ──→  (automatic in parse-schema)

(all above) ──→  make generate     ──→  protobuf classes + specs

All updated ──→  make test         ──→  171 tests pass ✓
```

### Quick Reference

| I Want To... | Use This | Then Run |
|---|---|---|
| See if everything works | `make all` | Nothing, tests run |
| After editing .feature files | `make features-to-specs && make generate && make test` | `make run` to see rules |
| After editing schema.sql | `make parse-schema && make generate && make test` | `make run` to see demo |
| Run only tests | `make test` | Repeat edits until pass |
| Generate REST API server | `make generate-server` | Check `generated-server/` |
| Start Spring Boot server | `make run-server` | Hit `http://localhost:8080` |
| Clean everything | `make clean` | Then `make all` |

---

## Generated Components: Where Things End Up

### Java Models (From schema.sql)

**Generated to**: `src/main/java-generated/dev/appget/*/model/`

```java
// Auto-generated via: schema.sql → .proto → protoc
// Protobuf message with Builder pattern
Employee employee = Employee.newBuilder()
    .setName("Alice")
    .setAge(30)
    .setRoleId("Manager")
    .build();
```

### Java Views (From views.sql)

**Generated to**: `src/main/java-generated/dev/appget/*/view/`

```java
// Auto-generated via: views.sql → .proto → protoc
// Protobuf message with Builder pattern
EmployeeSalaryView view = EmployeeSalaryView.newBuilder()
    .setEmployeeName("Alice")
    .setEmployeeAge(30)
    .setSalaryAmount(75000.0)
    .build();
```

### Specification Classes (From specs.yaml)

**Generated to**: `src/main/java-generated/dev/appget/specification/generated/`

```java
// Auto-generated from: rules: [- name: EmployeeAgeCheck ...]
public class EmployeeAgeCheck implements Specification<Employee> {
    @Override
    public boolean isSatisfiedBy(Employee target) {
        return target.getAge() > 18;
    }
}
```

### Spring Boot Server (From models.yaml + specs.yaml)

**Generated to**: `generated-server/dev/appget/server/`

```
generated-server/dev/appget/server/
├── Application.java                 (Spring Boot main class)
├── controller/                      (REST endpoints @RestController)
├── service/                         (Business logic @Service)
├── repository/                      (In-memory storage @Repository)
├── exception/                       (Error handling)
├── dto/                             (Request/response objects)
├── config/                          (MetadataExtractor for headers)
└── application.yaml                 (Spring Boot config)
```

---

## Spring Boot REST API Server

After running `make generate-server`, you get a complete REST API server with pre-compiled business rule enforcement.

### Generated REST Endpoints

```
POST   /employees         - Create new employee (validates rules)
GET    /employees         - List all employees
GET    /employees/{id}    - Get specific employee
PUT    /employees/{id}    - Update employee (validates rules)
DELETE /employees/{id}    - Delete employee

Same for: /departments, /salaries, /invoices, /roles
```

### Start the Server

```bash
make generate-server   # Generate Spring Boot code
make run-server        # Start server on http://localhost:8080
```

### Test an Endpoint

```bash
# Create employee (will validate EmployeeAgeCheck rule)
curl -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice",
    "age": 30,
    "roleId": "Manager"
  }'

# Response:
# HTTP 201 Created
# {
#   "data": { ... },
#   "ruleResults": {
#     "outcomes": [
#       { "ruleName": "EmployeeAgeCheck", "status": "APPROVED" },
#       { "ruleName": "SeniorManagerCheck", "status": "SENIOR_MANAGER" },
#       ...
#     ],
#     "hasFailures": false
#   }
# }
```

### Passing Metadata to Rules (Authorization)

Rules that check metadata (the `requires:` block) extract data from HTTP headers.
Headers follow the convention `X-{Category}-{Field-Name}`:

```bash
curl -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -H "X-Sso-Authenticated: true" \
  -H "X-Sso-Session-Id: session123" \
  -H "X-Roles-Role-Name: Admin" \
  -H "X-Roles-Role-Level: 5" \
  -H "X-User-User-Id: user456" \
  -H "X-User-Clearance-Level: 3" \
  -d '{
    "name": "Bob",
    "age": 25,
    "roleId": "Engineer"
  }'

# MetadataExtractor reads X-{Category}-{Field} headers
# Builds typed context POJOs (SsoContext, RolesContext, UserContext)
# Rules check both metadata AND model fields
```

### Blocking vs Informational Rules

Rules can be marked `@blocking` in `.feature` files. This affects HTTP responses:

- **Blocking rules** (`@blocking` tag): If unsatisfied, the request returns **422 Unprocessable Entity**
- **Informational rules** (default): Always reported in outcomes but never block the request

```gherkin
  # Blocking: causes 422 if employee is under 18
  @target:Employee @blocking @rule:EmployeeAgeCheck
  Scenario: Employee must be over 18
    When age is greater than 18
    Then status is "APPROVED"
    But otherwise status is "REJECTED"

  # Informational: reported but doesn't block
  @target:Employee @rule:EmployeeRoleCheck
  Scenario: Employee must hold Manager role
    When role_id equals "Manager"
    Then status is "APPROVED"
    But otherwise status is "REJECTED"
```

---

## System Architecture in Depth

### How Data Flows Through the System

```
0. You write business rules in Gherkin .feature files
   ↓
   FeatureToSpecsConverter parses features/*.feature + metadata.yaml
   ↓
   Generates specs.yaml (intermediate representation, git-ignored)
   ↓
1. You write SQL schema
   ↓
2. SQLSchemaParser reads schema.sql + views.sql
   - Parses table definitions
   - Resolves column types
   - Maps tables to domains
   - Resolves view column types from source tables
   ↓
3. Generates models.yaml
   - Intermediate representation
   - All models with fields + types
   - All views with fields + types
   ↓
4. SchemaToProtoConverter reads models.yaml + specs.yaml
   - Creates .proto files per domain (with rules as custom options)
   - protoc compiles .proto → Java protobuf classes + gRPC stubs
   - Output in build/generated/
   ↓
5. SpecificationGenerator reads specs.yaml + models.yaml
   - Creates Specification class for each rule
   - Creates metadata POJO classes (SsoContext, RolesContext, etc)
   ↓
6. ProtoOpenAPIGenerator reads .proto files
   - Creates OpenAPI 3.0.0 REST specification (full CRUD, security)
   ↓
7. SpringBootServerGenerator reads models.yaml + specs.yaml
   - Creates Spring Boot REST API
   - Controllers (HTTP endpoints)
   - Services (business logic with rule evaluation)
   - Repositories (storage)
   - RuleService uses pre-compiled spec classes directly (no runtime YAML)
   - MetadataExtractor reads typed headers into context POJOs
   ↓
8. At runtime
   - RuleService evaluates pre-compiled specification classes
   - Blocking rules cause 422 rejection; informational rules are reported only
   - MetadataExtractor builds typed context POJOs from HTTP headers
   - Rules are evaluated when creating/updating entities
```

### Type Safety: SQL → Java → REST

Every field maps consistently through the entire pipeline:

```
SQL Type         Java Type        OpenAPI Type    TypeScript Type
─────────────────────────────────────────────────────────────────
VARCHAR(100)     String           string          string
INT              int (Integer*)   integer         number
DECIMAL(15,2)    BigDecimal       number          number
DATE             LocalDate        string(date)    Date
TIMESTAMP        LocalDateTime    string(date-time) Date
BOOLEAN          boolean          boolean         boolean

* Integer if nullable in SQL, int if NOT NULL
```

---

## Logging

All non-generated Java classes include **Log4j2 logging**:

- **INFO level** (default): File loading, rule evaluation results, important milestones
- **DEBUG level**: Method entry/exit, detailed execution flow (useful for troubleshooting)
- **Configuration**: `src/main/resources/log4j2.properties`

**Adjust logging verbosity**:

```properties
# Development (see everything)
logger.dev_appget_codegen.level = DEBUG

# Production (see only important events)
logger.dev_appget_codegen.level = INFO
```

---

## Testing & Quality Assurance

### Why Testing Matters

Every time you change `schema.sql` or `specs.yaml`, the system regenerates Java code. Tests verify:

- ✓ Models generated correctly from schema
- ✓ Rules evaluated correctly
- ✓ Type mappings are correct
- ✓ Views resolved properly
- ✓ REST endpoint specs are valid

### 171 Comprehensive Tests

The system includes 171 unit tests across 13 test suites:

| Suite | Count | What It Tests |
|-------|-------|---|
| Feature To Specs Converter | 24 | Gherkin parsing, condition extraction, YAML generation |
| Schema To Proto Converter | 20 | Proto generation, field mapping, rule embedding, services |
| Proto-First OpenAPI Generator | 23 | Proto-first OpenAPI, CRUD, security, type mapping |
| Specification Generator | 13 | Rule generation, metadata, views |
| Spring Boot Server Generator | 12 | RuleService bridge, MetadataExtractor, blocking logic |
| Rule Interceptor | 11 | Loading rules from protobuf descriptors |
| gRPC Service Stubs | 7 | Service existence, CRUD method descriptors |
| Rule Engine | 15 | Rule evaluation, metadata, compound logic, type mismatch guard |
| Compound Specifications | 6 | AND/OR logic combinations |
| Metadata Context | 5 | Authorization metadata storage |
| Specification Patterns | 21 | All comparison operators, edge cases |
| Descriptor Registry | 8 | Model discovery, lookup, field descriptors |
| Test Data Builder | 6 | DynamicMessage generation, default values |

### Run Tests

```bash
make test                # Run all 171 tests (expect ~2s)
make all                 # Full pipeline (features → generate → test → build)
make clean && make test  # Fresh run
```

**All tests pass** → Your code is ready to use. **Some fail** → Check error messages, fix the issue, re-run.

### View Test Results

After running tests, detailed HTML report:

```bash
open build/reports/tests/test/index.html
```

Shows:
- Which test suites passed/failed
- Execution time per test
- Full stack traces for failures

### Performance Targets

| Task | Time |
|------|------|
| `make test` | ~2s (171 tests) |
| `make all` | ~5-6s (full pipeline) |
| `make run` | ~1s (demo execution) |
| `make generate-server` | ~1s (server generation) |

## All Make Commands Reference

### Everyday Commands

| Command | When to Use | What It Does |
|---------|---|---|
| `make all` | After editing ANY file | Full pipeline: clean → generate → test → build |
| `make test` | To verify your changes | Run all 171 tests (takes ~2s) |
| `make run` | To see rules in action | Build + execute demo rule engine |

### Code Generation Commands

| Command | When to Use | What It Does |
|---------|---|---|
| `make features-to-specs` | After editing .feature or metadata.yaml | Convert Gherkin → specs.yaml |
| `make parse-schema` | After editing schema.sql or views.sql | Parse SQL → Generate models.yaml |
| `make generate-proto` | After editing schema.sql | Protobuf models → build/generated/ |
| `make generate-specs` | After editing .feature files | Specification classes + metadata |
| `make generate-openapi` | After editing schema.sql | REST API specification → openapi.yaml (proto-first) |
| `make generate` | After editing schema, features, or metadata | All generation (features-to-specs + protoc + specs + openapi) |
| `make generate-server` | When ready for REST API | Spring Boot server → generated-server/ |

### Spring Boot Server Commands

| Command | What It Does |
|---------|---|
| `make generate-server` | Generate complete Spring Boot REST API server |
| `make run-server` | Build + start Spring Boot on port 8080 |

### System Commands

| Command | What It Does |
|---------|---|
| `make clean` | Remove all build artifacts + generated code |
| `make build` | Compile all code + create JAR |
| `make help` | Show all available commands |

### Typical Development Cycle

```bash
# 1. Edit your files
vim schema.sql
vim features/appget.feature

# 2. Regenerate + test
make all
# Output: Runs all steps, all 171 tests should pass

# 3. See rules in action
make run
# Demo shows Employee evaluated against all rules

# 4. When ready for REST API
make generate-server
make run-server
# Server runs on http://localhost:8080

# 5. Test an endpoint
curl -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob","age":25,"roleId":"Engineer"}'
```

## Key Features Explained

### 1. SQL Schema-First

**Your database schema defines everything.** No manual type definitions.

```sql
CREATE TABLE employees (
    name VARCHAR(100) NOT NULL,  -- ✓ Auto-becomes String name
    age INT NOT NULL,             -- ✓ Auto-becomes int age
    role_id VARCHAR(100)          -- ✓ Auto-becomes String roleId (camelCase)
);
```

✓ Multi-dialect SQL support (MySQL, PostgreSQL, SQLite, Oracle, MSSQL)
✓ NOT NULL constraints honored (primitives stay primitive, nullable fields wrapped)
✓ Automatic snake_case → camelCase conversion

### 2. SQL Views (Composite Read Models)

**Define read-optimized views for reporting.** System auto-resolves column types.

```sql
CREATE VIEW high_earner_view AS
SELECT
    e.name AS emp_name,
    s.amount AS salary
FROM employees e
JOIN salaries s ON e.id = s.employee_id;
```

Generated as Java class:
```java
@Data
public class HighEarnerView {
    private String empName;      // ✓ Type resolved from employees.name
    private BigDecimal salary;   // ✓ Type resolved from salaries.amount
}
```

✓ View classes in `view/` subpackage
✓ Views can be targeted by rules (just like models)

### 3. Business Rules with Compound Logic

**Write rules in YAML. System enforces them automatically.**

```yaml
- name: SeniorManagerCheck
  conditions:
    operator: AND                # All must be true
    clauses:
      - field: age >= 30
      - field: role_id == "Manager"
  then:
    status: "SENIOR_MANAGER"
```

✓ Simple conditions (single field checks)
✓ Compound conditions (AND/OR multiple fields)
✓ Rules evaluated automatically on create/update
✓ Rule failures return specific status values

### 4. Metadata-Aware Authorization

**Rules can check HTTP headers for authentication/authorization.**

```yaml
- name: ApproveIfAdmin
  requires:                      # Check these headers first
    roles:
      - field: roleLevel >= 3
  conditions:                    # Then check model fields
    - field: age >= 25
  then:
    status: "APPROVED"
```

✓ Extract metadata from HTTP headers
✓ Rules can require metadata before checking data
✓ Authorization logic defined in YAML, enforced automatically

### 5. Multi-Domain Organization

**Organize models by business domain. System auto-creates packages.**

```
schema.sql:
  employees      → dev.appget.model.Employee
  departments    → dev.appget.hr.model.Department
  invoices       → dev.appget.finance.model.Invoice
```

✓ Logical separation of concerns
✓ Prevents naming conflicts
✓ Each domain gets own package namespace

### 6. Full REST API Generation

**Generate complete Spring Boot REST server from your schema.**

```bash
make generate-server
# Generates:
#   Controllers (REST endpoints)
#   Services (business logic + rule validation)
#   Repositories (in-memory storage)
#   DTOs (request/response objects)
#   Exception handling
```

✓ CRUD endpoints for all models
✓ Rules validated on create/update
✓ Metadata extracted from HTTP headers
✓ Proper HTTP status codes (201 Created, 422 Unprocessable Entity, etc)

### 7. Type Safety Everywhere

**Every type maps consistently from SQL → Java → REST → TypeScript.**

```
SQL                Java              OpenAPI          TypeScript
────────────────────────────────────────────────────────────────
VARCHAR(100)       String            string           string
INT NOT NULL       int               integer          number
DECIMAL(15,2)      BigDecimal        number           number
DATE               LocalDate         string(date)     Date
```

✓ No type mismatches between layers
✓ TypeScript client can be auto-generated from OpenAPI spec
✓ Full end-to-end type safety

### Comprehensive Testing

All 171 tests pass automatically:
```
✓ Gherkin .feature file parsing and specs.yaml generation
✓ Proto generation correctness (schema → .proto → Java)
✓ Descriptor-based rule evaluation
✓ Type mapping validation
✓ View resolution
✓ REST endpoint specs (proto-first OpenAPI)
✓ gRPC service stubs
✓ Descriptor registry and test data builder
```

Run: `make test` to verify your changes at any time.

---

## File Structure: Where Everything Is

### Files YOU Edit (Commit These to Git)

```
appget.dev/java/
├── features/
│   ├── appget.feature      🖊️  Appget domain business rules (Gherkin)
│   └── hr.feature          🖊️  HR domain business rules (Gherkin)
├── metadata.yaml           🖊️  Context POJO definitions (sso, roles, user, location)
├── schema.sql              🖊️  Your database schema (tables)
├── views.sql               🖊️  Your read models (views)
├── build.gradle            🖊️  Java build configuration
├── Makefile                🖊️  Build commands
└── src/main/java/dev/appget/
    ├── codegen/            📝  Code generators (do not modify)
    ├── model/
    │   └── Rule.java       📝  Rule engine (do not modify)
    ├── specification/      📝  Rule evaluation logic (do not modify)
    ├── util/               📝  DescriptorRegistry, TestDataBuilder
    └── RuleEngine.java     📝  Descriptor-driven demo app (do not modify)
```

### Files SYSTEM GENERATES (Don't Edit - Git-Ignored)

```
appget.dev/java/
├── specs.yaml              ✨  Auto-generated from features/*.feature + metadata.yaml
├── models.yaml             ✨  Auto-generated from schema.sql
├── openapi.yaml            ✨  Auto-generated REST spec
├── src/main/java-generated/✨  All generated Java models, specs
│   └── dev/appget/
│       ├── model/          (Employee, Department, Invoice, etc)
│       ├── view/           (EmployeeSalaryView, etc)
│       ├── hr/model/       (Domain-specific models)
│       ├── finance/model/  (Domain-specific models)
│       └── specification/
│           ├── generated/  (Specification classes)
│           └── context/    (Metadata POJOs)
└── generated-server/       ✨  Spring Boot REST API
    └── dev/appget/server/
        ├── controller/     (REST endpoints)
        ├── service/        (Business logic)
        ├── repository/     (Storage)
        ├── dto/            (Request/response)
        └── exception/      (Error handling)
```

### Test Files (Don't Modify)

```
src/test/java/dev/appget/
├── codegen/
│   ├── FeatureToSpecsConverterTest.java     (24 tests)
│   ├── SchemaToProtoConverterTest.java      (20 tests)
│   ├── ProtoOpenAPIGeneratorTest.java       (23 tests)
│   ├── SpecificationGeneratorTest.java      (13 tests)
│   └── SpringBootServerGeneratorTest.java   (12 tests)
├── model/
│   └── RuleTest.java                        (15 tests)
├── service/
│   └── GrpcServiceTest.java                 (7 tests)
├── specification/
│   ├── SpecificationTest.java               (21 tests)
│   ├── CompoundSpecificationTest.java        (6 tests)
│   ├── MetadataContextTest.java              (5 tests)
│   └── RuleInterceptorTest.java             (11 tests)
└── util/
    ├── DescriptorRegistryTest.java           (8 tests)
    └── TestDataBuilderTest.java              (6 tests)
```

---

## Rule Syntax: Comparison Operators

Define rules in `.feature` files using natural language phrases (`is greater than`, `equals`, etc.). The generated `specs.yaml` uses these operators in conditions:

### Numbers (int, long, double, BigDecimal)
```yaml
conditions:
  - field: age
    operator: ">"    # Greater than
    value: 18

  - field: salary
    operator: ">="   # Greater than or equal
    value: 50000

  - field: years
    operator: "<"    # Less than
    value: 5

  - field: bonus
    operator: "<="   # Less than or equal
    value: 10000

  - field: level
    operator: "=="   # Equals
    value: 3

  - field: status
    operator: "!="   # Not equals
    value: "INACTIVE"
```

### Strings
```yaml
conditions:
  - field: name
    operator: "=="   # Equals
    value: "Manager"

  - field: role
    operator: "!="   # Not equals
    value: "Guest"
```

### Booleans
```yaml
conditions:
  - field: active
    operator: "=="   # Must be true
    value: true

  - field: verified
    operator: "!="   # Must be false
    value: false
```

---

## Troubleshooting

### "FileNotFoundException: schema.sql"
**Problem**: System can't find your database schema
**Solution**: Create `schema.sql` at project root with table definitions

### "Tests are failing"
**Problem**: Some of the 171 tests failed
**Solution**:
```bash
# 1. Check the error message
# 2. Look at what file caused it (schema.sql, specs.yaml, etc)
# 3. Fix that file
# 4. Run make all again
```

### "Generated code looks wrong"
**Problem**: Models generated incorrectly
**Solution**:
```bash
# Clean and regenerate everything
make clean
make all
```

### "Can't find class Employee"
**Problem**: Java model not found
**Solution**:
```bash
# Make sure table exists in schema.sql
# Make sure you ran: make parse-schema && make generate
make all
```

### "Rule is not being evaluated"
**Problem**: Business rule not running
**Solution**:
1. Check `features/*.feature` - does scenario exist with correct tags?
2. Check rule `@target` tag - does model exist?
3. Check field names - match your model exactly?
4. Run `make features-to-specs && make generate-specs` to regenerate
5. Run `make run` to see demo

### "View column type is wrong"
**Problem**: View field has wrong Java type
**Solution**:
1. Check source table in `schema.sql` - is column type correct?
2. Check view SQL - is alias correct?
3. Make sure source table defined BEFORE view in SQL file
4. Run `make parse-schema` to re-parse

### Spring Boot server won't start
**Problem**: `make run-server` fails
**Solution**:
```bash
# Make sure server is generated
make generate-server

# Check generated code exists
ls -la generated-server/dev/appget/server/

# If missing, regenerate everything
make clean && make generate-server
```

### "Port 8080 already in use"
**Problem**: Another process using the port
**Solution**:
```bash
# Kill the other process
lsof -i :8080
kill -9 <PID>

# Or use different port
# Edit generated-server/src/main/resources/application.yaml:
# server:
#   port: 8081
```

---

## Dependencies & Environment

### Required

- **Java**: 25+ (OpenJDK 25 or newer)
- **Gradle**: 9.3.1+ (automatic wrapper download)

### Included (via build.gradle)

- **Gherkin**: 38.0.0 (Cucumber Gherkin parser for `.feature` files)
- **Protobuf**: 3.25.3 (protoc compiler + Java runtime)
- **gRPC-Java**: 1.62.2 (gRPC service stubs)
- **Lombok**: 1.18.38+ (metadata POJOs, Java 25 compatible)
- **JSQLParser**: 5.3 (multi-dialect SQL support)
- **SnakeYAML**: 2.2 (YAML parsing)
- **Log4j2**: 2.23.1 (logging)
- **JUnit 5**: 5.11.3 (testing)

### Check Your Setup

```bash
# Check Java version
java -version
# Output should show 25 or higher

# Check Gradle
./gradlew --version
# Should work (auto-installs if needed)

# Run a quick test
make test
# Should show 171 tests passing
```

---

## Additional Documentation

For more details on implementation and architecture:

- **CLAUDE.md** - Technical implementation guide for Claude Code
- **PIPELINE.md** - Detailed pipeline architecture
- **ai.chat** - Original design discussion and concepts

---

## Quick Links

- [Gradle Documentation](https://gradle.org/)
- [Lombok Documentation](https://projectlombok.org/)
- [JSQLParser](https://github.com/JSQLParser/JSqlParser)
- [OpenAPI 3.0.0](https://spec.openapis.org/oas/v3.0.0)
- [Spring Boot](https://spring.io/projects/spring-boot)

---

**Last Updated**: 2026-02-12
**Status**: Production Ready
**Test Coverage**: 171 tests, 100% passing (13 suites)
**Pipeline**: Gherkin → specs.yaml → Schema → Proto → Protoc → Specs → REST API → gRPC → Fully Typed
**Java Version**: 25+ required
