---
name: appget-pipeline
description: "Deep knowledge of the appget code generation pipeline. Load this skill when working on any part of appget: running make targets, modifying SQL schema or Gherkin rules, interpreting build failures, understanding protobuf/descriptor-based runtime evaluation, or navigating the Java subproject. Also applies when discussing appget architecture, domain model design, or appget integration with iac/templisite."
allowed-tools: Bash, Read, Glob, Grep, Edit, Write
---

# appget Pipeline — DevixLabs CTO Knowledge

The appget project (`appget/`) is DevixLabs' multi-language application generation platform. **SQL DDL + Gherkin business rules → protobuf models → Java specs → Spring Boot REST API.** Deterministic, AI-free, schema-driven.

**Working directory**: Always operate from `appget/java/` unless explicitly working on the root or Rust subproject.

---

## Pipeline at a Glance

```
SOURCES (committed):
  features/*.feature + metadata.yaml → FeatureToSpecsConverter → specs.yaml
  schema.sql + views.sql             → SQLSchemaParser         → models.yaml

GENERATION:
  models.yaml              → ModelsToProtoConverter  → .proto → protoc → Java model classes
  specs.yaml + models.yaml → SpecificationGenerator → Spec classes + metadata POJOs
  models.yaml              → DescriptorRegistryGenerator → DescriptorRegistry.java
  .proto files             → ProtoOpenAPIGenerator → openapi.yaml
  models.yaml + specs.yaml → AppServerGenerator → generated-server/

RUNTIME:
  DescriptorRegistry + RuleInterceptor + RuleEngine + Specification → evaluate(target, metadata)
```

**Critical dependency rule**: `generateProto` depends on `parseSchema` only — NOT on `featuresToSpecs`. Proto files contain schema only; rules travel separately in specs.yaml.

All generated files are git-ignored. Regenerate with `make all` — never hand-edit.

---

## Makefile Quick Reference (`cd appget/java` first)

