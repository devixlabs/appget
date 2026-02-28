# CLAUDE.md - Java Subproject Technical Guidance

This file provides Claude Code with Java-specific guidance for the appget.dev/java SQL-first code generation system.

---

## Project Context

**appget.dev/java** is a production-ready code generation system within the DevixLabs platform. It converts Gherkin business rules and database schemas into fully typed, tested Java domain models with business rule specifications, compound conditions, and metadata-aware authorization.

**Key Responsibility**: Gherkin-first business rules, schema-first Java model generation with protobuf descriptor-based specifications, compound AND/OR logic, metadata authorization, blocking/informational rule enforcement, and comprehensive test coverage (0 failures, 0 errors expected).

---

## Architecture Overview

### Multi-Layer Generation Pipeline

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
│ DefaultDataBuilder: DynamicMessage samples         │
│ No hard-coded imports or if/else dispatch      │
└─────────────────────────────────────────────────┘
```

### Build Dependency Resolution

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

## Portability: Avoiding Java-Specific Patterns

**Critical**: appget.dev/java is a code generation platform targeting Go, Python, and Ruby. To keep generated code and examples portable across languages, avoid these Java idioms:

### Anti-Patterns to Avoid

| Anti-Pattern | Problem | Use Instead |
|--------------|---------|-------------|
| **Switch expressions** (`case -> value`) | Java 14+ only, no equivalent in Go/Python/Ruby | if-else chains |
| **Pattern matching vars** (`instanceof Type var`) | Java 25+ only, requires explicit casting in other languages | Explicit casting: `Type var = (Type) obj` |
| **Static initialization blocks** (`static { map.put(...) }`) | Not portable; replaced by package-level initialization in Go/Python/Ruby | Static factory methods returning initialized maps |
| **Method overloading** | Not supported in Python/Ruby; confusing in Go/Rust | Builder pattern for multiple constructor signatures |

### Examples

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

**All implementations use the same .proto files** (generated from models.yaml) as source of truth, then layer language-specific generators on top (like AppServerGenerator for Java, Django server generation for Python, etc.).

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

## Gherkin Business Rules (Feature Files)

### Overview

Business rules are defined in human-friendly Gherkin `.feature` files, one per domain. The `FeatureToSpecsConverter` parses these files with `io.cucumber:gherkin:38.0.0` and combines them with `metadata.yaml` to generate `specs.yaml` (the intermediate representation consumed by all downstream generators).

**Location**: `src/main/java/dev/appget/codegen/FeatureToSpecsConverter.java`

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

This ensures that business logic defined once in features/ is automatically available in all language implementations without re-implementing the Gherkin parser.

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
      - name: sessionId
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

### AppServerGenerator: YAML → Complete REST API Server

**Location**: `src/main/java/dev/appget/codegen/AppServerGenerator.java`

**Input**: `models.yaml` + `specs.yaml`
**Output**: Complete Spring Boot REST API server in `generated-server/dev/appget/server/`

**Generated Components**:

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
- `exception/GlobalExceptionHandler.java` - @ControllerAdvice error handling
- `exception/RuleViolationException.java` - Rule validation failure
- `exception/ResourceNotFoundException.java` - Not found errors
- `application.yaml` - Spring Boot configuration

**Key Features**:
- ✓ Automatic CRUD endpoints for all models
- ✓ Rules validated on POST/PUT (before save)
- ✓ Only blocking rules cause 422 rejection; informational rules reported in response
- ✓ RuleService uses pre-compiled spec classes directly (no runtime YAML parsing)
- ✓ MetadataExtractor reads typed HTTP headers into context POJOs via builder pattern
- ✓ Constructor injection for all dependencies
- ✓ Proper HTTP status codes (201 Created, 200 OK, 204 No Content, 422 Unprocessable Entity, 404 Not Found)
- ✓ In-memory repository (no database dependency for MVP)

**Bridge Architecture** (RuleService → Specification classes):
```
specs.yaml defines rules → SpecificationGenerator creates spec classes
                                       ↓
AppServerGenerator reads specs.yaml
  → generates RuleService that instantiates spec classes directly
  → generates MetadataExtractor that reads X-{Category}-{Field} headers
  → no runtime YAML parsing, no @PostConstruct, no reflection
