# Roadmap (Draft)

This roadmap reflects the current decisions and the latest pipeline constraints.

## Principles
- Build-time generation only. No runtime YAML evaluation.
- Parser parity across all languages. Outputs must match reference files exactly.
- Protobuf-aligned types in contracts, with a custom Decimal for precision.
- REST and gRPC enforce the same blocking rule semantics.

## Phase 1 - Parser Parity
Goal: every language parser produces identical `models.yaml` and `specs.yaml` outputs.

Deliverables
1. `PARSER_PARITY.md`
2. `CONFORMANCE_TESTS.md` fixture layout and golden outputs

Acceptance criteria
1. Given the same inputs, each language parser produces byte-for-byte identical YAML.
2. All fixture cases pass in Java, Go, Python, Ruby.

## Phase 2 - Proto and Rule Compilation
Goal: compile-time generation of schema and rules across languages.

Deliverables
1. Stable field numbers in `models.yaml`.
2. Nullable fields mapped to `optional` in proto.
3. Decimal type generated in proto and mapped to native Decimal types.
4. Rule generators emit typed constants and compile-time logic.

Acceptance criteria
1. No runtime YAML parsing in any runtime rule engine.
2. Rule code in each language compiles and matches reference output.

## Phase 3 - REST and gRPC Contract Standardization
Goal: standardize API naming, path rules, and error handling.

Deliverables
1. REST path naming rules for singular, plural, and composite keys.
2. gRPC service definitions for all models.
3. Standard error responses and metadata parsing rules.

Acceptance criteria
1. REST and gRPC semantics match across languages.
2. Blocking rules reject requests consistently.

## Phase 4 - Production-Grade Server Generators
Goal: generate server scaffolds with clean extension points.

Deliverables
1. Storage interfaces with default in-memory implementations.
2. Strict metadata parsing with standard errors.
3. Rule evaluation results returned in responses.

Acceptance criteria
1. Generated servers compile and run with no manual edits.
2. Storage interface is used by services in all languages.

## Phase 5 - Language Implementations
Goal: deliver Go, Python, Ruby first, then Rust and Node after research.

Deliverables
1. Go implementation using net/http and grpc-go.
2. Python implementation using FastAPI and grpcio.
3. Ruby implementation using Sinatra and grpc.

Acceptance criteria
1. Each language passes conformance tests.
2. REST and gRPC outputs match reference behavior.

## Phase 6 - Rust and Node
Goal: pick frameworks and protobuf libraries, then implement.

Deliverables
1. Research notes and final framework selection.
2. Rust and Node implementations.

Acceptance criteria
1. Rust and Node pass conformance tests.

## Non-goals
- No runtime YAML evaluation.
- No performance targets in conformance tests.
- No hard backward compatibility requirement in early iterations.
