# SPEC: Content Negotiation — Multi-Format Response from Same Endpoints

> **Status: Research Complete, Ready for Implementation** — Architecture confirmed. Spring Boot pattern validated. Implementation NOT started.

---

## Problem

The generated REST API returns JSON only. Different clients need different formats from the same endpoints:

- `Accept: application/json` → JSON (current behavior, no change)
- `Accept: text/html` → Server-rendered HTML pages with live data (tables, forms, detail views)
- `Accept: application/xml` → XML (future — requires `ContentTransform.toXml()`, not free like JSON)
- `Accept: text/csv` → CSV (future — same `ContentTransform` pattern as HTML)

Today, `generated-html/` produces static HTML scaffolds with no live data. Content negotiation makes those pages dynamic — same structure, real data, served from the same REST endpoints that already serve JSON.

**MVP focus: JSON ↔ HTML only.** XML and CSV follow the same pattern once HTML is proven — they are not in MVP scope.

---

## Core Architectural Decision

**The HTTP framework handles content negotiation. appget handles content transformation.**

This is a clean two-responsibility split:

| Responsibility | Owner | What It Does |
|---------------|-------|-------------|
| **Content Negotiation** | HTTP framework (Spring Boot, FastAPI, Gin, Sinatra, Rocket) | Reads `Accept` header, selects response format, sets `Content-Type`, routes to the correct serializer/converter |
| **Content Transformation** | appget-generated `ContentTransform` implementation | Converts between JSON (internal format) and HTML, XML, CSV, etc. Pure data transformation — no HTTP awareness |

**Why this split?**
- Every target framework already has excellent built-in Accept header parsing — reimplementing it is wasted effort and a source of bugs (quality factors, wildcard matching, browser quirks)
- The transform logic (JSON → HTML table, JSON → form, JSON → XML) is the part no existing tool generates — this is appget's value-add
- The `ContentTransform` interface is the boundary — framework wiring on one side, pure transformation on the other
- If a better transform library is found later, only the implementation behind the interface changes

---

## Architecture: Three Layers

### Layer 1: Controller (unchanged)

Controllers remain **format-unaware**. They handle CRUD, call services, evaluate rules, and return domain objects. They never check Accept headers or set Content-Type — that's the framework's job.

```java
// Controller code does NOT change — identical to today
@PostMapping("/{resource}")
public ResponseEntity<RuleAwareResponse<Users>> create(@RequestBody Users entity, ...) {
    // business logic, rule evaluation, save
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

The only change to controller annotations is adding `produces` to declare supported formats:

```java
// Before (JSON-only):
@GetMapping

// After (multi-format):
@GetMapping(produces = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_HTML_VALUE })
```

This is the portable pattern. Every target framework has an equivalent:

| Framework | Annotation/Decorator | Multi-Format Declaration |
|-----------|---------------------|------------------------|
| Spring Boot (Java) | `@GetMapping(produces = {...})` | Array of `MediaType.*_VALUE` constants |
| Rocket (Rust) | `#[get("/", format = "json")]` | `format` attribute (one per route, or responder-based) |
| FastAPI (Python) | `@app.get("/", response_class=...)` | `response_class` parameter or custom middleware |
| Sinatra (Ruby) | `respond_to` block in route | Format-specific blocks |
| Gin (Go) | `c.NegotiateFormat(...)` | MIME constants in handler/middleware |

**First listed media type = default** when no Accept header is sent (or `Accept: */*`). JSON should always be first — API clients get JSON by default, browsers explicitly request `text/html`.

### Layer 2: Framework Wiring (generated, framework-specific)

Each framework has a native extension point for registering custom format handlers. appget generates the wiring code that connects the framework's content negotiation to the `ContentTransform` interface.

| Framework | Extension Point | What appget Generates |
|-----------|----------------|----------------------|
| Spring Boot | `HttpMessageConverter<Object>` | `HtmlHttpMessageConverter` class + `@Configuration` registration |
| DRF (Python) | `BaseRenderer` subclass | `HtmlRenderer` class + `DEFAULT_RENDERER_CLASSES` config |
| Gin (Go) | Middleware function | `ContentNegotiationMiddleware` that calls `c.NegotiateFormat()` |
| Sinatra (Ruby) | `respond_to` block | Format-specific blocks inside each route |
| Rocket (Rust) | `Responder` trait implementation | Custom `HtmlResponder` struct |

**Spring Boot detail** (reference implementation):