| Command | What it does |
|---------|-------------|
| `make all` | clean → generate → test → build (always run this after major changes) |
| `make features-to-specs` | features/*.feature + metadata.yaml → specs.yaml |
| `make parse-schema` | schema.sql + views.sql → models.yaml |
| `make generate-proto` | SQL + specs → .proto → Java protobuf classes |
| `make generate-specs` | specs.yaml + models.yaml → Specification classes + metadata POJOs |
| `make generate-registry` | models.yaml → DescriptorRegistry.java |
| `make generate-openapi` | .proto files → openapi.yaml |
| `make generate-server` | models.yaml + specs.yaml → complete Spring Boot server |
| `make generate` | All of the above (features-to-specs through generate-server) |
| `make test` | Run JUnit 5 tests (expect 0 failures, 0 errors) |
| `make build` | Full build: parse schema → generate → compile → package |
| `make clean` | Remove build/, src/main/java-generated/, specs.yaml |
| `make run` | Build and execute the rule engine demo |
| `make run-server` | Build and start Spring Boot on http://localhost:8080 |
| `make test-api` | Run generated API test script (server must be running) |

**Always use `make` targets, never raw `./gradlew` commands, unless debugging a specific Gradle task.**

---

## Source Files

**Edit these (committed source of truth):**
- `schema.sql` — Add/modify tables → new domain models
- `views.sql` — Add/modify SQL VIEWs → composite read models
- `features/<domain>.feature` — Add/modify Gherkin rules → business logic
- `metadata.yaml` — Curated registry of 14 built-in categories with `enabled: true/false` toggle (3 pre-enabled: sso, user, roles; this project also enables oauth, api)
- `src/main/java/dev/appget/**/*.java` — Generator code, specification logic, runtime

**Never edit these (generated, git-ignored):**
- `specs.yaml`, `models.yaml`, `openapi.yaml` (regenerate from sources)
- `src/main/java-generated/` (generated models, specs, DescriptorRegistry)
- `generated-server/` (generated Spring Boot server)
- `build/` (compiled artifacts)

---

## Metadata Registry (Toggle Model)

`metadata.yaml` is a curated registry of 14 built-in categories, each with `enabled: true/false`. Only enabled categories are emitted to specs.yaml and flow through the pipeline.

**Categories are NOT tables** — they represent request-scoped cross-cutting concerns (auth, roles, etc.), independent of `schema.sql`.

**Pre-enabled defaults**: sso, user, roles. **This project enables**: sso, user, roles, oauth, api (5 total).

**Pipeline flow**:
```
metadata.yaml (14 categories, 5 enabled)
    + features/*.feature (Given <category> context requires:)
    → FeatureToSpecsConverter (filters enabled-only, validates references)
    → specs.yaml metadata section (5 categories)
    → SpecificationGenerator → 5 Context POJOs (SsoContext, RolesContext, etc.)
    → AppServerGenerator → MetadataExtractor (reads X-{Category}-{Field} headers)
```

**Build-time validation** (`FeatureToSpecsConverter`):
- Reference to unknown category → error
- Reference to disabled category → error with "Set 'enabled: true' to use it"
- Reference to unknown field in enabled category → error

**To enable a built-in category**: Set `enabled: true` in `metadata.yaml`.
**To add a custom category**: Add entry at bottom of `metadata.yaml` with same format (name, enabled, description, fields).

---

## Domain → Package Mapping

```
Tables → Domain → Java Package:
  admin:  roles, user_roles, moderation_actions, company_settings → dev.appget.admin.model
  auth:   users, oauth_providers, oauth_tokens, api_keys, sessions → dev.appget.auth.model
  social: posts, comments, likes, follows, feeds                  → dev.appget.social.model

Views → Domain:
  admin:  user_role_view, moderation_queue_view, company_health_view → dev.appget.admin.view
  auth:   user_oauth_view, api_key_stats_view                       → dev.appget.auth.view
  social: post_detail_view, comment_detail_view, user_feed_view, etc → dev.appget.social.view
```

**Domain assignment**: SQL comments (`-- auth domain`) before table groups drive domain assignment. No hardcoded DOMAIN_MAPPING.

**Adding a new table**: Edit `schema.sql`, place after the correct `-- <domain> domain` comment, run `make all`.

**Adding a new domain**: Create `features/<domain>.feature` with `@domain:<name>` tag, add tables to `schema.sql` with `-- <domain> domain` comment, run `make clean && make all`.

SQL naming: `role_id` (snake_case) → proto field `role_id` → Java getter `getRoleId()` (camelCase).

---

## Gherkin DSL Essentials

Feature-level: `@domain:appget` (assigns all scenarios to this domain)

Scenario-level tags:
- `@target:employees` — target model/view name (snake_case plural)
- `@rule:EmployeeAgeCheck` — explicit rule name
- `@blocking` — 422 rejection when unsatisfied (non-blocking = informational only)
- `@view` — target is a view, not a model

Step patterns:
```gherkin
When age is greater than 18          # Simple condition
When role_id equals "Manager"        # String condition
When all conditions are met:         # Compound AND
  | field | operator | value |
  | age   | >=       | 30    |
When any condition is met:           # Compound OR
Given sso context requires:          # Metadata authorization requirement
  | field         | operator | value |
  | authenticated | ==       | true  |
Then status is "APPROVED"
But otherwise status is "REJECTED"
```

**Operator phrases**: equals(==), does not equal(!=), is greater than(>), is less than(<), is at least(>=), is at most(<=)

**Gotcha**: Use `But otherwise` not `But` — "Otherwise" alone is not standard Gherkin.

For the complete DSL reference — validation rules, common mistakes, non-comparable types, field validation checklist — see the **appget-feature-dsl** skill.

---

## Design Principles (Never Violate)

1. **Schema-first**: SQL DDL is the single source of truth for models. Never manually write model classes.
2. **Gherkin-first**: Business rules live in `.feature` files. Never hard-code business logic in generators.
3. **Generated code is disposable**: Regenerate from sources. Never edit `src/main/java-generated/`.
4. **Portable patterns**: No switch expressions, no pattern-matching vars, no static blocks, no method overloading. Use if-else chains and static factory methods.
5. **StringBuilder default**: Use StringBuilder for generators with complex conditional logic. Use Handlebars `.hbs` templates only for structural output with variable slots.
6. **Handlebars triple-braces**: Always `{{{var}}}` (not `{{var}}`) in `.hbs` templates for Java code values — avoids HTML-escaping operators like `>`.

---

## Type Mapping Reference

| SQL Type | Java Type (NOT NULL) | Java Type (nullable) |
|----------|---------------------|---------------------|
| VARCHAR, CHAR, TEXT | String | String |
| INT, INTEGER | int | Integer |
| BIGINT | long | Long |
| DECIMAL, NUMERIC | BigDecimal | BigDecimal |
| FLOAT, DOUBLE | double | Double |
| DATE | LocalDate | LocalDate |
| TIMESTAMP, DATETIME | LocalDateTime | LocalDateTime |
| BOOLEAN | boolean | Boolean |

---

## Runtime Evaluation Architecture

```
DescriptorRegistry    — auto-generated registry of all protobuf models/views
Specification.java    — dual-path: descriptor API for protobuf, reflection for Lombok POJOs
CompoundSpecification — AND/OR logic combining multiple Specification instances
MetadataContext       — holds typed auth POJOs (SsoContext, RolesContext, UserContext, OauthContext, ApiContext)
Rule.java             — evaluate(target) or evaluate(target, metadata)
RuleEngine            — orchestrates specs.yaml-driven evaluation
```

---

## Spring Boot Server Quick Reference

Generated server lives in `generated-server/dev/appget/server/` after `make generate-server`.

Per-model: Controller (REST endpoints) + Service (rule validation) + Repository (ConcurrentHashMap).

Infrastructure: Application.java, MetadataExtractor (X-{Category}-{Field} headers), RuleService (instantiates spec classes directly — no runtime YAML), GlobalExceptionHandler.

HTTP conventions: 201 Created (POST), 200 OK (GET/PUT), 204 No Content (DELETE), 422 Unprocessable Entity (blocking rule violation), 404 Not Found.

Only `blocking: true` rules cause 422 rejection. Informational rules (`blocking: false`) are always reported in response body but never block.

Test the running server:
```bash
curl -X POST http://localhost:8080/employees \
  -H "Content-Type: application/json" \
  -H "X-Sso-Authenticated: true" \
  -H "X-Roles-Role-Level: 5" \
  -d '{"name":"Alice","age":30,"roleId":"Manager"}'
```

---

## Test Suite (0 failures, 0 errors expected — 16 Suites)

| Suite | Coverage |
|-------|---------|
| CodeGenUtilsTest | Code generation utilities |
| JavaTypeRegistryTest | Java type mappings (SQL → proto → OpenAPI → Java) |
| JavaUtilsTest | Java-specific utility functions |
| FeatureToSpecsConverterTest | Gherkin parsing, condition extraction, value coercion |
| ModelsToProtoConverterTest | Proto generation from models.yaml, type mapping |
| ProtoOpenAPIGeneratorTest | OpenAPI 3.0 generation, CRUD endpoints, security |
| SpecificationGeneratorTest | YAML → Java spec classes, metadata POJOs |
| AppServerGeneratorTest | RuleService, MetadataExtractor, blocking rules |
| ConformanceTest | Cross-language output conformance |
| RuleTest | Rule evaluation, compound specs, metadata requirements |
| GrpcServiceTest | gRPC service stubs, 5 domain services |
| SpecificationTest | Comparison operators, type handling, edge cases |
| CompoundSpecificationTest | AND/OR logic |
| MetadataContextTest | POJO evaluation via reflection |
| DescriptorRegistryTest | Models/views registered, field descriptor access |
| TestDataBuilderTest | DynamicMessage sample data generation |

Run specific test: `gradle test --tests "dev.appget.<package>.<ClassName>"`

After ANY source change: `make all` — do not skip tests.

---

## Common Failure Patterns & Fixes

| Symptom | Root Cause | Fix |
|---------|-----------|-----|
| `FileNotFoundException: schema.sql` | Wrong working directory | `cd appget/java` first |
| `FileNotFoundException: specs.yaml` | features-to-specs not run | `make features-to-specs` then retry |
| `duplicate class` compile error | Manual class conflicts with generated | Delete manual version from `src/main/java/`, keep generated |
| Tests fail after schema change | Stale generated code | `make clean && make all` |
| View column type unresolved | Source table missing or alias wrong | Verify source table in `schema.sql` before view |
| Metadata POJO not generated | `metadata:` section format wrong in specs.yaml | `make features-to-specs` then check specs.yaml format |
| `io.cucumber.messages.types.*` conflict | Wildcard import conflicts with java.lang.Exception | Use specific imports only |
| Gherkin 38.0.0 not found | Wrong version referenced | Use exactly `38.0.0`, not `38.0.1` (doesn't exist) |

---

## Performance Baselines

| Command | Expected Time |
|---------|--------------|
| `make clean` | ~0.7s |
| `make parse-schema` | ~0.9s |
| `make generate` | ~1s |
| `make test` | ~2s |
| `make build` | ~1s |
| `make all` | ~5-6s |

If `make all` takes significantly longer, investigate Gradle daemon startup or Java toolchain issues.

---

## Logging (Log4j2)

All non-generated classes log to `src/main/resources/log4j2.properties`.

Log levels: DEBUG (method flow), INFO (milestones), WARN (skipped ops), ERROR (exceptions).

**Never remove logging** — it is critical for troubleshooting generator failures.

Adjust verbosity: Edit `log4j2.properties`, change `dev.appget.codegen.level` from `DEBUG` to `INFO` for quieter output.

---

## Subproject Status

| Subproject | Status | Notes |
|------------|--------|-------|
| `java/` | Production-ready | 290+ tests passing, AppServerGenerator (Spring Boot) complete |
| `rust/` | POC | `cargo run/build/test/clean` via Makefile; Actix-web |
| `python/` | Planned | Not started |
| `node/` | Planned | Not started |
| `ruby/` | Planned | Not started |

For Rust subproject, work from `appget/rust/` and use `make` (Cargo-based).

---

## Git Rules

Committed (source of truth):
- `features/*.feature`, `metadata.yaml`, `schema.sql`, `views.sql`
- `src/main/java/`, `src/test/` (handwritten code)
- `build.gradle`, `Makefile`

Git-ignored (always regenerated):
- `specs.yaml`, `models.yaml`, `openapi.yaml`
- `src/main/java-generated/`, `generated-server/`, `build/`, `.gradle/`

**NEVER commit generated code.** If you see generated files unstaged, verify `.gitignore` is correct.
