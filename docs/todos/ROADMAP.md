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

### 0a. NamingConvention Interface — **Done** (2026-03-29)

### 0b. Server Framework Abstraction — **Done** (2026-04-02)

### 0c. Multi-Protocol Server Generation (gRPC + GraphQL)
- **Doc**: [GRPC_CONTRACT.md](GRPC_CONTRACT.md) (gRPC spec, draft — needs expansion)
- **What**: Extract shared infrastructure (repos, rules, DTOs, exceptions) from SpringBootEmitter, then build protocol-specific generators (`GrpcServerGenerator`, future `GraphQLServerGenerator`) as separate generators — NOT as ServerEmitter implementations.
- **Why**: ServerEmitter is REST-framework variation (Spring→FastAPI→Gin). Protocol variation (REST→gRPC→GraphQL) needs separate generators sharing common infrastructure.
- **Blocks**: Phase 3 completion (gRPC half)
- **Blocked by**: Spec expansion needed — shared infra extraction plan, GrpcServerGenerator interface, test harness
- **Effort**: Large (spec work + shared extraction + gRPC impl + GraphQL tracking)

### 0d. HTML CRUD Code Generation — **Done** (2026-04-02)

### 0e. Multi-Industry Verification Harness
- **Doc**: [SPEC-multi-industry-verification.md](SPEC-multi-industry-verification.md)
- **What**: Interactive loop that exercises domain-architect + full pipeline (API + HTML) across diverse industry verticals (finance, healthcare, e-commerce, food services, supply chain) until all pass.
- **Why**: Proves the Java reference implementation handles real-world domain complexity — not just the social media demo. Must pass before expanding to other languages.
- **Blocked by**: Content Negotiation (0f) — HTML pages need live data via Accept-header routing
- **Effort**: Large (5 industry scenarios, iterative debugging)

### 0f. Content Negotiation — Accept-Header-Driven Response Formats
- **Doc**: [SPEC-content-negotiation.md](SPEC-content-negotiation.md)
- **What**: Framework handles content negotiation (Accept header parsing, format routing). appget generates per-model `*PageRenderer` classes and static HTML template files with `{{CONTENT}}` placeholders. No framework templating engines (Thymeleaf, Jinja2, ERB). Controllers gain `produces` annotations and separate handler methods per format. Centralized `HtmlEscapeUtils` for XSS prevention.
- **Why**: Browsers get server-rendered HTML (old-fashioned GET/POST, no JS). API clients get JSON. Same endpoints. No framework templating dependency — build-time generated, auditable, fuzz-testable, portable across all target languages.
- **Research**: ✅ Complete (2026-04-03) — See [RESEARCH-content-negotiation-survey.md](RESEARCH-content-negotiation-survey.md).
- **Architecture**: ✅ Revised (2026-04-03) — PageRenderer + templates replaces original ContentTransform approach. Templates are pipeline-level artifacts (like `.proto`, `openapi.yaml`). PageRenderers are generated per-model at build time with all field metadata baked in.
- **Blocked by**: Nothing — ready for implementation
- **Prep landed (2026-06-01)** — three independent slices implemented + verified (470 tests green). Execution specs removed post-impl; code + tests are the record:
  - **A** — `emitHtmlEscapeUtils` (centralized XSS boundary) + `HtmlEscapeUtilsEmitTest` (15 fuzz/drift tests). Done.
  - **B** — `HtmlStructuralNormalizer` + golden snapshots of `generated-html/` under `java/src/test/resources/html-structure-golden/` (test-first contract for runtime HTML). Done.
  - **C** — `HtmlCrudGenerator.generateTemplates()` emits `templates/**/*.html` with `{{CONTENT}}` (Artifact 1). Done.
- **Remaining (0f core)**: per-model/per-view `*PageRenderer` classes (load templates, fill `{{CONTENT}}` with escaped data via HtmlEscapeUtils), controller `produces`/`text/html` handlers + PRG form handling, runtime-vs-golden structural diff wiring, error re-rendering.
- **Effort**: Medium-Large (PageRenderers + controller changes + structural diff tests + error re-rendering). Prep slices A/B/C complete.
- **MVP**: JSON + HTML only. XML and CSV follow same PageRenderer pattern post-MVP.

### 0g. Java Codegen Package Refactoring — **Done** (2026-04-05)
- Follow-up resolved (2026-06-01): `emitRuleService()` has no catch blocks — typed `EvaluableRule` dispatch, no dead `log.warn`.

### Suggested order: ~~0a~~ → ~~0b~~ → ~~0d~~ → ~~0g~~ → 0f → 0e → 0c

0a, 0b, 0d, and 0g are done. 0f (content negotiation) is next — research complete, ready for implementation. 0e (multi-industry) validates everything. 0c (gRPC/GraphQL) deferred until REST pipeline is proven.

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
> **Status: Not started** — Blocked on Phase 0 completion and Phase 1 (conformance).

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
