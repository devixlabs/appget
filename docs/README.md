# docs/ — appget Platform Documentation

Platform-level documentation for appget.dev, applicable across all language implementations. For language-specific docs, see the subproject directories (e.g., `java/`).

**Status convention**: Files with no banner are currently implemented. Files with `> Status: Not Yet Implemented` describe planned behavior only.

---

## Architecture & Design

| Document | Status | Purpose |
|----------|--------|---------|
| [DESIGN.md](DESIGN.md) | Active | Architecture rationale — the "why" behind every pipeline decision |
| [PIPELINE.md](PIPELINE.md) | Active | Language-agnostic pipeline overview — entry point for new language implementations |
| [ROADMAP.md](ROADMAP.md) | Active | Phase-by-phase plan for multi-language rollout |
| [GHERKIN_GUIDE.md](GHERKIN_GUIDE.md) | Active | Gherkin DSL reference — keywords, operators, patterns, and complete University domain examples for writing `.feature` business rule files |

## Cross-Language Contracts

Implemented in Java today. All future language targets must conform.

| Document | Status | Purpose |
|----------|--------|---------|
| [MODELS_YAML_SCHEMA.md](MODELS_YAML_SCHEMA.md) | Active | `models.yaml` schema — field types, ordering, field number stability |
| [SPECS_YAML_SCHEMA.md](SPECS_YAML_SCHEMA.md) | Active | `specs.yaml` schema — rules, conditions, metadata, operators |
| [REST_CONTRACT.md](REST_CONTRACT.md) | Active | REST naming, CRUD endpoints, rule responses, error codes |
| [GRPC_CONTRACT.md](GRPC_CONTRACT.md) | Active | gRPC service pattern, key messages, error mapping |
| [DECIMAL.md](DECIMAL.md) | Active | Decimal proto message and per-language type mappings |
| [PROTO_CONVENTIONS.md](PROTO_CONVENTIONS.md) | Active | Proto file naming, package names, language options |

## Future Implementations

Phase 1+ — no non-Java implementation exists yet.

| Document | Status | Purpose |
|----------|--------|---------|
| [CONFORMANCE_TESTS.md](CONFORMANCE_TESTS.md) | Not Yet Implemented | Cross-language fixture layout and golden outputs |
| [PARSER_PARITY.md](PARSER_PARITY.md) | Not Yet Implemented | Rules for byte-for-byte identical parser output across languages |
| [LANGUAGE_IMPLEMENTATION.md](LANGUAGE_IMPLEMENTATION.md) | Not Yet Implemented | Framework selections and implementation guide per language |
