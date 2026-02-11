# appget.dev - Application Generation Platform

> DevixLabs application generation and rule engine system

## Project Overview

**appget.dev** is a DevixLabs project focused on application generation, code scaffolding, and business rule specification. It contains multiple subsystems for different code generation purposes.

## Subprojects

### 📦 java/ - SQL-First Code Generation System

**Status**: ✅ Production Ready

A comprehensive Java code generation system that makes your database schema the single source of truth for all domain models.

**Key Features**:
- Schema-first, protobuf-first architecture (SQL → .proto → protoc → Java)
- Multi-dialect SQL support (MySQL, SQLite, Oracle, MSSQL, PostgreSQL)
- Protobuf models with gRPC service stubs (5 services, 3 domains)
- Proto-first OpenAPI 3.0 generation (full CRUD, security)
- Descriptor-based rule evaluation (language-agnostic protobuf API)
- Multi-domain support with namespace isolation
- 147 comprehensive unit tests (100% passing, 12 suites)
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
│   ├── models.yaml        # Auto-generated (git-ignored)
│   ├── specs.yaml         # Business rule specifications
│   ├── README.md          # User documentation
│   ├── CLAUDE.md          # Technical guidance
│   ├── PIPELINE.md        # Pipeline architecture
│   ├── TESTING.md         # Testing guide
│   └── src/
│       ├── main/java/     # Source code (generators, rule engine)
│       ├── main/java-generated/  # Generated models & specs
│       └── test/java/     # 147 unit tests (12 suites)
└── CLAUDE.md              # Project-level guidance
```

---

## Prerequisites

Before starting, verify your environment:

```bash
# Check Java version (must be 25+)
java -version
# Expected: openjdk 25.0.x or later

# Verify Gradle works
make help
# Expected: List of available make commands

# Optional: Verify all tests pass
cd java && make test
# Expected: 147/147 tests passing ✅
```

If any check fails, see [Troubleshooting](#troubleshooting) below.

---

## Getting Started

### For Java Subproject

```bash
cd java

# View available commands
make help

# Full pipeline test (recommended)
make all

# Individual steps
make parse-schema      # SQL → YAML
make generate         # YAML → Java
make test             # Run all 147 tests
make build            # Compile and package
make run              # Execute application
```

---

## Key Principles

### 1. Schema-First Design
- Database schema is the single source of truth
- All domain models generated from schema
- Changes to models go through schema updates

### 2. Deterministic Generation
- Same schema input → Same code output (reproducible every time)
- Version-controlled source: Only `schema.sql` and `specs.yaml` need to be in git
- Generated code is **disposable** - never edit `src/main/java-generated/`, regenerate from sources instead
- If your build fails, `make clean && make all` always fixes it

### 3. Comprehensive Testing
- 147 unit tests verify entire pipeline (12 suites)
- Proto generation, specifications, rule engine, descriptors, server generation
- All tests must pass before deployment

### 4. Production-Ready
- Zero technical debt in generated code
- Full type safety with nullability handling
- Clean architecture with clear separation of concerns

---

## Technology Stack

### Java Subproject
- **Java**: 25+
- **Build**: Gradle 9.3.1+
- **Code Generation**:
  - JSQLParser 5.3 (SQL parsing, multi-dialect)
  - SchemaToProtoConverter + protoc (SQL → .proto → Java)
  - ProtoOpenAPIGenerator (.proto → OpenAPI YAML)
  - SpecificationGenerator (YAML → Specs)
- **Protobuf/gRPC**:
  - Protobuf 3.25.3, gRPC-Java 1.62.2
- **Code Quality**:
  - Lombok 1.18.38+ (metadata POJOs, Java 25 compatible)
  - JUnit 5 (147 tests, 12 suites)
- **Build Automation**: Makefile

---

## Development Workflow

### Understanding the Pipeline

The system automates: **You Edit** → **System Generates** → **System Tests** → **System Builds**

```
schema.sql (you edit)     ──┐
                            ├──→ .proto files ──→ protoc ──→ Java models
views.sql (you edit)      ──┘

specs.yaml (you edit)     ──→ Specification classes ✓

