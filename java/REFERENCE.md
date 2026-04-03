# Java Subproject — Deep Reference

This file contains detailed implementation reference for the appget.dev/java code generation system. Read sections on demand using `offset`/`limit` — do not load fully into context.

**See also**: [PIPELINE.md](PIPELINE.md) for the full pipeline architecture diagrams.

---

## Table of Contents

| Section | Line | Topic |
|---------|------|-------|
| Multi-Layer Pipeline | ~25 | Full ASCII pipeline diagram and layer descriptions |
| Build Dependency Resolution | ~80 | Step-by-step build order with outputs |
| Portability Anti-Patterns | ~145 | Code examples for avoiding Java-specific idioms |
| Schema vs. Policy | ~210 | Proto = schema, specs.yaml = policy separation |
| TypeRegistry Pattern | ~235 | Type mapping tables and usage |
| Language Utility Pattern | ~275 | JavaNaming, CodeGenUtils, JavaUtils with examples |
| Protocol Buffers Layer | ~320 | Why protobuf, protoc plugins by language |
| SQL Schema-First Design | ~365 | SQLSchemaParser algorithm, type mapping, domain org |
| Metadata Field Naming | ~430 | Snake_case canonical form, per-language transforms |
| Gherkin Business Rules | ~445 | Feature file DSL, step patterns, operator phrases |
| Specification System | ~510 | Specification.java dual-path, CompoundSpec, MetadataContext, Rule.java |
| Language-Agnostic Intermediates | ~560 | models.yaml and specs.yaml format and usage |
| Code Generation Strategy | ~600 | All generators: ModelsToProto, SpecGen, OpenAPI, AppServer, HTML |
| ServerEmitter Abstraction | ~640 | Framework-agnostic emitter interface |
| Generated Server Components | ~665 | Per-model/view files, infrastructure files, features |
| Generated Server Error Handling | ~710 | GlobalExceptionHandler, 5 handlers |
| RuleEngine Details | ~740 | specs.yaml loading, buildSpec(), condition shapes |
| RuleService & MetadataExtractor | ~785 | Generated patterns, blocking rules list |
| Generated Server Classpath | ~835 | Classpath restriction on codegen imports |
| HtmlCrudGenerator | ~845 | Static HTML CRUD page generation |
| HTTP Test Harness | ~860 | YAML-driven test runner |
| Logging Configuration | ~875 | Log4j2 setup, levels, classes, format |
| Testing Strategy | ~935 | 16 test suites, organization, fixtures |
| Build Artifacts | ~1095 | Generated code layout, compiled output |
| Performance Benchmarks | ~1120 | Timing for each make target |

---

## Multi-Layer Pipeline

```
┌─────────────────────────────────────────────────┐
│ Layer 0: Gherkin Business Rules                  │
│ source: features/*.feature + metadata.yaml      │
│ status: Source of truth for rules, committed    │
└──────────┬──────────────────────────────────────┘
           │ FeatureToSpecsConverter (Gherkin parser)
           │ .feature files + metadata → specs.yaml
           ↓
┌─────────────────────────────────────────────────┐
│ Layer 1: Schema + Rules                          │
│ source: schema.sql + views.sql (SQL DDL)        │
│ generated: specs.yaml (git-ignored)             │
│ status: Source of truth for models              │
└──────────┬──────────────────────────────────────┘
           │ SQLSchemaParser (regex-based)
           │ Multi-dialect SQL + view parsing
           ↓
┌─────────────────────────────────────────────────┐
│ Layer 2: Intermediate Representation            │
│ output: models.yaml (models + views per domain) │
│ format: YAML with type mappings                │
│ purpose: Intermediate representation            │
└──────────┬──────────────────────────────────────┘
           │ ModelsToProtoConverter + protoc
           │ generates .proto files → protobuf Java classes
           ↓
┌─────────────────────────────────────────────────┐
│ Layer 3: Implementation (Protobuf)              │
│ output: Java model + view classes (protobuf)    │
│ location: build/generated/ (protoc output)      │
│ features: MessageOrBuilder, Builder pattern     │
│ subpackages: model/ and view/ per domain       │
└──────────┬──────────────────────────────────────┘
           │ SpecificationGenerator
           │ generates specs + metadata POJOs
           ↓
┌─────────────────────────────────────────────────┐
│ Layer 4: Specifications + Metadata              │
│ output: Specification classes + context POJOs   │
│ features: Simple, compound, metadata-aware      │
│ targets: Any protobuf model or view class      │
└──────────┬──────────────────────────────────────┘
           │ DescriptorRegistry
           │ dynamic model discovery
           ↓
┌─────────────────────────────────────────────────┐
│ Layer 5: Runtime (Descriptor-Based)             │
│ RuleEngine: specs.yaml-driven evaluation        │
│ Specification: protobuf getField() API          │
│ DefaultDataBuilder: DynamicMessage samples      │
│ No hard-coded imports or if/else dispatch      │
└─────────────────────────────────────────────────┘
```

---

## Build Dependency Resolution

**Problem**: Circular dependencies (generators need compilation, generated code needs models, specs reference models)

**Solution**: Isolated generator compilation

