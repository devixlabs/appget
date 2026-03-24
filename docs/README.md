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

| Document | Purpose |
|----------|---------|
| [CONTRACT_GAPS.md](todos/CONTRACT_GAPS.md) | Tracked implementation gaps in the active contract docs above |
| [ROADMAP.md](todos/ROADMAP.md) | Phase-by-phase plan for multi-language rollout |
| [GRPC_CONTRACT.md](todos/GRPC_CONTRACT.md) | gRPC server contract — proto stubs exist, server implementation pending |
| [CONFORMANCE_TESTS.md](todos/CONFORMANCE_TESTS.md) | Cross-language fixture layout and golden outputs |
| [PARSER_PARITY.md](todos/PARSER_PARITY.md) | Rules for byte-for-byte identical parser output across languages |
| [LANGUAGE_IMPLEMENTATION.md](todos/LANGUAGE_IMPLEMENTATION.md) | Framework selections and implementation guide per language |