```

### RuleEngine.java: Specs.yaml-Driven Rule Loading

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
- This pattern is portable across all future language implementations (Python, Go, Ruby, etc.)

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
// Header convention: X-{Category}-{CamelToKebab(fieldName)}
String ssoAuthenticated = request.getHeader("X-Sso-Authenticated");
// Type-aware parsing: boolean → Boolean.parseBoolean, int → Integer.parseInt
SsoContext ssoContext = SsoContext.builder()
    .authenticated(Boolean.parseBoolean(ssoAuthenticated))
    .build();
context.with("sso", ssoContext);
```

**Blocking Rules** (`specs.yaml`):
- `blocking: true` → unsatisfied rule causes 422 rejection (hasFailures = true)
- `blocking: false` (default) → informational, reported in outcomes but never blocks
- Currently blocking: UserEmailValidation, UserSuspensionCheck, SessionActiveValidation, SessionTokenPresence, VerifiedUserRequirement, UsernamePresence, SeverityLevelValidation, ReasonPresence, AdminAuthorizationRequired, HighSeverityEscalation, ContentTargetValidation, PublicPostVerification, PostContentValidation, CommentCreationValidation, ActiveFollowValidation

**Usage**:
```bash
make generate-server
# Generates: generated-server/dev/appget/server/

make run-server
# Builds + starts Spring Boot on http://localhost:8080

curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -H "X-Sso-Authenticated: true" \
  -H "X-Roles-Is-Admin: true" \
  -d '{"username":"alice","email":"alice@example.com","isVerified":true,"isSuspended":false}'
# Returns: 201 Created with ruleResults
```

**Integration with Specification Classes**:
- RuleService directly instantiates pre-compiled spec classes (no YAML loading at runtime)
- Service layer calls `ruleService.evaluateAll(target, metadata)` before save
- MetadataExtractor builds typed MetadataContext POJOs from HTTP headers
- Only blocking rules prevent entity creation/update (422 response)

---

## Dependencies & Versions

### Required
- **Java**: 25+ (tested with OpenJDK 25)
- **Gradle**: 9.3.1+
- **Lombok**: 1.18.38+ (Java 25 compatibility critical)

### Included
- **Gherkin**: 38.0.0 (Cucumber Gherkin parser for `.feature` files)
- **JSQLParser**: 5.3 (SQL parsing, multi-dialect support)
- **SnakeYAML**: 2.2 (YAML parsing)
- **Log4j2**: 2.23.1 (logging and debugging)
- **JUnit 5**: 5.11.3 (testing framework)

---

## Development Workflow

### Large-Scale Refactoring Workflow

When refactoring across multiple files (e.g., updating patterns, simplifying code, improving portability):

1. **Plan with tasks**: Use `TaskCreate` to define each logical change (e.g., "Replace switch expressions in Specification.java")
2. **Group by file**: One task per source file, one final test task (easier to track progress)
3. **Use precise string matching**: When replacing patterns, use `replace_all: true` with exact strings to avoid partial matches across test files
4. **Edit dependencies**: Run edits in order (e.g., update core files before test files that import them)
5. **Test after groups**: Run `make test` after each file group (not after each edit) to catch issues early
6. **Preserve logging**: Do NOT remove logging during refactoring—Log4j2 debugging is critical for troubleshooting
7. **Final validation**: Run `make clean && make all` to ensure generated code still integrates correctly after all changes

**Example workflow**: Refactoring 7 patterns across 5 files → 7 tasks (one per pattern) + 1 test task = 8 total, all tracked

---

### Adding a New Table

1. Edit `schema.sql`
2. Add domain mapping in SQLSchemaParser if needed
3. Run: `make parse-schema && make generate && make test`

### Adding a New View

1. Edit `views.sql` with `CREATE VIEW` statement
2. Add view domain mapping in SQLSchemaParser (`VIEW_DOMAIN_MAPPING`)
3. Run: `make parse-schema && make generate && make test`

### Adding New Business Rules

1. Edit `features/<domain>.feature` - add a new `Scenario` with `@target`, `@rule` tags
2. Use `When <field> <operator_phrase> <value>` for conditions
3. Use `Then status is "<value>"` and `But otherwise status is "<value>"` for outcomes
4. Run: `make features-to-specs && make generate-specs && make test`

