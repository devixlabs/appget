# docs/ — appget Platform Documentation

Platform-level documentation for appget.dev, applicable across all language implementations. For language-specific docs, see the subproject directories (e.g., `java/`).

---

## Document Lifecycle

Files in `docs/` follow a two-tier structure:

| Location | Meaning |
|----------|---------|
| `docs/*.md` | **Active** — describes implemented behavior. Authoritative reference. |
| `docs/todos/*.md` | **Pending** — not yet implemented, future specs, or tracked gaps. |

See the root [CLAUDE.md](../CLAUDE.md) for the full lifecycle rules.

---

## Active Reference (docs/)

### Architecture & Design

| Document | Purpose |
|----------|---------|
| [DESIGN.md](DESIGN.md) | Architecture rationale — the "why" behind every pipeline decision |
| [PIPELINE.md](PIPELINE.md) | Language-agnostic pipeline overview — entry point for new language implementations |
| [GHERKIN_GUIDE.md](GHERKIN_GUIDE.md) | Gherkin DSL reference — keywords, operators, patterns, and complete University domain examples |

### Cross-Language Contracts

Implemented in Java today. All future language targets must conform. Minor gaps tracked in [todos/CONTRACT_GAPS.md](todos/CONTRACT_GAPS.md).

| Document | Purpose |
|----------|---------|
| [MODELS_YAML_SCHEMA.md](MODELS_YAML_SCHEMA.md) | `models.yaml` schema — field types, ordering, field number stability |
| [SPECS_YAML_SCHEMA.md](SPECS_YAML_SCHEMA.md) | `specs.yaml` schema — rules, conditions, metadata, operators |
| [REST_CONTRACT.md](REST_CONTRACT.md) | REST naming, CRUD endpoints, rule responses, error codes |
| [DECIMAL.md](DECIMAL.md) | Decimal proto message and per-language type mappings |
| [PROTO_CONVENTIONS.md](PROTO_CONVENTIONS.md) | Proto file naming, package names, language options |

---

## Pending Work (docs/todos/)

**Entry point**: [ROADMAP.md](todos/ROADMAP.md) — every pending doc maps to a roadmap phase.

### Phase 0 — Java Reference Hardening (current focus)

| Document | Phase | Status | Purpose |
|----------|-------|--------|---------|
| ~~TODO-naming-convention-interface.md~~ (deleted) | 0a | **Done** (2026-03-29) | NamingConvention interface — `dev.appget.naming` package with cross-language field-name resolution pattern |
| [SPEC-server-framework-abstraction.md](todos/SPEC-server-framework-abstraction.md) | 0b | Pending | Refactor AppServerGenerator into ServerGenerator + ServerEmitter for portability |
| [GRPC_CONTRACT.md](todos/GRPC_CONTRACT.md) | 0c | Pending | gRPC server implementation (proto stubs exist, server pending) |
| [SPEC-html-crud-codegen.md](todos/SPEC-html-crud-codegen.md) | 0d | Pending | Generate static HTML CRUD pages (depends on 0b) |

### Phase 1+ — Cross-Language Rollout

| Document | Phase | Purpose |
|----------|-------|---------|
| [PARSER_PARITY.md](todos/PARSER_PARITY.md) | 1 | Rules for byte-for-byte identical parser output across languages |
| [CONFORMANCE_TESTS.md](todos/CONFORMANCE_TESTS.md) | 1 | Cross-language fixture layout and golden outputs |
| [LANGUAGE_IMPLEMENTATION.md](todos/LANGUAGE_IMPLEMENTATION.md) | 5-6 | Framework selections and implementation guide per language |
| [CONTRACT_GAPS.md](todos/CONTRACT_GAPS.md) | 5 | Remaining gap (GAP-P1): non-Java package options, blocked on language implementations |