```
Step 1: compileGenerators (independent)
        ├─ Compiles: FeatureToSpecsConverter, SQLSchemaParser, ModelsToProtoConverter,
        │             SpecificationGenerator, ProtoOpenAPIGenerator, AppServerGenerator
        └─ Output: build/generators/*.class

Step 1b: featuresToSpecs (depends on compileGenerators)
        ├─ Runs: FeatureToSpecsConverter (features/*.feature + metadata.yaml → specs.yaml)
        └─ Output: specs.yaml (git-ignored, generated intermediate)

Step 2: parseSchema (depends on compileGenerators)
        ├─ Runs: SQLSchemaParser (schema.sql + views.sql → models.yaml)
        └─ Output: models.yaml with models + views (git-ignored)

Step 3: generateProto (depends on parseSchema only)
        ├─ Runs: ModelsToProtoConverter + protoc (models.yaml → .proto → Java)
        └─ Output: protobuf model + view classes per domain (build/generated/)
        Note: Business rules are NOT embedded in .proto files; they travel in specs.yaml

Step 3b: generateOpenAPI (depends on generateProto)
        ├─ Runs: ProtoOpenAPIGenerator (.proto files → openapi.yaml)
        └─ Output: openapi.yaml (git-ignored)

Step 3c: generateServer (depends on compileGenerators + generateProto + generateSpecs)
        ├─ Runs: AppServerGenerator (models.yaml + specs.yaml → REST API)
        ├─ Output: generated-server/dev/appget/server/ (complete Spring Boot project)
        └─ Note: RuleService uses pre-compiled spec classes (no runtime YAML parsing)

Step 4: generateSpecs (depends on compileGenerators, featuresToSpecs, parseSchema)
        ├─ Runs: SpecificationGenerator (specs.yaml + models.yaml → Java)
        └─ Output: specification/generated/ + specification/context/

Step 4b: generateDescriptorRegistry (depends on compileGenerators, parseSchema)
        ├─ Runs: DescriptorRegistryGenerator (models.yaml → DescriptorRegistry.java)
        └─ Output: src/main/java-generated/dev/appget/util/DescriptorRegistry.java

Step 5: compileJava (depends on generateSpecs, generateDescriptorRegistry)
        ├─ Compiles: Main code + generated code
        └─ Output: build/classes/main/*.class

Step 6: test (depends on compileJava)
        ├─ Runs: JUnit 5 tests (0 failures, 0 errors expected)
        └─ Result: 280/280 passing

Step 7: build (depends on test)
        ├─ Packages: JAR, distributions
        └─ Output: build/libs/appget.dev-java.jar
```

**Critical Rule**: `generateProto` depends on `parseSchema` only (not on `featuresToSpecs`). Business rules are not embedded in proto. This breaks the circular dependency.

**Why this dependency order**:
- featuresToSpecs and parseSchema are INDEPENDENT (can run in parallel)
- generateProto waits only on parseSchema (models.yaml) — it does NOT wait for specs.yaml
- specs.yaml is used by downstream generators (SpecificationGenerator, RuleEngine) but NOT by ModelsToProtoConverter
- This design enables future language implementations to load rules from the same specs.yaml without regenerating proto

**Note**: `generateServer` depends on `generateSpecs` (spec classes must exist first) but runs separately from default pipeline. Generates complete Spring Boot REST API server in `generated-server/`.

---

## Portability Anti-Patterns

appget.dev/java is a code generation platform targeting Go, Python, and Ruby. Avoid these Java idioms to keep generated code portable:

| Anti-Pattern | Problem | Use Instead |
|--------------|---------|-------------|
| **Switch expressions** (`case -> value`) | Java 14+ only, no equivalent in Go/Python/Ruby | if-else chains |
| **Pattern matching vars** (`instanceof Type var`) | Java 25+ only, requires explicit casting in other languages | Explicit casting: `Type var = (Type) obj` |
| **Static initialization blocks** (`static { map.put(...) }`) | Not portable; replaced by package-level initialization in Go/Python/Ruby | Static factory methods returning initialized maps |
| **Method overloading** | Not supported in Python/Ruby; confusing in Go/Rust | Builder pattern for multiple constructor signatures |
| **Singleton pattern** (`INSTANCE` field) | Mutable state risk, concurrency hazard, not idiomatic in Go/Python/Rust | Static utility class with static methods (private constructor, no instances) |

### Code Examples

```java
// ❌ Not portable: switch expression
return switch (operator) {
    case "==" -> true;
    case "!=" -> false;
    default -> null;
};

// ✅ Portable: if-else
if (operator.equals("==")) {
    return true;
} else if (operator.equals("!=")) {
    return false;
}
return null;
```

```java
// ❌ Not portable: pattern matching variable
if (target instanceof MessageOrBuilder mob) {
    return evaluate(mob);
}

// ✅ Portable: explicit casting
if (target instanceof MessageOrBuilder) {
    MessageOrBuilder mob = (MessageOrBuilder) target;
    return evaluate(mob);
}
```

```java
// ❌ Not portable: static block
private static final Map<String, String> TYPES = new HashMap<>();
static {
    TYPES.put("VARCHAR", "String");
    TYPES.put("INT", "int");
}

// ✅ Portable: static factory method
private static final Map<String, String> TYPES = createTypeMapping();

private static Map<String, String> createTypeMapping() {
    Map<String, String> map = new HashMap<>();
    map.put("VARCHAR", "String");
    map.put("INT", "int");
    return map;
}
```

---

## Schema vs. Policy Architecture

**Critical Rule**: Proto files define SCHEMA (models, fields, types). specs.yaml defines POLICY (rules, authorization, outcomes).

**Problem Avoided**: Embedding business rules in proto custom options mixes schema with policy, creating tight coupling and making rules hard to update without regenerating proto files.

**Solution**: Clean separation:
- **proto files** (generated from models.yaml): Field names, types, proto type mappings only
- **specs.yaml** (generated from features + metadata): Rules, metadata requirements, blocking flags, success/failure status