```java
// Generated: HtmlHttpMessageConverter.java
// Registered via @Configuration in Application.java
public class HtmlHttpMessageConverter extends AbstractHttpMessageConverter<Object> {
    private final ContentTransform transform;

    public HtmlHttpMessageConverter(ContentTransform transform) {
        super(MediaType.TEXT_HTML);
    }

    @Override
    protected void writeInternal(Object object, HttpOutputMessage outputMessage) {
        // Delegate to ContentTransform for the actual HTML generation
        String html = transform.toHtml(object, outputMessage.getHeaders());
        outputMessage.getBody().write(html.getBytes(StandardCharsets.UTF_8));
    }
}
```

Spring's `ContentNegotiationManager` handles all the hard parts:
- Parsing `Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8`
- Quality factor (`q=`) priority ordering
- Matching against registered converters
- Setting `Content-Type` response header
- Returning 406 Not Acceptable when no converter matches

**JSON is free. HTML requires a generated transform. XML and CSV follow the same pattern later.**
- JSON: works out of the box with `spring-boot-starter-web` (Jackson auto-configured)
- HTML: requires the custom `HtmlHttpMessageConverter` + `ContentTransform` (this is what appget generates)
- XML: will require `ContentTransform.toXml()` — Jackson XML may help with serialization but the JSON→XML mapping still needs explicit transform logic. Low priority, same pattern as HTML.
- CSV: will require `ContentTransform.toCsv()` — same `HttpMessageConverter` pattern as HTML. Low priority, future reference only.

### Layer 3: Content Transform (generated, framework-agnostic)

Pure data transformation functions. No HTTP awareness, no framework imports, no Accept header parsing. Takes data in, returns formatted string out.

```
ContentTransform (interface)
├── toHtml(Object data, context)     → HTML string
│   ├── renders array  → <table> (list page)
│   ├── renders object → <dl> (read-only detail)
│   └── renders object → <form> (pre-filled edit form)
├── toXml(Object data)               → XML string (future)
├── toCsv(Object data)               → CSV string (future)
├── fromFormData(Map<String,String>)  → JSON-compatible object
└── fromXml(String xml)              → JSON-compatible object (future)
```

**Language implementations:**

| Language | Interface | Default Implementation |
|----------|-----------|----------------------|
| Java | `interface ContentTransform` | `class DefaultHtmlTransform implements ContentTransform` |
| Go | `type ContentTransform interface` | `type HtmlTransform struct` |
| Python | `class ContentTransform(ABC)` | `class HtmlTransform(ContentTransform)` |
| Ruby | `module ContentTransform` | `class HtmlTransform` |
| Rust | `trait ContentTransform` | `struct HtmlTransform` |

**The interface is the key.** It allows:
- Swapping in a third-party library if one is found
- Testing transforms in isolation (no HTTP server needed)
- Replacing the implementation without touching controllers or framework wiring
- Each language choosing its best approach (library, hand-rolled, or generated)

---

## Request/Response Flow

### Outbound (server → client): Response Body Transformation

```
Controller returns domain object (e.g., Users protobuf message)
    │
    ▼
Framework checks Accept header
    │
    ├── Accept: application/json
    │   → Default JSON serializer (Jackson)
    │   → No transform needed — JSON is the internal format
    │
    ├── Accept: text/html
    │   → HtmlHttpMessageConverter
    │   → Calls ContentTransform.toHtml(object, context)
    │   → Returns HTML string (table, form, or detail page)
    │
    ├── Accept: application/xml  (future)
    │   → XmlHttpMessageConverter
    │   → Calls ContentTransform.toXml(object)
    │   → Returns XML string
    │
    ├── Accept: text/csv  (future)
    │   → CsvHttpMessageConverter
    │   → Calls ContentTransform.toCsv(object)
    │   → Returns CSV string
    │
    └── No match
        → 406 Not Acceptable (framework handles this)
```

### Inbound (client → server): Request Body Transformation

```
Client sends request body
    │
    ├── Content-Type: application/json
    │   → Default JSON deserializer (Jackson)
    │   → No transform needed
    │
    ├── Content-Type: application/x-www-form-urlencoded
    │   → Framework parses form fields
    │   → ContentTransform.fromFormData(fields) → domain object
    │   → Controller receives same type as JSON path
    │
    └── Content-Type: application/xml  (future)
        → ContentTransform.fromXml(xml) → domain object
```

### HTML-Specific Considerations

**Form submissions from HTML pages** use `application/x-www-form-urlencoded`, not JSON. The transform must handle:

