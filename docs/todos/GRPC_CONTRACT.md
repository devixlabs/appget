# gRPC Contract (Draft)

> **Status: Not Ready for Implementation** — Spec needs expansion. Deferred in favor of 0d (HTML MVP).

## Architectural Decision (2026-04-02)

The Phase 0b ServerEmitter abstraction was designed for **framework variation within REST** (Spring Boot → FastAPI → Gin). It does NOT cleanly accommodate **protocol variation** (REST vs gRPC vs GraphQL). The method names (`emitController`, `emitApplicationClass`, `emitBuildGradle`) are REST-shaped.

### Multi-Protocol Architecture

Each protocol gets its own generator. Shared infrastructure is extracted as common code.

```
Shared Infrastructure (protocol-agnostic)
├── Repository layer (interfaces + in-memory impls)
├── Rule evaluation (RuleService, SpecificationRegistry)
├── DTOs (RuleAwareResponse, RuleEvaluationResult, RuleOutcome, ErrorResponse)
├── Exceptions (RuleViolationException, ResourceNotFoundException, MetadataParsingException)
└── Metadata context (MetadataContext, context POJOs)

Protocol-Specific Generators
├── REST: AppServerGenerator + ServerEmitter/SpringBootEmitter  ← DONE (Phase 0b)
├── gRPC: GrpcServerGenerator (separate generator, own interface) ← THIS SPEC
└── GraphQL: GraphQLServerGenerator (future)                     ← TRACKED
```

**Key insight**: The repository, rule, DTO, and exception layers are identical across protocols. Only the presentation layer (controllers/service impls, entry point, build config, metadata extraction) differs. The implementation plan should extract shared infrastructure first, then build protocol-specific generators on top.

### What must be resolved before implementation

1. **Extract shared infrastructure** — Repository interfaces, RuleService, SpecificationRegistry, DTOs, and exceptions currently live in `SpringBootEmitter`. The protocol-agnostic parts need to be extracted so gRPC and GraphQL generators can reuse them without depending on Spring Boot.

2. **Design GrpcServerGenerator interface** — Similar to ServerEmitter but with gRPC-specific methods (`emitServiceImpl`, `emitInterceptor`, `emitServerBootstrap`). NOT a second implementation of ServerEmitter.

3. **Metadata mapping** — gRPC metadata keys are lowercase strings (e.g., `x-sso-authenticated`), not HTTP headers. The MetadataExtractor pattern must be adaptable per protocol.

4. **Test harness** — gRPC endpoints can't be tested with curl. Need a gRPC client-based verification script (equivalent of `make verify` for gRPC).

---

## Service Pattern (unchanged from original draft)

gRPC services are generated for models, not views.

For each model `Users`:
- `message UsersId` contains primary key fields
- `rpc CreateUsers(Users) returns (Users)`
- `rpc GetUsers(UsersId) returns (Users)`
- `rpc UpdateUsers(Users) returns (Users)`
- `rpc DeleteUsers(UsersId) returns (google.protobuf.Empty)`
- `rpc ListUsers(google.protobuf.Empty) returns (UsersList)`

`UsersList` has `repeated Users items`.

## Composite Keys

- `UsersId` includes all primary key fields in order

## Blocking Rules

- Rule evaluation mirrors REST behavior
- If any blocking rule fails, return `INVALID_ARGUMENT`
- If `google.rpc.Status` is available, attach rule outcomes as details
- If not, return a minimal error message

## Metadata

- Metadata keys mirror REST header names but lowercased for gRPC metadata
- Example: `x-sso-authenticated`

## Error Mapping

| Condition | gRPC Status | REST Equivalent |
|-----------|-------------|-----------------|
| Metadata parse failure | `INVALID_ARGUMENT` | 400 Bad Request |
| Entity not found | `NOT_FOUND` | 404 Not Found |
| Blocking rule violation | `INVALID_ARGUMENT` | 422 Unprocessable Entity |
| Malformed request | `INVALID_ARGUMENT` | 400 Bad Request |
| Internal error | `INTERNAL` | 500 Internal Server Error |

---

## Spec Gaps to Resolve

- [ ] Shared infrastructure extraction plan (which SpringBootEmitter methods are protocol-agnostic?)
- [ ] GrpcServerGenerator interface design (methods, context objects)
- [ ] gRPC test harness design (grpcurl? custom Java client? protobuf reflection?)
- [ ] Streaming support decision (server streaming for List operations? unary only for MVP?)
- [ ] Health check / reflection service inclusion
- [ ] Build integration (`make generate-grpc`, Gradle task, protoc gRPC plugin wiring)