### Adding a New Domain

1. Create `features/<domain>.feature` with `@domain:<name>` tag
2. Add tables to `schema.sql` and domain mapping in SQLSchemaParser
3. Run: `make clean && make all`

### Adding New Metadata Categories

`metadata.yaml` is a curated registry with an `enabled: true/false` toggle per category. It ships with 14 built-in categories (3 pre-enabled: `sso`, `user`, `roles`).

**To enable a built-in category**: Set `enabled: true` on the category in `metadata.yaml`.

**To add a custom category**: Add a new entry at the bottom of `metadata.yaml` with the same format:
```yaml
  my_category:
    enabled: true
    description: "What this category provides"
    fields:
      - name: myField
        type: String
```

**Validation**: The pipeline validates all `Given <category> context requires:` references at build time:
- Unknown category → error
- Disabled category → error with guidance to enable
- Unknown field in enabled category → error

Run: `make features-to-specs && make generate-specs && make test`
Generated POJO appears in `specification/context/`

### Full Development Cycle

```bash
vim schema.sql                # 1. Modify schema
vim views.sql                 # 2. Modify views (optional)
vim features/appget.feature   # 3. Modify business rules
vim metadata.yaml             # 4. Modify metadata (optional)
make clean && make all        # 5. Full pipeline
make run                      # 6. Execute
```

---

## Make Target Details

### make clean
- Removes: `build/`, `src/main/java-generated/`, `specs.yaml`

### make features-to-specs
- Runs: `FeatureToSpecsConverter features metadata.yaml specs.yaml`
- Input: `features/*.feature` + `metadata.yaml`
- Output: `specs.yaml` (generated intermediate, git-ignored)

### make parse-schema
- Runs: `SQLSchemaParser schema.sql views.sql models.yaml`
- Input: `schema.sql` + `views.sql`
- Output: `models.yaml` (models + views)

### make generate-proto
- Runs: `ModelsToProtoConverter` + `protoc` (models.yaml + specs → .proto → Java)
- Output: Protobuf model + view classes in `build/generated/`

### make generate-specs
- Runs: `SpecificationGenerator specs.yaml models.yaml src/main/java-generated/`
- Output: Specification classes + metadata POJOs

### make generate-openapi
- Runs: `ProtoOpenAPIGenerator` (.proto files → openapi.yaml)
- Output: OpenAPI 3.0.0 spec with full CRUD and security

### make generate-server
- Runs: `AppServerGenerator models.yaml specs.yaml generated-server/`
- Output: Complete Spring Boot REST API server
- Location: `generated-server/dev/appget/server/`
- Note: Separate from main build, independent generation

### make run-server
- Builds generated Spring Boot server
- Starts server on http://localhost:8080
- Requires: `make generate-server` first
- Note: Requires Spring Boot dependencies installed

### make test
- Runs: `gradle test` (0 failures, 0 errors expected)

### make build
- Full pipeline: parse → generate → compile → package

### make all
- Runs: `clean → generate → test → build`
- Note: Does NOT include generate-server (separate optional step)

---

## Logging Configuration

### Overview

All non-generated Java classes include Log4j2 logging for debugging and operation tracing:

**File**: `src/main/resources/log4j2.properties`

**Log Levels**:
- **DEBUG**: Method entry/exit, detailed operation flow (useful for development/debugging)
- **INFO**: Important milestones - file loading, rule counts, evaluation results
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
- `RuleEngine.java` - Main rule engine execution and rule loading

### Log Format

```
[ISO8601_TIMESTAMP] [LEVEL] [THREAD] [CLASS] - message
[2026-02-10T18:48:07,277] DEBUG [main] RuleEngine - Entering main method
```

### Adjusting Logging Levels

Edit `src/main/resources/log4j2.properties` to change logging verbosity:

```properties
# Development with detailed tracing
logger.dev_appget_codegen.level = DEBUG

# Production with minimal logging
logger.dev_appget_codegen.level = INFO
```

Package-specific loggers can be configured independently:
- `dev.appget.codegen` - Code generators (DEBUG by default)
- `dev.appget.specification` - Specification evaluation (DEBUG by default)
- `dev.appget.model` - Rule evaluation (DEBUG by default)
- `dev.appget` - RuleEngine main (DEBUG by default)