**Impact on Generators**:
- ModelsToProtoConverter: Converts models.yaml → proto (schema only, no rules)
- RuleEngine: Loads rules from specs.yaml, not from proto
- All downstream generators (SpecificationGenerator, AppServerGenerator): Read rules from specs.yaml

This separation ensures that:
1. Proto files remain language-agnostic schema contracts for all future implementations
2. Rules can be updated without regenerating proto
3. Each language implementation reads the same specs.yaml

---

## TypeRegistry Pattern

All type mappings are consolidated in a single per-language registry. This is the primary extension point for adding new language implementations.

**Interface**: `src/main/java/dev/appget/codegen/TypeRegistry.java`
**Java implementation**: `src/main/java/dev/appget/codegen/JavaTypeRegistry.java`

```java
TypeRegistry.INSTANCE.neutralToProto("decimal")   // → "appget.common.Decimal"
TypeRegistry.INSTANCE.neutralToJava("decimal")    // → "BigDecimal"
TypeRegistry.INSTANCE.neutralToOpenApi("datetime") // → ["string", "date-time"]
```

**Neutral types** stored in `models.yaml` (language-agnostic):

| models.yaml type | Java (neutralToJava) | Proto (neutralToProto) | OpenAPI |
|-----------------|----------------------|------------------------|---------|
| `string` | `String` | `string` | string |
| `int32` | `int` / `Integer` | `int32` | integer/int32 |
| `int64` | `long` / `Long` | `int64` | integer/int64 |
| `float64` | `double` / `Double` | `double` | number/double |
| `bool` | `boolean` / `Boolean` | `bool` | boolean |
| `date` | `LocalDate` | `google.protobuf.Timestamp` | string/date |
| `datetime` | `LocalDateTime` | `google.protobuf.Timestamp` | string/date-time |
| `decimal` | `BigDecimal` | `appget.common.Decimal` | string/decimal |

**For new language implementations** (Go, Python, Ruby): Create `GoTypeRegistry implements TypeRegistry`, `PythonTypeRegistry implements TypeRegistry`, etc. Each language writes exactly one registry class; all generators read from it. Never add type mappings inside individual generators.

**`appget_common.proto`**: Generated automatically by `ModelsToProtoConverter` when any domain has `decimal` fields. Contains the shared `Decimal` message (`bytes unscaled`, `int32 scale`).

---

## Language Utility Pattern

Appget separates **language-agnostic** utilities from **language-specific** naming convention utilities. This separation is a primary extension point for multi-language portability.

**Language-agnostic** (`CodeGenUtils.java`): String operations that apply to any language — `capitalize()`, `escapeString()`, `smartSplit()`, `findMatchingParen()`. Shared across all generators, never language-specific.

**Runtime naming** (`dev.appget.naming`): The `NamingConvention` interface defines the cross-language contract for field-name resolution at runtime. Each language implements this with a static utility class. `Specification.java` and generators both call `JavaNaming.toFieldAccessor()` for snake_case-to-camelCase conversion.

| Utility class | Language | `snake_case` input | Casing output |
|--------------|----------|-------------------|--------------|
| `JavaNaming.java` | Java | `role_level` | `roleLevel` (camelCase) |
| `GoNaming.go` | Go | `role_level` | `RoleLevel` (PascalCase) |
| `python_naming.py` | Python | `role_level` | `role_level` (identity) |
| `rust_naming.rs` | Rust | `role_level` | `role_level` (identity) |
| `node_naming.js` | Node/JS | `role_level` | `roleLevel` (camelCase) |

**Codegen-only** (`JavaUtils.java`): Methods used only at generation time that don't belong in the runtime naming contract: `snakeToPascal()`, `snakeToHeaderCase()`, `JAVA_TO_PROTO_TYPE`.

```java
// Language-agnostic (CodeGenUtils) — shared by all
CodeGenUtils.capitalize("role_level")              // → "Role_level"
CodeGenUtils.escapeString("he said \"hi\"")        // → "he said \\\"hi\\\""

// Runtime naming (JavaNaming) — field accessor resolution
JavaNaming.toFieldAccessor("role_level")           // → "roleLevel"

// Codegen-only (JavaUtils) — generation-time transforms
JavaUtils.snakeToPascal("role_level")              // → "RoleLevel"
JavaUtils.snakeToHeaderCase("role_level")          // → "Role-Level"
```

**Key rule**: Generators read snake_case from intermediates (`models.yaml`, `specs.yaml`) and call the language-specific utility at codegen time. Never store language-specific casing in intermediate files.

---

## Protocol Buffers as Universal Schema Layer

Protobuf is the shared schema and code generation medium for ALL future appget.dev language implementations.

**Why Protobuf (not JSON, not custom YAML)?**
- **Type Safety**: Strongly typed schema with compile-time validation in all major languages
- **Cross-Language Runtime**: Descriptor/reflection API available in Java, Python, Go, Ruby, Rust, JavaScript/TypeScript, C#
- **Backward Compatibility**: Wire format evolution (adding fields, changing types) is safe and tested
- **Code Generation**: `protoc` plugins exist for all target languages; no need to build custom generators per language
- **Ecosystem**: Wide adoption means library support, documentation, and battle-tested implementations

**Protoc Plugins by Language** (using models.yaml via ModelsToProtoConverter):

| Language | Plugin | Output | Usage |
|----------|--------|--------|-------|
| Java | built-in | .java classes, MessageOrBuilder | appget.dev/java reference implementation |
| Python | built-in | .py dataclasses, descriptor API | future appget.dev/python |
| Go | built-in | .pb.go structs with descriptor API | future appget.dev/go |
| Ruby | built-in | .pb.rb classes | future appget.dev/ruby |
| Rust | rust-protobuf | .rs structs | future appget.dev/rust |
| JavaScript/TypeScript | ts-proto or pbjs | .ts/.js classes | future appget.dev/node |
| C# | built-in | .cs classes | future appget.dev/csharp |

