# CLAUDE.md - appget.dev Code Generation Platform

This file provides strategic guidance to Claude Code when working on the appget.dev project.

---

## Project Overview

**appget.dev** is a DevixLabs platform for application generation from structured domain specifications. The project is organized into **language-specific subprojects**, each with its own complete code generation pipeline.

### Architecture

```
appget.dev/
├── java/                          # Java code generation system
│   ├── README.md                  # User guide and quick start
│   ├── CLAUDE.md                  # Technical implementation guide
│   ├── PIPELINE.md                # Detailed pipeline architecture
│   ├── schema.sql                 # SQL source of truth
│   ├── features/                  # Gherkin business rules
│   ├── metadata.yaml              # Context POJOs
│   └── src/                       # Java generators and runtime
│
└── [Future subprojects...]        # Python, Go, Rust, etc.
```

---

## Subproject Structure

Each subproject is **self-contained** with:
- Complete documentation in its own directory
- Independent build system
- Language-specific tooling
- Full test coverage

### Current Subproject: Java

**Status**: ✅ Production Ready

**What it generates**:
- Protocol Buffer models from SQL schema
- Business rule specifications from Gherkin `.feature` files
- Spring Boot REST API servers
- gRPC service stubs
- OpenAPI 3.0 specifications

**See**: [java/README.md](java/README.md) for user guide and [java/CLAUDE.md](java/CLAUDE.md) for technical details.

---

## Design Principles

### 1. Language-First Code Generation
Each subproject generates code for a specific language:
- **java/**: Protobuf models, Java specifications, Spring Boot servers
- **[Future]**: Python Django, Go gRPC, Rust Actix, etc.

No cross-language dependencies. Each generates independently.

### 2. Schema-First, Rule-First Design
- **SQL schema** (schema.sql) = source of truth for domain models
- **Gherkin features** (features/*.feature) = source of truth for business rules
- Generated code is **disposable** — regenerate from sources, never edit

### 3. Descriptor-Based Runtime
Runtime evaluation uses protobuf descriptors (dynamic model inspection) rather than hard-coded class lists. Enables:
- Generic rule evaluation across any model
- Metadata-aware authorization
- Compound AND/OR conditions
- View-targeted rules

### 4. Comprehensive Testing
Every subproject includes:
- Comprehensive test coverage (see `src/test/` folder)
- Multi-stage test suites (parsing, generation, evaluation)
- All tests must pass before deployment

### 5. Portable Code Generation Strategy
Generators use **plain string building** (StringBuilder) as the default approach. This is intentional — every target language (Go, Ruby, Python) has string concatenation, so the pattern ports directly without hunting for templating library equivalents.

**Handlebars (.hbs) templates** are used selectively in the Java subproject for two generators where the output is mostly structural with variable slots:
- `DescriptorRegistryGenerator` → `templates/descriptor/DescriptorRegistry.java.hbs`
- `SpecificationGenerator` → `templates/specification/*.java.hbs`

The remaining generators (`AppServerGenerator`, `ProtoOpenAPIGenerator`, `ModelsToProtoConverter`, etc.) intentionally stay with StringBuilder — their output involves complex conditional logic that would be harder to maintain in a template.

**Rule for future subprojects**: Use plain string building. Do not introduce templating libraries unless the generated output is simple and structural.

### 6. Git-Friendly Artifacts
Only source files are committed:
- ✅ schema.sql, features/*.feature, metadata.yaml (sources)
- ✅ src/main/java/, src/test/ (handwritten code)
- ✅ build.gradle, Makefile (build config)
- ❌ models.yaml, specs.yaml (generated intermediates)
- ❌ src/main/java-generated/ (generated code)
- ❌ build/ directory (artifacts)

---

## Development Workflow

### For Java Subproject

```bash
cd java

# View available commands
make help

# Full pipeline (recommended)
make all              # clean → generate → test → build

# Individual steps
make features-to-specs   # .feature files + metadata → specs.yaml
make parse-schema        # schema.sql → models.yaml
make generate            # protobuf + specifications + OpenAPI
make test                # 250+ tests
make run                 # Execute rule engine demo
make generate-server     # Generate Spring Boot REST API
```

**See**: [java/README.md](java/README.md) for complete user guide

### Adding Features

1. **New models**: Edit `schema.sql` in java/
2. **New rules**: Edit `features/*.feature` in java/ (see [docs/GHERKIN_GUIDE.md](docs/GHERKIN_GUIDE.md) for the complete DSL reference)
3. **New authorization**: Edit `metadata.yaml` in java/
4. **Run pipeline**: `make all` in java/

---

## Documentation Navigation

| Document | Purpose |
|----------|---------|
| **docs/README.md** | Index of all platform docs with status indicators |
| **docs/ROADMAP.md** | Phase-by-phase plan for multi-language rollout |
| **docs/GHERKIN_GUIDE.md** | Complete Gherkin DSL reference — keywords, operators, patterns, and full University domain examples for writing `.feature` files |
| **java/README.md** | User guide, quickstart, examples |
| **java/CLAUDE.md** | Technical implementation, build system, generators |
| **java/PIPELINE.md** | Detailed pipeline architecture, type mappings |
| **This file** | Strategic guidance, subproject navigation |

---

## docs/ Status Convention

Every `.md` file in `docs/` that describes **not-yet-implemented** behavior must begin with this exact banner on **line 1**, before the title heading:

```
> **Status: Not Yet Implemented** — Phase N+. See [ROADMAP.md](ROADMAP.md).
```

Adjust "Phase N" to match the relevant phase from `ROADMAP.md`. No banner = currently implemented. Do not create TODO.md or similar tracking files — use this banner instead.

---

## Key Principles for Claude Code

1. **Understand the subproject first** - Read the README in the subproject directory
2. **Check the Makefile** - Use provided make commands for builds
3. **Never edit generated code** - Modify sources (schema.sql, features/), regenerate
4. **Run tests after changes** - `make test` verifies entire pipeline
5. **Keep root docs abstracted** - Language-agnostic info only; details go in subproject CLAUDE.md

---

## Resources

- **Java Subproject**: [java/README.md](java/README.md), [java/CLAUDE.md](java/CLAUDE.md), [java/PIPELINE.md](java/PIPELINE.md)
- **Root README**: [README.md](README.md) - Project overview
- **Parent CLAUDE.md**: [../CLAUDE.md](../CLAUDE.md) - DevixLabs organizational guidance

---

**Last Updated**: 2026-02-24
**Status**: Language-agnostic guidance (Java details in java/CLAUDE.md)
