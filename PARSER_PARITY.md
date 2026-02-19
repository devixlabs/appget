# Parser Parity (Draft)

All language implementations must produce byte-for-byte identical outputs for the same inputs.

## SQL parsing rules
1. Preserve table and column declaration order.
2. Extract primary keys from inline and table-level declarations.
3. Map SQL types to protobuf-aligned types.
4. Preserve DECIMAL precision and scale.
5. Views resolve column types from source tables and aliases.

## Gherkin parsing rules
1. Domain is resolved from `@domain:` tags.
2. Scenario tags define target model or view, rule name, and blocking.
3. Conditions are normalized to the canonical operator set.
4. String values are double-quoted in `specs.yaml`.
5. Numbers and booleans are unquoted.

## YAML formatting rules
1. Two-space indentation.
2. Domains sorted by name.
3. Models and views ordered by SQL appearance.
4. Rules ordered by feature file and scenario order.
5. Strings are double-quoted where required.

## Reference output
- Conformance fixtures define expected `models.yaml` and `specs.yaml`.
- Parsers must match these outputs exactly.
