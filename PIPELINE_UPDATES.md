# Pipeline Updates (Draft)

This document summarizes changes required to align the pipeline with the new contracts.

## Contract changes
1. `models.yaml` switches to protobuf-aligned types.
2. `models.yaml` includes `field_number` and primary key metadata.
3. `specs.yaml` supports explicit `value_type` and null operators.

## Generator changes
1. SQL parser must preserve field numbers across regenerations.
2. ModelsToProtoConverter must emit optional fields for nullables.
3. Decimal fields use a custom Decimal message.
4. SpecificationGenerator emits typed constants only.
5. RuleEngine uses generated rule classes, not runtime YAML.

## REST changes
1. Deterministic pluralization and path naming.
2. Composite key path params.
3. Standardized error response for metadata parse failures.

## gRPC changes
1. Key messages for composite keys.
2. Rule enforcement and error mapping.
