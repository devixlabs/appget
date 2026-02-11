# CLAUDE.md - appget.dev/java Code Generation System

This file documents the Java Rule Engine POC with YAML-driven code generation for models and specifications.

---

## Project Overview

**appget.dev/java** implements a **schema-first** multi-domain code generation system using:
- **SQLSchemaParser**: Converts `schema.sql` → Domain-organized `models.yaml` (with JSQLParser 5.3)
- **ModelGenerator**: Converts `models.yaml` → Domain-namespaced Java model classes (with Lombok)
- **SpecificationGenerator**: Converts `specs.yaml` → Business rule specification classes
- **RuleEngine**: Evaluates specifications against generated model instances

**Architecture**: SQL (source of truth) → YAML → Code Generation → Compilation → Runtime Evaluation

This approach makes the database schema the single source of truth for all generated domain models, with support for multiple SQL dialects (MySQL, SQLite, Oracle, MSSQL, PostgreSQL).

---

## Build System (Gradle + Makefile)

### Critical Design: Circular Dependency Resolution

The build pipeline must avoid circular dependencies where:
- Generators need to be compiled before they can run
- Generated code must exist before main compilation
- Generated specs reference model classes

**Solution**: Separate `compileGenerators` task

```
compileGenerators → generateModels → generateSpecs → compileJava
     ↓ independent
  (generators only)
```

**Key Rule**: `generateModels` must depend on `compileGenerators`, NOT on `classes`. This breaks the circular dependency.

### Task Dependencies in build.gradle

```groovy
task compileGenerators(type: JavaCompile) {
    // Compile ONLY generator classes (ModelGenerator, SpecificationGenerator, SQLSchemaParser)
    // Does NOT depend on 'classes' - standalone compilation
}

task parseSchema(type: JavaExec) {
    dependsOn 'compileGenerators'  // Parse SQL with pre-compiled SQLSchemaParser
}

task generateModels(type: JavaExec) {
    dependsOn 'parseSchema'  // Use SQL-generated models.yaml
}

task generateSpecs(type: JavaExec) {
    dependsOn 'generateModels'  // Models must exist first for specs to reference
}

compileJava.dependsOn 'generateSpecs'  // All code ready before compilation
```

---

## SQL Schema-First Architecture (NEW)

### SQLSchemaParser: SQL to YAML Conversion

**Location**: `src/main/java/dev/appget/codegen/SQLSchemaParser.java`

**Purpose**: Parse `schema.sql` (database source of truth) and auto-generate `models.yaml`

**Key Features**:
- **Multi-dialect support**: MySQL, SQLite, Oracle, MSSQL, PostgreSQL (via JSQLParser 5.3)
- **Automatic type mapping**: SQL types (VARCHAR, INT, DECIMAL, DATE, etc.) → Java types (String, int, BigDecimal, LocalDate, etc.)
- **Nullability handling**: Respects SQL `NOT NULL` constraints, wraps primitives for nullable fields
- **Domain mapping**: Tables automatically grouped by naming convention (table → domain assignment)
- **Name conversion**: SQL snake_case → Java camelCase automatically
- **Singularization**: `employees` → `Employee`, `departments` → `Department`, etc.

**Input**: `schema.sql` with CREATE TABLE statements

**Output**: `models.yaml` with proper type mappings and domain organization

**Example**:
```sql
-- schema.sql
CREATE TABLE employees (
    name VARCHAR(100) NOT NULL,
    age INT NOT NULL,
    role_id VARCHAR(100) NOT NULL
);
```

Generates:
```yaml
domains:
  appget:
    namespace: dev.appget
    models:
      - name: Employee
        fields:
          - name: name
            type: String
            nullable: false
          - name: age
            type: int
            nullable: false
          - name: roleId
            type: String
            nullable: false
```

**Domain Mapping Configuration** (in SQLSchemaParser):
```java
DOMAIN_MAPPING.put("employees", "appget");
DOMAIN_MAPPING.put("departments", "hr");
DOMAIN_MAPPING.put("invoices", "finance");
```

**SQL Type Mapping**:
| SQL Type | Java Type | Nullable Wrapper |
|----------|-----------|------------------|
| VARCHAR, CHAR, TEXT | String | String |
| INT, INTEGER, SMALLINT | int | Integer |
| BIGINT, LONG | long | Long |
| DECIMAL, NUMERIC | BigDecimal | BigDecimal |
| FLOAT, DOUBLE, REAL | double | Double |
| DATE | LocalDate | LocalDate |
| TIMESTAMP, DATETIME | LocalDateTime | LocalDateTime |
| BOOLEAN, BOOL | boolean | Boolean |

**Makefile Command**:
```bash
make parse-schema  # Runs SQLSchemaParser: schema.sql → models.yaml
```

---

## Code Generation Pipeline

### 1. models.yaml → ModelGenerator → Java Models

**Location**: `models.yaml` (project root)

**Structure**:
```yaml
organization: appget
domains:
  appget:
    namespace: dev.appget
    models:
      - name: Employee
        fields:
          - name: name
            type: String
            nullable: false
```

