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

## Domain Architect Agent

The **domain-architect** agent ([.claude/agents/domain-architect.md](.claude/agents/domain-architect.md)) translates high-level business descriptions into appget source files (`schema.sql`, `views.sql`, `features/*.feature`, `metadata.yaml`).

**Example prompt**:
```
Create a social media application with MVP functionality for regular users, company admins, GitHub OAuth2, and API keys for 3rd party developers. Define SQL views for cross-table reads (JOINs), aggregated analytics (GROUP BY), and transactional writes spanning multiple tables. The REST API must be fully featured — not just basic CRUD, but complete endpoints for every complex operation.
```

---

## Writing Business Rules (.feature Files)

Business rules are defined in Gherkin `.feature` files — one per domain — alongside `schema.sql` and `metadata.yaml` as inputs to the code generation pipeline. If you write them by hand, or want to understand what the **domain-architect** agent produces, this section teaches the syntax through a University application example.

> **Prerequisite**: Every field name in a `When` condition must be a column in `schema.sql` (for base table rules) or in the `SELECT` clause of `views.sql` (for `@view` rules). Write your schema files first, run `make parse-schema` to confirm they parse, then write `.feature` files against those definitions.

**Full reference with complete University domain examples**: [docs/GHERKIN_GUIDE.md](docs/GHERKIN_GUIDE.md)

### Keywords

| Keyword | Purpose |
|---------|---------|
| `@domain:<name>` | Tags all rules in the file to a domain — placed on its own line before `Feature:` |
| `@target:<Model>` | Which table or view the rule applies to — plural PascalCase matching the table name |
| `@rule:<Name>` | Unique rule identifier across **all** feature files — PascalCase |
| `@blocking` | Failed rule returns HTTP 422 — omit for informational (label-only) rules |
| `@view` | Target is a view from `views.sql`, not a base table — add alongside `@target` |
| `Given … context requires:` | Authorization pre-check from HTTP request headers — evaluated before `When` |
| `When` | The field condition to evaluate — simple phrase or compound `all/any` data table |
| `Then status is "…"` | Outcome label when the condition is satisfied |
| `But otherwise status is "…"` | Outcome label when the condition fails — required in every scenario |

### Operator Phrases

| Phrase | Symbol | Example |
|--------|--------|---------|
| `equals` | `==` | `When enrollment_status equals "ACTIVE"` |
| `does not equal` | `!=` | `When application_status does not equal "WITHDRAWN"` |
| `is greater than` | `>` | `When gpa is greater than 2.0` |
| `is less than` | `<` | `When credit_hours_completed is less than 30` |
| `is at least` | `>=` | `When grade_points is at least 3.5` |
| `is at most` | `<=` | `When financial_hold_count is at most 0` |

For compound conditions, use `When all conditions are met:` (AND) or `When any condition is met:` (OR) with a three-column data table. Data table operators use symbols (`==`, `>=`, etc.) — not the natural language phrases above.

### Pattern Examples (University Domain)

**Simple blocking rule** — student must be actively enrolled:

```gherkin
@domain:academic
Feature: Academic Domain Business Rules

  @target:Students @blocking @rule:StudentEnrollmentCheck
  Scenario: Student must be actively enrolled to access academic services
    When is_enrolled equals true
    Then status is "ENROLLED"
    But otherwise status is "NOT_ENROLLED"
```

**Informational rule** — classify students on academic probation (no HTTP rejection):

```gherkin
  @target:Students @rule:AcademicProbationStatus
  Scenario: Student GPA below threshold is placed on academic probation
    When gpa is less than 2.0
    Then status is "ON_PROBATION"
    But otherwise status is "GOOD_STANDING"
```

**Compound AND rule** — tuition account must be fully paid with no financial holds:

```gherkin
  @target:TuitionAccounts @blocking @rule:FullTuitionClearance
  Scenario: Account must be paid in full with no financial holds
    When all conditions are met:
      | field                | operator | value |
      | is_paid              | ==       | true  |
      | financial_hold_count | <=       | 0     |
    Then status is "ACCOUNT_CLEARED"
    But otherwise status is "ACCOUNT_HOLD"
```

**Compound OR rule** — applicant qualifies for fast-track via GPA or test score:

```gherkin
  @target:Admissions @rule:FastTrackEligibility
  Scenario: High GPA or high test score qualifies for fast-track review
    When any condition is met:
      | field         | operator | value |
      | gpa_submitted | >=       | 3.8   |
      | test_score    | >=       | 1400  |
    Then status is "FAST_TRACK"
    But otherwise status is "STANDARD_REVIEW"
```

**Metadata-gated blocking rule** — only admissions staff can review applications:

```gherkin
  @target:Admissions @blocking @rule:AdmissionsStaffOnly
  Scenario: Only admissions staff with sufficient role can review applications
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 4     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_complete equals true
    Then status is "REVIEW_PERMITTED"
    But otherwise status is "REVIEW_DENIED"
```

**View-targeting rule** — computed field from a SQL JOIN (`available_seats = capacity − enrollment_count`):

```gherkin
  @view @target:CourseAvailabilityView @blocking @rule:OpenSeatRequired
  Scenario: Course must have open seats before enrollment is allowed
    When available_seats is greater than 0
    Then status is "SEATS_AVAILABLE"
    But otherwise status is "COURSE_FULL"
```

> **Critical constraints**: Never compare `DATE`/`TIMESTAMP`/`DATETIME` columns in `When` — they are not comparable scalars. Values must always be literals, never field references. Status values use `SCREAMING_SNAKE_CASE`. `@rule` names must be unique across all feature files. See [docs/GHERKIN_GUIDE.md](docs/GHERKIN_GUIDE.md) for the complete University schema, all five domain feature files, and the full constraint and validation checklist.

---

## Resources

- **Java Subproject Documentation**:
  - [java/README.md](java/README.md) - User guide
  - [java/CLAUDE.md](java/CLAUDE.md) - Technical details
  - [java/PIPELINE.md](java/PIPELINE.md) - Pipeline architecture

- **Parent Organization**:
  - [../CLAUDE.md](../CLAUDE.md) - DevixLabs organizational guidance