- **Hidden `_method` field**: HTML forms only support GET/POST. Edit forms use `method="POST"` with `<input type="hidden" name="_method" value="PUT">`. Spring's `HiddenHttpMethodFilter` converts this to a PUT request before the controller sees it.
- **Checkbox fields**: Unchecked checkboxes are absent from form data (not `false`). The transform must treat missing boolean fields as `false`.
- **Type coercion**: Form data is all strings. The transform must coerce to the correct types (string → int, string → boolean, string → BigDecimal, etc.) using the model's field type metadata from `models.yaml`.

**Redirect after POST/PUT**: HTML clients expect a redirect after form submission (POST → 303 See Other → GET). The wiring layer should detect `Accept: text/html` on write operations and return a redirect instead of the JSON response body. This is the PRG (Post/Redirect/Get) pattern.

---

## What appget Generates (Per Framework)

### Files Added to Generated Server

For the Spring Boot reference implementation (MVP — HTML only):

| File | Group | Purpose |
|------|-------|---------|
| `ContentTransform.java` | B (Middleware) | Interface — `toHtml()`, `fromFormData()` (future: `toXml()`, `toCsv()`) |
| `DefaultHtmlTransform.java` | B (Middleware) | Default implementation — StringBuilder-based HTML generation |
| `HtmlHttpMessageConverter.java` | B (Middleware) | Spring `HttpMessageConverter` for `text/html` that delegates to `ContentTransform` |

### Files Modified in Generated Server

| File | Change |
|------|--------|
| `*Controller.java` (per model) | Add `produces = {JSON, HTML}` to `@GetMapping`/`@PostMapping`/`@PutMapping` annotations |
| `*Controller.java` (per view) | Add `produces = {JSON, HTML}` to `@GetMapping` annotations |
| `Application.java` | Register `HtmlHttpMessageConverter` bean |

### ServerEmitter Interface Changes

New methods added to `ServerEmitter.java`:

```java
// Group B: Middleware
String emitContentTransformInterface(String basePackage);
String emitDefaultHtmlTransform(String basePackage, List<ModelDef> models, List<ViewDef> views);
String emitHtmlMessageConverter(String basePackage);
```

`SpringBootEmitter` implements these. Future emitters (FastAPI, Gin, Sinatra) implement their framework-specific equivalents (e.g., `HtmlRenderer` for DRF, `ContentNegotiationMiddleware` for Gin).

### Existing emitController / emitViewController Changes

The `emitController()` and `emitViewController()` methods in `SpringBootEmitter` gain `produces` attributes on their `@GetMapping` / `@PostMapping` / `@PutMapping` annotations. No other controller logic changes.

---

## HTML Routing Strategy

### Decision: Same URLs, Accept Header Differentiation (no dedicated form routes)

HTML pages are served from the **same REST endpoints** as JSON. No `/users/new`, no `/users/{id}/edit` — the framework's content negotiation selects the response format based on the `Accept` header.

| Request | Accept: application/json | Accept: text/html |
|---------|------------------------|-------------------|
| `GET /users` | JSON array | HTML list table |
| `GET /users/{id}` | JSON object | HTML detail view |
| `POST /users` | JSON response (201) | Redirect after save (303) |
| `PUT /users/{id}` | JSON response (200) | Redirect after save (303) |
| `DELETE /users/{id}` | 204 No Content | Redirect to list (303) |
| `GET /views/user-profile` | JSON array | HTML list table (read-only) |

**How create/edit forms are accessed**: The HTML list page includes "Create New" and "Edit" links. When the transform renders the list table for a model, it includes action links. When the transform renders the detail page, it includes an edit link. The edit form is a variation of the detail page — same endpoint (`GET /users/{id}`), same Accept header, but the HTML transform renders a form instead of a read-only `<dl>`. The distinction is handled via a query parameter like `?action=edit` (or the transform always renders the editable form for detail views — TBD during implementation).

### Considered Alternative: URL Path Prefixes (`/html/` vs `/api/`)

An alternative approach would use path-based routing instead of Accept header:
- `/api/users` → always JSON
- `/html/users` → always HTML

**Pros**: Simpler routing, no conneg complexity, no `Vary` header issues, easy to debug.
**Cons**: Doubles the number of generated routes, breaks the REST principle of "same resource at different URLs", can't use standard HTTP clients that set Accept headers properly, and most importantly — every target framework already handles Accept-based routing natively.

**Decision**: Use Accept header. Path prefixes remain a fallback if a specific framework forces path-based separation, but no known target framework requires this.

---

## HTML Output Structure

