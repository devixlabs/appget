# CLAUDE.md - Java Subproject Technical Guidance

Java-specific guidance for the appget.dev/java SQL-first code generation system.

**Deep reference**: [REFERENCE.md](REFERENCE.md) — read sections on demand with `offset`/`limit`.
**Pipeline diagrams**: [PIPELINE.md](PIPELINE.md) — full pipeline architecture.
**Gherkin DSL**: [../docs/GHERKIN_GUIDE.md](../docs/GHERKIN_GUIDE.md) — complete DSL reference.

---

## Project Context

**appget.dev/java** generates typed Java domain models, business rule specifications, and a Spring Boot REST API from Gherkin `.feature` files and SQL schemas. It is the reference implementation — design patterns here set the template for all future language subprojects (Go, Python, Rust, etc.).

**Pipeline summary**: `.feature` files + `metadata.yaml` → `specs.yaml` | `schema.sql` + `views.sql` → `models.yaml` → `.proto` → protobuf Java classes → specifications → REST API server

---

## Key Design Principles

1. **Schema = proto, Policy = specs.yaml** — Proto files hold field names/types only. Business rules live in specs.yaml. Never embed rules in proto.
2. **TypeRegistry as extension point** — All type mappings in one per-language registry (`JavaTypeRegistry`). Future languages add their own. See REFERENCE.md §TypeRegistry.
3. **Naming utilities separated** — `CodeGenUtils` (language-agnostic), `JavaNaming` (runtime field access), `JavaUtils` (codegen-only). See REFERENCE.md §Language Utility Pattern.
4. **Protobuf as universal schema layer** — All languages share the same `.proto` files from models.yaml. See REFERENCE.md §Protocol Buffers.
5. **Intermediates are snake_case** — `models.yaml` and `specs.yaml` store snake_case. Language-specific casing applied at codegen time only.
6. **ServerEmitter abstraction** — `AppServerGenerator` orchestrates; `SpringBootEmitter` emits. To add a framework, implement `ServerEmitter`. Never edit `AppServerGenerator` for output changes.

### Portability Anti-Patterns (Avoid)

| Anti-Pattern | Use Instead |
|--------------|-------------|
| Switch expressions (`case -> value`) | if-else chains |
| Pattern matching vars (`instanceof Type var`) | Explicit casting |
| Static initialization blocks | Static factory methods |
| Method overloading | Builder pattern |
| Singleton pattern | Static utility class (no instances) |

See REFERENCE.md §Portability for code examples.

---

## Build Dependency Order (Compact)

```
compileGenerators → featuresToSpecs + parseSchema (parallel)
                  → generateProto (needs parseSchema only, NOT featuresToSpecs)
                  → generateSpecs + generateDescriptorRegistry + generateOpenAPI
                  → generateServer (needs specs + proto)
                  → compileJava → test → build
```

**Critical**: `generateProto` depends on `parseSchema` only — business rules are NOT in proto. This breaks the circular dependency. See REFERENCE.md §Build Dependency Resolution for full step details.

---

## Development Workflows

### Adding a New Table
1. Edit `schema.sql` (ensure `-- <domain> domain` comment exists before table group)
2. Run: `make parse-schema && make generate && make test`

### Adding a New View
1. Edit `views.sql` with `CREATE VIEW` (ensure `-- <domain> domain` comment)
2. Run: `make parse-schema && make generate && make test`

### Adding New Business Rules
1. Edit `features/<domain>.feature` — add `Scenario` with `@target`, `@rule` tags
2. Use `When <field> <operator_phrase> <value>` for conditions
3. Use `Then status is "<value>"` and `But otherwise status is "<value>"` for outcomes
4. Run: `make features-to-specs && make generate-specs && make test`

### Adding a New Domain
1. Create `features/<domain>.feature` with `@domain:<name>` tag
2. Add tables to `schema.sql` with `-- <domain> domain` comment
3. Run: `make clean && make all`

### Adding New Metadata Categories
Set `enabled: true` on a built-in category in `metadata.yaml`, or add a custom entry:
```yaml
  my_category:
    enabled: true
    description: "What this category provides"
    fields:
      - name: my_field
        type: String
```
Pipeline validates all `Given <category> context requires:` references at build time (unknown/disabled category → error).
Run: `make features-to-specs && make generate-specs && make test`

### Full Development Cycle
```bash
vim schema.sql              # 1. Modify schema
vim views.sql               # 2. Modify views (optional)
vim features/*.feature      # 3. Modify business rules
vim metadata.yaml           # 4. Modify metadata (optional)
make clean && make all      # 5. Full pipeline
make run-server             # 6. Start server
make verify                 # 7. Integration tests (requires server)
```

---

## Make Targets

