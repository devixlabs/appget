# SQL-to-Java Code Generation Pipeline

## Overview

The appget.dev/java project uses a **schema-first, protobuf-first** approach to code generation. Business rules are defined in human-friendly **Gherkin `.feature` files** (the source of truth for rules), while **SQL** remains the source of truth for domain models. The pipeline converts `.feature` files + `metadata.yaml` into `specs.yaml`, SQL schemas into `.proto` files, then uses `protoc` to generate Java protobuf model classes. It supports SQL views, generic specifications with descriptor-based evaluation, compound AND/OR conditions, metadata-aware authorization rules, gRPC service stubs, and proto-first OpenAPI generation.

## Pipeline Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  Gherkin + Schema-First, Protobuf-First Code Generation      │
└──────────────────────────────────────────────────────────────┘

LAYER 0: HUMAN-FRIENDLY BUSINESS RULES (Source of Truth)
┌──────────────────────┐  ┌─────────────────┐
│  features/*.feature  │  │  metadata.yaml  │
│  (Gherkin BDD rules) │  │  (context POJOs)│
└──────────┬───────────┘  └────────┬────────┘
           │                       │
           │ FeatureToSpecsConverter
           │ (Gherkin parser + YAML assembly)
           ↓
┌─────────────────┐
│   specs.yaml    │  ← GENERATED (git-ignored)
│  (rules + meta) │
└─────┬───────┬───┘
      │       │
      │       │
      │       └─────────────────────────────────────────┐
      │                                                 │
LAYER 1: SCHEMA SOURCE OF TRUTH                         │
┌─────────────────┐  ┌─────────────────┐                │
│   schema.sql    │  │   views.sql     │                │
│  (SQL DDL)      │  │  (SQL views)    │                │
└────────┬────────┘  └────────┬────────┘                │
         │                    │                         │
         └────────┬───────────┘                         │
                  │                                     │
                  │ SQLSchemaParser                     │
                  │ (regex-based, multi-dialect)        │
                  ↓                                     │
LAYER 2: INTERMEDIATE REPRESENTATION                    │
┌─────────────────┐                                     │
│  models.yaml    │  ← Auto-generated                   │
└────────┬────────┘                                     │
         │                                              │
         │ ModelsToProtoConverter ◄─────────────────────┤
         │ (models.yaml + specs → .proto with rules)    │
         ↓                                              │
┌─────────────────────────────────┐                     │
│  .proto files (per domain)      │                     │
│  appget_models.proto            │                     │
│  hr_models.proto                │                     │
│  finance_models.proto           │                     │
│  *_views.proto, *_services.proto│                     │
│  rules.proto (custom options)   │                     │
└────────┬────────────────────────┘                     │
         │                                              │
    ┌────┴──────────────┐                               │
    │                   │                               │
    │ protoc            │ ProtoOpenAPIGenerator         │
    │ (protobuf         │ (proto-first REST)            │
    │  compiler)        │                               │
    ↓                   ↓                               │
┌─────────────────┐  ┌──────────────────┐               │
│  Java Protobuf  │  │  openapi.yaml    │               │
│  Models + Views │  │  (REST spec)     │               │
│  gRPC Stubs     │  └──────────────────┘               │
│  (MessageOrBuilder)                                   │
└────────┬────────────────────────┐                     │
         │                        │                     │
         │ (models.yaml)          │ (parallel)          │
         └────────┐       ┌───────┘                     │
                  │       │                             │
                  └───────┤ SpecificationGenerator ◄────┘
                          │ (specs.yaml + models.yaml → Java specs)
                          ↓
LAYER 3: SPECIFICATIONS + METADATA
┌─────────────────────────────────────────────────────────┐
│  Generated specification classes                        │
│  ├── EmployeeAgeCheck.java (simple condition)           │
│  ├── SeniorManagerCheck.java (compound AND)             │
│  ├── HighEarnerCheck.java (view-targeting)              │
│  ├── AuthenticatedApproval.java (metadata-required)     │
│  └── SalaryAmountCheck.java (cross-domain)              │
│                                                         │
│  Generated metadata POJOs (Lombok)                      │
│  ├── SsoContext.java                                    │
│  ├── RolesContext.java                                  │
│  └── UserContext.java                                   │
└────────┬────────────────────────────────────────────────┘
         │
         ↓ DescriptorRegistry + RuleInterceptor + TestDataBuilder

LAYER 4: RUNTIME EVALUATION (Descriptor-Based)
┌───────────────────────────────────────────────────────────┐
│  Descriptor-driven business logic evaluation              │
│  - DescriptorRegistry: dynamic model discovery            │
│  - RuleInterceptor: loads rules from .proto custom opts   │
│  - Specification: protobuf getField() for models/views    │
│  - Specification: reflection fallback for metadata POJOs  │
│  - Compound rules: AND/OR logic                           │
│  - Metadata rules: authorization checks before evaluation │
│  - TestDataBuilder: DynamicMessage-based sample data      │
│  Result: APPROVED / REJECTED / SENIOR_MANAGER / etc.      │
└───────────────────────────────────────────────────────────┘
```

## Build Tasks

### User-Friendly Commands

```bash
make features-to-specs # Convert .feature files + metadata.yaml -> specs.yaml
make parse-schema      # Parse schema.sql + views.sql -> generate models.yaml
make generate-proto    # (schema.sql + specs.yaml) -> .proto -> protoc -> Java model classes
make generate-specs    # Parse specs.yaml + models.yaml -> generate specs + metadata POJOs
make generate-registry # Generate DescriptorRegistry from models.yaml
make generate-openapi  # .proto files -> generate openapi.yaml (ProtoOpenAPIGenerator)
make generate          # Generate all (features-to-specs + protoc + specs + registry + openapi)
make generate-server   # Generate Spring Boot server from models and specs
make build             # Full pipeline: parse schema, generate, compile
make test              # Run all 171 tests
make run               # Build and execute the application
make run-server        # Build and run the Spring Boot server
make clean             # Clean all build artifacts
make all               # clean -> generate -> test -> build
```

### Gradle Tasks (Direct)

```bash
gradle compileGenerators           # Compile generator classes only
gradle featuresToSpecs             # Run FeatureToSpecsConverter (features + metadata -> specs.yaml)
gradle parseSchema                 # Run SQLSchemaParser (schema.sql + views.sql -> models.yaml)
gradle generateProto               # Run ModelsToProtoConverter + protoc (models.yaml -> .proto -> Java)
gradle generateSpecs               # Run SpecificationGenerator (specs.yaml + models.yaml -> Java)
gradle generateDescriptorRegistry  # Run DescriptorRegistryGenerator (models.yaml -> DescriptorRegistry)
gradle generateOpenAPI             # Run ProtoOpenAPIGenerator (.proto -> openapi.yaml)
gradle compileJava                 # Compile all (main + generated)
gradle test                        # Run all src/tests
gradle build                       # Full build with packaging
```

## Build Task Dependencies

```
compileGenerators (independent)
    ↓
    ├→ featuresToSpecs (features/*.feature + metadata.yaml -> specs.yaml) [NEW]
    │       ↓
    ├→ parseSchema (schema.sql + views.sql -> models.yaml)
    │       ↓
    ├→ modelsToProto (depends on: parseSchema + featuresToSpecs + compileGenerators)
    │       ↓
    │   generateProto (protoc: .proto -> Java model classes)
    │       ↓
    │       ├─────────────────────────────────────────────────────┐
    │       │                                                     │
    │   generateSpecs                                         generateOpenAPI
    │       ↓ (depends on: featuresToSpecs + parseSchema)         ↓ (depends on: modelsToProto)
    ├→ generateDescriptorRegistry (models.yaml -> DescriptorRegistry.java)
    │       ↓ (depends on: parseSchema)
    compileJava
        ↓ (depends on: generateSpecs, generateDescriptorRegistry, generateProto)
    test
        ↓ (depends on: classes)
    build
```

**Critical Rule**: `modelsToProto` depends on `parseSchema` (models.yaml must exist) and `featuresToSpecs` (specs.yaml must exist). `generateProto` depends on `modelsToProto`, NOT on `classes`. This breaks the circular dependency.

## File Structure

### Input Files (Source of Truth - Committed)

- **features/*.feature** - Gherkin business rule definitions (human-friendly BDD format)
  - One `.feature` file per domain (`appget.feature`, `hr.feature`)
  - Feature-level tags: `@domain:appget` assigns domain to all scenarios
  - Scenario-level tags: `@target:Employee`, `@rule:RuleName`, `@blocking`, `@view`
  - Step patterns: `When <field> <operator_phrase> <value>`, `Then status is "<value>"`, `But otherwise status is "<value>"`
  - Compound conditions: `When all conditions are met:` (AND) / `When any condition is met:` (OR) + data table
  - Metadata requirements: `Given <category> context requires:` + data table

- **metadata.yaml** - Context POJO definitions (extracted from old specs.yaml metadata section)
  - Defines authorization POJOs: sso, roles, user, location
  - Committed separately from rules (rules live in `.feature` files)

- **schema.sql** - SQL DDL statements (CREATE TABLE)
  - Supports multiple database dialects (MySQL, SQLite, Oracle, MSSQL, PostgreSQL)
  - Tables mapped to domains via `DOMAIN_MAPPING` in SQLSchemaParser
  - Column naming: SQL `role_id` (snake_case) → models.yaml stores `role_id` (snake_case, language-agnostic); proto field names are `role_id` (snake_case direct); Java getters are `getRoleId()` (camelCase, from protobuf)
  - Nullability: `NOT NULL` -> non-nullable types; nullable -> wrapper types

- **views.sql** - SQL CREATE VIEW statements
  - Column types resolved from source table definitions
  - Aliases mapped to source tables (e.g., `e` -> `employees`)
  - Aggregate functions supported (COUNT -> long, SUM -> BigDecimal, AVG -> double)
  - Views mapped to domains via `VIEW_DOMAIN_MAPPING`

### Generated Intermediate Files (Git-Ignored)

- **specs.yaml** - Generated from `features/*.feature` + `metadata.yaml` by FeatureToSpecsConverter
  - `metadata:` section from metadata.yaml (context POJOs)
  - `rules:` section from feature files (Gherkin scenarios → YAML rules)
  - Conditions use snake_case for proto model/view fields (`role_id`, `salary_amount`)
  - Metadata `requires:` conditions use camelCase (`roleLevel`, `authenticated`) for Lombok POJO reflection

### Generated Files
 - Use `tree` if installed, else `ls -lR` will be good enough 
```bash
tree src/main/java-generated/
```

## Type Mapping

### SQL -> Java Types

| SQL Type | Java Type | Nullable | Notes |
|----------|-----------|----------|-------|
| VARCHAR, CHAR, TEXT | String | String | Unicode text |
| INT, INTEGER, SMALLINT | int | Integer | 32-bit integer |
| BIGINT, LONG | long | Long | 64-bit integer |
| DECIMAL, NUMERIC | BigDecimal | BigDecimal | Precise decimals |
| FLOAT, DOUBLE, REAL | double | Double | 64-bit floating point |
| DATE | LocalDate | LocalDate | Date without time |
| TIMESTAMP, DATETIME | LocalDateTime | LocalDateTime | Date and time |
| BOOLEAN, BOOL | boolean | Boolean | True/false |

### Aggregate Functions (Views)

| SQL Function | Java Type |
|--------------|-----------|
| COUNT(*) | long |
| SUM(x) | BigDecimal |
| AVG(x) | double |
| MIN(x) / MAX(x) | Same as source column |

## Domain Mapping

Tables and views are grouped into domains:

```
DOMAIN_MAPPING = {
    "roles" -> "appget",
    "employees" -> "appget",
    "departments" -> "hr",
    "salaries" -> "hr",
    "invoices" -> "finance"
}

VIEW_DOMAIN_MAPPING = {
    "employee_salary_view" -> "appget",
    "department_budget_view" -> "hr"
}
```

**Namespace Convention**:
- Domain `appget` -> `dev.appget.model` / `dev.appget.view`
- Domain `hr` -> `dev.appget.hr.model` / `dev.appget.hr.view`
- Domain `finance` -> `dev.appget.finance.model`

## specs.yaml Format

### Condition Shapes

**Simple (single condition)**:
```yaml
conditions:
  - field: age
    operator: ">"
    value: 18
```

**Compound (AND/OR)**:
```yaml
conditions:
  operator: AND
  clauses:
    - field: age
      operator: ">="
      value: 30
    - field: role_id
      operator: "=="
      value: "Manager"
```

### Metadata Requirements

Metadata fields use camelCase (matching Lombok POJO getters):
```yaml
requires:
  sso:
    - field: authenticated
      operator: "=="
      value: true
  roles:
    - field: roleLevel
      operator: ">="
      value: 3
```

### Target Types

```yaml
target:
  type: model    # or "view"
  name: Employee # model/view class name
  domain: appget # domain for import resolution
```

## Dependencies

- **Gherkin 38.0.0** - Cucumber Gherkin parser for `.feature` files
- **Handlebars.java 4.5.0** - Template engine for structural code generation (selective use)
- **Protobuf 3.25.3** - Protocol Buffers (protoc compiler + Java runtime)
- **gRPC-Java 1.62.2** - gRPC service stubs (protoc-gen-grpc-java)
- **JSQLParser 5.3** - SQL parsing and multi-dialect support
- **SnakeYAML 2.2** - YAML processing
- **Lombok 1.18.38** - Metadata POJO annotations (SsoContext, RolesContext, etc.)
- **Log4j2 2.23.1** - Logging
- **JUnit 5 5.11.3** - Testing framework (171 tests)

## Code Generation Approach

Generators use two approaches, chosen based on output complexity:

**Handlebars `.hbs` templates** (structural output with variable slots):
| Generator | Template |
|-----------|----------|
| `DescriptorRegistryGenerator` | `templates/descriptor/DescriptorRegistry.java.hbs` |
| `SpecificationGenerator` | `templates/specification/SimpleSpecification.java.hbs` |
| `SpecificationGenerator` | `templates/specification/CompoundSpecification.java.hbs` |
| `SpecificationGenerator` | `templates/specification/MetadataPojo.java.hbs` |

**StringBuilder** (complex conditional logic):
| Generator | Output |
|-----------|--------|
| `SpringBootServerGenerator` | Spring Boot REST API (controllers, services, repos) |
| `ProtoOpenAPIGenerator` | OpenAPI 3.0 YAML |
| `ModelsToProtoConverter` | .proto files |
| `OpenAPITestScriptGenerator` | Test scripts |
| `SQLSchemaParser` | models.yaml |
| `FeatureToSpecsConverter` | specs.yaml |

**Note on `{{{triple-braces}}}`**: Handlebars HTML-escapes `{{var}}` by default (e.g., `>` becomes `&gt;`). Since we generate Java code (not HTML), templates use `{{{var}}}` (triple-brace, raw output) for any value containing operators, generics, or special characters. This is a known maintenance gotcha — future template authors must use `{{{` for code values.

Supporting classes:
- `TemplateEngine.java` - Handlebars wrapper with file-based `.hbs` loader and registered helpers (lowerFirst, capitalize, camelToKebab, camelToSnake)
- `CodeGenUtils.java` - Shared utility methods for string transforms, parenthesis matching, smart splitting

Template files live in `src/main/resources/templates/` with `.hbs` extension.

## Key Features

- **Gherkin Business Rules**: Human-friendly `.feature` files as source of truth for business rules (BDD format)
- **Feature-to-Specs Conversion**: `FeatureToSpecsConverter` parses Gherkin → generates `specs.yaml` (intermediate representation)
- **Multi-Dialect Support**: Regex-based parsing handles MySQL, SQLite, Oracle, MSSQL, PostgreSQL
- **SQL View Parsing**: Resolves column types from source tables via alias mapping
- **Automatic Singularization**: `employees` -> `Employee`, `departments` -> `Department`
- **Protobuf-First Models**: SQL → .proto → protoc → Java protobuf classes (MessageOrBuilder)
- **Proto-First OpenAPI**: .proto files → OpenAPI 3.0 YAML (full CRUD, security)
- **gRPC Service Stubs**: 5 services across 3 domains (protoc-gen-grpc-java)
- **Domain Isolation**: Multiple domains prevent naming conflicts
- **Descriptor-Based Evaluation**: Protobuf `getField()` API for models/views (no reflection)
- **Dual-Path Specification**: Descriptor API for protobuf, reflection fallback for Lombok POJOs
- **Compound Conditions**: AND/OR logic for multi-field business rules
- **Metadata Authorization**: Rules require authentication/role context before evaluation
- **Descriptor Registry**: Dynamic model discovery via auto-generated protobuf descriptor registry (from models.yaml)
- **Rule Interceptor**: Reads business rules from .proto custom options at runtime
- **Test Data Builder**: DynamicMessage-based sample data generation
