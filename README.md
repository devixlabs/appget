# appget.dev - Application Generation Platform

> DevixLabs application generation and rule engine system

## Project Overview

**appget.dev** is a DevixLabs platform for application generation from structured domain specifications. It contains multiple subsystems for different code generation purposes.

## Subprojects

### 📦 java/ - SQL-First Code Generation System

**Status**: ✅ Production Ready

A comprehensive Java code generation system that makes your database schema the single source of truth for all domain models.

**Key Features**:
- Schema-first, protobuf-first architecture (SQL → .proto → protoc → Java)
- Multi-dialect SQL support (MySQL, SQLite, Oracle, MSSQL, PostgreSQL)
- Protobuf models with gRPC service stubs
- Proto-first OpenAPI 3.0 generation (full CRUD, security)
- Descriptor-based rule evaluation (language-agnostic protobuf API)
- Multi-domain support with namespace isolation
- Comprehensive test coverage (see test folder)
- Handlebars templates for structural generators (StringBuilder for complex ones)
- Production-ready build system (Gradle + Makefile)

**Quick Start**:
```bash
cd java
make all              # Full pipeline: clean → generate → test → build
make run              # Execute rule engine with generated models
```

**Documentation**:
- [java/README.md](java/README.md) - User guide and quick start
- [java/CLAUDE.md](java/CLAUDE.md) - Technical guidance for developers
- [java/PIPELINE.md](java/PIPELINE.md) - Pipeline architecture

---

## Architecture

```
appget.dev/
├── java/                   # SQL-first Java code generation
│   ├── schema.sql         # Source of truth (database DDL)
│   ├── features/          # Gherkin business rules
│   ├── metadata.yaml      # Authorization context POJOs
│   ├── README.md          # User documentation
│   ├── CLAUDE.md          # Technical guidance
│   ├── PIPELINE.md        # Pipeline architecture
│   └── src/
│       ├── main/java/     # Generators and rule engine
│       ├── main/java-generated/  # Generated models & specs
│       ├── main/resources/templates/  # Handlebars .hbs templates (selective use)
│       └── test/java/     # 171 unit tests (13 suites)
├── CLAUDE.md              # Strategic guidance (language-agnostic)
└── [Future subprojects]   # Python, Go, Rust, etc.
```

---

## Core Design Principles

### 1. Schema-First, Rule-First
- **SQL schema** is the single source of truth for domain models
- **Gherkin features** define business rules in human-readable BDD format
- Generated code is **disposable** — regenerate from sources when schema changes

### 2. Multi-Language Code Generation
Each subproject generates code for a specific language independently:
- **java/** → Protobuf models, Java specifications, Spring Boot servers
- **[Future]** → Python, Go, Rust, etc.

### 3. Descriptor-Based Runtime Evaluation
Uses protobuf descriptors for dynamic model inspection rather than hard-coded class lists. Enables:
- Generic rule evaluation across any model
- Metadata-aware authorization (from HTTP headers)
- Compound AND/OR business rules
- View-targeted specifications

### 4. Comprehensive Testing
Every subproject includes 100+ unit tests covering:
- Code generation correctness
- Type mapping validation
- Rule evaluation
- All tests must pass before deployment

### 5. Git-Friendly Artifacts
Only source files are committed:
- ✅ Commit: `schema.sql`, `features/*.feature`, `metadata.yaml`, build configs, tests
- ❌ Ignore: Generated YAML (models.yaml, specs.yaml), Java-generated code, build artifacts

---

## Getting Started

### For Java Subproject

**Prerequisites:**
```bash
# Check Java version (must be 25+)
java -version

# Verify Gradle works
cd java && make help
```

**Quick Start:**
```bash
cd java

# View available commands
make help

# Full pipeline (recommended)
make all

# Individual steps
make features-to-specs   # .feature files + metadata → specs.yaml
make parse-schema        # schema.sql → models.yaml
make generate            # protobuf + specifications + OpenAPI
make test                # 171 comprehensive tests
make run                 # Execute rule engine demo
```

**See**: [java/README.md](java/README.md) for complete user guide and examples

---

## Development Principles

### For any subproject in appget.dev:

1. **Understand the architecture** - Read the subproject's README.md first
2. **Check the Makefile** - Use provided make commands for builds
3. **Never edit generated code** - Modify sources (schema.sql, features/), regenerate
4. **Run tests after changes** - `make test` verifies entire pipeline
5. **Commit source files only** - generated code is git-ignored by design

---

## Documentation Navigation

| Document | Purpose |
|----------|---------|
| **java/README.md** | User guide, quickstart, examples, workflows |
| **java/CLAUDE.md** | Technical implementation, build system, generators |
| **java/PIPELINE.md** | Detailed pipeline architecture, data flows, type mappings |
| **This file (README.md)** | Project overview and subproject navigation |
| **CLAUDE.md** | Strategic guidance for Claude Code (language-agnostic) |

---

## Project Status

| Component | Status | Tests | Documentation |
|-----------|--------|-------|---|
| **java/** | ✅ Production | ✅ Passing | Complete |

---

## Performance Targets (Java)

| Metric | Time |
|--------|------|
| Schema parsing | 0.9s |
| Code generation | ~1s |
| Full test suite (171 tests) | ~2s |
| Complete build pipeline | 5-6s |

---

## Resources

- **Java Subproject Documentation**:
  - [java/README.md](java/README.md) - User guide
  - [java/CLAUDE.md](java/CLAUDE.md) - Technical details
  - [java/PIPELINE.md](java/PIPELINE.md) - Pipeline architecture

- **Parent Organization**:
  - [../CLAUDE.md](../CLAUDE.md) - DevixLabs organizational guidance

---

**Last Updated**: 2026-02-16
**Status**: Active Development (Java subproject production-ready)
**Maintainer**: DevixLabs CTO (Claude Code)