### Generator Logger Instances

Generators (`SQLSchemaParser`, `ModelsToProtoConverter`, `SpecificationGenerator`, etc.) create static final logger fields:
```java
private static final Logger logger = LogManager.getLogger(ClassName.class);
```

These are created once and reused efficiently across all invocations.

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

**Run all tests**:
```bash
make test
```

**Run specific test class**:
```bash
gradle test --tests "dev.appget.specification.CompoundSpecificationTest"
```

**Run specific test method**:
```bash
gradle test --tests "dev.appget.model.RuleTest.testRuleWithCompoundSpecification"
```

**Run with full pipeline** (recommended):
```bash
make all
# Runs: clean → parse-schema → generate → test → build
```

### End-to-End Verification Checklist

After any source change (generators, schema, features, metadata), run these steps in order to confirm the full pipeline works:

1. **`make all`** — MUST pass cleanly (all unit tests green, build successful)
2. **Inspect `generated-server/test-api.sh`** — all generated API endpoints should have 200-level happy-path test scenarios (POST → 201, GET → 200, PUT → 200, DELETE → 204)
3. **`make run-server`** (starts Spring Boot on port 8080), then in a separate terminal **`make test-api`** — all API tests should pass with exit 0

If step 3 fails, check server logs for `NoSuchMethodException` (reflection bugs) or `422 Unprocessable Entity` (rule violations from bad test data or missing metadata headers).

### Build Artifact Generation

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

### Performance Benchmarks

- `make clean`: ~0.7s
- `make parse-schema`: ~0.9s
- `make generate`: ~1s
- `make test`: ~2s (0 failures expected)
- `make build`: ~1s
- `make all`: ~5-6s total

---

## Common Patterns & Anti-Patterns

### DO: Keep schema.sql as source of truth
### DON'T: Manually edit models.yaml
### DO: Run tests before committing (`make all`)
### DON'T: Commit generated code (`src/main/java-generated/`)
### DO: Use meaningful table/view names
### DON'T: Mix manual and generated models

---

## Troubleshooting Guide

### Issue: `FileNotFoundException: schema.sql`
**Solution**: Ensure `schema.sql` exists at project root

### Issue: `duplicate class`
**Solution**: Delete manual version from `src/main/java/`, keep generated version

### Issue: Tests fail after schema changes
**Solution**: `make clean && make all`

### Issue: View column type not resolved
**Solution**: Ensure the source table is defined in `schema.sql` before the view references it

### Issue: Metadata POJO not generated
**Solution**: Ensure `metadata:` section exists in `specs.yaml` with proper format

---

## Git Integration

### .gitignore Entries
```
src/main/java-generated/
generated-server/
specs.yaml
models.yaml
openapi.yaml
build/
.gradle/
```

### Commit Strategy
```
DO commit:
  - features/*.feature (Gherkin business rules - source of truth)
  - metadata.yaml (context POJO definitions)
  - schema.sql, views.sql (SQL source of truth)
  - src/main/java/, src/test/ (manual code)
  - build.gradle, Makefile (build config)
  - README.md, CLAUDE.md, PIPELINE.md (documentation)

DON'T commit (all auto-generated):
  - specs.yaml (generated from features + metadata)
  - models.yaml, openapi.yaml (generated from schema)
  - src/main/java-generated/ (generated models + specs)
  - generated-server/ (generated Spring Boot server)
  - build/, .gradle/ (build artifacts)
```

---

## Files to Know

