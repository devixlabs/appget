# appget.dev — Code Generation Pipeline

## Overview

appget.dev converts three human-authored files — a database schema, Gherkin business rules, and metadata definitions — into a fully-typed, tested, production-ready application layer. The pipeline is language-agnostic: the same inputs produce equivalent output in Java, Go, Python, Ruby, or any future target.

```
You write:                          The pipeline generates:
──────────────                      ─────────────────────────────────────
schema.sql        ─┐               Domain models (type-safe, versioned)
views.sql         ─┤               View models (derived, read-only)
features/*.feature ┤─→ Pipeline →  Business rule specifications
metadata.yaml     ─┘               REST API server (controllers, services)
                                   OpenAPI contract
                                   Test suite support (descriptor registry)
```

**Design mandate**: Every generator must be portable. All languages have string concatenation. Not all languages have reflection or template engines. The default generation strategy is plain string building; templating is used only where output is purely structural.

---

## User-Defined Inputs

These four file types are the only files a developer writes. Everything else is generated.

### 1. `schema.sql` — Domain Models

SQL `CREATE TABLE` statements. Any dialect (MySQL, PostgreSQL, SQLite, Oracle, MSSQL).

```sql
CREATE TABLE employees (
    id          VARCHAR(36)    NOT NULL,
    name        VARCHAR(255)   NOT NULL,
    age         INT            NOT NULL,
    role_id     VARCHAR(50)    NOT NULL,
    salary      DECIMAL(15,2),          -- nullable → wrapper type
    created_at  TIMESTAMP      NOT NULL
);
```

**Why SQL?** Every language ecosystem has SQL. Schema changes are tracked in git, type constraints are enforceable, and the schema is already the source of truth for any database-backed system.

**What gets extracted**: table name, column names (snake_case), column types, nullability, primary keys.

---

### 2. `views.sql` — Derived Models (Optional)

SQL `CREATE VIEW` statements. Column types are resolved from the source tables — no manual type annotation.

```sql
CREATE VIEW employee_salary_view AS
    SELECT e.name, e.age, s.amount AS salary_amount, COUNT(*) AS dept_count
    FROM employees e
    JOIN salaries s ON e.id = s.employee_id;
```

**What gets extracted**: view name, column names + types (resolved via table aliases), aggregate function types (`COUNT` → int, `SUM` → decimal, `AVG` → float).

---

### 3. `features/*.feature` — Business Rules (Gherkin DSL)

One file per domain. Uses standard Gherkin syntax with a restricted DSL for conditions.

```gherkin
@domain:appget
Feature: Employee Business Rules

  @target:Employee @rule:EmployeeAgeCheck @blocking
  Scenario: Minimum age requirement
    When age is greater than 18
    Then status is "APPROVED"
    But otherwise status is "REJECTED"

  @target:Employee @rule:SeniorManagerCheck
  Scenario: Senior manager classification
    When all conditions are met:
      | field   | operator | value   |
      | age     | >=       | 30      |
      | role_id | ==       | Manager |
    Then status is "SENIOR_MANAGER"
    But otherwise status is "NOT_SENIOR_MANAGER"

  @target:Employee @rule:AuthenticatedApproval @blocking
  Scenario: Authenticated high-level approval
    Given sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 3     |
    When age is at least 25
    Then status is "APPROVED_WITH_AUTH"
    But otherwise status is "DENIED"
```

**Scenario tags:**
| Tag | Meaning |
|-----|---------|
| `@domain:<name>` | Feature-level: all scenarios belong to this domain |
| `@target:<Model>` | Target model or view class name |
| `@rule:<Name>` | Generated class name for this rule |
| `@blocking` | Unsatisfied rule causes hard rejection (422) |
| `@view` | Target is a view, not a model |

**Operator phrases** (natural language → symbol):
| Phrase | Symbol |
|--------|--------|
| equals | `==` |
| does not equal | `!=` |
| is greater than | `>` |
| is less than | `<` |
| is at least | `>=` |
| is at most | `<=` |

