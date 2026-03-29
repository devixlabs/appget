# Roadmap

This roadmap reflects the current decisions and the latest pipeline constraints. It serves as the **entry point** for `docs/todos/` — every pending doc maps to a phase below.

## Principles
- Build-time generation only. No runtime YAML evaluation.
- Parser parity across all languages. Outputs must match reference files exactly.
- Protobuf-aligned types in contracts, with a custom Decimal for precision.
- REST and gRPC enforce the same blocking rule semantics.

---

## Phase 0 - Java Reference Hardening
> **Status: In Progress** — Refactoring and completing the Java reference implementation before cross-language work begins.

Goal: the Java subproject is clean, well-abstracted, and establishes every structural pattern that future language subprojects replicate.

### 0a. NamingConvention Interface
- **Doc**: [TODO-naming-convention-interface.md](TODO-naming-convention-interface.md)
- **What**: Extract duplicated `snakeToCamel` from `Specification.java` and `JavaUtils.java` into a `NamingConvention` interface + `JavaNaming` static utility in `dev.appget.naming`
- **Why**: Establishes the cross-language pattern for field-name resolution. Every future language replicates this interface.
- **Blocks**: Nothing directly, but reduces tech debt before larger refactors touch the same files
- **Effort**: Small (~9 files, focused refactor)

### 0b. Server Framework Abstraction
- **Doc**: [SPEC-server-framework-abstraction.md](SPEC-server-framework-abstraction.md)
- **What**: Refactor `AppServerGenerator` into `ServerGenerator` (what to generate) + `ServerEmitter` interface (how to write it), with `SpringBootEmitter` as the first implementation
- **Why**: Currently porting the server generator to another language means rewriting everything. This separation makes AppGet logic reusable.
- **Blocks**: HTML codegen (0d), all Phase 5 language server generators
- **Effort**: Large (AppServerGenerator is the biggest generator)

### 0c. gRPC Server Implementation
- **Doc**: [GRPC_CONTRACT.md](GRPC_CONTRACT.md)
- **What**: Implement gRPC server generation (proto stubs already exist from `ModelsToProtoConverter`)
- **Why**: Completes the "REST and gRPC enforce the same blocking rule semantics" principle. Validates the ServerEmitter abstraction as a second emitter type.
- **Blocks**: Phase 3 completion
- **Effort**: Medium

### 0d. HTML CRUD Code Generation
- **Doc**: [SPEC-html-crud-codegen.md](SPEC-html-crud-codegen.md)
- **What**: Generate static HTML CRUD pages from `models.yaml` + `specs.yaml`. No JS, no CSS frameworks. Pure HTML5 with native form semantics.
- **Why**: First-class pipeline artifact alongside `openapi.yaml`. First real test of the ServerEmitter content-type abstraction.
- **Blocked by**: Server Framework Abstraction (0b) — needs emitter support for `application/x-www-form-urlencoded`
- **Effort**: Medium

### Suggested order: 0a → 0b → 0c → 0d

0c and 0d can be parallelized once 0b is done. 0a is independent and should go first.

---

## Phase 1 - Parser Parity
> **Status: Pending** — Java parsers exist but no cross-language conformance yet.

Goal: every language parser produces identical `models.yaml` and `specs.yaml` outputs.

Deliverables
1. [PARSER_PARITY.md](PARSER_PARITY.md) — rules for byte-for-byte identical output
2. [CONFORMANCE_TESTS.md](CONFORMANCE_TESTS.md) — fixture layout and golden outputs

Acceptance criteria
1. Given the same inputs, each language parser produces byte-for-byte identical YAML.
2. All fixture cases pass in Java, Go, Python, Ruby.

## Phase 2 - Proto and Rule Compilation
> **Status: Done in Java** (2026-03-24)

Goal: compile-time generation of schema and rules across languages.

Deliverables (all implemented in Java):
1. Stable field numbers in `models.yaml`.
2. Nullable fields mapped to `optional` in proto.
3. Decimal type generated in proto and mapped to native Decimal types.
4. Rule generators emit typed constants and compile-time logic.

## Phase 3 - REST and gRPC Contract Standardization
> **Status: REST done in Java** (2026-03-24). gRPC server pending (see Phase 0c).

Goal: standardize API naming, path rules, and error handling.

Deliverables (REST implemented in Java):
1. REST path naming rules for singular, plural, and composite keys.
2. Standard error responses and metadata parsing rules.

Remaining:
- gRPC service implementations — tracked in Phase 0c above.

## Phase 4 - Production-Grade Server Generators
> **Status: Done in Java** (2026-03-25)

Goal: generate server scaffolds with clean extension points.

Deliverables (all implemented in Java):
1. In-memory storage with repository interfaces.
2. Strict metadata parsing with standard errors (400 BAD_REQUEST).
3. Rule evaluation results returned in responses (blocking = 422, informational = reported).

## Phase 5 - Language Implementations
> **Status: Not started** — Blocked on Phase 0 (server abstraction) and Phase 1 (conformance).

Goal: deliver Go, Python, Ruby first, then Rust and Node after research.

- **Doc**: [LANGUAGE_IMPLEMENTATION.md](LANGUAGE_IMPLEMENTATION.md)
- **Gaps**: [CONTRACT_GAPS.md](CONTRACT_GAPS.md) (GAP-P1 resolves as part of this phase)

Deliverables
1. Go implementation using net/http and grpc-go.
2. Python implementation using FastAPI and grpcio.
3. Ruby implementation using Sinatra and grpc.

Acceptance criteria
1. Each language passes conformance tests.
2. REST and gRPC outputs match reference behavior.

## Phase 6 - Rust and Node
> **Status: Not started**

Goal: pick frameworks and protobuf libraries, then implement.

Deliverables
1. Research notes and final framework selection.
2. Rust and Node implementations.

Acceptance criteria
1. Rust and Node pass conformance tests.

---

## Non-goals
- No runtime YAML evaluation.
- No performance targets in conformance tests.
- No hard backward compatibility requirement in early iterations.
