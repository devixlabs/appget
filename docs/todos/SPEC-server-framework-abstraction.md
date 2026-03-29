# SPEC: Server Framework Abstraction — AppServerGenerator Refactor

## Context & Priority

This refactor is **foundational to multi-language portability**. Currently `AppServerGenerator`
emits Spring Boot-specific Java code directly — annotations, imports, class structures — tightly
coupling AppGet's generation logic to a single framework in a single language.

This must be resolved in the Java reference implementation before any other language subproject
is attempted. Getting this wrong now means retrofitting the abstraction across N languages later.

This work is closely related to `SPEC-html-crud-codegen.md` — the Agent working on HTML codegen
will need to touch `AppServerGenerator` for content-type support. These two specs should be
planned together. The server abstraction refactor should be completed or at least structurally
in place before `HtmlCrudGenerator` is finalized, since HTML form support is the first real
test of the abstraction (it requires `application/x-www-form-urlencoded` support alongside JSON,
which is the first case where the emitter must handle a variation from the default).

---

## Problem Statement

`AppServerGenerator` currently has two concerns conflated:

1. **What to generate** — CRUD operations, route structure, rule application points,
   auth/metadata context injection. This is AppGet logic. It is language-agnostic and
   should be permanent across all server targets.

2. **How to write it** — Spring Boot annotations (`@RestController`, `@GetMapping`,
   `@RequestBody`, `@RequestMapping`, `@PathVariable`, etc.), Java class structure,
   import statements, `HiddenHttpMethodFilter` configuration. This is Spring Boot-specific.
   It has no place in AppGet's core logic.

The result is that porting the server generator to Python, Go, or Rust currently means
rewriting everything — including the AppGet logic that should be shared.

---

## Goal

Refactor `AppServerGenerator` so that:

- AppGet's generation logic (what to generate) is **framework-agnostic and reusable**
- Framework-specific syntax emission is **isolated and swappable**
- Adding a new server target (FastAPI, Gin, Actix, etc.) requires writing only a new
  emitter, not re-implementing the generation logic
- The Java Spring Boot output is **identical to current** after refactor (no behavior change)
- The abstraction is simple — avoid over-engineering; this is a plugin point, not a framework

---

## Proposed Architecture

### Core Abstraction: `ServerEmitter` Interface

```
ServerGenerator (orchestrator — AppGet logic, language-agnostic)
      │
      │  delegates syntax emission to →
      ▼
ServerEmitter (interface/contract)
      │
      ├── SpringBootEmitter     (java/ — current behavior, refactored out)
      ├── FastAPIEmitter        (python/ — future)
      ├── GinEmitter            (go/ — future)
      ├── ActixEmitter          (rust/ — future)
      └── ...
```

### `ServerGenerator` — AppGet-Owned Logic (What to Generate)

Responsibilities that stay in the generator, framework-agnostic:
- Iterate entities from `models.yaml`
- Determine CRUD operations per entity
- Determine route patterns (`/api/{domain}/{table}`, `/{id}` variants)
- Identify business rule application points from `specs.yaml`
- Identify auth/metadata context injection points from `specs.yaml`
- Determine field types and nullability for request/response shapes
- Determine primary key field per entity
- Decide file structure (one file per domain, one per entity, etc.)

`ServerGenerator` calls `ServerEmitter` methods for all syntax output — it never
writes a framework-specific string directly.

### `ServerEmitter` Interface — Contract (How to Write It)

The interface defines the vocabulary of server generation. Methods should map to
semantic operations, not syntax fragments. Illustrative (not exhaustive — Agent
derives the full contract from `AppServerGenerator` source):

```java
interface ServerEmitter {

    // File/class structure
    String emitFileHeader(String domain, String entity);
    String emitClassOpen(String className);
    String emitClassClose();
    String emitImports(List<String> dependencies);

    // Route handlers
    String emitListHandler(RouteContext ctx);
    String emitGetByIdHandler(RouteContext ctx);
    String emitCreateHandler(RouteContext ctx);
    String emitUpdateHandler(RouteContext ctx);
    String emitDeleteHandler(RouteContext ctx);

    // Request/response
    String emitRequestModel(EntityModel entity);
    String emitResponseModel(EntityModel entity);

    // Content type support
    List<String> supportedContentTypes();   // e.g. ["application/json",
                                            //        "application/x-www-form-urlencoded"]

    // Middleware / filter hooks
    String emitMethodOverrideConfig();      // HiddenHttpMethodFilter in Spring,
                                            // middleware in FastAPI, etc.

    // Business rule application
    String emitRuleCheck(RuleContext rule);

    // Auth/metadata context injection
    String emitMetadataContextParam(MetadataContext ctx);

    // File naming convention
    String outputFileName(String domain, String entity);
    String outputDirectory();
}
```

`RouteContext`, `EntityModel`, `RuleContext`, `MetadataContext` are plain data classes
(no framework dependency) populated by `ServerGenerator` from `models.yaml` + `specs.yaml`.

### `SpringBootEmitter` — Current Behavior, Extracted

