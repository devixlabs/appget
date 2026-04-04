# SPEC: Multi-Industry Verification Harness

## Context

appget must prove it works across a wide breadth of real-world application scenarios before expanding to other languages. The current test coverage validates the pipeline mechanics (parsing, generation, evaluation) but only against a single social-media-style schema. This spec defines an interactive verification harness that exercises the entire pipeline — domain-architect generation, API server, HTML frontend — across diverse industry verticals until all pass.

**Dependency**: HTML CRUD code generation (Phase 0d) must be complete before HTML verification can run. API verification (`make verify`) is already functional.

---

## Goal

An interactive Claude Code loop that:

1. Generates complete appget source files for an industry scenario (via domain-architect agent)
2. Runs the full pipeline (`make all`)
3. Starts the server and runs API verification (`make verify`)
4. Verifies generated HTML pages (structure, form actions, field coverage)
5. Reports failures with diagnostics
6. Iterates — fixes issues and re-verifies — until the scenario passes completely
7. Moves to the next industry scenario and repeats

The harness proves that appget's Java reference implementation handles diverse domain complexity, not just the social media demo.

---

## Industry Scenarios

### Tier 1 — Core Verticals (must pass for MVP confidence)

| Scenario | Key Domains | Complexity Signals |
|----------|------------|-------------------|
| **Finance / Banking** | accounts, transactions, loans, compliance | DECIMAL precision, compound authorization rules, audit metadata |
| **Healthcare** | patients, providers, appointments, prescriptions, insurance | HIPAA-style metadata, multi-role access, compound AND/OR rules |
| **E-Commerce** | products, orders, customers, inventory, payments | Views with JOINs + aggregates, transactional writes, multi-domain |
| **Food & Services** | restaurants, menus, orders, deliveries, reviews | Real-time status rules, geolocation metadata, rating aggregates |
| **Supply Chain** | suppliers, distributors, shipments, warehouses, purchase_orders | Multi-hop relationships, inventory views, compliance rules |

### Tier 2 — Breadth Validation (post-MVP, proves generality)

| Scenario | Key Domains |
|----------|------------|
| **Packaging / Manufacturing** | materials, production_runs, quality_checks, shipments |
| **Education / University** | students, courses, enrollments, grades, tuition |
| **Real Estate** | properties, listings, agents, transactions, inspections |
| **SaaS / Multi-tenant** | tenants, subscriptions, features, usage, billing |

---

## Harness Workflow

### Per-Scenario Loop

```
┌─────────────────────────────────────────┐
│ 1. Domain Architect generates sources   │
│    schema.sql, views.sql, features/,    │
│    metadata.yaml                        │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ 2. Pipeline build                       │
│    make clean && make all               │
│    Exit: build failure → diagnose → fix │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ 3. API verification                     │
│    make run-server & make verify        │
│    Exit: verify failure → diagnose →    │
│    fix source files or generators → (2) │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ 4. HTML verification                    │
│    Structural checks on generated HTML  │
│    Exit: HTML failure → diagnose → fix  │
│    generator or templates → (2)         │
└──────────┬──────────────────────────────┘
           │
           ▼
┌─────────────────────────────────────────┐
│ 5. SCENARIO PASS                        │
│    Archive source files as fixture      │
│    Record pass/fail summary             │
│    → Next scenario                      │
└─────────────────────────────────────────┘
```

### Failure Categories

| Failure Point | Likely Root Cause | Fix Target |
|--------------|-------------------|------------|
| `make all` — parse error | Domain-architect generated invalid SQL syntax or bad Gherkin | Source files (regenerate or patch) |
| `make all` — test failure | Generator doesn't handle a type/pattern the scenario uses | Generator source code |
| `make verify` — endpoint 500 | Server generator produced bad code for this schema shape | AppServerGenerator |
| `make verify` — rule 422 | verify.sh sample data doesn't satisfy blocking rules | verify.sh generation logic |
| HTML — missing pages | HtmlCrudGenerator didn't emit pages for all entities | HtmlCrudGenerator |
| HTML — wrong form actions | Route mismatch between HTML forms and server endpoints | HtmlCrudGenerator route logic |
| HTML — missing fields | Generator skipped columns or used wrong input types | HtmlCrudGenerator field mapping |

