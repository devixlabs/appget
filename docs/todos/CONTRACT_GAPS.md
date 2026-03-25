# Contract Implementation Gaps

Tracked gaps in the cross-language contract docs that live in `docs/`. Each gap references the source doc and the Java source file where the fix belongs.

---

## All Java Gaps Resolved

### ~~GAP-M1: `resource` field not emitted~~ — RESOLVED 2026-03-24
- SQLSchemaParser now emits `resource:` (kebab-case) per model/view in models.yaml

### ~~GAP-M2: `original_sql_type` field not emitted~~ — RESOLVED 2026-03-24
- SQLSchemaParser now captures and emits `original_sql_type:` per field in models.yaml

### ~~GAP-S1: `IS_NULL` and `IS_NOT_NULL` operators~~ — RESOLVED 2026-03-24
- FeatureToSpecsConverter: added "is null" and "is not null" operator phrases
- Specification.java: added IS_NULL/IS_NOT_NULL evaluation in compare() and compareNulls()

### ~~GAP-R1: Composite key path variables~~ — RESOLVED 2026-03-24
- AppServerGenerator: controllers, services, and repositories now support composite primary keys with multiple path variables in primary key order

### ~~GAP-R2: 400 for metadata type parsing errors~~ — RESOLVED 2026-03-24
- MetadataExtractor generates safe parse helpers; GlobalExceptionHandler returns 400 BAD_REQUEST

### ~~GAP-R3: RFC 3339 timestamp format~~ — RESOLVED 2026-03-24
- ErrorResponse uses OffsetDateTime; all handlers use OffsetDateTime.now()

### ~~GAP-D1: OpenAPI `x-precision` and `x-scale` extensions~~ — RESOLVED 2026-03-24
- ProtoOpenAPIGenerator now reads models.yaml for precision/scale and emits x-precision/x-scale on decimal fields

---

## Remaining

### GAP-P1: Non-Java language package options are aspirational
- **Spec**: Defines Go, Python, Ruby, Node package conventions
- **Current**: Only Java `java_package` options are generated
- **Status**: Blocked on Phase 5 (language implementations). No action needed until then.

---

## Summary

| ID | Doc | Status |
|----|-----|--------|
| ~~GAP-M1~~ | MODELS_YAML | Resolved 2026-03-24 |
| ~~GAP-M2~~ | MODELS_YAML | Resolved 2026-03-24 |
| ~~GAP-S1~~ | SPECS_YAML | Resolved 2026-03-24 |
| ~~GAP-R1~~ | REST_CONTRACT | Resolved 2026-03-24 |
| ~~GAP-R2~~ | REST_CONTRACT | Resolved 2026-03-24 |
| ~~GAP-R3~~ | REST_CONTRACT | Resolved 2026-03-24 |
| ~~GAP-D1~~ | DECIMAL | Resolved 2026-03-24 |
| GAP-P1 | PROTO_CONVENTIONS | Blocked on Phase 5 |