**Output**: `src/main/java-generated/dev/appget/model/Employee.java` with Lombok annotations

**Namespacing**: Domain name becomes package suffix
- Domain `appget` → package `dev.appget`
- Domain `hr` → package `dev.appget.hr`
- Domain `finance` → package `dev.appget.finance`

### 2. specs.yaml → SpecificationGenerator → Java Specs

**Location**: `specs.yaml` (project root) - **REQUIRED for build to succeed**

**Structure**:
```yaml
rules:
  - name: AAAEmployeeAgeCheck
    if:
      age: ">"
      value: 25
    then:
      status: "APPROVED"
    else:
      status: "REJECTED"
```

**Output**: `src/main/java-generated/dev/appget/specification/generated/AAAEmployeeAgeCheck.java`

**Requirement**: Both `models.yaml` AND `specs.yaml` must exist at project root. Missing either file causes build failure.

---

## Type System

### Type Mapping (YAML → Java)

| YAML Type | nullable=false | nullable=true |
|-----------|----------------|---------------|
| int       | int            | Integer       |
| long      | long           | Long          |
| double    | double         | Double        |
| String    | String         | String        |
| LocalDate | LocalDate      | LocalDate     |
| BigDecimal| BigDecimal     | BigDecimal    |

**Rule**: Primitives + nullable=true → wrapper type (can hold null)

**Auto-imports**: LocalDate, LocalDateTime, BigDecimal are automatically imported in generated code

---

## Lombok Integration

**Annotations Used** (all generated models):
- `@Data` - Getters, setters, toString, equals, hashCode
- `@Builder` - Builder pattern support
- `@AllArgsConstructor` - Constructor with all fields
- `@NoArgsConstructor` - Default no-args constructor

**Version**: 1.18.38+ required for Java 25 compatibility
- ❌ 1.18.34 fails with `java.lang.NoSuchFieldException: TypeTag :: UNKNOWN`
- ✅ 1.18.38 supports Java 25

**Dependencies** (in build.gradle):
```groovy
compileOnly 'org.projectlombok:lombok:1.18.38'
annotationProcessor 'org.projectlombok:lombok:1.18.38'
```

---

## Makefile Commands

```bash
make help              # Show all commands
make all               # Default: clean → generate → test → build (RECOMMENDED)
make clean             # Remove build artifacts and generated code
make parse-schema      # Parse schema.sql → generate models.yaml (NEW)
make generate-models   # Run ModelGenerator on models.yaml only
make generate-specs    # Run SpecificationGenerator on specs.yaml only
make generate          # Generate both models and specs
make generate-server   # Generate Spring Boot REST API server
make build             # Full: parse schema → generate → compile → package
make test              # Run all unit tests (100 tests, ~2 seconds)
make run               # Build and execute RuleEngine with generated classes
```

**Recommended verification**:
```bash
# Full pipeline test (recommended)
make all              # Runs: clean → generate → test → build

# Individual steps
make parse-schema     # schema.sql → models.yaml
make generate         # models.yaml + specs.yaml → Java classes
make test             # All 100 tests pass
make build            # Full build with compilation
make run              # Execute RuleEngine
```

**Quick Development**:
```bash
# After modifying schema.sql or YAML files
make parse-schema && make generate && make test
```

---

## Generated Code Locations

```
src/main/java-generated/
├── dev/appget/model/
│   └── Employee.java                    (from models.yaml: domain: appget)
├── dev/appget/hr/model/
│   ├── Department.java                  (from models.yaml: domain: hr)
│   └── Salary.java
├── dev/appget/finance/model/
│   └── Invoice.java                     (from models.yaml: domain: finance)
└── dev/appget/specification/generated/
    ├── AAAEmployeeAgeCheck.java         (from specs.yaml)
    └── AAAEmployeeRoleCheck.java
```

**Note**: `.gitignore` already excludes `src/main/java-generated/` - do NOT commit generated code

---

## Common Issues & Solutions

### Issue: `FileNotFoundException: specs.yaml`

**Cause**: `specs.yaml` missing from project root
**Solution**: Create `specs.yaml` with at least one rule defined (see Type System section)

### Issue: `duplicate class: dev.appget.model.Employee`

**Cause**: Both manual and generated versions of same model exist
**Solution**: Delete manual version from `src/main/java/` - use generated version only

### Issue: `java.lang.NoSuchFieldException: TypeTag :: UNKNOWN`

**Cause**: Lombok version incompatible with Java 25
**Solution**: Update `build.gradle` to use `lombok:1.18.38` or later

### Issue: `cannot find symbol: class Employee`

**Cause**: Build task ordering - generated models don't exist yet when compiling specs
**Solution**: Verify `compileGenerators` runs first and `generateSpecs` depends on `generateModels`

---

## Development Workflow

### Adding a New Domain & Models

1. Edit `models.yaml`:
   ```yaml
   domains:
     mydom:
       namespace: dev.appget.mydom
       models:
         - name: MyModel
           fields:
             - name: id
               type: String
               nullable: false
   ```

