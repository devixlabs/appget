# appget - Application Generation Platform

Write your database schema and business rules once. Generate complete backend applications automatically.

**appget.dev** is a platform for application generation from structured domain specifications. Each subproject targets a specific language and generates independently from the same source inputs — SQL schema and Gherkin business rules.

---

## Subprojects

| Language | Status | Getting Started |
|----------|--------|----------------|
| **Java** | ✅ Production Ready | [java/README.md](java/README.md) |
| Python | 🚀 Coming Soon | — |
| Go | 🚀 Coming Soon | — |
| Node.js | 🚀 Coming Soon | — |
| Ruby | 🚀 Coming Soon | — |
| Rust | 🚀 Coming Soon | — |

---

## Architecture

```
appget.dev/
├── java/                  # Reference implementation — SQL-first Java code generation
├── docs/                  # Platform-level documentation
├── CLAUDE.md              # Strategic guidance (language-agnostic)
└── [Future subprojects]   # Python, Go, Rust, etc.
```

---

## Project Status

| Component | Status | Tests | Documentation |
|-----------|--------|-------|---------------|
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
| `@target:<table_name>` | Which table or view the rule applies to — snake_case plural matching the SQL table name |
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

  @target:students @blocking @rule:StudentEnrollmentCheck
  Scenario: Student must be actively enrolled to access academic services
    When is_enrolled equals true
    Then status is "ENROLLED"
    But otherwise status is "NOT_ENROLLED"
```

**Informational rule** — classify students on academic probation (no HTTP rejection):

```gherkin
  @target:students @rule:AcademicProbationStatus
  Scenario: Student GPA below threshold is placed on academic probation
    When gpa is less than 2.0
    Then status is "ON_PROBATION"
    But otherwise status is "GOOD_STANDING"
```

**Compound AND rule** — tuition account must be fully paid with no financial holds:

```gherkin
  @target:tuition_accounts @blocking @rule:FullTuitionClearance
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
  @target:admissions @rule:FastTrackEligibility
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
  @target:admissions @blocking @rule:AdmissionsStaffOnly
  Scenario: Only admissions staff with sufficient role can review applications
    Given roles context requires:
      | field      | operator | value |
      | role_level | >=       | 4     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_complete equals true
    Then status is "REVIEW_PERMITTED"
    But otherwise status is "REVIEW_DENIED"
```

**View-targeting rule** — computed field from a SQL JOIN (`available_seats = capacity − enrollment_count`):

```gherkin
  @view @target:course_availability_view @blocking @rule:OpenSeatRequired
  Scenario: Course must have open seats before enrollment is allowed
    When available_seats is greater than 0
    Then status is "SEATS_AVAILABLE"
    But otherwise status is "COURSE_FULL"
```

> **Critical constraints**: Never compare `DATE`/`TIMESTAMP`/`DATETIME` columns in `When` — they are not comparable scalars. Values must always be literals, never field references. Status values use `SCREAMING_SNAKE_CASE`. `@rule` names must be unique across all feature files. See [docs/GHERKIN_GUIDE.md](docs/GHERKIN_GUIDE.md) for the complete University schema, all five domain feature files, and the full constraint and validation checklist.

---

## Claude Code Agents & Skills

**Agents** and **Skills** are AI-driven automations for appget development. Use the `/invoke` slash command in Claude Code to load any skill, or ask Claude to use an agent.

### Agents (Autonomous Assistants)

| Agent | Purpose | When to Use |
|-------|---------|-----------|
| **domain-architect** | Translate business descriptions into appget source files | Starting from scratch: "Create a Twitter-like app" — generates schema.sql, views.sql, features/*.feature, metadata.yaml |
| **appget-specialist** | Deep expertise on the pipeline, schema, rules, and builds | Debugging builds, understanding how layers connect, adding tables/domains, troubleshooting tests |
| **feature-engineer** | Write, review, and audit Gherkin business rule files | Creating/auditing .feature files, translating requirements into Gherkin, checking field validity |

### Skills (Reference Materials)

| Skill | Content | Used By |
|-------|---------|---------|
| **prompt-zero** | Elicitation interview template, abstract rule format, metadata inference rules, approval gate | domain-architect agent (essential for "from scratch" workflows) |
| **appget-feature-dsl** | Complete Gherkin DSL for appget: tags, operators, conditions, metadata, outcomes, non-comparable types, validation | feature-engineer agent, writing .feature files |
| **appget-pipeline** | Deep pipeline knowledge: make targets, schema parsing, code generation, descriptor-based evaluation | appget-specialist agent, debugging builds |
| **appget-contracts** | Authoritative schemas for models.yaml, specs.yaml, REST/gRPC contracts, proto naming | Cross-language conformance, new language implementations |
| **gherkin-authoring** | Standard Gherkin syntax (Feature, Scenario, Given/When/Then, data tables, tags) | General Gherkin work (broader than appget) |
| **bdd-methodology** | BDD principles, Three Amigos, example mapping, requirement decomposition | Translating requirements into behavior specifications |

### Quick Reference

**Creating from scratch?** → Use `domain-architect` agent (loads `prompt-zero` skill)
**Writing Gherkin rules?** → Use `feature-engineer` agent (loads `appget-feature-dsl` skill)
**Debugging the pipeline?** → Use `appget-specialist` agent (loads `appget-pipeline` skill)
**Auditing existing rules?** → Use `feature-engineer` agent with schema validation

---

## Resources

- **Java Subproject Documentation**:
  - [java/README.md](java/README.md) - User guide
  - [java/CLAUDE.md](java/CLAUDE.md) - Technical details
  - [java/PIPELINE.md](java/PIPELINE.md) - Pipeline architecture

- **Parent Organization**:
  - [../CLAUDE.md](../CLAUDE.md) - DevixLabs organizational guidance


| Document | Purpose |
|----------|---------|
| **docs/README.md** | Index of all platform docs |
| **docs/GHERKIN_GUIDE.md** | Complete Gherkin DSL reference for writing business rules |
| **java/README.md** | Java user guide and quickstart |
| **java/CLAUDE.md** | Java technical implementation details |
| **CLAUDE.md** | Strategic guidance for Claude Code |