Implements `ServerEmitter`. Contains all Spring Boot-specific strings:
- `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc.
- `@PathVariable`, `@RequestBody`, `@RequestParam`
- Spring import statements
- `ResponseEntity<>` return types
- `HiddenHttpMethodFilter` bean configuration
- `application/x-www-form-urlencoded` consumes annotation (needed for HTML form support)
- `application/json` consumes annotation

The `SpringBootEmitter` is the **only place** Spring Boot strings appear in the codebase.

---

## Content-Type Handling (Direct Link to HTML Spec)

The `supportedContentTypes()` method and `emitMethodOverrideConfig()` on `ServerEmitter`
are the abstraction points that solve the HTML form integration problem identified in
`SPEC-html-crud-codegen.md` item #5 (content-type mismatch) and #4 (HTTP method override).

For `SpringBootEmitter`:
- `supportedContentTypes()` returns both `application/json` and
  `application/x-www-form-urlencoded`
- `emitMethodOverrideConfig()` emits `HiddenHttpMethodFilter` bean
- Route handler annotations include `consumes` attribute covering both content types

This means HTML form POST and JSON API POST both work against the same generated endpoint.
The abstraction makes this a per-emitter decision, not a global pipeline concern.

---

## Data Classes (Framework-Agnostic Context Objects)

The Agent should define minimal plain data classes that carry the semantic information
`ServerGenerator` passes to `ServerEmitter`. These replace the current pattern of building
strings inline in `AppServerGenerator`. Candidates (derive final set from source):

- `EntityModel` — table name, domain, fields, primary key field
- `FieldModel` — name, type, nullable, is_pk
- `RouteContext` — entity, HTTP method, path pattern, path params, request model, response model
- `RuleContext` — rule name, target entity, blocking flag, condition summary
- `MetadataContext` — context category, fields required

These data classes live in the generator layer, not in any framework package.

---

## `AppServerGenerator` Refactor Steps (Suggested Order)

1. Read current `AppServerGenerator.java` in full — map every Spring-specific string to
   its semantic operation (this mapping becomes the `ServerEmitter` interface)
2. Define `ServerEmitter` interface and data classes
3. Extract all Spring Boot emission into `SpringBootEmitter implements ServerEmitter`
4. Refactor `AppServerGenerator` to delegate to `ServerEmitter` — no Spring strings remain
5. Wire `SpringBootEmitter` as the default/configured emitter
6. Add `application/x-www-form-urlencoded` support to `SpringBootEmitter`
   (required for HTML form integration)
7. Add `HiddenHttpMethodFilter` emission to `SpringBootEmitter`
8. Run `make all` — all 380+ tests must pass, generated Spring Boot output must be identical

---

## Configuration / Wiring

The emitter should be selectable — either via a constructor parameter to `AppServerGenerator`
or via a config property in the build pipeline. For the Java subproject, `SpringBootEmitter`
is the default and only implementation. Future subprojects pass their own emitter.

Keep it simple — no DI framework, no reflection. Direct instantiation is fine.

---

## What This Is NOT

- Not a plugin marketplace or extensibility framework
- Not a runtime configuration system
- Not an abstraction over HTTP concepts (routes are still routes)
- Not a replacement for language-specific codegen — each language subproject still has its
  own generator entry point; this abstraction lives within the server generation concern only

---

## Future Emitter Guidance (For Documentation, Not Implementation Now)

When a future subproject implements a new emitter:
- Implement `ServerEmitter` interface
- Map each method to the target framework's equivalent idiom
- Do not modify `ServerGenerator` — if a modification is needed, the interface contract
  needs to be revisited
- Example mappings (informational):

| Operation              | SpringBoot              | FastAPI (Python)      | Gin (Go)               |
|------------------------|-------------------------|-----------------------|------------------------|
| Route handler          | `@GetMapping`           | `@app.get(...)`       | `r.GET(...)`           |
| Path param             | `@PathVariable`         | `def f(id: int)`      | `c.Param("id")`        |
| Request body           | `@RequestBody`          | `Pydantic model`      | `c.ShouldBindJSON`     |
| Method override        | `HiddenHttpMethodFilter`| middleware            | middleware             |
| Form content type      | `consumes=...urlenc`    | `Form(...)` param     | `c.ShouldBind`         |

---

## Testing

- All existing `AppServerGenerator` tests must pass unchanged after refactor
- Add `SpringBootEmitterTest` — unit tests for each emitter method in isolation
- Add `ServerGeneratorTest` with a mock/stub emitter — verifies generator logic independent
  of any framework output
- Verify generated Spring Boot output is byte-for-byte (or semantically) identical to
  pre-refactor output for all existing test cases
- Test `application/x-www-form-urlencoded` consumes annotation is present on POST/PUT handlers
- Test `HiddenHttpMethodFilter` config is emitted

---

## Key Files for Agent to Read Before Planning

In order:
1. `java/PIPELINE.md` — pipeline context
2. `java/CLAUDE.md` — generator patterns and StringBuilder convention
3. `src/main/java/AppServerGenerator.java` — **primary source**, must be read completely
4. `src/main/java/ProtoOpenAPIGenerator.java` — reference for generator structure
5. `src/main/java/CodeGenUtils.java` — available utilities
6. `build.gradle` + `Makefile` — task wiring
7. `src/test/java/` — existing test patterns, especially server generator tests
8. `SPEC-html-crud-codegen.md` — HTML spec, since content-type and method override
   in this refactor are direct prerequisites for HTML form integration
