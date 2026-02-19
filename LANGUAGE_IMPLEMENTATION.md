# Language Implementation Guide (Draft)

## Shared requirements
- Implement SQL and Gherkin parsers that match conformance outputs.
- Generate proto files and rule classes at build time.
- Implement Decimal mapping to native high-precision types.
- Enforce blocking rule semantics in REST and gRPC.

## Go
- REST: `net/http`
- gRPC: grpc-go
- Decimal: custom wrapper using `math/big.Int` + scale

## Python
- REST: FastAPI
- gRPC: grpcio
- Decimal: `decimal.Decimal` (stdlib)

## Ruby
- REST: Sinatra
- gRPC: grpc
- Decimal: `BigDecimal` (stdlib `bigdecimal`)

## Rust
- Framework selection pending
- gRPC library pending
- Decimal strategy pending

## Node
- Framework selection pending
- Protobuf runtime pending
- Decimal strategy pending