---

## Verification Checks

### API Verification (existing — `make verify`)
- CRUD happy path for every model (POST, GET, PUT, DELETE)
- View GETs for every view
- Error paths: metadata 400, 404, RFC 3339 timestamps
- Blocking rule 422 responses

### HTML Verification (new — needs implementation)

| Check | Method |
|-------|--------|
| Every model has 4 pages (index, create, edit, view) | File existence check |
| Every view has 1 page (view-index) | File existence check |
| Root index.html lists all domains and entities | Grep for expected links |
| Form actions match REST routes | Parse `action=` attributes, compare to openapi.yaml paths |
| Every non-PK column has a form input | Count inputs vs model fields |
| NOT NULL fields have `required` attribute | Parse inputs, check attribute |
| Field types map correctly (DECIMAL → number step=0.01, etc.) | Parse input types |
| Business rules section present on create/edit pages | Grep for rule names from specs.yaml |

### Cross-Scenario Consistency
- Generator produces valid output for schemas with 3-20+ tables
- Views with JOINs, aggregates, and subqueries all generate correctly
- Metadata categories beyond the default 3 (sso, user, roles) work end-to-end
- Compound AND/OR rules with 2-5 conditions evaluate correctly

---

## Scenario Fixture Management

### Phase 1: Generated (Interactive)
- Domain-architect generates sources fresh for each scenario
- Claude reviews output, fixes issues, iterates until pass
- No permanent fixtures — each run is from scratch

### Phase 2: Curated (Permanent)
- Passing scenarios are saved as fixtures in `tests/scenarios/` (or similar)
- Each fixture: `schema.sql`, `views.sql`, `features/*.feature`, `metadata.yaml`
- User refines/extends fixtures with domain expertise
- Fixtures become regression tests — any pipeline change must pass all scenarios

### Phase 3: User-Augmented
- User provides partial specs ("here's our actual healthcare schema")
- Domain-architect fills gaps, generates rules
- Combined human + generated fixtures

---

## Interactive Loop Mechanism

For the initial implementation, the loop runs as an interactive Claude Code session:

1. Human says: "Run the finance scenario"
2. Claude invokes domain-architect (in worktree) to generate sources
3. Claude runs `make all`, `make verify`, HTML checks
4. On failure: Claude diagnoses, proposes fix, applies it, re-runs
5. On pass: Claude reports success, asks to proceed to next scenario
6. Human can intervene at any point to adjust, skip, or redirect

**Future**: Convert to a CI-like mechanism (GitHub Actions, scheduled agent, or custom harness script) that runs all scenarios unattended and reports a matrix of pass/fail.

---

## Success Criteria

### MVP (Phase 0 complete)
- All 5 Tier 1 industry scenarios pass:
  - `make all` green (0 test failures)
  - `make verify` green (all API endpoints correct)
  - HTML structural checks pass (all pages generated, correct form actions, correct field mapping)
- No generator code changes required that break the existing social media schema
- Each scenario exercises at least: 3 domains, 8+ tables, 3+ views, 10+ rules, 2+ metadata categories

### Post-MVP
- Tier 2 scenarios pass
- Curated fixtures committed as regression tests
- CI harness runs all scenarios on every pipeline change

---

## Non-Goals (v1)

- Cross-language verification (Java only for now)
- Performance benchmarking per scenario
- Production data loading (structural verification only)
- CSS/styling quality of HTML output
- JavaScript functionality
- Database integration testing (in-memory repositories only)

---

## Dependencies

| Dependency | Status | Blocks |
|-----------|--------|--------|
| HTML CRUD code generation (Phase 0d) | Done (2026-04-02) | HTML verification checks |
| `make verify` script generation | Done | API verification |
| Domain-architect agent | Done (tested today) | Scenario generation |
| Feature-engineer agent | Done (tested today) | Rule auditing |

---

## Key Files

| File | Relevance |
|------|-----------|
| `.claude/agents/domain-architect.md` | Generates industry scenarios |
| `.claude/agents/feature-engineer.md` | Audits generated rules |
| `java/Makefile` | Build targets (`make all`, `make verify`) |
| `docs/todos/ROADMAP.md` | Phase tracking |