2. Run: `make generate-models`

3. Verify output in: `src/main/java-generated/dev/appget/mydom/model/MyModel.java`

### Adding New Business Rules

1. Edit `specs.yaml`:
   ```yaml
   rules:
     - name: AAAMyRuleCheck
       if:
         fieldName: "operator"
         value: valueToCheck
       then:
         status: "APPROVED"
       else:
         status: "REJECTED"
   ```

2. Run: `make generate-specs`

3. Verify output in: `src/main/java-generated/dev/appget/specification/generated/AAAMyRuleCheck.java`

### Full Development Cycle

```bash
# 1. Modify YAML files
edit models.yaml
edit specs.yaml

# 2. Generate code
make generate

# 3. Compile and test
make build

# 4. Execute
make run
```

---

## Files to Know

| File | Purpose |
|------|---------|
| `schema.sql` | SQL source of truth (database tables) |
| `models.yaml` | Auto-generated model definitions (from schema.sql) |
| `specs.yaml` | Business rule definitions |
| `build.gradle` | Gradle build config (task dependencies) |
| `Makefile` | User-facing build commands |
| `src/main/java/dev/appget/codegen/SQLSchemaParser.java` | SQL parser → YAML generator |
| `src/main/java/dev/appget/codegen/ModelGenerator.java` | YAML → Java model code generator |
| `src/main/java/dev/appget/codegen/SpecificationGenerator.java` | YAML → Specification code generator |
| `src/main/java/dev/appget/codegen/SpringBootServerGenerator.java` | Models + specs → REST API server generator |
| `src/main/java/dev/appget/RuleEngine.java` | Runtime rule evaluation engine |
| `src/test/java/dev/appget/...` | 100 unit tests (8 suites, all domains, generation, rules, server) |

---

## Testing Framework

### Test Coverage: 100 Tests, 100% Passing ✅

**Test Suites** (8 suites):
1. **Model Generator Tests** (10 tests) - models, views, namespaces
2. **OpenAPI Generator Tests** (18 tests) - REST spec, endpoints, type mappings
3. **Specification Generator Tests** (13 tests) - rules, metadata POJOs, compound, views
4. **Spring Boot Server Generator Tests** (12 tests) - RuleService bridge, MetadataExtractor, blocking logic
5. **Rule Engine Tests** (15 tests) - generic evaluation, compound, metadata
6. **Compound Specification Tests** (6 tests) - AND/OR logic
7. **Metadata Context Tests** (5 tests) - category storage, POJO evaluation
8. **Specification Pattern Tests** (21 tests) - all comparison operators, edge cases

**Running Tests**:
```bash
make test                 # Run all 100 tests (~2 seconds)
make clean && make test   # Clean rebuild + test
```

**Test Report**:
- HTML report: `build/reports/tests/test/index.html`
- All tests pass in CI/CD pipeline
- Pre-commit hook recommended: `make test` before commit

### Quality Gates

Required for production deployment:
- ✅ All 100 tests passing
- ✅ `make all` succeeds (clean → generate → test → build)
- ✅ Generated code compiles without warnings
- ✅ No manual modifications to `src/main/java-generated/`

---

## Future Enhancements (Not Implemented)

- Collections support: `List<T>`, `Set<T>`, `Map<K,V>`
- Validation annotations: `@NotNull`, `@Size`, `@Min`, `@Max`
- JPA relationships: `@ManyToOne`, `@OneToMany`
- Custom annotations from YAML
- Inheritance support
- Foreign key constraint parsing and relationship generation

---

## Key Principles

1. **SQL is source of truth**: Database schema defines all domain models
   - Maintain `schema.sql` as the canonical definition
   - Run `make parse-schema` to auto-generate `models.yaml`
   - Never manually edit `models.yaml` - it's generated

2. **Code generation is deterministic**: Same SQL input → Same Java output
   - schema.sql (fixed) → models.yaml (generated) → Java models (generated)
   - Reproducible builds every time

3. **Generated code is disposable**: Never edit generated files; regenerate from sources
   - Don't modify `src/main/java-generated/` files
   - `.gitignore` already excludes generated code
   - Changes to models go in `schema.sql`, not generated Java

4. **Build is self-contained**: No external dependencies beyond Gradle
   - JSQLParser handles multi-dialect SQL parsing
   - All generators are self-contained Java classes
   - Reproducible builds across environments

5. **Namespace isolation**: Domains prevent naming conflicts across business areas
   - Each domain gets its own Java package (dev.appget, dev.appget.hr, etc.)
   - Tables grouped by domain in schema.sql via DOMAIN_MAPPING

6. **Comprehensive testing**: 100 unit tests verify entire pipeline
   - Model generation correctness
   - Specification generation
   - Rule engine evaluation
   - Run `make test` after any changes

7. **Type safety**: Full SQL type → Java type mapping with nullability
   - Nullable fields automatically wrapped (int → Integer)
   - `NOT NULL` constraints respected in generated code
   - Type validation at generation time
