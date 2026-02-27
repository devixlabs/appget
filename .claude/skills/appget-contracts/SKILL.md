---
name: appget-contracts
description: "Load this skill when working on cross-language contracts, adding a new language implementation, or verifying conformance. Contains the authoritative schemas for models.yaml and specs.yaml, REST and gRPC contracts, Decimal representation, and proto naming conventions. These contracts are implemented in Java today and must be followed by all future language targets (Go, Python, Ruby, etc.)."
allowed-tools: Read, Glob, Grep
---

# appget Cross-Language Contracts

These contracts define the shared, language-agnostic intermediate representations and API semantics for the appget platform. All language implementations must produce identical outputs for the same inputs.

**Sources of Truth**:
1. `schema.sql` — domain models
2. `views.sql` — derived read models
3. `features/*.feature` — business rules
4. `metadata.yaml` — auth context types

**Authoritative Artifacts**:
1. `models.yaml` — authoritative model and view schemas
2. `specs.yaml` — authoritative rules and metadata definitions

**Build-Time Only Rule**: All evaluation and typing must be done at build time. No runtime YAML parsing in production runtimes.

---

## models.yaml Schema

```yaml
schema_version: 1
organization: appget

domains:
  appget:
    namespace: dev.appget
    models:
      - name: employees
        source_table: employees
        resource: employees
        fields:
          - name: id
            type: string
            nullable: false
            field_number: 1
            primary_key: true
            primary_key_position: 1
          - name: salary
            type: decimal
            precision: 15
            scale: 2
            nullable: true
            field_number: 2
    views:
      - name: employee_salary_view
        source_view: employee_salary_view
        resource: employee-salary-view
        fields:
          - name: salary_amount
            type: decimal
            precision: 15
            scale: 2
            nullable: false
            field_number: 1
```

**Required top-level fields**: `schema_version` (int), `organization` (string), `domains` (map).

**Domain fields**: `namespace` (string), `models` (list, optional), `views` (list, optional).

**Model/View fields**: `name` (snake_case plural, matches table/view name), `source_table` or `source_view`, `resource` (kebab-case for REST), `fields` (list).

**Field definition**:
- Required: `name` (snake_case), `type`, `nullable` (bool), `field_number` (int, stable across regenerations)
- Optional: `primary_key` (bool), `primary_key_position` (int), `precision`+`scale` (required for decimal), `original_sql_type`

**Type set**: `string`, `int32`, `int64`, `double`, `bool`, `bytes`, `timestamp` (→ google.protobuf.Timestamp), `decimal` (→ custom Decimal message)

**Ordering rules**:
1. Domains sorted by name
2. Models and views preserve SQL declaration order
3. Fields preserve SQL declaration order

**Field number stability**: New fields get the next available number. Existing fields keep their original number. Removal does not renumber.

---

## specs.yaml Schema

```yaml
schema_version: 1
metadata:
  sso:
    fields:
      - name: authenticated
        type: bool
rules:
  - name: EmployeeAgeCheck
    target:
      type: model
      name: employees
      domain: appget
    blocking: true
    requires:
      sso:
        - field: authenticated
          operator: "=="
          value: true
          value_type: bool
    conditions:
      - field: age
        operator: ">"
        value: 18
        value_type: int32
    then:
      status: "APPROVED"
    else:
      status: "REJECTED"
```

**Metadata section**: Map of categories, each with `fields` (name + type). Type set is identical to models.yaml.

**Rule fields**: `name` (string, used as class name), `target` (type/name/domain), `blocking` (bool, default false), `requires` (metadata conditions), `conditions` (list or compound object), `then`/`else` (status strings).

**Condition object**: `field` (snake_case), `operator` (string), `value` (scalar or null, optional), `value_type` (optional, inferred from models.yaml/metadata if absent).

**Operators**: `==`, `!=`, `>`, `>=`, `<`, `<=`, `IS_NULL`, `IS_NOT_NULL`

**Compound conditions**:
```yaml
conditions:
  operator: AND   # or OR
  clauses:
    - field: age
      operator: ">="
      value: 30
```

**Evaluation semantics**: Evaluate `requires` first → if any metadata requirement fails, rule fails → evaluate main `conditions` only if metadata passes.

---

## REST Contract

