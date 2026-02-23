# appget - Application Generation Platform

Experimental project to generate a simple business application with as little configuration and definitions as possible.

## Project Overview

**appget.dev** is a platform for application generation from structured domain specifications. It contains multiple subsystems for different code generation purposes.

## Subprojects

### 📦 java/ - SQL-First Code Generation System

**Status**: ✅ Production Ready

A comprehensive Java code generation system that makes your database schema the single source of truth for all domain models. This ended up being the prodigy implementation to drive all other implementations and references to.

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
├── java/                  # SQL-first Java code generation
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
│       └── test/java/     # 250+ unit tests (over a dozen suites)
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

Each subproject in appget.dev is **self-contained** with its own build system, testing, and documentation. Choose your language:

| Language | Status | Getting Started |
|----------|--------|---|
| **Java** | ✅ Production Ready | [java/README.md](java/README.md) |
| **Python** | 🚀 Coming Soon | [python/README.md](python/README.md) |
| **Go** | 🚀 Coming Soon | [go/README.md](go/README.md) |
| **Node.js** | 🚀 Coming Soon | [node/README.md](node/README.md) |
| **Ruby** | 🚀 Coming Soon | [ruby/README.md](ruby/README.md) |
| **Rust** | 🚀 Coming Soon | [rust/README.md](rust/README.md) |

Each subproject's README includes:
- Quick start guide with prerequisites
- Complete user documentation
- Workflow examples
- Troubleshooting guide

---

## Development Principles

### For any subproject in appget.dev:

1. **Understand the architecture** - Read the subproject's README.md first
2. **Check the Makefile** - Use provided make commands for builds (if present)
3. **Never edit generated code** - Modify sources (schema files, rules), regenerate
4. **Run tests after changes** - Verify entire pipeline with build commands
5. **Commit source files only** - generated code is git-ignored by design

---

## Documentation Navigation

| Document | Purpose |
|----------|---------|
| **docs/README.md** | Index of all platform docs with status indicators |
| **docs/ROADMAP.md** | Phase-by-phase plan for multi-language rollout |
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
| Full test suite (250+ tests) | ~2s |
| Complete build pipeline | 5-6s |

---

## Resources

- **Java Subproject Documentation**:
  - [java/README.md](java/README.md) - User guide
  - [java/CLAUDE.md](java/CLAUDE.md) - Technical details
  - [java/PIPELINE.md](java/PIPELINE.md) - Pipeline architecture

- **Parent Organization**:
  - [../CLAUDE.md](../CLAUDE.md) - DevixLabs organizational guidance


