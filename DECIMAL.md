# Decimal Representation (Draft)

DECIMAL values must preserve precision and scale at compile time.
All languages must use high-precision native decimal types.

## Proto message
Define a shared Decimal message in a common proto file.
```
message Decimal {
  bytes unscaled = 1;
  int32 scale = 2;
}
```

## Schema mapping
- SQL `DECIMAL(p,s)` maps to `type: decimal` with `precision` and `scale` in `models.yaml`.
- In proto, decimal fields use the custom Decimal message.

## JSON representation
REST JSON represents decimal values as strings.
OpenAPI uses:
- `type: string`
- `format: decimal`
- `x-precision` and `x-scale`

## Build-time generation
- Rule generators emit native decimal constants.
- No runtime YAML parsing is allowed.

## Language mappings
- Java: `java.math.BigDecimal`
- Python: `decimal.Decimal`
- Ruby: `BigDecimal` (stdlib `bigdecimal`)
- Go: custom Decimal wrapper using `math/big.Int` and scale
- Rust: custom Decimal wrapper or a selected crate after framework selection
- Node: custom Decimal wrapper using `BigInt` and scale

## Conversions
- Proto Decimal to native decimal: unscaled + scale.
- Native decimal to proto Decimal: serialize unscaled + scale.
- REST JSON to native decimal: parse from string.