**All implementations use the same .proto files** (generated from models.yaml) as source of truth, then layer language-specific generators on top.

---

## SQL Schema-First Design

### Why Schema-First?

1. **Single source of truth**: Database schema defines domain models
2. **Type safety**: SQL type constraints reflected in Java type system
3. **Consistency**: All generated models aligned with database
4. **Auditability**: Schema changes tracked in version control
5. **Reproducibility**: Same schema → same models, always

### SQLSchemaParser Implementation

**Location**: `src/main/java/dev/appget/codegen/SQLSchemaParser.java`

**Algorithm**:
1. Read schema.sql file as string
2. Extract CREATE TABLE statements (handle nested parentheses)
3. For each table: extract columns, parse types/constraints, map to domain
4. Read views.sql file (if provided)
5. Extract CREATE VIEW statements via regex
6. Resolve view column types from source table columns via alias mapping
7. Generate models.yaml with models + views per domain
8. Write to file

**Key Features**:
- **Parenthesis matching**: Correctly handles `DECIMAL(15,2)` nested types
- **Type mapping**: Comprehensive SQL → Java type conversion
- **Constraint parsing**: Respects `NOT NULL`, wraps primitives appropriately
- **Domain assignment**: comment-based detection (`-- auth domain` before tables assigns domain)
- **Name preservation**: table and column names kept as snake_case (e.g., `users`, `severity_level`)
- **View alias resolution**: `p.content` → resolves `p` → `posts` → lookup column type
- **Aggregate functions**: COUNT → long, SUM → BigDecimal, AVG → double

### Type Mapping Strategy

**Primitives with Nullability**:
```
int + nullable=false → int
int + nullable=true  → Integer

double + nullable=false → double
double + nullable=true  → Double

String + nullable=any → String (always nullable in Java)
BigDecimal + nullable=any → BigDecimal (can hold null)
LocalDate + nullable=any → LocalDate (can hold null)
```

### Domain Organization

**Table Mapping** (comment-based in schema.sql — `-- <domain> domain` before each table group):
```
-- auth domain:   users, sessions  → dev.appget.auth.model
-- social domain: posts, comments, likes, follows, reposts → dev.appget.social.model
-- admin domain:  moderation_flags → dev.appget.admin.model
```

**View Mapping** (comment-based in views.sql — same `-- <domain> domain` pattern):
```
-- social domain: user_profile_view, post_detail_view,
                  comment_detail_view, feed_post_view → dev.appget.social.view
```

**Package Generation**:
- Domain `auth`   → `dev.appget.auth.model`
- Domain `social` → `dev.appget.social.model` / `dev.appget.social.view`
- Domain `admin`  → `dev.appget.admin.model`

---

## Metadata Field Naming Convention

`metadata.yaml` and `specs.yaml` use **snake_case** field names (`role_level`, `session_id`, `is_admin`). This is the language-agnostic canonical form, consistent with `models.yaml`. Each language's codegen applies its own casing: Java → `JavaNaming.toFieldAccessor`, Go → `GoNaming.toFieldAccessor`, Python/Rust/Ruby → identity. HTTP header derivation: `snakeToHeaderCase("role_level")` → `Role-Level` → `X-Roles-Role-Level`.

---

## Gherkin Business Rules (Feature Files)

### Overview

Business rules are defined in human-friendly Gherkin `.feature` files, one per domain. The `FeatureToSpecsConverter` parses these files with `io.cucumber:gherkin:38.0.0` and combines them with `metadata.yaml` to generate `specs.yaml`.

**Location**: `src/main/java/dev/appget/codegen/FeatureToSpecsConverter.java`

**See also**: [docs/GHERKIN_GUIDE.md](../docs/GHERKIN_GUIDE.md) for the complete DSL reference.

### Feature File DSL

**Feature-level tags**: `@domain:auth` assigns domain to all scenarios

**Scenario-level tags**:
- `@target:users` - target model/view name (snake_case plural, matches SQL table name)
- `@rule:UserEmailValidation` - explicit rule name
- `@blocking` - rule causes 422 rejection when unsatisfied
- `@view` - target is a view (not a model)

**Step patterns**:
| Purpose | Pattern | Example |
|---------|---------|---------|
| Simple condition | `When <field> <operator_phrase> <value>` | `When severity_level is greater than 8` |
| String condition | `When <field> <operator_phrase> "<value>"` | `When username does not equal ""` |
| Compound AND | `When all conditions are met:` + data table | See features/auth.feature |
| Compound OR | `When any condition is met:` + data table | |
| Metadata req | `Given <category> context requires:` + data table | `Given sso context requires:` |
| Success outcome | `Then status is "<value>"` | `Then status is "APPROVED"` |
| Failure outcome | `But otherwise status is "<value>"` | `But otherwise status is "REJECTED"` |

**Operator phrases** (natural language → symbol):
| Phrase | Symbol |
|--------|--------|
| equals | == |
| does not equal | != |
| is greater than | > |
| is less than | < |
| is at least | >= |
| is at most | <= |

### Feature Files

- `features/admin.feature` - 7 rules (ModerationFlags model)
- `features/auth.feature` - 10 rules (Users + Sessions models)
- `features/social.feature` - 10 rules (Posts, Comments, Follows models + 4 views)
- `metadata.yaml` - context POJOs (sso, roles, user, tenant) committed separately