The runtime HTML output must match the structure of `generated-html/` (Phase 0d static pages). Same page types, same field mappings, same navigation — but with real data from the API.

### Page Types and Transform Methods

| Page Type | Endpoint | Transform Method | Output |
|-----------|----------|-----------------|--------|
| **List** | `GET /users` | `toHtmlTable(data[], modelDef)` | `<table>` with column headers from field names, one row per entity, action links per row |
| **Detail** | `GET /users/{id}` | `toHtmlDetail(data{}, modelDef)` | `<dl>` with `<dt>`/`<dd>` pairs for each field, edit link |
| **Create Form** | `GET /users` (empty state or link) | `toHtmlCreateForm(modelDef)` | `<form>` with empty inputs, `method="POST"` action |
| **Edit Form** | `GET /users/{id}` (with edit context) | `toHtmlEditForm(data{}, modelDef)` | `<form>` with pre-filled inputs, hidden `_method=PUT` |
| **View List** | `GET /views/user-profile` | `toHtmlTable(data[], viewDef)` | Same as model list, but read-only (no edit/delete/create links) |

### Field → HTML Input Mapping

Same mapping as `HtmlCrudGenerator` (Phase 0d):

| models.yaml type | original_sql_type | HTML Input | Notes |
|-----------------|-------------------|------------|-------|
| string | VARCHAR | `<input type="text">` | |
| string | TEXT | `<textarea>` | Check `original_sql_type` |
| int32/int64 | INT/BIGINT | `<input type="number" step="1">` | |
| decimal | DECIMAL | `<input type="number" step="0.01">` | |
| float64 | FLOAT/DOUBLE | `<input type="number" step="any">` | |
| bool | BOOLEAN | `<input type="checkbox">` | Never `required` |
| date | DATE | `<input type="date">` | |
| datetime | TIMESTAMP | `<input type="datetime-local">` | |

### Business Rules Display

If the model has rules in `specs.yaml`, the HTML form pages include:

```html
<details>
<summary>Business Rules</summary>
<ul>
  <li>[BLOCKING] UserEmailValidation</li>
  <li>PostEngagementTracking</li>
</ul>
</details>
```

### Navigation

Every HTML page includes:
- Link back to root index
- Link to the model/view list page
- Model list pages: links to create, edit, delete per row
- View list pages: links to detail only (read-only)

---

## Relationship to generated-html/ (Phase 0d)

| Aspect | Phase 0d (HtmlCrudGenerator) | Phase 0f (ContentTransform) |
|--------|-----------------------------|-----------------------------|
| **Output** | Static HTML files on disk | HTML strings in HTTP responses |
| **Data** | No data — empty forms, placeholder tables | Live data from API |
| **When** | Build time (`make generate-html`) | Runtime (on each HTTP request) |
| **Purpose** | Reference scaffold + offline preview | Production HTML serving |
| **Structure** | Defines the page layout standard | Must match Phase 0d's structure exactly |

`generated-html/` becomes the **golden reference** for what the runtime transform should produce. Tests can diff the static output against runtime output (with data stripped) to verify structural consistency.

---

## Default Format Behavior

| Scenario | Format Returned | Why |
|----------|----------------|-----|
| `Accept: application/json` | JSON | Explicit JSON request |
| `Accept: text/html` | HTML | Explicit HTML request (browser navigation) |
| `Accept: */*` | JSON | Wildcard — default to first in `produces` list |
| No `Accept` header | JSON | Missing header — default to first in `produces` list |
| `Accept: text/plain` (unsupported) | 406 Not Acceptable | Framework returns error — no matching converter |

**Browser default Accept headers** include `text/html` with highest priority, so browser navigation naturally gets HTML. API clients (curl, Postman, SDKs) default to JSON. This means the Accept header alone is sufficient — no `?format=` query param needed for MVP.

---

## Research Summary

### Decision: Hybrid (Option 3)

**Use framework built-in conneg for WIRING. Generate the TRANSFORM logic.**

See [RESEARCH-content-negotiation-survey.md](RESEARCH-content-negotiation-survey.md) for the full survey.

### Key Findings

1. **No existing tool generates JSON→HTML transforms.** OpenAPI Generator, Swagger Codegen, and others handle `produces` annotations but leave actual format transformation to the developer. API Platform (PHP/Symfony) is the only exception but is locked to PHP.

2. **Every target framework has excellent built-in Accept parsing.** Spring Boot (`ContentNegotiationManager`), DRF (renderers), Gin (`c.NegotiateFormat()`), Sinatra (`respond_to`). No need to hand-parse.