make all runs: clean → generate → test (147 tests) → build
```

### Adding a New Model or Feature

1. **Edit schema.sql**:
   ```sql
   CREATE TABLE new_table (
       id VARCHAR(50) NOT NULL,
       name VARCHAR(100) NOT NULL
   );
   ```

2. **Edit specs.yaml** (if adding business rules):
   ```yaml
   rules:
     - name: MyNewRule
       target:
         type: model
         name: NewTable
         domain: appget
       conditions:
         - field: name
           operator: "=="
           value: "Active"
   ```

3. **Run the full pipeline**:
   ```bash
   cd java
   make clean && make all
   # This: cleans → generates → tests → builds
   # All tests must pass ✅
   ```

4. **Verify generated code**:
   ```bash
   # Check Java models were created
   ls -la src/main/java-generated/dev/appget/model/

   # Run the demo
   make run
   ```

5. **Commit your changes**:
   ```bash
   # Only commit SOURCE files (not generated code)
   git add schema.sql specs.yaml
   git commit -m "Add new_table model and MyNewRule"
   ```

### What NOT to Commit

❌ `models.yaml` - Auto-generated from schema.sql
❌ `src/main/java-generated/` - Auto-generated models and specs
❌ `build/` directory - Build artifacts
❌ `.gradle/` directory - Gradle cache

These are already in `.gitignore`

---

## After `make all` Succeeds

If all 147 tests pass, here's what was generated:

1. **Java Models** (protobuf):
   ```
   src/main/java-generated/dev/appget/model/
   ```
   Use these in your application code

2. **OpenAPI Specification**:
   ```
   openapi.yaml
   ```
   REST API contract for frontend developers

3. **Specification Classes**:
   ```
   src/main/java-generated/dev/appget/specification/
   ```
   Business rule implementation

4. **Demo Execution**:
   ```bash
   make run
   # Runs RuleEngine with generated models
   # Shows which business rules pass/fail
   ```

---

## Full Development Cycle (Checklist)

```bash
# 1. Navigate to project
cd java

# 2. Modify your source files
vim schema.sql    # Add/edit tables
vim specs.yaml    # Add/edit rules

# 3. Generate and verify
make clean && make all
# If tests fail: read error message, fix schema.sql/specs.yaml, re-run

# 4. Test the rules
make run

# 5. Commit (only source files!)
git add schema.sql specs.yaml
git commit -m "Descriptive commit message"
```

---

## CI/CD Integration

### GitHub Actions Example
```yaml
name: Build and Test
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '25'
      - run: cd java && make all
```

---

## Project Status

| Component | Status | Tests | Documentation |
|-----------|--------|-------|---|
| **java/** | ✅ Production | 147/147 ✅ | Complete |

---

## Performance Metrics

### Java Subproject Build Times
- Schema parsing: 0.9s
- Code generation: 2s
- Tests (147): 2s
- Full build: 5-6s

### Test Coverage
- Schema To Proto Converter: 20 tests ✅
- Proto-First OpenAPI Generator: 23 tests ✅
- Specification Generator: 13 tests ✅
- Spring Boot Server Generator: 12 tests ✅
- Rule Interceptor: 11 tests ✅
- gRPC Service Stubs: 7 tests ✅
- Rule Engine: 15 tests ✅
- Compound Specifications: 6 tests ✅
- Metadata Context: 5 tests ✅
- Specification Patterns: 21 tests ✅
- Descriptor Registry: 8 tests ✅
- Test Data Builder: 6 tests ✅
- **Total**: 147 tests, 100% passing

---

## Documentation Structure

### 👤 New to the Project?
**Start here**: [java/README.md](java/README.md) - Complete user guide with examples

### 👨‍💻 For Developers
- [java/CLAUDE.md](java/CLAUDE.md) - Technical implementation details, architecture, logging
- [java/PIPELINE.md](java/PIPELINE.md) - How the generation pipeline works
- This `README.md` - Project overview and workflow

### 🏢 For DevixLabs Organization
- `CLAUDE.md` - Project-level technical guidance
- Parent `../CLAUDE.md` - Organizational principles and architecture

---

## File Guidelines

### Version Control (What to Commit)

**DO commit**:
- `schema.sql` - Source of truth
- `specs.yaml` - Business rules
- `src/main/java/` - Manual source code
- `src/test/` - Test code
- `build.gradle`, `Makefile` - Build configuration
- `CLAUDE.md`, `README.md` - Documentation

**DON'T commit**:
- `models.yaml` - Generated from schema.sql
- `src/main/java-generated/` - Generated code
- `build/` - Build artifacts

### .gitignore Template
```
# Generated files
models.yaml
src/main/java-generated/