| File | Purpose |
|------|---------|
| `features/appget.feature` | Appget domain business rules (Gherkin, 6 rules) |
| `features/hr.feature` | HR domain business rules (Gherkin, 1 rule) |
| `metadata.yaml` | Context POJO definitions (sso, roles, user, location) |
| `schema.sql` | SQL source of truth (database tables) |
| `views.sql` | SQL view definitions (composite models) |
| `specs.yaml` | Auto-generated rules + metadata YAML (git-ignored) |
| `models.yaml` | Auto-generated model + view definitions (git-ignored) |
| `build.gradle` | Gradle build config (task dependencies) |
| `Makefile` | User-facing build commands |
| `src/main/java/dev/appget/codegen/FeatureToSpecsConverter.java` | Gherkin .feature files + metadata.yaml → specs.yaml |
| `src/main/java/dev/appget/codegen/SQLSchemaParser.java` | SQL + views → YAML generator |
| `src/main/java/dev/appget/codegen/ModelsToProtoConverter.java` | models.yaml + specs → .proto files (models, views, services, rules) |
| `src/main/java/dev/appget/codegen/SpecificationGenerator.java` | YAML → Spec + metadata POJO generator |
| `src/main/java/dev/appget/codegen/DescriptorRegistryGenerator.java` | YAML → Auto-generated DescriptorRegistry |
| `src/main/java/dev/appget/codegen/ProtoOpenAPIGenerator.java` | .proto files → OpenAPI 3.0 YAML (full CRUD, security) |
| `src/main/java/dev/appget/codegen/AppServerGenerator.java` | Models + specs → Complete REST API server |
| `src/main/java/dev/appget/specification/Specification.java` | Dual-path evaluation (descriptor + reflection) |
| `src/main/java/dev/appget/specification/CompoundSpecification.java` | AND/OR compound logic |
| `src/main/java/dev/appget/specification/MetadataContext.java` | Authorization metadata holder |
| `src/main/java/dev/appget/RuleEngine.java` | Loads rules from specs.yaml, evaluates against protobuf model instances |
| `src/main/java/dev/appget/model/Rule.java` | Generic rule with metadata support |
| `src/main/java-generated/dev/appget/util/DescriptorRegistry.java` | Auto-generated protobuf descriptor registry (from models.yaml) |
| `src/main/java/dev/appget/util/DefaultDataBuilder.java` | DynamicMessage-based sample data builder |
| `src/main/java/dev/appget/RuleEngine.java` | Descriptor-driven rule evaluation engine |
| `generated-server/` | Auto-generated Spring Boot REST API (git-ignored) |
| `src/test/java/dev/appget/...` | Unit tests across 16 suites (0 failures expected) |

---

## References

- [PIPELINE.md](PIPELINE.md) - Complete pipeline architecture
- [README.md](README.md) - User-facing documentation
- [Lombok](https://projectlombok.org/)
- [JSQLParser](https://github.com/JSQLParser/JSqlParser)
- [Gradle](https://gradle.org/)
- [JUnit 5](https://junit.org/junit5/)
- [Log4j2](https://logging.apache.org/log4j/2.x/)

---

## Summary

**appget.dev/java** demonstrates a complete, production-ready code generation system:

### Core Generation Pipeline
1. **Gherkin-first business rules**: Human-friendly `.feature` files as source of truth
2. **Schema-first models**: Database + views define domain models
3. **Multi-dialect support**: Works with any SQL database (MySQL, PostgreSQL, SQLite, Oracle, MSSQL)
4. **Type safety**: Complete SQL → Java type mapping with view column resolution
5. **REST API contract**: Auto-generated OpenAPI 3.0.0 specification

### Business Logic & Authorization
6. **Descriptor-based specifications**: Protobuf descriptor API for model/view, reflection for metadata POJOs
7. **Compound conditions**: AND/OR logic for multi-field rules
8. **Metadata authorization**: Rules require auth/role context (from headers)
9. **Spring Boot REST API**: Complete server with Controllers/Services/Repositories

### Quality & Operations
10. **Comprehensive testing**: 16 test suites verify entire pipeline (0 failures, 0 errors expected)
11. **Reproducible builds**: Same schema + features → same code, always
12. **Logging**: Log4j2 integrated in all non-generated classes
13. **Isolated generation**: Independent compilation breaks circular dependencies

### Generated Outputs
- ✓ Java models + views (src/main/java-generated/)
- ✓ OpenAPI specification (openapi.yaml)
- ✓ Specification classes + metadata POJOs
- ✓ Complete Spring Boot REST API server (generated-server/)

---

**Last Updated**: 2026-02-24
**Status**: Production Ready
**Test Coverage**: 0 failures, 0 errors expected across 16 suites
**Logging**: Log4j2 integrated in all non-generated classes
**Testing**: 16 test suites, comprehensive pipeline coverage
