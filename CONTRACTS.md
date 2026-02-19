# Contracts (Draft)

This document summarizes the authoritative artifacts and their roles.

## Sources of Truth
1. `schema.sql` defines domain models.
2. `views.sql` defines derived read models.
3. `features/*.feature` defines business rules.
4. `metadata.yaml` defines auth context types.

## Authoritative Artifacts
1. `models.yaml` is authoritative for model and view schemas.
2. `specs.yaml` is authoritative for rules and metadata definitions.

## Generated Artifacts
1. `.proto` files are generated from `models.yaml`.
2. Rule classes are generated from `specs.yaml` plus `models.yaml`.
3. REST and gRPC servers are generated from `models.yaml` and `specs.yaml`.
4. OpenAPI spec is generated from `.proto` and REST naming rules.

## Contract Files
- `MODELS_YAML_SCHEMA.md` defines the `models.yaml` schema.
- `SPECS_YAML_SCHEMA.md` defines the `specs.yaml` schema.
- `DECIMAL.md` defines the Decimal representation.
- `REST_CONTRACT.md` defines REST semantics.
- `GRPC_CONTRACT.md` defines gRPC semantics.
- `PROTO_CONVENTIONS.md` defines proto file naming and package conventions.
- `PARSER_PARITY.md` defines canonical parsing rules.
- `CONFORMANCE_TESTS.md` defines fixtures and golden outputs.

## Build-Time Only
All evaluation and typing must be done at build time.
No runtime YAML parsing is allowed in production runtimes.