# Build artifacts
build/
.gradle/
*.jar

# IDE
.idea/
.vscode/
*.iml
```

---

## Troubleshooting

### ❌ `make all` Fails

**Error: "Cannot find symbol: class Employee"**
- Ensure `schema.sql` exists with CREATE TABLE statements
- Run: `cd java && make parse-schema && make all`

**Error: "FileNotFoundException: specs.yaml"**
- Ensure `specs.yaml` exists (even if empty, need at least one rule)
- Copy from example in [java/README.md](java/README.md#defining-business-rules)

**Error: Java version incompatibility**
```bash
java -version
# Must be Java 25+ (e.g., OpenJDK 25, not Java 21)
```

**Error: Lombok not compatible**
```bash
grep "lombok" build.gradle
# Must be 1.18.38 or later for Java 25
```

### ❌ Tests Fail

**Some tests fail, others pass**
```bash
# Clean rebuild often fixes this
make clean && make all

# If still failing, read the test output
# It will show which specific rule/model failed
```

**All tests pass locally but fail in CI/CD**
```bash
# This is usually environment differences
# Ensure CI/CD uses Java 25+
```

### ❌ Generated Code Issues

**Can't find generated models?**
```bash
ls -la src/main/java-generated/dev/appget/
# If empty: run make parse-schema && make generate
```

**Generated code looks wrong**
```bash
# Schema has a typo? Fix it in schema.sql, then:
make clean && make all
# Generated code is disposable - regenerate from schema
```

### ✅ General Debugging

**"I'm not sure what went wrong"**
```bash
# 1. Check Java version
java -version

# 2. Run the verification
make help
make test

# 3. Check output
ls -la src/main/java-generated/
cat models.yaml

# 4. Read full error message carefully
# Most errors tell you exactly what's wrong
```

**"Everything looks right but I'm stuck"**
1. Run: `make clean && make all`
2. Check [java/README.md](java/README.md) for examples
3. Review [java/CLAUDE.md](java/CLAUDE.md) for technical details

---

## Quick Reference (Cheat Sheet)

### Essential Commands

```bash
cd java

# Initial setup - verify everything works
make test               # Run all 147 tests (expect all passing)

# Regular development
make all                # Full pipeline: clean → generate → test → build
make run                # Execute rule engine demo

# Individual steps (if needed)
make parse-schema       # SQL → models.yaml (auto-run by make all)
make generate           # YAML → Java code (auto-run by make all)
make build              # Compile and package
make clean              # Remove all generated code and build artifacts

# Help
make help               # Show all available commands
```

### File Quick Reference

| File | Purpose | You Edit? |
|------|---------|-----------|
| `schema.sql` | Database tables (source of truth) | ✏️ YES |
| `specs.yaml` | Business rules | ✏️ YES |
| `views.sql` | SQL views (read models) | ✏️ YES |
| `models.yaml` | Auto-generated model list | ❌ NO (generated) |
| `src/main/java-generated/` | Generated Java models and specs | ❌ NO (generated) |
| `build/` | Compiled classes and artifacts | ❌ NO (generated) |

### Common Workflows

**Adding a new table:**
1. Edit `schema.sql` - add CREATE TABLE
2. Run: `cd java && make all`
3. Check: `src/main/java-generated/dev/appget/model/YourTable.java`

**Adding a business rule:**
1. Edit `specs.yaml` - add rule under `rules:`
2. Run: `cd java && make all`
3. Check: `src/main/java-generated/dev/appget/specification/generated/YourRule.java`

**Debugging a build failure:**
1. Read the error message (it's usually clear)
2. Fix the issue in schema.sql or specs.yaml
3. Run: `make clean && make all`

**Verifying setup is correct:**
```bash
cd java
make test
# Expected: 147/147 passing ✅
```

---

## Contact & Support

For issues or questions:
1. Check project `CLAUDE.md` files
2. Review `README.md` in relevant subproject
3. Run `make help` for available commands

---

## References

- [DevixLabs Main CLAUDE.md](../CLAUDE.md) - Organizational guidance
- [Java Subproject README](java/README.md) - Java-specific documentation
- [Java Subproject CLAUDE.md](java/CLAUDE.md) - Technical guidance

---

**Last Updated**: 2026-02-11
**Status**: Active Development
**Maintainer**: DevixLabs CTO (Claude Code)