3. **JSON and XML are nearly free.** Spring Boot: add `jackson-dataformat-xml` dependency → automatic. DRF: built-in `XMLRenderer`. The only format requiring custom transform code is HTML (and CSV).

4. **htmx creator argues against shared endpoints** (different stability requirements for HTML vs JSON APIs). This does not apply to appget — both formats are generated from the same schema and equally stable.

5. **`?format=` query param is a useful fallback** for browser testing. Easy to implement in every framework alongside Accept header.

### Research Status

| Researcher | Status | Details |
|-----------|--------|---------|
| **Claude (broad survey)** | ✅ Complete (2026-04-02) | OpenAPI generators, htmx/Hypermedia patterns, multi-format approaches |
| **Jeff (Spring Boot)** | ✅ Confirmed (2026-04-02) | `produces` attribute + `HttpMessageConverter` is the right pattern. Articles: [Multi-Format API Output](https://alexanderobregon.substack.com/p/multi-format-api-output-in-spring), [Return XML/JSON](https://www.appsdeveloperblog.com/return-xml-json-spring-mvc/) |
| **Jeff (other frameworks)** | ✅ Non-blocking (2026-04-03) | All target frameworks (Sinatra, FastAPI, Gin, Rocket) use similar declarative request mapping annotations in controllers. The pattern is confirmed portable. Framework-specific details will be filled in when those language subprojects start (Phase 5+). |

**Research gate: PASSED.** The architectural pattern (framework handles conneg, appget generates transforms) is validated across all target frameworks. Spring Boot reference implementation can proceed.

---

## MVP Scope

### In Scope (JSON ↔ HTML only)

- JSON array → HTML `<table>` (list pages with action links)
- JSON object → HTML `<dl>` (read-only detail pages)
- JSON object → HTML `<form>` (pre-filled edit forms)
- Empty model metadata → HTML `<form>` (create forms)
- Form POST (`application/x-www-form-urlencoded`) → JSON conversion (inbound)
- PRG pattern (Post/Redirect/Get) for HTML form submissions
- Re-render form with inline error messages when blocking rule fires (422)
- Navigation links (root index, list ↔ detail ↔ edit ↔ create)
- Business rules display on form pages
- `Vary: Accept` response header on endpoints that support multiple formats (best practice for caching correctness)
- Automated structural diff tests: runtime HTML vs `generated-html/` static pages (strip data, compare structure)

### Out of Scope (future — same `ContentTransform` pattern)

- XML support (`Accept: application/xml`) — requires `ContentTransform.toXml()` implementation
- CSV support (`Accept: text/csv`) — requires `ContentTransform.toCsv()` implementation
- `?format=html` query param fallback — nice-to-have, not needed since browsers send `Accept: text/html` natively
- CSS styling beyond minimal inline
- JavaScript of any kind
- Pagination, sorting, filtering in HTML views
- Authentication/session handling in HTML
- AJAX/htmx partial page updates

---

## Testing Strategy

### Unit Tests (no server needed)

| Test | What It Verifies |
|------|-----------------|
| `DefaultHtmlTransform` produces valid HTML for each page type | Transform correctness |
| Table output matches `generated-html/` list page structure (structural diff) | Structural consistency with Phase 0d |
| Form output matches `generated-html/` create/edit page structure (structural diff) | Structural consistency with Phase 0d |
| `fromFormData()` correctly coerces types (string→int, string→boolean) | Inbound transform |
| Missing checkbox fields treated as `false` | HTML form gotcha |
| Business rules rendered in form pages | Rule integration |
| Error form re-rendered with inline messages on 422 | Error display |

### Structural Diff Tests (unit level)

Compare runtime HTML output against `generated-html/` static pages:
1. Generate runtime HTML via `DefaultHtmlTransform` with sample data
2. Strip data values (replace cell contents with placeholders)
3. Diff against `generated-html/` static pages (also stripped)
4. Structural elements must match: same tags, same attributes, same nesting

This catches rendering drift between the static scaffold (Phase 0d) and the runtime transform.

### HTTP Tests (server required, added to `tests/http-tests.yaml`)

| Test | Request | Expected |
|------|---------|----------|
| JSON default | `GET /users` (no Accept) | `Content-Type: application/json` |
| HTML via Accept | `GET /users` with `Accept: text/html` | `Content-Type: text/html`, contains `<table>` |
| HTML detail | `GET /users/{id}` with `Accept: text/html` | Contains `<dl>`, field values |
| Form POST | `POST /users` with `Content-Type: application/x-www-form-urlencoded` | Creates entity, redirects (303) |
| Form POST with blocking rule violation | `POST /users` with invalid data, `Accept: text/html` | Re-renders form with error messages |
| Unsupported format | `GET /users` with `Accept: text/plain` | 406 Not Acceptable |
| View HTML | `GET /views/user-profile` with `Accept: text/html` | Contains `<table>`, no edit/delete links |
| Vary header | Any multi-format endpoint | `Vary: Accept` in response headers |

---

## Relationship to Other Phases

| Phase | Relationship |
|-------|-------------|
| **0b (ServerEmitter)** | `ContentTransform` interface + wiring generated by emitter. New methods added to `ServerEmitter` interface. |
| **0c (gRPC/GraphQL)** | Independent — content negotiation is REST-only. gRPC has its own serialization (protobuf wire format). |
| **0d (HTML CRUD static)** | Static HTML is the **structural reference** for runtime HTML output. Phase 0f makes those pages live with real data. |
| **0e (Multi-Industry)** | Verification harness tests HTML responses via Accept header, not just JSON. Proves the full pipeline works for browsers. |

---

## Key Files When Ready to Implement

| File | Relevance |
|------|-----------|
| `ServerEmitter.java` | Interface gains 4 new methods for content negotiation |
| `SpringBootEmitter.java` | Implements the 4 new methods + modifies `emitController()`/`emitViewController()` to add `produces` |
| `AppServerGenerator.java` | Orchestrator calls new emitter methods, writes new files |
| `EntityContext.java` | May need model field metadata (types, original SQL types) for transform |
| `generated-html/` | Reference output — runtime HTML must match this structure |
| `HtmlCrudGenerator.java` | Reference for field→input mapping, page layout, nav structure |
| `tests/http-tests.yaml` | Add tests with `Accept: text/html` header expectations |
| `tests/run-http-tests.py` | May need to support `Accept` header in test requests |

---

## Decisions Log

Resolved questions from spec review (2026-04-03):

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | Dedicated `/new` and `/edit` routes? | **No** — reuse existing endpoints, same URL with Accept header differentiation | No new routes unless a framework forces it. Keeps route count stable. |
| 2 | PRG redirect target (detail vs list)? | **Implementation-time decision** — either is acceptable | Jeff sees arguments for both. Choose whichever is simpler during implementation. |
| 3 | Error display on blocking rule (422)? | **Re-render form with inline errors** | Better UX than a generic error page. User sees what they submitted + what went wrong. |
| 4 | Structural diff tests? | **Yes** — automated structural diff between runtime HTML and `generated-html/` | Catches rendering drift between static scaffold and runtime transform. |
| 5 | `?format=` query param? | **Deferred** — not in MVP | Browsers send `Accept: text/html` natively. API clients can set Accept headers. Nice-to-have for debugging but not required. |
| 6 | `Vary: Accept` header? | **Yes, on multi-format endpoints only** | Best practice for caching correctness. Only emit on endpoints that declare multiple `produces` types, not globally. |
| 7 | Non-Spring framework research blocking? | **No** — all target frameworks confirmed to use similar declarative request mapping | Implementation proceeds with Spring Boot. Other frameworks filled in during Phase 5+. |
| 8 | XML support approach? | **Not free — needs `ContentTransform.toXml()`** | Jackson XML may help but the JSON→XML mapping still needs explicit transform logic. Same pattern as HTML, deferred to post-MVP. |

## Open Questions

Items to resolve during implementation:

1. **Detail vs Edit form rendering**: When `GET /users/{id}` returns HTML, should it render a read-only detail (`<dl>`) or an editable form (`<form>`)? Options: (a) always render detail, with an "Edit" link that adds `?action=edit`, (b) always render the form, (c) detail by default, form on click via same page anchor. → *Resolve during implementation based on what feels natural.*

2. **Model metadata in transform**: The `DefaultHtmlTransform` needs field names, types, and `original_sql_type` to generate correct `<input>` elements. Should this metadata be passed as a parameter per call, or should the transform hold a reference to the full model definitions at construction time? → *Impacts `ContentTransform` interface signature. Resolve during implementation.*

3. **Create form access**: Without a dedicated `/users/new` route, how does a user navigate to the create form? Options: (a) "Create New" link in list page that links to a form rendered by the transform, (b) the list page always includes an empty form at the top/bottom, (c) JavaScript-free approach TBD. → *Resolve during implementation.*