| Target | Purpose |
|--------|---------|
| `make help` | Show all available commands |
| `make all` | Full pipeline: clean → generate → test → build (no server needed) |
| `make clean` | Remove build artifacts and generated code |
| `make features-to-specs` | .feature + metadata → specs.yaml |
| `make parse-schema` | schema.sql + views.sql → models.yaml |
| `make generate` | All generation (proto, specs, registry, OpenAPI, server, HTML) |
| `make generate-proto` | models.yaml → .proto → protobuf Java classes |
| `make generate-specs` | specs.yaml + models.yaml → specification classes |
| `make generate-registry` | models.yaml → DescriptorRegistry.java |
| `make generate-openapi` | .proto → openapi.yaml |
| `make generate-server` | models.yaml + specs.yaml → Spring Boot REST API |
| `make generate-html` | models.yaml + specs.yaml → static HTML CRUD pages |
| `make build` | Full build: parse → generate → compile → package |
| `make test` | Unit tests (0 failures expected) |
| `make run-server` | Build and start server on http://localhost:8080 |
| `make verify` | All server-dependent tests (requires server running) |
| `make test-api` | Regenerate + run API tests (requires server) |

---

## Dependencies & Versions

| Dependency | Version | Notes |
|-----------|---------|-------|
| Java | 25+ | OpenJDK 25. Always use `./gradlew`, never system `gradle` (snap uses Java 21) |
| Gradle | 9.3.1+ | Via wrapper |
| Lombok | 1.18.38+ | Java 25 compatibility critical |
| Gherkin | 38.0.0 | Not 38.0.1 (doesn't exist) |
| JSQLParser | 5.3 | Multi-dialect SQL parsing |
| SnakeYAML | 2.2 | YAML parsing |
| Log4j2 | 2.23.1 | Logging — do NOT remove during refactoring |
| JUnit 5 | 5.11.3 | 16 test suites, 400+ tests |

---

## QA Pipeline (Mandatory)

**Run after any source change** (generators, schema, features, metadata):

```bash
# Step 1: Full build pipeline (unit tests + compilation)
make all                    # MUST exit 0

# Step 2: Start server
fuser -k 8080/tcp 2>/dev/null
make run-server &           # Wait for "Started Application" (~15-20s)

# Step 3: Integration tests
make verify                 # MUST exit 0

# Step 4: Stop server
fuser -k 8080/tcp
```

| Step | Catches |
|------|---------|
| `make all` | Compilation errors, unit test regressions, stale generated code, shellcheck violations |
| `make verify` | Server startup failures, endpoint routing, protobuf serialization, rule evaluation, metadata parsing, HTTP status codes |

**Common failures**: See REFERENCE.md §Testing Strategy for the full troubleshooting table.

---

## Refactoring Workflow

1. Use `TaskCreate` to define each logical change (one task per file)
2. Use `replace_all: true` with exact strings to avoid partial matches across test files
3. Run `make test` after each file group
4. Do NOT remove Log4j2 logging during refactoring
5. Final validation: `make clean && make all`

---

## Common Patterns & Anti-Patterns

- **DO**: Keep schema.sql as source of truth
- **DON'T**: Manually edit models.yaml or specs.yaml (generated, git-ignored)
- **DO**: Run `make all` before declaring work complete
- **DON'T**: Commit generated code (`src/main/java-generated/`, `generated-server/`)
- **DON'T**: Mix manual and generated models

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `FileNotFoundException: schema.sql` | Ensure schema.sql exists at project root |
| `duplicate class` | Delete manual version from `src/main/java/`, keep generated |
| Tests fail after schema changes | `make clean && make all` |
| View column type not resolved | Ensure source table is defined in schema.sql before view references it |
| Metadata POJO not generated | Ensure `metadata:` section exists in specs.yaml |
| `No static resource <name>` (500) | Stale server — `make all` regenerates everything |
| 422 on POST/PUT | Blocking rule unsatisfied — check test data and metadata headers |
| Port 8080 already in use | `fuser -k 8080/tcp` then restart |

---

## Git Integration

```
DO commit:   features/*.feature, metadata.yaml, schema.sql, views.sql,
             src/main/java/, src/test/, build.gradle, Makefile, docs

DON'T commit: specs.yaml, models.yaml, openapi.yaml, src/main/java-generated/,
              generated-server/, generated-html/, build/, .gradle/
```

---

## Key Files

| File | Purpose |
|------|---------|
| `schema.sql` | SQL source of truth (database tables) |
| `views.sql` | SQL view definitions |
| `features/*.feature` | Gherkin business rules (3 domains, 27 rules) |
| `metadata.yaml` | Context POJO definitions (14 categories, 3 enabled) |
| `build.gradle` | Gradle build config |
| `Makefile` | User-facing build commands |
| `src/main/java/dev/appget/codegen/` | All generators (7 files) |
| `src/main/java/dev/appget/specification/` | Specification, CompoundSpecification, MetadataContext |
| `src/main/java/dev/appget/model/Rule.java` | Generic rule with metadata support |
| `src/main/java/dev/appget/RuleEngine.java` | specs.yaml-driven rule evaluation |
| `src/main/java/dev/appget/codegen/TypeRegistry.java` | Type mapping interface |
| `src/main/java/dev/appget/codegen/JavaTypeRegistry.java` | Java type mappings |
| `src/main/java/dev/appget/naming/JavaNaming.java` | Runtime naming (snake_case → camelCase) |
| `src/main/java/dev/appget/codegen/JavaUtils.java` | Codegen-only transforms |
| `src/main/java/dev/appget/codegen/CodeGenUtils.java` | Language-agnostic string ops |
| `src/test/java/dev/appget/` | 16 test suites (400+ tests) |

---

**Last Updated**: 2026-04-03
**Status**: Production Ready
