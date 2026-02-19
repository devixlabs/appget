# appget.dev — Architecture Design, Rationale & Improvement Roadmap

This document captures the *why* behind every architectural decision in the appget framework — a platform for generating fully functional, production-ready applications from three user-defined inputs: a SQL schema, Gherkin business rules, and an authorization metadata definition. The design decisions here apply to any language implementation. Java is the reference implementation and is used throughout as a concrete example, but every *reason* stated in this document must hold equally for Go, Python, Ruby, or any other major language target.

**Purpose**: Break this document into individually discussable and implementable items.

---

## Table of Contents

1. [Pipeline Step Rationale](#1-pipeline-step-rationale)
2. [Library & Framework Rationale — Java Reference Implementation](#2-library--framework-rationale--java-reference-implementation)
3. [Known Trade-offs & Proposed Improvements](#3-known-trade-offs--proposed-improvements)
4. [Language Implementation Guide](#4-language-implementation-guide)

---

## 1. Pipeline Step Rationale

Each step is justified below. The order matters: later steps depend on the contracts established by earlier ones. Steps 1A–2B are entirely language-agnostic and shared across all implementations. Step 3 onward is where each language implementation diverges.

---

### Step 1A — User writes `schema.sql` (tables) and `views.sql` (views)

**Why SQL DDL as the model source of truth?**

SQL is the most universal language for describing structured data. Every developer, DBA, and data engineer already understands it. SQL DDL:
- Is versionable and diffable (plain text, no binary format)
- Generates ERDs and migration scripts with standard tooling
- Is dialect-portable in intent (MySQL, PostgreSQL, SQLite all express the same concepts)
- Is the closest shared language between the application layer and the database layer — using it as the source of truth eliminates drift between schema and application model

**Why separate `views.sql`?**

Views represent *derived* or *read-optimized* models — they have a fundamentally different lifecycle from base tables. A view can join, aggregate, and alias source columns. Keeping views in a separate file makes clear that they are not first-class domain entities. The parser handles them with a separate resolution pass (alias mapping → type resolution from base table columns).

**Why not use an ORM model or JSON Schema as the source of truth?**

- ORM models are language-specific — they couple the data model definition to a single framework and language, making cross-language generation impossible without a secondary schema
- JSON Schema is verbose and lacks the rich constraint vocabulary SQL already has (`NOT NULL`, `UNIQUE`, `REFERENCES`, composite keys)
- SQL is already what drives the actual database — any other format creates two sources of truth that will inevitably drift apart

---

### Step 1B — User writes `features/*.feature` (Gherkin business rules)

**Why Gherkin `.feature` files?**

Gherkin (BDD format) serves two masters simultaneously:
1. **Human-readable contracts** — non-technical stakeholders (product managers, QA, compliance) can read and verify rules without understanding code
2. **Machine-parseable** — the Gherkin format has a well-specified grammar with libraries in every major language, producing a structured AST

The DSL defined on top of Gherkin (`When age is greater than 18`, `Given sso context requires:`) maps cleanly to condition objects without requiring a custom parser. Gherkin handles indentation, tags, data tables, and comment stripping across all implementations.

**Why not define rules directly in YAML or JSON?**

YAML rules (e.g., `operator: >`, `value: 18`) are precise but opaque — a product manager or QA engineer cannot verify them. Gherkin bridges the communication gap. The `FeatureToSpecsConverter` step is the translation layer between the human-readable contract and the machine-processable intermediate form.

**Why one `.feature` file per domain?**

Keeps rule sets bounded and ownership clear. A domain expert for HR does not need to read finance rules. It also mirrors the domain isolation used throughout the pipeline — one output module per domain per layer.

---

### Step 1C — User writes `metadata.yaml` (authorization context models)

**Why a separate file for metadata?**

Authorization context (sso, roles, user, location) has a different lifecycle from business rules:
- Rules change frequently (driven by new business requirements)
- Authorization context model structure changes rarely (driven by new auth systems, which are rare)

Keeping them in separate files prevents metadata changes from touching rule files and vice versa. `metadata.yaml` is read once during the pipeline and merged into `specs.yaml`.

**Why YAML and not a language-specific format?**

Defining authorization context models in a language-specific way (annotations, decorators, struct tags, attributes) would couple the definition to a single target language. Every additional language implementation would then need to either duplicate or translate that definition — two sources of truth that will eventually diverge.

YAML is the correct format because:
- It is read by all generators regardless of target language
- It carries no compilation step — changes to the context model structure require no build
- The same `metadata.yaml` produces the correct authorization context model in every language implementation
- It decouples the *what* (what authorization fields exist and their types) from the *how* (how any specific language represents them at runtime)

---

### Step 2A — Schema Parser → `models.yaml`

**Why a YAML intermediate representation?**

`models.yaml` is the first language-agnostic checkpoint in the pipeline. After this step, all downstream generators read YAML — not SQL. This is the architectural split that makes the platform multi-language:
- Any language implementation reads the same `models.yaml` without needing a SQL parser
- A bug in SQL parsing is fixed once, in one place, and all language generators benefit
- `models.yaml` can be inspected and debugged by humans without tooling

**Why snake_case field names in `models.yaml`?**

Most target languages use snake_case for identifiers (`role_id`, `salary_amount`). `models.yaml` stores field names in snake_case as the lowest-common-denominator convention. Each generator converts to the target language's naming convention at generation time — not at parse time. This is the correct split: parse once in the source system's convention; emit in each target language's convention.

**Why regex-based SQL parsing as the core approach?**

The regex-based core (parenthesis matching, smart splitting on delimiters) handles the 95% case — column extraction, type parsing, constraint detection — with zero external dependencies. This logic is simple enough to reimplement in any language without pulling in a SQL parsing library. Language-specific SQL parsing libraries (like JSQLParser for Java) can supplement edge cases but must remain optional enhancements, not the foundation.

---

### Step 2B — Feature Converter → `specs.yaml`

**Why a YAML intermediate for rules?**

Same reasoning as `models.yaml`: normalizes a complex input format (Gherkin AST) into a flat, inspectable, language-agnostic form. All downstream generators read `specs.yaml` — none parse Gherkin directly. This means the Gherkin parsing library is only needed in the converter step, not in every generator across every language.

**Why not evaluate Gherkin at runtime?**

Gherkin is a *human* format designed for readability, not runtime evaluation. Parsing it at runtime adds a Gherkin library dependency to the production runtime, introduces parse-time failures in production, and makes debugging harder. `specs.yaml` is the compiled form of the rules; the runtime rule engine works from that.

---

### Step 3 — Model schema definition and code generation (`models.yaml` → `.proto` → native model types)

This step converts the language-agnostic `models.yaml` into native model types for the target language. It is shared across all language implementations via Protocol Buffers.

**Why Protocol Buffers as the shared schema/codegen layer?**

Protocol Buffers is not a Java tool — it is a cross-language, type-safe schema definition system with a code generator (`protoc`) that targets every major language from a single `.proto` definition:

| Language | `protoc` plugin | Output |
|----------|----------------|--------|
| Java | `protoc-gen-java` + `protoc-gen-grpc-java` | Builder-pattern model classes + gRPC stubs |
| Python | `protoc-gen-python` + `grpc_tools` | Python message classes + gRPC stubs |
| Go | `protoc-gen-go` + `protoc-gen-go-grpc` | Go structs + gRPC stubs |
| Ruby | `grpc_tools_ruby_protoc` | Ruby classes + gRPC stubs |
| Rust | `prost` | Rust structs + gRPC stubs |
| JavaScript/TypeScript | `protoc-gen-js` | JS/TS message classes + gRPC stubs |
| C# | `protoc-gen-csharp` | C# classes + gRPC stubs |

This means `models.yaml → .proto → protoc` is a **shared step** across all language implementations. Write the proto schema once; generate native model types in every target language without building a separate model generator for each.

**Why this satisfies all model generation requirements:**

1. **Build-time type safety** — `.proto` IDL defines field names and types; `protoc` enforces these at code generation time across all target languages
2. **Runtime field-by-name lookup** — the Descriptor/reflection API is available in all major language runtimes; the rule evaluation engine uses it to look up field values by name without language-specific code:
   - Java: `descriptor.findFieldByName(name)` / `message.getField(fieldDescriptor)`
   - Python: `getattr(message, field_name)` or proto reflection API
   - Go: `message.ProtoReflect().Get(descriptor.Fields().ByName(field_name))`
   - Ruby: `message[field_name]`
3. **Service contracts** — `service` blocks in `.proto` generate gRPC stubs in every target language via the corresponding `protoc` plugin — the same service definition works everywhere

**What does NOT belong in `.proto` files:**

Business rules and authorization policy have a different lifecycle from model schema. They belong in `specs.yaml`, not in `.proto` custom options:
- Proto custom options embed policy (rules, roles, authorization) in the schema layer — two concerns with very different change rates become tightly coupled
- `specs.yaml` is the authoritative rule source; every generator that needs rules reads `specs.yaml` directly
- When rules change, only `specs.yaml` changes; `.proto` files and all generated model classes are unaffected

---

### Step 4 — Specification generator (`specs.yaml` → native specification modules)

**Why generate specification modules rather than evaluating `specs.yaml` at runtime?**

Generating native specification modules from `specs.yaml` instead of loading and evaluating YAML at runtime provides:
1. **Build-time verification** — invalid or malformed rule definitions fail the build, not production
2. **No runtime YAML dependency** — the generated application carries no YAML parsing library
3. **Independent testability** — each specification module is a standalone unit that can be tested in isolation
4. **Performance** — no YAML parsing at evaluation time; the condition logic is already compiled

**Why one specification module per rule?**

One-to-one traceability: every Gherkin scenario becomes exactly one specification module with the same name. Finding, reading, and modifying a rule requires navigating to a single, predictably-named file. The verbosity trade-off (many small files) is acknowledged in [Section 3, Improvement 6](#improvement-6-spec-module-verbosity--n-rules--n-files).

---

### Step 5 — Model registry generation (`models.yaml` → static model registry)

**Why generate a model registry rather than discovering models at runtime?**

The rule engine needs to locate a model type by name at runtime (e.g., resolve `"Employee"` to its type and field descriptor). Runtime discovery — scanning modules, packages, or file systems — is fragile, slow, and produces different results across different packaging and deployment scenarios.

A generated registry, built at build time from `models.yaml`, is:
- **Explicit** — contains exactly the models defined in `models.yaml`, no more, no less
- **Fast** — a direct lookup with no scanning
- **Stable** — packaging or deployment changes do not affect which models are found
- **Verifiable** — the registry is generated code that can be read and tested like any other output

---

### Step 6 — Server generator (`models.yaml` + `specs.yaml` → complete application server)

**Why generate the entire application server rather than writing it by hand?**

The server is entirely determined by two inputs:
- `models.yaml` — what entities exist, their fields and types
- `specs.yaml` — what rules apply to those entities, and which are blocking

Given these two inputs, the server's request handlers, business logic layer, data storage layer, and error handling are fully mechanical. A hand-written server will drift from the schema as requirements change — fields get added to the schema but not the handler, rules change in Gherkin but not in the service layer. A generated server is always consistent because it is regenerated from the same sources on every build.

**Why should the generated rule engine load pre-compiled specification modules rather than evaluating `specs.yaml` at runtime?**

The generated specification modules already encode the complete rule logic. Reloading `specs.yaml` at runtime would duplicate that logic, introduce a YAML parsing dependency into the generated application, and risk divergence between the compiled specification and the runtime-loaded YAML. Loading pre-compiled modules is faster, has zero additional dependencies, and fails at build time if a specification module is missing rather than at runtime.

---

## 2. Library & Framework Rationale — Java Reference Implementation

This section documents the library and tooling choices made for the Java reference implementation. Each choice is specific to Java but the *criteria* used to evaluate them (portability of the core logic, minimal external dependencies, separation of generator from generated code) apply to every language implementation.

---

### 2.1 JSQLParser 5.3

**Used for**: Multi-dialect SQL parsing support in the schema parser

**Why**: Multi-dialect SQL support (MySQL, PostgreSQL, SQLite, Oracle, MSSQL) without maintaining per-dialect regex. Provides a proper AST for complex SQL constructs.

**Critical constraint**: The custom regex core (`findMatchingParen`, `smartSplit`) must remain the portable foundation. JSQLParser is an enhancement for edge cases, not the mechanism that other language implementations should replicate. Each language implementation should reimplement the regex core natively.

**Alternative considered**: Pure regex — sufficient for the core; JSQLParser supplements edge cases only.

---

### 2.2 SnakeYAML 2.2

**Used for**: Reading and writing `models.yaml` and `specs.yaml`

**Why YAML over JSON for intermediate files**: YAML is human-readable without escaping, making intermediate files (`models.yaml`, `specs.yaml`) inspectable and debuggable without tooling. YAML also aligns with the conventions of the ecosystems where this platform operates (Kubernetes manifests, Spring Boot config, etc.).

**Why SnakeYAML**: Standard Java YAML library with no transitive dependencies beyond what the build already requires.

**Trade-off**: Generators use `StringBuilder`-based YAML output (not SnakeYAML serialization) for exact format control over intermediate files. SnakeYAML is used only for reading.

---

### 2.3 Protocol Buffers (protobuf-java 3.25.3 + protoc 3.25.3)

**Used for**: Model class generation, gRPC stub generation, descriptor-based runtime field lookup

**Why proto3 over proto2**: proto3 has cleaner semantics (no `required` fields, uniform default values), broader cross-language ecosystem support, and a lighter runtime.

**Why protobuf over Avro, Thrift, or FlatBuffers**:
- Avro's schema is JSON-based with a weaker type system; protobuf's IDL is more expressive for service definitions
- Thrift has inconsistent code generation quality across language targets and a smaller ecosystem
- FlatBuffers optimizes for zero-copy reads; the Descriptor API (required for runtime field-by-name lookup in the Java rule engine) is a protobuf-only feature — this is the decisive factor

**Trade-off**: Requires a `protoc` native binary at build time. This complexity is justified for Java specifically because of the Descriptor API. It should not be introduced in other language implementations unless the same trade-off applies.

---

### 2.4 gRPC-Java 1.62.2

**Used for**: gRPC service stub generation from `*_services.proto`

**Why gRPC alongside OpenAPI**:
- gRPC service stubs are generated automatically from `service` blocks in `.proto`, requiring no additional schema work
- gRPC serves service-to-service communication (efficient, typed, streaming-capable)
- OpenAPI serves external clients, documentation, and browser-based tools

Both are derived from the same source (`.proto` definitions for Java), keeping them in sync automatically.

---

### 2.5 Gherkin parser (io.cucumber:gherkin 38.0.0)

**Used for**: Parsing `.feature` files in `FeatureToSpecsConverter`

**Why the raw Gherkin parser over a full Cucumber test framework**: Full Cucumber requires step definitions, glue code, and a test runner. Only the Gherkin AST is needed. Using the raw parser keeps the dependency minimal and avoids coupling the code generator to a test framework.

**Important**: Every major language has a Gherkin parsing library. Each implementation should use its language's native Gherkin library — do not attempt to share the Java parser across implementations.

**Known gotcha**: `io.cucumber.messages.types.*` wildcard imports conflict with `java.lang.Exception`. Always use specific imports in code that touches Gherkin message types.

---

### 2.6 Handlebars.java 4.5.0

**Used for**: Structural code generation templates in `DescriptorRegistryGenerator` and `SpecificationGenerator`

**Why Handlebars selectively and not for all generators**:

Templates are used only where output is *mostly static structure with variable slots* — the spec class skeleton and the registry boilerplate. Generators with complex conditional output logic use `StringBuilder`, because template conditionals for complex branching logic are harder to debug and test than native conditionals in the generator's own language.

**Known gotcha**: Handlebars HTML-escapes `{{var}}` by default. All code generation templates must use `{{{triple-braces}}}` for values that contain operators (`>`, `<`), generics, or any character with HTML meaning. This is a recurring maintenance trap.

**Guidance for other implementations**: Choose the template strategy appropriate to the target language's ecosystem. The split between templates (structural output) and native code (conditional output) should be preserved regardless of which template engine is used.

---

### 2.7 Lombok 1.18.42

**Used for**: Authorization context model types only (e.g., `SsoContext`, `RolesContext`)

**Why Lombok for context models but not for domain models**: Domain models use protobuf (builder pattern and accessors generated by `protoc`). Context models are simple data holders that need a builder pattern for the `MetadataExtractor` in the generated server. Lombok provides this at zero runtime cost via annotation processing.

**Why not use protobuf for context models too**: Context models are accessed via standard reflection in the specification system (the reflection fallback path). Keeping them outside the protobuf type system allows the same specification evaluation logic to handle both protobuf model types and plain context types without requiring two different lookup mechanisms.

**Version constraint**: Lombok 1.18.38+ is required for Java 25 compatibility. Earlier versions fail silently or produce incorrect bytecode.

---

### 2.8 SLF4J 2.0.17 + Log4j2 2.25.3

**Used for**: All logging in non-generated generator and runtime classes

**Why logging inside generators**: Generators run as build pipeline steps. When a step produces unexpected output, the DEBUG log shows exactly which tables were parsed, which fields were resolved, and which type conversions were applied. This is the most direct path to diagnosing generation problems.

**Why Log4j2 over alternatives**: Properties-based configuration (`log4j2.properties`) is simpler for a code generation tool than XML-based alternatives. Package-level logger configuration allows DEBUG output to be silenced per subsystem without source changes.

---

### 2.9 Gradle 9.3.1

**Used for**: Build pipeline orchestration

**Why a task graph instead of sequential scripts**: The pipeline has real dependency structure — the schema parser must run before the model converter, which must run before the application compiler. A task graph expresses these dependencies explicitly, enables parallel execution where steps are independent, and makes the dependency structure auditable.

**The generator compilation isolation problem**: Generators need to be compiled before they can run; the application code needs generated files before it can compile; but the generators need to compile before those files are generated. Naive build configurations deadlock.

**Solution**: Compile generators into an isolated output directory in a separate step (`compileGenerators`). Generator tasks run from that isolated classpath. The main application compilation step depends on the generator *outputs*, not on the generator *source*. The cycle is broken.

This problem and its solution are universal — every language implementation will face a variant of this and should solve it with the same isolation pattern, regardless of which build tool is used.

---

## 3. Known Trade-offs & Proposed Improvements

Each improvement is labeled **[IMPROVEMENT-N]** for reference in discussion and tracking.

---

### [IMPROVEMENT-1] Type Mapping is Scattered Across Generators

**Current state** (Java reference implementation):

Four separate type mapping tables exist, each maintained independently:

| Component | Mapping | Location |
|-----------|---------|----------|
| Schema parser | SQL type → application type | `createSqlToJavaTypeMapping()` in `SQLSchemaParser` |
| Model converter | Application type → proto type | `JavaUtils.JAVA_TO_PROTO_TYPE` |
| OpenAPI generator | Proto type → OpenAPI type | internal to `ProtoOpenAPIGenerator` |
| Server generator | Application type → framework annotations | internal to `SpringBootServerGenerator` |

A divergence in any one table produces inconsistent output across the pipeline. Today these are aligned; as the system evolves, they will drift.

**Proposed solution**:

Define a `TypeRegistry` interface that holds all mappings for a given language target in one place:

```
SQL type → native application type     (schema parser reads this)
Native type → wire/serialization type  (model converter reads this)
Native type → OpenAPI schema type      (OpenAPI generator reads this)
Native type → framework type/decorator (server generator reads this)
```

`JavaUtils` expands into the `JavaTypeRegistry` implementation. Future `GoTypeRegistry`, `PythonTypeRegistry` implementations follow the same interface with their own mappings.

**Why this is the right architecture**: Adding a new language implementation means writing exactly one `*TypeRegistry` class. Every generator in that implementation reads from the registry. No generator contains type mapping logic directly.

**Effort**: Medium — requires auditing all four generators to extract current mappings, then updating call sites.

**Files affected** (Java): `JavaUtils.java`, `SQLSchemaParser.java`, `ModelsToProtoConverter.java`, `ProtoOpenAPIGenerator.java`, `SpringBootServerGenerator.java`

---

### [IMPROVEMENT-2] Date/Time Fields Use `string` Instead of `google.protobuf.Timestamp`

**Current state**:

SQL `DATE` and `DATETIME` columns map to `String` (Java type) in `SQLSchemaParser`, then to `string` (proto type) in `ModelsToProtoConverter`. Proto has a well-typed time representation: `google.protobuf.Timestamp`.

**Problem**:

Using `string` for dates and timestamps:
- Requires callers to know the encoding format (ISO 8601? locale-specific? with or without timezone?)
- Loses sub-second precision for `DATETIME` fields
- Is inconsistent with proto best practices and the established `google/protobuf/timestamp.proto` well-known type
- Prevents type-aware serialization in generated gRPC clients and servers across all languages

**Proposed solution**:

Map `DATETIME`/`LocalDateTime` → `google.protobuf.Timestamp`. Map `DATE`/`LocalDate` → `google.protobuf.Timestamp` (midnight UTC convention). Import `google/protobuf/timestamp.proto` in any generated `.proto` file that uses date or time fields. Update `JavaUtils.JAVA_TO_PROTO_TYPE` and the SQL→Java type mapping in `SQLSchemaParser`.

**Effort**: Low-Medium

**Files affected** (Java): `JavaUtils.java`, `SQLSchemaParser.java`, `ModelsToProtoConverter.java`, `JavaUtilsTest.java`, `SQLSchemaParserTest.java`, `ModelsToProtoConverterTest.java`

---

### [IMPROVEMENT-3] Nullable SQL Columns Have No Proto Representation

**Current state**:

SQL `NOT NULL` columns and nullable columns produce identical proto field definitions. Proto3's implicit default-value semantics make every field optionally present, losing the `NOT NULL` constraint that was defined in the source SQL schema.

**Problem**:

The `NOT NULL` constraint from SQL is dropped after `models.yaml`. Generated model classes cannot distinguish between "this field was not set" and "this field was explicitly set to zero/empty". For business rules that check field presence, this produces incorrect results.

**Proposed solution**:

The nullable flag is already tracked in `models.yaml` (the schema parser records it). `ModelsToProtoConverter` should emit the `optional` keyword (available in proto3.15+) for nullable fields, enabling `hasField()` checks in generated classes. This accurately preserves the SQL schema semantics in the generated types.

**Effort**: Low

**Files affected**: `ModelsToProtoConverter.java`, `ModelsToProtoConverterTest.java`

---

### [IMPROVEMENT-4] Field Numbers Are Sequential and Not Stable

**Current state**:

Field numbers in generated `.proto` files are assigned sequentially (1, 2, 3, …) in the order fields appear in `models.yaml`. Adding a column to a SQL table between existing columns shifts all subsequent field numbers.

**Problem**:

Protobuf uses field numbers (not field names) for wire encoding. Changing a field number is a **breaking wire-format change**: existing serialized data cannot be decoded correctly after the field numbers shift. The current sequential assignment makes any column insertion a silent breaking change.

**Proposed solution**:

Persist field numbers in `models.yaml` alongside field definitions. The schema parser assigns new (incrementing) field numbers only to new fields, preserving existing ones across regenerations. `ModelsToProtoConverter` reads persisted field numbers from `models.yaml` instead of sequentially assigning.

**Effort**: Medium — requires `models.yaml` to carry field numbers and `SQLSchemaParser` to track them across regenerations

**Files affected**: `SQLSchemaParser.java`, `ModelsToProtoConverter.java`, `models.yaml` format, `SQLSchemaParserTest.java`, `ModelsToProtoConverterTest.java`

---

### [IMPROVEMENT-5] Generated Server Uses In-Memory Storage Only

**Current state**:

The server generator produces one in-memory storage implementation per entity (e.g., a `ConcurrentHashMap`-backed store in Java). This is suitable for demos and integration testing but not for production.

**Problem**:

There is no storage abstraction. Replacing the in-memory store with a real database adapter requires modifying generated code — which is never supposed to be modified.

**Proposed solution**:

Generate a storage interface alongside the default in-memory implementation. The business logic layer depends on the interface, not the implementation. A database adapter implementing the same interface is a separate, non-generated concern.

**Example** (Java reference implementation):
```java
// Generated interface
interface EmployeeRepository {
    Employee save(Employee e);
    Optional<Employee> findById(String id);
    List<Employee> findAll();
    void delete(String id);
}

// Generated default (in-memory)
class InMemoryEmployeeRepository implements EmployeeRepository { ... }
```

**Why this matters**: The generator should produce a complete, swappable storage abstraction. The in-memory default is the right starting point; the interface is the right architecture.

**Effort**: Low-Medium

**Files affected** (Java): `SpringBootServerGenerator.java`, `SpringBootServerGeneratorTest.java`

---

### [IMPROVEMENT-6] Spec Module Verbosity — N Rules = N Files

**Current state**:

Each Gherkin scenario generates one standalone specification file. With many rules across many domains, this produces a large number of small, nearly-identical files.

**Proposed solution A — Rule registry with grouped specifications** (recommended long-term):

Generate one rule registry per domain that contains all specifications for that domain and exposes them by name. Consistent with the model registry pattern already in use.

Pro: File count scales with domains, not with rule count.
Con: Individual specifications inside the registry are harder to test in isolation.

**Proposed solution B — Keep separate files, add auto-discovery** (lower effort, implement first):

Keep the current one-file-per-rule structure but generate a specification registry that auto-registers all specification modules by name at startup. This enables dynamic rule loading without enumeration — the same benefit as Solution A for the rule engine, without restructuring the file layout.

**Recommendation**: Implement Solution B first (low effort, immediate value for the rule engine). Evaluate Solution A when rule count becomes a maintenance burden.

**Effort**: Low (Solution B). Medium (Solution A).

**Files affected** (Java): `SpecificationGenerator.java`, `SpringBootServerGenerator.java`, `SpecificationGeneratorTest.java`

---

## 4. Language Implementation Guide

The framework was designed with multiple language implementations in mind. Java is the reference implementation. This section defines what is shared, what must be reimplemented, what should be skipped, and the recommended approach for adding a new language.

---

### 4.1 What Is Shared — No Changes Needed

These components are language-agnostic by design. Any language implementation uses them as-is:

| Component | Why it is shared | Notes |
|-----------|-----------------|-------|
| `schema.sql` + `views.sql` | SQL is universal | No changes |
| `features/*.feature` | Gherkin is language-agnostic | No changes |
| `metadata.yaml` | Pure YAML | No changes |
| `models.yaml` format | YAML, snake_case field names | Designed to be the shared contract |
| `specs.yaml` format | YAML, operator/value schema | Designed to be the shared contract |
| String transformation logic | `snakeToCamel`, `camelToSnake`, etc. | Trivial in any language |
| Domain mapping configuration | `DOMAIN_MAPPING`, `VIEW_DOMAIN_MAPPING` | Move to config file if not already |
| SQL-to-type mapping logic | Regex-based column type extraction | Reimplement in the target language |
| Gherkin operator phrase table | `"is greater than" → ">"` | Small, copy directly |

---

### 4.2 What Must Be Reimplemented Per Language

| Framework component | Java reference | Target language equivalent |
|--------------------|---------------|--------------------------|
| **Model type generation** | `models.yaml` → `.proto` → `protoc-gen-java` | Same: `models.yaml` → `.proto` → language-specific `protoc` plugin |
| **Model construction pattern** | Protobuf builder (`newBuilder()`) | Language-native protobuf pattern (e.g., Python message(), Go struct literals) |
| **Field-by-name lookup** | Protobuf Descriptor API (`getField()`) | Protobuf reflection API in the target language's runtime |
| **Service contracts (gRPC)** | `protoc-gen-grpc-java` | Target language's gRPC `protoc` plugin |
| **Model registry** | Generated `DescriptorRegistry` | Simple map from `models.yaml` at build time |
| **Specification modules** | Generated Java classes | Generate in target language |
| **Compound specification** | Java generics + streams | Equivalent with target language idioms |
| **Authorization context models** | Lombok-annotated classes | Language-native data types |
| **Code generation templates** | Handlebars.java | Choose a template engine for the target language |
| **Build pipeline** | Gradle with `JavaExec` tasks | Target language's build tool with equivalent task isolation |
| **Logging** | SLF4J + Log4j2 | Language-native logging |

---

### 4.3 What Should Not Be Replicated

| Java-specific component | Why to skip it in other implementations |
|------------------------|----------------------------------------|
| Proto custom options for rules | Rules belong in `specs.yaml`; embedding them in proto mixes schema with policy |
| `RuleInterceptor` pattern | No proto options to read; all generators read rules from `specs.yaml` directly |
| Java-specific Handlebars templates | Rewrite templates for the target language — do not port template syntax |
| Lombok for context POJOs | Use language-native data types (Python dataclasses, Go structs, Ruby Struct, etc.) |

---

### 4.4 Generator Compilation Isolation — Universal Problem

Every language implementation will face a variant of the circular dependency described in section 2.9:

> Generators must compile/load before they can run. Generated code must exist before the application can compile/load. But generators must run before generated code exists.

**Universal solution**: Compile or load generators in an isolated step, separate from the main application. Generator tasks run from that isolated context. The main application compilation depends on generator *outputs* (the generated files), not on the generator *source*. The cycle is broken.

The specific mechanism differs by language:
- Java: Gradle `compileGenerators` task writing to an isolated `build/generators/` directory
- Python: generators run as standalone scripts with a `requirements-generators.txt` separate from the application's `requirements.txt`
- Go: generators built as separate `cmd/` binaries; `go generate` runs them before `go build`

---

### 4.5 Recommended Implementation Order for a New Language

For any new language target, implement in this order:

**Phase 1 — Shared pipeline** (no language-specific work)
1. Verify `schema.sql` → `models.yaml` produces correct output (run existing schema parser)
2. Verify `features/` → `specs.yaml` produces correct output (run existing feature converter)

**Phase 2 — Model generation** (first language-specific step)
3. Implement a model type generator: `models.yaml` → native model types
4. Verify field-by-name lookup works on generated types

**Phase 3 — Rule system**
5. Implement the `Specification` base (interface or base class) with `isSatisfiedBy(target)` method
6. Implement `CompoundSpecification` with AND/OR logic
7. Implement the specification generator: `specs.yaml` → native specification modules
8. Implement `MetadataContext` — a typed holder for authorization context values
9. Implement the model registry: a build-time-generated map from name to model type

**Phase 4 — Rule evaluation**
10. Implement the rule engine: loads specifications, evaluates against model instances, applies metadata requirements
11. Implement the specification registry (auto-discovers all generated specification modules)

**Phase 5 — Server generation** (optional)
12. Implement the server generator: `models.yaml` + `specs.yaml` → complete application server for the target language's web framework

---

### 4.6 The One Invariant All Implementations Must Preserve

> `models.yaml` and `specs.yaml` are the language-agnostic contract. No generator in any language implementation should parse `schema.sql`, `views.sql`, or `features/*.feature` directly. All generators read YAML intermediates only.

This invariant is what makes the platform multi-language. If a generator bypasses it and parses SQL or Gherkin directly, the parsing logic gets duplicated. Two independent parsers of the same source will eventually diverge, producing inconsistent output across language implementations.

---

## Summary Table

| Item | Type | Effort | Priority |
|------|------|--------|----------|
| [IMPROVEMENT-1] Unified type registry (`TypeRegistry` interface) | Refactor | Medium | High — prerequisite for clean language portability |
| [IMPROVEMENT-2] Date/time fields → `google.protobuf.Timestamp` | Proto correctness | Low-Medium | High — affects all generated model classes |
| [IMPROVEMENT-3] Nullable fields → `optional` keyword in proto | Proto correctness | Low | High — preserves SQL `NOT NULL` semantics |
| [IMPROVEMENT-4] Stable field numbers in generated `.proto` files | Proto correctness | Medium | High — prevents silent wire-format breaking changes |
| [IMPROVEMENT-5] Storage interface abstraction in generated server | Feature | Low-Medium | Medium — required for production-suitable output |
| [IMPROVEMENT-6B] Specification registry auto-discovery | Feature | Low | Medium — enables dynamic rule loading |
| [IMPROVEMENT-6A] Domain-grouped rule registry | Refactor | Medium | Low — addresses file count at scale |
| First non-Java language (Phase 1–4) | New implementation | High | Future |

---

**Last Updated**: 2026-02-18
**Status**: Design document — items are proposals for discussion, not committed changes