**Why Gherkin?** Business analysts and domain experts can read and write rules without knowing the target programming language. Rules are versioned, diffable, and human-auditable. The Gherkin DSL is an industry-standard format with parsers available in every major language.

---

### 4. `metadata.yaml` — Authorization Context POJOs

Defines the typed context objects passed as authorization headers at runtime.

```yaml
metadata:
  sso:
    fields:
      - name: authenticated
        type: boolean
      - name: sessionId
        type: String

  roles:
    fields:
      - name: roleLevel
        type: int
      - name: roleName
        type: String
```

**Why separate from features?** Metadata context definitions rarely change (they represent the authorization infrastructure). Business rules change frequently. Separating them prevents noisy diffs in rule files.

**What gets generated**: One typed POJO per category (e.g., `SsoContext`, `RolesContext`) with fields, getters, and a builder pattern. These are used to carry HTTP header values into rule evaluations.

---

## Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     USER-DEFINED INPUTS                                 │
├─────────────────┬───────────────────┬───────────────────────────────────┤
│  schema.sql     │  features/*.feature│  metadata.yaml                   │
│  views.sql      │  (Gherkin DSL)     │  (auth context definitions)      │
│  (SQL DDL)      │                   │                                   │
└────────┬────────┴────────┬──────────┴────────────────────┬─────────────┘
         │                 │                               │
         │                 ▼                               │
         │    ┌────────────────────────┐                   │
         │    │ Step 1: Rules Parser   │                   │
         │    │ (Gherkin + metadata)   │◄──────────────────┘
         │    └────────────┬───────────┘
         │                 │ specs.yaml
         │                 │ (rules + metadata, intermediate)
         ▼                 │
┌────────────────────┐     │
│ Step 2: Schema     │     │
│ Parser (SQL)       │     │
└────────┬───────────┘     │
         │ models.yaml      │
         │ (models + views, │
         │  intermediate)   │
         │                  │
         └────────┬─────────┘
                  │ (both intermediates used below)
                  │
         ┌────────┴─────────────────────────┐
         │                                  │
         ▼                                  ▼
┌────────────────────┐            ┌──────────────────────┐
│ Step 3: Spec       │            │ Step 4: Model         │
│ Generator          │            │ Compiler              │
│ (specs + models    │            │ (schema → type-safe   │
│  → rule classes)   │            │  model classes)       │
└────────┬───────────┘            └──────────┬───────────┘
         │ Rule Spec classes                  │ Model classes
         │                                   │
         └───────────────┬───────────────────┘
                         │
          ┌──────────────┼──────────────────┐
          │              │                  │
          ▼              ▼                  ▼
┌─────────────────┐ ┌─────────────┐ ┌─────────────────────┐
│ Step 5:         │ │ Step 6:     │ │ Step 7:             │
│ REST API Server │ │ API Contract│ │ Registry Generator  │
│ Generator       │ │ Generator   │ │ (descriptor lookup) │
│ (controllers,   │ │ (OpenAPI    │ │                     │
│  services,      │ │  3.0 spec)  │ │                     │
│  repositories)  │ │             │ │                     │
└────────┬────────┘ └──────┬──────┘ └──────────┬──────────┘
         │                 │                   │
         ▼                 ▼                   ▼
┌────────────────────────────────────────────────────────┐
│                   GENERATED OUTPUTS                     │
├────────────────────────────────────────────────────────┤
│  generated-server/  (runnable REST API server)         │
│  openapi.yaml       (REST contract)                    │
│  DescriptorRegistry (runtime model discovery)          │
│  Rule Spec classes  (business logic, compiled)         │
│  Domain models      (type-safe, from schema)           │
└────────────────────────────────────────────────────────┘
```

---

## Step-by-Step Pipeline

### Step 1 — Rules Parser (Gherkin + Metadata → `specs.yaml`)

**Input**: `features/*.feature`, `metadata.yaml`
**Output**: `specs.yaml` (intermediate, not committed)

**What it does**:
1. Parse each `.feature` file with a Gherkin parser
2. For each scenario: extract tags, parse step text with regex, coerce value types
3. Map operator phrases ("is greater than") to symbols (">")
4. Assemble metadata section (from `metadata.yaml`) + rules section (from scenarios)
5. Write `specs.yaml`

**Output format**:
```yaml
metadata:
  sso:
    fields: [{ name: authenticated, type: boolean }, ...]

rules:
  - name: EmployeeAgeCheck
    target: { type: model, name: Employee, domain: appget }
    blocking: true
    conditions:
      - { field: age, operator: ">", value: 18 }
    then: { status: "APPROVED" }
    else: { status: "REJECTED" }

  - name: SeniorManagerCheck
    target: { type: model, name: Employee, domain: appget }
    conditions:
      operator: AND
      clauses:
        - { field: age, operator: ">=", value: 30 }
        - { field: role_id, operator: "==", value: "Manager" }
    then: { status: "SENIOR_MANAGER" }
    else: { status: "NOT_SENIOR_MANAGER" }
```

**Why this intermediate?** Downstream generators (spec generator, server generator) need structured rule data. The YAML intermediate decouples Gherkin parsing from code generation — each generator can be developed and tested independently.

**Key design decisions**:
- Value coercion at parse time (not at evaluation time): `"18"` → integer `18`, `"true"` → boolean
- Compound conditions use a discriminated union shape: a list signals simple conditions; an object with `operator` + `clauses` signals compound
- `requires:` block (metadata checks) is separate from `conditions:` (model field checks)

---

### Step 2 — Schema Parser (SQL → `models.yaml`)

**Input**: `schema.sql`, `views.sql`
**Output**: `models.yaml` (intermediate, not committed)

**What it does**:
1. Read SQL DDL, extract `CREATE TABLE` blocks (handle nested parentheses for types like `DECIMAL(15,2)`)
2. For each column: parse name, SQL type, nullability, skip constraint lines
3. Map SQL types to language-neutral types (see type table below)
4. Assign tables to domains via a mapping table
5. For views: resolve column types from source tables via alias expansion; handle aggregate functions
6. Write `models.yaml`

**SQL → Neutral Type Mapping**:

| SQL Types | Neutral Type | Notes |
|-----------|--------------|-------|
| VARCHAR, CHAR, TEXT | string | Always nullable in most languages |
| INT, INTEGER, SMALLINT | int32 | Primitive unless nullable |
| BIGINT, LONG | int64 | 64-bit |
| DECIMAL, NUMERIC | decimal | Exact precision arithmetic |
| FLOAT, DOUBLE, REAL | float64 | |
| DATE | date | Date without time |
| TIMESTAMP, DATETIME | datetime | Date + time |
| BOOLEAN, BOOL | bool | |

**Aggregate function types** (views):

| SQL Function | Type |
|--------------|------|
| `COUNT(*)` | int64 |
| `SUM(col)` | decimal |
| `AVG(col)` | float64 |
| `MIN(col)` / `MAX(col)` | same as source column |

**Why this intermediate?** `models.yaml` is consumed by multiple generators (spec generator, descriptor registry generator, server generator). A single parse of SQL avoids each generator re-implementing SQL parsing independently — a known source of bugs and divergence.

**Single-parse design**: All SQL parsing is consolidated in `SQLSchemaParser`, which emits `models.yaml`. All other generators (`ModelsToProtoConverter`, `SpecificationGenerator`, `DescriptorRegistryGenerator`, `SpringBootServerGenerator`) consume only `models.yaml` — they never re-parse SQL directly.

---

### Step 3 — Spec Generator (`specs.yaml` + `models.yaml` → Rule Classes)

**Input**: `specs.yaml`, `models.yaml`
**Output**: Generated specification classes (one per rule), metadata context POJOs

**What it does**:
1. Parse `specs.yaml` metadata section → generate one typed POJO per context category (SsoContext, RolesContext, etc.)
2. For each rule in `specs.yaml`:
   - Resolve target type using `models.yaml` → get fully-qualified import path
   - Determine condition shape: simple (list) or compound (AND/OR object)
   - Generate a spec class that encapsulates the condition and outcomes

**Generated class shapes**:

*Simple rule*:
```
class EmployeeAgeCheck:
    spec = Specification("age", ">", 18)

    evaluate(target: Employee) → bool
    getResult(target: Employee) → "APPROVED" | "REJECTED"
```

*Compound rule (AND)*:
```
class SeniorManagerCheck:
    spec = CompoundSpecification(AND, [
        Specification("age", ">=", 30),
        Specification("role_id", "==", "Manager")
    ])
```

*Metadata-aware rule*:
```
class AuthenticatedApproval:
    spec = Specification("age", ">=", 25)
    metaSpecs = {
        sso: [Specification("authenticated", "==", true)],
        roles: [Specification("roleLevel", ">=", 3)]
    }

    evaluate(target: Employee, metadata: MetadataContext) → bool
        check all metaSpecs against metadata contexts first
        then evaluate spec against target
```

**Why pre-compile rules into classes?** The alternative — loading `specs.yaml` at runtime — adds startup latency, requires YAML parsing in production, and prevents the compiler from type-checking rule targets. Pre-compiled spec classes are type-safe, fast, and testable in isolation.

**Template strategy**: Use templates only for structural output with variable slots (e.g., spec classes). Use string building for generators with complex conditional logic. Both approaches must produce identical output.

---

### Step 4 — Model Compiler (Schema → Type-Safe Model Classes)

**Input**: SQL schema (via `models.yaml` or direct)
**Output**: Type-safe model classes (one per table, one per view)

**What it does**: Converts schema definitions into the language's native model/struct/class system. In Java, this uses Protocol Buffers (.proto → protoc → Java classes) to gain:
- Dynamic descriptor introspection at runtime (field names, types, values without reflection)
- Multi-language model generation from a single `.proto` definition
- gRPC service stubs as a side-effect of `.proto` compilation

**Why Protocol Buffers?** Protocol Buffers provide a typed descriptor API that allows reading field values by name at runtime without reflection. This is the foundation of the descriptor-based rule evaluation engine — a `Specification` can call `getField("age")` on any protobuf message and compare the result without knowing the model class at compile time. This makes the rule engine fully generic.

**For non-Java targets**: Generate the language's native types directly from `models.yaml`. The protobuf step is optional. The descriptor-based runtime is the goal; how you get there (protobuf, reflection, a custom registry) is an implementation detail.

---

### Step 5 — REST API Server Generator (`models.yaml` + `specs.yaml` → Server)

**Input**: `models.yaml`, `specs.yaml`
**Output**: Complete runnable REST API server

**Generated components**:
```
generated-server/
├── Application                  (entry point)
├── controller/
│   └── {Model}Controller        (REST endpoints: POST, GET, PUT, DELETE)
├── service/
│   ├── {Model}Service           (business logic, rule validation before save)
│   └── RuleService              (instantiates + evaluates compiled spec classes)
├── repository/
│   └── {Model}Repository        (in-memory storage, replace with DB adapter)
├── config/
│   └── MetadataExtractor        (HTTP headers → typed MetadataContext)
├── dto/
│   ├── RuleAwareResponse        (response includes rule evaluation results)
│   ├── RuleOutcome              (per-rule: name, satisfied, status)
│   └── ErrorResponse            (structured error format)
└── exception/
    └── GlobalExceptionHandler   (422 for blocking violations, 404 for not found)
```

**Rule evaluation in the server**:
```
POST /employees { body } + headers
    │
    ▼
Controller → MetadataExtractor.extract(request)
    │                ↓
    │         MetadataContext { sso: SsoContext, roles: RolesContext }
    │
    ▼
Service → RuleService.evaluateAll(entity, metadata)
    │
    ├── Blocking rules unsatisfied? → throw RuleViolationException → 422
    └── Non-blocking rules → record outcome in response
    │
    ▼
Repository.save(entity)
    │
    ▼
201 Created + { ruleOutcomes: [...], entity: {...} }
```

**HTTP header convention for metadata**:
```
X-{Category}-{CamelToKebab(fieldName)}: value

Examples:
  X-Sso-Authenticated: true
  X-Sso-Session-Id: abc123
  X-Roles-Role-Level: 5
  X-Roles-Role-Name: admin
```

**Blocking vs informational rules**:
| Rule type | Server behavior |
|-----------|----------------|
| `blocking: true` | Unsatisfied → 422 Unprocessable Entity, entity not saved |
| `blocking: false` (default) | Outcome reported in response, entity always saved |

**Why include informational rules?** Business rules are not always hard gates. Sometimes you want to label an entity ("SENIOR_MANAGER") without blocking creation. Both modes are generated from the same Gherkin feature file — only the `@blocking` tag differs.

---

### Step 6 — API Contract Generator (Models + Services → `openapi.yaml`)

**Input**: Model definitions (from `models.yaml` or compiled .proto files)
**Output**: `openapi.yaml` (OpenAPI 3.0.0)

**What it generates per model**:
- Schema component (object with properties and types)
- `POST /{resources}` — create (201 or 422)
- `GET /{resources}` — list (200)
- `GET /{resources}/{id}` — get by ID (200 or 404)
- `PUT /{resources}/{id}` — update (200 or 422)
- `DELETE /{resources}/{id}` — delete (204 or 404)
- Bearer JWT security scheme (if any rule has `requires:` blocks)

**Type mapping** (neutral → OpenAPI):
| Neutral | OpenAPI format |
|---------|---------------|
| string | `type: string` |
| int32 | `type: integer, format: int32` |
| int64 | `type: integer, format: int64` |
| decimal | `type: number, format: double` |
| float64 | `type: number, format: double` |
| bool | `type: boolean` |
| date | `type: string, format: date` |
| datetime | `type: string, format: date-time` |

---

### Step 7 — Registry Generator (`models.yaml` → Descriptor Registry)

**Input**: `models.yaml`
**Output**: A registry class mapping model names to their runtime descriptors

**What it does**: Generates a lookup table from model/view simple name → descriptor object. This enables the rule engine to evaluate rules against *any* model without a hard-coded dispatch table.

```
DescriptorRegistry:
    "Employee"           → Employee descriptor
    "Role"               → Role descriptor
    "EmployeeSalaryView" → EmployeeSalaryView descriptor
    ...

Usage:
    descriptor = registry.get("Employee")
    fieldValue = descriptor.getField("age", instance)
```

**Why a registry?** Without it, the rule engine would need an `if model == "Employee" ... else if model == "Role" ...` dispatch. The registry makes the engine generic — new models are added to the registry at generation time and the runtime requires no changes.

---

## Runtime Architecture

The runtime evaluates compiled spec classes against model instances. No YAML is parsed at runtime.

```
┌──────────────────────────────────────────────────────────────────┐
│                    RUNTIME EVALUATION                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Request arrives (POST /employees)                               │
│       ↓                                                          │
│  MetadataExtractor                                               │
│  reads HTTP headers → MetadataContext { sso, roles, ... }       │
│       ↓                                                          │
│  RuleService.evaluateAll(entity, metadataContext)               │
│       ↓                                                          │
│  For each matching spec class:                                   │
│    ┌─ MetadataAwareSpec.evaluate(entity, metadata):             │
│    │    1. For each metadata requirement:                        │
│    │       metadata.get("sso") → ssoCtx                         │
│    │       Specification.isSatisfiedBy(ssoCtx) ← reflection     │
│    │    2. Specification.isSatisfiedBy(entity) ← descriptor API │
│    │       entity.getField("age") → 30                          │
│    │       30 > 18 → true                                       │
│    └─ Return APPROVED | REJECTED | SENIOR_MANAGER | ...         │
│       ↓                                                          │
│  Aggregate outcomes:                                             │
│    blocking rule failed? → 422 Unprocessable Entity             │
│    all passing (or only informational)? → 201 Created           │
│       + { ruleOutcomes: [...] }                                  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### Dual-Path Field Access

A `Specification` evaluates one field of any target object. Two access paths handle the two kinds of targets:

```
Target is a domain model (protobuf message / struct / class):
    Use the descriptor API (getField by name)
    → No reflection, type-safe, fast

Target is a metadata POJO (SsoContext, RolesContext, etc.):
    Use reflection / introspection (getAuthenticated(), getRoleLevel())
    → Simple, works with any POJO, acceptable for small auth context objects
```

**For non-Java implementations**: Use the language's native reflection or struct introspection for metadata POJOs. For domain models, use whatever mechanism provides field access by name (struct tags in Go, `__dict__` in Python, `send` in Ruby, etc.).

### Condition Evaluation

```
Specification(field, operator, expectedValue).isSatisfiedBy(target):
    actual = target.getField(field)
    compare(actual, operator, expectedValue)

Operators:  ==  !=  >  <  >=  <=

Type coercion rules:
    int32, int64, float64 → promote to float64 for comparison
    decimal             → use precision-aware comparison (no float)
    bool                → direct equality only
    string              → lexicographic for ordering, exact for equality
    null                → only == and != are valid; others return false
```

### CompoundSpecification

```
CompoundSpecification(logic: AND | OR, clauses: [Specification]):
    AND: all clauses must be satisfied (short-circuit on first false)
    OR:  any clause must be satisfied (short-circuit on first true)
```

---

## External Dependencies

Each dependency below is used in the Java reference implementation. Future language implementations should choose language-native equivalents or implement the functionality directly.

### Gherkin Parser (io.cucumber:gherkin 38.0.0)
**What it does**: Parses `.feature` files into a structured AST (Feature, Scenario, Step, Tag, DataTable).
**Why not regex?**: Gherkin has context-sensitive syntax (Background, Scenario Outline, Examples, multiline strings). A proper parser handles all edge cases.
**Port note**: Gherkin parsers exist for most languages (cucumber-js, cucumber-ruby, cucumber-go). Use them. Do not re-implement the Gherkin grammar.

### SQL Parser (JSQLParser 5.3)
**What it does**: Parses SQL DDL and DML into an AST. Handles multi-dialect SQL.
**Why not regex?**: SQL has recursive structure (`DECIMAL(15,2)`, nested subqueries in views, inline comments). The Java implementation uses regex for a subset of DDL and JSQLParser for views. A full SQL parser reduces edge cases.
**Port note**: `sqlparse` (Python), `go-sqlparser` (Go). Alternatively, the `models.yaml` intermediate can be hand-authored for simple schemas.

### YAML Library (SnakeYAML 2.2)
**What it does**: Parses and generates YAML for intermediate files (`specs.yaml`, `models.yaml`).
**Why YAML?**: Human-readable, diffable, supports comments, widely supported. The intermediates are inspectable without special tooling.
**Port note**: PyYAML (Python), `gopkg.in/yaml.v3` (Go), Psych (Ruby).

### Protocol Buffers (protobuf-java 3.25.3 + protoc)
**What it does**: Compiles `.proto` schema files into typed model classes with runtime descriptor support.
**Why protobuf?**: Provides a descriptor API for reading field values by name at runtime without reflection. Enables generic rule evaluation and gRPC service generation as a side-effect.
**Port note**: This is Java-specific. For Go, generate Go structs from `models.yaml` and use a custom field registry. For Python, use dataclasses. The descriptor-driven runtime pattern is the goal — protobuf is one implementation of it.

### Handlebars (handlebars.java 4.5.0)
**What it does**: Template engine for structural code generation (Specification classes, Metadata POJOs, DescriptorRegistry).
**Why selective?**: Templates simplify generators where the output is mostly static text with variable slots. Where output requires complex conditional logic, plain string building is clearer and more portable.
**Port note**: Mustache (any language), Jinja2 (Python), Go `text/template`. Or eliminate templates entirely — plain string building works for all generators.

### Lombok (1.18.38+)
**What it does**: Annotation processor that generates `@Data`, `@Builder`, `@AllArgsConstructor`, `@NoArgsConstructor` boilerplate for metadata POJOs.
**Why?**: Reduces generated file verbosity for simple value objects.
**Port note**: Python dataclasses, Go struct literals with builder functions, Ruby Struct. Or generate the boilerplate explicitly — it is straightforward.

### Log4j2 (2.23.1)
**What it does**: Structured logging across all generator and runtime classes.
**Port note**: `logging` (Python), `log/slog` (Go), Ruby Logger.

### JUnit 5 (5.11.3)
**What it does**: Test framework for 171 unit tests across 13 suites.
**Port note**: pytest (Python), `testing` (Go), RSpec (Ruby).

---

## Domain Organization

Tables and views are assigned to domains. Each domain gets its own namespace/package.

```
DOMAIN_MAPPING (tables):
  employees  → appget
  roles      → appget
  departments → hr
  salaries   → hr
  invoices   → finance

VIEW_DOMAIN_MAPPING (views):
  employee_salary_view    → appget
  department_budget_view  → hr
```

**Namespace convention**:
| Domain | Namespace |
|--------|-----------|
| `appget` | `dev.appget.model` / `dev.appget.view` |
| `hr` | `dev.appget.hr.model` / `dev.appget.hr.view` |
| `finance` | `dev.appget.finance.model` |

**Why domains?** Multi-table schemas often have logical groupings (HR tables, finance tables, core tables). Domains prevent naming conflicts, allow independent deployment, and map naturally to microservice boundaries.

---

## Build Dependency Graph

The dependency graph must break a circular dependency: generators need compilation before running, and running generators produces code that the compiler needs.

```
compile-generators (independent step)
    ↓ (compiled generator classes)
    ├──→ features-to-specs (features + metadata → specs.yaml)
    │         ↓
    └──→ parse-schema (schema + views → models.yaml)
              ↓
         schema-to-proto (schema + specs → .proto files)
              ↓
         compile-models (protoc: .proto → model classes)
              ↓
    ┌─────────┴─────────────────┐
    │                           │
    ↓                           ↓
generate-specs              generate-openapi
(specs + models → Java)     (.proto → openapi.yaml)
    ↓
generate-registry
(models → DescriptorRegistry)
    ↓
compile-java
(all generated + handwritten code)
    ↓
test (171 unit tests)
    ↓
build (JAR + distributions)
```

**Critical rule**: `compile-models` must depend on `features-to-specs` + `parse-schema`, not on `compile-java`. This is the only way to break the circular dependency without introducing a separate bootstrap phase.

---

## Portability Guide for New Language Targets

When implementing appget for a new language (Go, Python, Ruby, etc.), follow these steps:

### Step 1: Reuse the intermediates

The YAML intermediates (`specs.yaml`, `models.yaml`) are language-agnostic. You can reuse the Java `FeatureToSpecsConverter` and `SQLSchemaParser` to generate these files, then write only the language-specific downstream generators.

### Step 2: Implement the four core generators

| Generator | Input | Output | Complexity |
|-----------|-------|--------|------------|
| Spec generator | `specs.yaml` + `models.yaml` | Spec classes | Medium — needs template or string building |
| Model generator | `models.yaml` | Struct/class definitions | Low — straightforward type mapping |
| Server generator | `models.yaml` + `specs.yaml` | REST API server | High — many components |
| Registry generator | `models.yaml` | Field lookup registry | Low — simple map generation |

### Step 3: Implement the three runtime classes

These are handwritten (not generated) and must be implemented in the target language:

| Class | Purpose | Notes |
|-------|---------|-------|
| `Specification` | Single-field condition evaluation | Needs dual-path: struct field access + reflection/introspection for metadata POJOs |
| `CompoundSpecification` | AND/OR logic over a list of Specifications | Trivial once Specification exists |
| `MetadataContext` | Typed container for auth context POJOs | Map of category name → POJO |

### Step 4: Implement field access

This is the most language-specific piece. You need to read a struct/class field by name at runtime:

| Language | Domain models | Metadata POJOs |
|----------|---------------|----------------|
| Go | Custom registry or reflection (`reflect.Value`) | `reflect.Value.FieldByName()` |
| Python | `getattr(obj, field_name)` | Same |
| Ruby | `obj.send(field_name)` | Same |
| Rust | Enum dispatch or proc macros | Limited |

### Step 5: Type mapping

Implement the SQL → language type mapping table. Each language has different primitives:

| Neutral type | Go | Python | Ruby |
|--------------|-----|--------|------|
| string | `string` | `str` | `String` |
| int32 | `int32` | `int` | `Integer` |
| int64 | `int64` | `int` | `Integer` |
| decimal | `*big.Float` | `Decimal` | `BigDecimal` |
| float64 | `float64` | `float` | `Float` |
| bool | `bool` | `bool` | `TrueClass/FalseClass` |
| date | `time.Time` | `datetime.date` | `Date` |
| datetime | `time.Time` | `datetime.datetime` | `DateTime` |
| nullable | pointer `*T` | `Optional[T]` | `nil`-able |

---

## Known Trade-offs and Improvement Opportunities

### Redundancy: Type Mapping Scattered

SQL-to-type mappings exist in `SQLSchemaParser`, `ModelsToProtoConverter`, `ProtoOpenAPIGenerator`, and `SpringBootServerGenerator`. A divergence in any one of them produces inconsistent output.

**Improvement**: Extract a single shared type registry (neutral type → language type, neutral type → OpenAPI type, neutral type → proto type) consumed by all generators. In future language implementations, define this table once.

### Complexity: Protobuf Intermediary

Using `.proto` as the model intermediate adds a compilation step (`protoc`), requires the protobuf compiler binary, and couples the Java implementation to a protobuf ecosystem.

**Improvement for future targets**: Generate model classes directly from `models.yaml`. Use a custom field registry pattern (similar to `DescriptorRegistry`) that does not require protobuf. The descriptor-driven runtime is the valuable pattern; protobuf is an implementation detail.

### In-Memory Repositories

The generated server uses `ConcurrentHashMap` repositories with no persistence. This is suitable for demos and integration testing but not for production.

**Improvement**: Add a repository interface abstraction so that the generated layer is swappable. The generator should emit an interface + in-memory default; a database adapter is a separate concern.

### Spec Class Verbosity

Each rule generates a separate class file. With many rules, this becomes a large number of small files.

**Alternative**: A single `RuleRegistry` class that loads all compiled rules and exposes them by name. The spec classes become private inner classes or are replaced by a data record approach. This reduces file count but may increase per-file complexity.

---

## Summary

| Concern | Source of truth | Changes how? |
|---------|----------------|-------------|
| Domain models | `schema.sql` | Edit SQL, run pipeline |
| View models | `views.sql` | Edit SQL, run pipeline |
| Business rules | `features/*.feature` | Edit Gherkin, run pipeline |
| Auth context | `metadata.yaml` | Edit YAML, run pipeline |
| REST contract | Generated (`openapi.yaml`) | Never edit directly |
| Server code | Generated (`generated-server/`) | Never edit directly |
| Spec classes | Generated (`java-generated/`) | Never edit directly |

**Key invariant**: The same source files always produce the same generated outputs. Generated code is disposable and never committed to version control.
