> **Status: Not Yet Implemented** — Phase 1+. See [ROADMAP.md](ROADMAP.md).

# Conformance Tests (Draft)

Conformance tests validate parser parity and generator consistency across languages.

## Fixture layout
```
conformance/
  inputs/
    basic/
      schema.sql
      views.sql
      metadata.yaml
      features/
        appget.feature
      expected/
        models.yaml
        specs.yaml
        openapi.yaml
        proto/
          appget_models.proto
```

## Test categories
1. SQL parser parity
2. Gherkin parser parity
3. models.yaml to proto parity
4. proto to OpenAPI parity
5. Rule evaluation parity using generated rule classes

## Required assertions
- Byte-for-byte YAML matching.
- Stable field numbers across regenerations.
- Decimal and timestamp handling identical across languages.

## Non-goals
- No performance benchmarks.
