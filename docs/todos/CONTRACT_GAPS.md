# Contract Implementation Gaps

Tracked gaps in the cross-language contract docs that live in `docs/`. Each gap references the source doc and the Java source file where the fix belongs.

---

## MODELS_YAML_SCHEMA.md

### GAP-M1: `resource` field not emitted
- **Spec**: Each model/view should have a `resource` field (kebab-case REST resource name)
- **Current**: `SQLSchemaParser.java` does not emit `resource` in models.yaml
- **Fix location**: `SQLSchemaParser.generateYaml()` — add `resource:` line per model/view
- **Effort**: Low

### GAP-M2: `original_sql_type` field not emitted
- **Spec**: Optional traceability field preserving the original SQL type (e.g., `VARCHAR(255)`)
- **Current**: Not captured or emitted anywhere
- **Fix location**: `SQLSchemaParser.parseColumn()` — capture raw SQL type; `generateYaml()` — emit it
- **Effort**: Low

---

## SPECS_YAML_SCHEMA.md

### GAP-S1: `IS_NULL` and `IS_NOT_NULL` operators not supported
- **Spec**: Required operators per specs.yaml schema
- **Current**: `FeatureToSpecsConverter.OPERATOR_MAP` has only 6 operators (==, !=, >, <, >=, <=)
- **Fix location**: `FeatureToSpecsConverter` (parsing), `Specification.java` (evaluation)
- **Needs**: Gherkin DSL phrase mapping (e.g., "is null" -> `IS_NULL`) and evaluation logic
- **Effort**: Medium — touches parser, evaluator, and feature DSL

---

## REST_CONTRACT.md

### GAP-R1: Composite key path variables
- **Spec**: "Composite keys use multiple path params in primary key order"
- **Current**: All controllers use generic `{id}` single path variable
- **Fix location**: `AppServerGenerator.generateController()` — use `primary_key_position` from models.yaml
- **Effort**: Medium

### GAP-R2: 400 for metadata type parsing errors
- **Spec**: "Invalid metadata or type parsing returns 400"
- **Current**: `MetadataExtractor` has no error handling; `Integer.parseInt()` would throw unhandled `NumberFormatException`
- **Fix location**: `AppServerGenerator.generateMetadataExtractor()` + `generateExceptionHandler()`
- **Effort**: Low

### GAP-R3: RFC 3339 timestamp format
- **Spec**: ErrorResponse timestamp should be RFC 3339
- **Current**: Uses `LocalDateTime` (ISO-8601 via Jackson, close but not identical)
- **Fix location**: `AppServerGenerator.generateErrorResponse()` — use `OffsetDateTime` or explicit formatter
- **Effort**: Low

---

## DECIMAL.md

### GAP-D1: OpenAPI `x-precision` and `x-scale` extensions
- **Spec**: OpenAPI decimal properties should include `x-precision` and `x-scale` vendor extensions
- **Current**: `ProtoOpenAPIGenerator` emits `type: string, format: decimal` only
- **Fix location**: `ProtoOpenAPIGenerator` schema property generation
- **Effort**: Low

---

## PROTO_CONVENTIONS.md

### GAP-P1: Non-Java language package options are aspirational
- **Spec**: Defines Go, Python, Ruby, Node package conventions
- **Current**: Only Java `java_package` options are generated
- **Status**: Blocked on Phase 5 (language implementations). No action needed until then.

---

## Summary

| ID | Doc | Severity | Effort | Blocked? |
|----|-----|----------|--------|----------|
| GAP-M1 | MODELS_YAML | Low | Low | No |
| GAP-M2 | MODELS_YAML | Low | Low | No |
| GAP-S1 | SPECS_YAML | Medium | Medium | No |
| GAP-R1 | REST_CONTRACT | Medium | Medium | No |
| GAP-R2 | REST_CONTRACT | Medium | Low | No |
| GAP-R3 | REST_CONTRACT | Low | Low | No |
| GAP-D1 | DECIMAL | Low | Low | No |
| GAP-P1 | PROTO_CONVENTIONS | N/A | N/A | Phase 5 |