**Resource naming**: Base name from SQL table name. snake_case → kebab-case for URLs. Deterministic pluralization:
- Ends with `y` (non-vowel before it) → replace with `ies` (e.g., `salary` → `/salaries`)
- Ends with `s`, `x`, `z`, `ch`, `sh`, or `o` → add `es`
- Otherwise → add `s` (e.g., `employee` → `/employees`)

**CRUD endpoints** (models only, not views):
- `POST /{resource}` — create
- `GET /{resource}` — list
- `GET /{resource}/{pk...}` — get by key
- `PUT /{resource}/{pk...}` — update
- `DELETE /{resource}/{pk...}` — delete

Composite keys use multiple path params in primary key order.

**Rule-aware response**:
```
RuleAwareResponse<T>:
  data: T
  ruleResults: RuleEvaluationResult

RuleEvaluationResult:
  outcomes: [RuleOutcome]
  hasFailures: boolean

RuleOutcome:
  ruleName: string
  status: string
  satisfied: boolean
```

**Blocking rules**: If any `blocking: true` rule fails, return 422 with `RuleEvaluationResult`.

**Metadata headers**: `X-{Category}-{Field}` with case preserved. Example: `X-Sso-Authenticated: true`.

**Error codes**: 400 (invalid metadata/type), 404 (not found), 422 (rule violation).

**Error response**:
```
ErrorResponse:
  errorCode: string
  message: string
  timestamp: string (RFC3339)
```

---

## gRPC Contract

**Service pattern** (models only, not views). For each model `employees` (→ class `Employees`):
- `message EmployeesId` contains primary key fields in order
- `message EmployeesList` has `repeated Employees items`
- `rpc CreateEmployees(Employees) returns (Employees)`
- `rpc GetEmployees(EmployeesId) returns (Employees)`
- `rpc UpdateEmployees(Employees) returns (Employees)`
- `rpc DeleteEmployees(EmployeesId) returns (google.protobuf.Empty)`
- `rpc ListEmployees(google.protobuf.Empty) returns (EmployeesList)`

**Blocking rules**: Mirror REST behavior. Unsatisfied blocking rule → return `INVALID_ARGUMENT`. Attach rule outcomes as details if `google.rpc.Status` is available.

**Metadata**: Mirror REST header names, lowercased for gRPC metadata. Example: `x-sso-authenticated`.

**Error mapping**: Metadata parse failure → `INVALID_ARGUMENT`. Not found → `NOT_FOUND`. Rule violations → `INVALID_ARGUMENT`.

---

## Decimal Representation

**Proto message** (in `appget_common.proto`, generated automatically when any domain has decimal fields):
```proto
message Decimal {
  bytes unscaled = 1;
  int32 scale = 2;
}
```

**Schema mapping**: SQL `DECIMAL(p,s)` → `type: decimal` with `precision` and `scale` in models.yaml → custom Decimal message in proto.

**JSON (REST)**: Represented as strings. OpenAPI uses `type: string`, `format: decimal`, `x-precision`, `x-scale`.

**Language mappings**:
| Language | Native type |
|----------|-------------|
| Java | `java.math.BigDecimal` |
| Python | `decimal.Decimal` |
| Ruby | `BigDecimal` (stdlib bigdecimal) |
| Go | custom wrapper using `math/big.Int` + scale |
| Rust | custom wrapper or selected crate |
| Node | custom wrapper using `BigInt` + scale |

**Conversions**: proto Decimal ↔ native: unscaled bytes + scale. REST JSON ↔ native: string form.

---

## Proto Conventions

**File naming**:
- `${domain}_models.proto` — models
- `${domain}_views.proto` — views
- `${domain}_services.proto` — services
- `appget_common.proto` — shared types (Decimal)

**Proto package names**:
- Models: `package ${domain};`
- Views: `package ${domain}_views;`
- Services: `package ${domain}_services;`

**Java options**:
- `java_package = "dev.appget[.<domain>].model"` for models
- `java_package = "dev.appget[.<domain>].view"` for views
- `java_package = "dev.appget[.<domain>].service"` for services

**Go options**: `go_package = "${module_path}/gen/${domain};${domain}pb"` (module path from `go/go.mod`)

**Python**: Output under `<base>.gen.<domain>` (base from `python/pyproject.toml`)

**Ruby**: Base module `Appget::Gen::<Domain>`, file path `appget/gen/<domain>`

**Node**: Output under `gen/<domain>` (base from `node/package.json`)

Fallback: use `appget` as base name if metadata is missing.