### Important Gotchas

- `io.cucumber.messages.types.*` wildcard import conflicts with `java.lang.Exception` — always use specific imports
- "Otherwise" is not a standard Gherkin keyword — use `But otherwise` instead
- Gherkin version 38.0.0 (not 38.0.1 which doesn't exist)
- StringBuilder-based YAML output (not SnakeYAML dump) for exact format control

---

## Specification System

### Specification.java: Dual-Path Field Access (Descriptor + Reflection)

**Location**: `src/main/java/dev/appget/specification/Specification.java`

The `isSatisfiedBy(T target)` method uses a dual-path approach:
- **Protobuf messages** (`MessageOrBuilder`): Uses protobuf descriptor API (`getField(fieldDescriptor)`) — no reflection
- **Lombok POJOs** (metadata contexts like `SsoContext`, `RolesContext`): Falls back to reflection-based getter invocation

Handles:
- **Number comparison**: int, long, Integer, Long, Double via `doubleValue()`
- **BigDecimal comparison**: `compareTo()` for precise decimal comparison
- **Boolean comparison**: direct equality
- **String comparison**: `equals()`, `compareTo()` for ordering operators
- **Null handling**: equality/inequality checks for null values

### CompoundSpecification.java: AND/OR Logic

**Location**: `src/main/java/dev/appget/specification/CompoundSpecification.java`

Combines multiple `Specification` instances with AND or OR logic:
- `AND`: All specifications must be satisfied (`allMatch`)
- `OR`: At least one specification must be satisfied (`anyMatch`)

### MetadataContext.java: Authorization Context

**Location**: `src/main/java/dev/appget/specification/MetadataContext.java`

Stores typed POJO instances per category (sso, roles, user). Rules with `requires:` blocks check metadata context before evaluating the main condition.

### Rule.java: Generic Rule with Metadata Support

**Location**: `src/main/java/dev/appget/model/Rule.java`

- Accepts `Specification` or `CompoundSpecification` as spec
- `evaluate(T target)` and `evaluate(T target, MetadataContext metadata)`
- Checks metadata requirements first, then evaluates main spec
- Supports any target type via generics

---

## Language-Agnostic Intermediates

**models.yaml** and **specs.yaml** are the shared intermediate representations consumed by ALL language implementations (Java, Python, Go, Ruby, Rust, etc.).

### models.yaml (generated from schema.sql + views.sql)
- **Source**: SQLSchemaParser
- **Format**: YAML with per-domain model/view definitions
- **Content**: Field names (snake_case), types (Java types), domains
- **Usage**: ModelsToProtoConverter reads this to generate .proto files for ANY language
- **Stability**: Name, type, field number must be stable across language implementations

### specs.yaml (generated from features/*.feature + metadata.yaml)
- **Source**: FeatureToSpecsConverter
- **Format**: YAML with per-domain rules, metadata definitions, conditions, outcomes
- **Content**: Rule names, target models, conditions (simple + compound), metadata requirements, blocking flags, status values
- **Usage**: RuleEngine loads these directly (no proto option parsing); all language implementations use same rules

### Critical Principle
**No generator should directly parse schema.sql, views.sql, or .feature files** — these are internal implementation details of the Java subproject. Future language implementations will:
1. Import models.yaml and specs.yaml from the appget.dev/java project
2. Run their own generators against these intermediates
3. Generate language-specific code independently
4. Load rules from specs.yaml at runtime (not from proto)

---

## Code Generation Strategy

### ModelsToProtoConverter + protoc: models.yaml → .proto → Java Models + Views

**Input**: `models.yaml`
**Output**: `.proto` files → protoc → Java protobuf model + view classes in `build/generated/`
**Note**: Rules are NOT embedded in proto. All generators that need rules read `specs.yaml` directly.

### SpecificationGenerator: YAML → Specifications + Metadata POJOs

**Input**: `specs.yaml` + `models.yaml`
**Output**: Specification classes in `specification/generated/` + metadata POJOs in `specification/context/`

**specs.yaml Format**:
```yaml
metadata:
  sso:
    fields:
      - name: authenticated
        type: boolean
      - name: session_id
        type: String

rules:
  - name: UserEmailValidation
    target:
      type: model
      name: users
      domain: auth
    blocking: true             # Causes 422 rejection if unsatisfied
    conditions:
      - field: email
        operator: "!="
        value: ""
    then:
      status: "VALID_EMAIL"
    else:
      status: "INVALID_EMAIL"
```

**Condition shapes**:
- **Simple list**: `conditions: [{ field, operator, value }]`
- **Compound object**: `conditions: { operator: AND/OR, clauses: [...] }`

**Target resolution**: Uses `models.yaml` to resolve `domain:type:name` → fully qualified import path

### ProtoOpenAPIGenerator: Proto → REST Contract

**Input**: `.proto` files (generated by ModelsToProtoConverter)
**Output**: `openapi.yaml` (OpenAPI 3.0.0 REST API specification with full CRUD, security)

View paths are generated automatically alongside model paths. Each view gets two GET-only paths: `/views/{kebab-name}` (list) and `/views/{kebab-name}/{id}` (by ID). No POST/PUT/DELETE for views.

URL transform: `post_detail_view` → strip `_view` → `post_detail` → kebab → `post-detail` → `/views/post-detail`

---

## ServerEmitter Abstraction

`AppServerGenerator` is a framework-agnostic orchestrator that delegates all code emission to a `ServerEmitter` interface. `SpringBootEmitter` is the sole implementation.

**Location**: `src/main/java/dev/appget/codegen/AppServerGenerator.java` (orchestrator, ~650 lines)

| File | Purpose | Lines |
|------|---------|-------|
| `ServerEmitter.java` | Interface — 22 methods (one per output file) | ~270 |
| `SpringBootEmitter.java` | All Spring Boot strings (annotations, imports, class structures) | ~1400 |
| `EntityContext.java` | Per-model/view data passed to emitter (name, PK info, resource path) | ~110 |
| `MetadataEmitContext.java` | Metadata categories + field definitions for emitter | ~45 |
| `RuleEmitContext.java` | Pre-filtered rules + blocking map + target map for emitter | ~100 |

**Rule**: To change generated server code, edit `SpringBootEmitter.java` — never `AppServerGenerator.java` (which only handles orchestration and file I/O). To add a new framework, implement `ServerEmitter`.

---

## Generated Server Components

**Per-Model**: 4 files each (Interface + InMemory impl + Service + Controller) — full CRUD (POST, GET, PUT, DELETE), rule validation via RuleService.

**Per-View**: 4 files each (Interface + InMemory impl + Service + Controller) — GET-only (`/views/{kebab-name}` list, `/views/{kebab-name}/{id}` by ID). No RuleService injection. View import path uses `.view.` not `.model.`

**Per-Model**:
- `{Model}Controller.java` - REST endpoints (POST, GET, PUT, DELETE)
- `{Model}Service.java` - Business logic with rule validation
- `{Model}Repository.java` - In-memory storage (ConcurrentHashMap)

**Infrastructure (11 files)**:
- `Application.java` - Spring Boot entry point (@SpringBootApplication)
- `config/MetadataExtractor.java` - Extract auth headers → MetadataContext
- `service/RuleService.java` - Load + evaluate rules from specs.yaml
- `dto/RuleAwareResponse.java` - Response with rule evaluation results
- `dto/RuleEvaluationResult.java` - Rule outcomes
- `dto/RuleOutcome.java` - Individual rule result
- `dto/ErrorResponse.java` - Error format
- `exception/GlobalExceptionHandler.java` - @ControllerAdvice error handling (5 handlers)
- `exception/RuleViolationException.java` - Rule validation failure
- `exception/ResourceNotFoundException.java` - Not found errors
- `exception/MetadataParsingException.java` - Invalid metadata header type
- `application.yaml` - Spring Boot configuration

**Key Features**:
- Automatic CRUD endpoints for all models
- Rules validated on POST/PUT (before save)
- Only blocking rules cause 422 rejection; informational rules reported in response
- RuleService uses pre-compiled spec classes directly (no runtime YAML parsing)
- MetadataExtractor reads typed HTTP headers into context POJOs via builder pattern
- Constructor injection for all dependencies
- Proper HTTP status codes (201, 200, 204, 422, 404, 400)
- In-memory repository (no database dependency for MVP)

---

## Generated Server Error Handling

GlobalExceptionHandler has 5 handlers in priority order:
- `RuleViolationException` → 422 UNPROCESSABLE_ENTITY (blocking rule failure)
- `ResourceNotFoundException` → 404 NOT_FOUND
- `MetadataParsingException` → 400 BAD_REQUEST (invalid metadata header type)
- `HttpMessageNotReadableException` → 400 BAD_REQUEST (malformed JSON body)
- `Exception` (catch-all) → 500 INTERNAL_SERVER_ERROR

All responses use the `ErrorResponse` DTO with `OffsetDateTime` timestamps (RFC 3339).

**Jackson gotcha**: Use direct `ObjectMapper` construction with `mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)`. `Jackson2ObjectMapperBuilder.featuresToDisable()` is unreliable when combined with `.modules()` and `ProtobufModule`.

---

## RuleEngine Details

**Location**: `src/main/java/dev/appget/RuleEngine.java`

The RuleEngine loads business rules from specs.yaml using `loadRulesFromSpecs(String specsFile)`:

```java
// Parse specs.yaml into Rule objects with pre-built Specification/CompoundSpecification
List<Rule<?>> rules = ruleEngine.loadRulesFromSpecs("specs.yaml");
rules.forEach(rule -> evaluate(target, metadata));
```

**Why from specs.yaml, not from proto?**
- specs.yaml is the source of truth for rules (generated from features + metadata)
- Proto contains only schema (model definitions, field types)
- Separating schema from policy enables rules to be updated without regenerating proto files
- This pattern is portable across all future language implementations

**Internal Pattern**:
The `buildSpec()` method converts YAML condition objects into Specification or CompoundSpecification instances:

```java
// Simple condition: { field: age, operator: ">", value: 18 }
// → Specification("age", ">", 18)

// Compound condition: { operator: "AND", clauses: [...] }
// → CompoundSpecification(Logic.AND, [Specification(...), ...])
```

**Pattern for Future Implementations**:
All language implementations should:
1. Use protoc to generate models from shared .proto files
2. Load rules directly from specs.yaml (not from proto custom options)
3. Implement a RuleEngine equivalent that parses specs.yaml and evaluates conditions against protobuf model instances
4. The logic is language-agnostic: parse condition objects, build specification instances, evaluate against target

---

## RuleService & MetadataExtractor (Generated Patterns)

**RuleService Generated Pattern**:
```java
// Direct instantiation (no runtime YAML loading)
private final UserEmailValidation userEmailValidation = new UserEmailValidation();
private final AdminAuthorizationRequired adminAuthorizationRequired = new AdminAuthorizationRequired();

// instanceof grouping per target model
if (target instanceof Users) {
    // blocking rules set hasFailures = true when unsatisfied
    // informational rules just report outcome
    // metadata-aware rules use evaluate(target, metadata)
}
```

**MetadataExtractor Generated Pattern**:
```java
// Header convention: X-{Category}-{SnakeToHeaderCase(field_name)}
String ssoAuthenticated = request.getHeader("X-Sso-Authenticated");
// Type-aware parsing: boolean → Boolean.parseBoolean, int → Integer.parseInt
SsoContext ssoContext = SsoContext.builder()
    .authenticated(Boolean.parseBoolean(ssoAuthenticated))
    .build();
context.with("sso", ssoContext);
```

**Bridge Architecture** (RuleService → Specification classes):
```
specs.yaml defines rules → SpecificationGenerator creates spec classes
                                       ↓
AppServerGenerator reads specs.yaml
  → generates RuleService that instantiates spec classes directly
  → generates MetadataExtractor that reads X-{Category}-{Field} headers
  → no runtime YAML parsing, no @PostConstruct, no reflection
```

**Blocking Rules** (`specs.yaml`):
- `blocking: true` → unsatisfied rule causes 422 rejection (hasFailures = true)
- `blocking: false` (default) → informational, reported in outcomes but never blocks
- Currently blocking: UserEmailValidation, UserSuspensionCheck, SessionActiveValidation, SessionTokenPresence, VerifiedUserRequirement, UsernamePresence, SeverityLevelValidation, ReasonPresence, AdminAuthorizationRequired, HighSeverityEscalation, ContentTargetValidation, PublicPostVerification, PostContentValidation, CommentCreationValidation, ActiveFollowValidation

---

## Generated Server Classpath Restriction

The generated-server `build.gradle` excludes `**/codegen/**` from its source sets. Classes in `dev.appget.specification` (like `Specification.java`) CANNOT import from `dev.appget.codegen`. If utilities are needed at runtime, move them to a runtime-accessible package (e.g., `dev.appget.naming`) — never duplicate logic to work around the restriction.

---

## HtmlCrudGenerator

**Location**: `src/main/java/dev/appget/codegen/HtmlCrudGenerator.java`

**Input**: `models.yaml` + `specs.yaml` (optional)
**Output**: 67 static HTML files in `generated-html/` (git-ignored)

Standalone generator (NOT an emitter). Generates per-model pages (index/create/edit/view) and per-view pages (read-only index). Form actions match `SpringBootEmitter` REST routes (`/{resource}`). Static structural scaffold — list/detail pages do not display live data.

---

## HTTP Test Harness (YAML-Driven)

**Spec**: `tests/http-tests.yaml` — 28 endpoint tests (CRUD + views + error paths)
**Runner**: `tests/run-http-tests.py` — Python script, reads YAML, executes curl, colored output
**Target**: `make test-http` (requires server running on port 8080)
**Agent**: `~/.claude/agents/http-tester.md` — generic agent for any project's HTTP tests

---

## Logging Configuration

### Overview

All non-generated Java classes include Log4j2 logging for debugging and operation tracing.

**File**: `src/main/resources/log4j2.properties`

**Log Levels**:
- **DEBUG**: Method entry/exit, detailed operation flow
- **INFO**: Important milestones — file loading, rule counts, evaluation results
- **WARN**: Non-critical issues (missing files, skipped operations)
- **ERROR**: Exceptions and error conditions with stack traces

### Classes with Logging

All non-generated classes have logging:
- `FeatureToSpecsConverter.java` - Gherkin .feature parsing, specs.yaml generation
- `SQLSchemaParser.java` - Schema parsing, table/view extraction, YAML generation
- `ModelsToProtoConverter.java` - models.yaml + specs → .proto file generation
- `SpecificationGenerator.java` - Specification and metadata POJO generation
- `ProtoOpenAPIGenerator.java` - Proto-first OpenAPI spec generation
- `AppServerGenerator.java` - Spring Boot REST API generation
- `RuleEngine.java` - Loading rules from specs.yaml and evaluating them
- `Specification.java` - Specification evaluation with field resolution
- `CompoundSpecification.java` - AND/OR compound condition evaluation
- `MetadataContext.java` - Metadata context management
- `DescriptorRegistry.java` - Dynamic model discovery and registration
- `DefaultDataBuilder.java` - Sample data generation
- `Rule.java` - Rule evaluation with metadata requirements

### Log Format

```
[ISO8601_TIMESTAMP] [LEVEL] [THREAD] [CLASS] - message
[2026-02-10T18:48:07,277] DEBUG [main] RuleEngine - Entering main method
```

### Adjusting Logging Levels

Edit `src/main/resources/log4j2.properties`:

```properties
# Development with detailed tracing
logger.dev_appget_codegen.level = DEBUG

# Production with minimal logging
logger.dev_appget_codegen.level = INFO
```

Package-specific loggers:
- `dev.appget.codegen` - Code generators (DEBUG by default)
- `dev.appget.specification` - Specification evaluation (DEBUG by default)
- `dev.appget.model` - Rule evaluation (DEBUG by default)
- `dev.appget` - RuleEngine main (DEBUG by default)

Generator logger pattern:
```java
private static final Logger logger = LogManager.getLogger(ClassName.class);
```

---

## Testing Strategy

### Test Suite Overview

**Comprehensive unit tests** in 16 suites covering all components (0 failures, 0 errors expected):

#### Feature To Specs Converter Tests
- Gherkin `.feature` file parsing
- Simple condition extraction (6 operators)
- Value coercion (integer, boolean, string)
- YAML value formatting
- Feature file parsing (appget.feature → 6 rules, hr.feature → 1 rule)
- Rule structure verification (target, conditions, blocking, metadata)
- Full conversion with metadata + rules
- Structural equivalence with original specs.yaml

#### Models To Proto Converter Tests
- Proto file generation from models.yaml
- Field type mapping (Java type → proto type)
- No rule options embedded in generated proto
- Service CRUD operations
- View proto generation

#### Proto-First OpenAPI Generator Tests
- Proto-first OpenAPI 3.0.0 specification generation
- Schema definitions for all models
- Full CRUD endpoint generation (GET, POST, PUT, DELETE)
- Type mapping validation (proto → OpenAPI)
- Security (Bearer auth) and operationId/tags

#### Specification Generator Tests
- YAML rule parsing and Java class generation
- Compound specification generation (AND/OR)
- Metadata context POJO generation (SsoContext, RolesContext, UserContext)
- View-targeting specification generation
- Proper package structure and imports

#### App Server Generator Tests
- RuleService has no TODO stubs
- MetadataExtractor has no TODO stubs
- RuleService imports and instantiates pre-compiled spec classes
- RuleService skips view-targeting rules
- RuleService groups by instanceof
- RuleService uses metadata-aware evaluate for auth rules
- RuleService has no @PostConstruct (no runtime YAML)
- Blocking logic: only blocking rules set hasFailures
- MetadataExtractor imports context POJOs
- MetadataExtractor reads correct X-{Category}-{Field} headers
- MetadataExtractor uses builder pattern with context.with()

#### gRPC Service Stub Tests
- Service stub existence and CRUD method descriptors
- All 5 domain services verified

#### Rule Engine Tests
- RuleEngine loads rules from specs.yaml (not from proto custom options)
- Generic rule evaluation with any model/view
- Compound specification evaluation
- Metadata requirement validation
- Custom status values
- Multiple rule consistency
- View field evaluated against wrong model type returns failure (type mismatch guard)
- View field evaluated against correct view type succeeds

#### Compound Specification Tests
- AND logic (all conditions must be true)
- OR logic (at least one condition true)
- Single condition edge cases

#### Metadata Context Tests
- Category storage and retrieval
- Reflection-based POJO evaluation (metadata contexts are Lombok)
- Missing category handling

#### Specification Pattern Tests
- Comparison operators: >, <, >=, <=, ==, !=
- Type handling: Number, BigDecimal, Boolean, String
- Edge cases and boundary values
- Invalid field/operator handling

#### Descriptor Registry Tests
- Registry contains all 7 models and views
- Lookup by name for each model/view
- Unknown model returns null
- Field descriptors accessible

#### Test Data Builder Tests
- Build Employees/Salaries/View with generic defaults
- String fields get "Sample_" prefix
- Int fields default to 42, double to 42.0

### Test Organization

```
src/test/java/dev/appget/
├── codegen/
│   ├── AppServerGeneratorTest.java
│   ├── CodeGenUtilsTest.java
│   ├── FeatureToSpecsConverterTest.java
│   ├── JavaTypeRegistryTest.java
│   ├── JavaUtilsTest.java
│   ├── ModelsToProtoConverterTest.java
│   ├── ProtoOpenAPIGeneratorTest.java
│   └── SpecificationGeneratorTest.java
├── conformance/
│   └── ConformanceTest.java
├── model/
│   └── RuleTest.java
├── service/
│   └── GrpcServiceTest.java
├── specification/
│   ├── CompoundSpecificationTest.java
│   ├── MetadataContextTest.java
│   └── SpecificationTest.java
└── util/
    ├── DescriptorRegistryTest.java
    └── DefaultDataBuilderTest.java
```

### Test Fixtures

- `@TempDir`: Temporary directories for generated code output
- `@BeforeEach`: Setup with protobuf `newBuilder()` for fluent test object creation
- `@DisplayName`: Descriptive test names in test runner output

### Conformance Test Fixture

`src/test/resources/conformance/inputs/metadata.yaml` mirrors the project's `metadata.yaml` format.
When changing metadata.yaml structure (e.g., adding `enabled` field), update this fixture too or the conformance test fails.

### Test Execution

**Gotcha:** Always use `./gradlew`, never system `gradle`. The project requires Java 25; the system Gradle (snap) uses Java 21 and will fail to compile.

**Run all tests**:
```bash
make test
```

**Run specific test class**:
```bash
./gradlew test --tests "dev.appget.specification.CompoundSpecificationTest"
```

**Run specific test method**:
```bash
./gradlew test --tests "dev.appget.model.RuleTest.testRuleWithCompoundSpecification"
```

**Run with full pipeline** (recommended):
```bash
make all
# Runs: clean → parse-schema → generate → test → build
```

---

## Build Artifacts

After running tests, generated code in:
```
src/main/java-generated/
├── dev/appget/model/                     (5 models)
├── dev/appget/view/                      (1 view)
├── dev/appget/hr/model/                  (2 models)
├── dev/appget/hr/view/                   (1 view)
├── dev/appget/finance/model/             (1 model)
└── dev/appget/specification/
    ├── generated/                        (6 specs)
    └── context/                          (3 metadata POJOs)
```

Compiled output in:
```
build/
├── classes/main/              (compiled .class files)
├── classes/test/              (test classes)
├── libs/appget.dev-java.jar  (executable JAR)
└── reports/tests/test/        (HTML test report)
```

---

## Performance Benchmarks

- `make clean`: ~0.7s
- `make parse-schema`: ~0.9s
- `make generate`: ~1s
- `make test`: ~2s (0 failures expected)
- `make build`: ~1s
- `make all`: ~5-6s total

---

**Last Updated**: 2026-04-03
**Moved from**: java/CLAUDE.md (deep reference content extracted to reduce context load)
