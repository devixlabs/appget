# SPEC: Content Negotiation — Accept-Header-Driven Response Formats

> **Status: Research Phase** — Architecture defined, implementation NOT started. Significant research needed before building.

## Problem

The generated REST API returns JSON only. Browsers, legacy systems, and different clients need different formats from the same endpoints:

- `Accept: application/json` → JSON (current, no change)
- `Accept: text/html` → HTML pages with data (tables, forms, detail views)
- `Accept: application/xml` → XML (future, for legacy system integration)

## Architectural Decision

**Content negotiation lives inside the generated server, not in an external proxy.** Each language's HTTP framework reads the `Accept` header and routes to the appropriate transform. No sidecars, no Python scripts, no additional processes.

### Three Layers

| Layer | Responsibility | Framework-Specific? |
|-------|---------------|-------------------|
| **Transform** | `jsonToHtmlTable()`, `jsonToHtmlForm()`, `jsonToXml()` — pure functions, no framework dependency | No — same algorithm in every language |
| **Wiring** | Check `Accept` header, call transform, set `Content-Type` response header | Yes — each framework has its own mechanism |
| **Controller** | Business logic, CRUD, rule evaluation | Unchanged — format-unaware |

### The Transform Interface

Each language implements a `ContentTransform` interface (or equivalent) behind which the actual transform logic lives. This keeps the implementation swappable — if a better library is found later, only the transform implementation changes.

```
ContentTransform (interface)
├── jsonToHtmlTable(data[])  → HTML string
├── jsonToHtmlForm(data{})   → HTML string (pre-filled form)
├── jsonToHtmlDetail(data{}) → HTML string (read-only detail)
├── jsonToXml(data)          → XML string (future)
├── formDataToJson(form)     → JSON string
└── xmlToJson(xml)           → JSON string (future)
```

Java: `interface ContentTransform` with `DefaultHtmlTransform implements ContentTransform`
Go: `type ContentTransform interface` with `type HtmlTransform struct`
Python: `class ContentTransform(ABC)` with `class HtmlTransform(ContentTransform)`
Ruby: module with `HtmlTransform` class

**The interface is the key.** It allows:
- Swapping in a third-party library if one is found
- Testing transforms in isolation
- Replacing the implementation without touching controllers or framework wiring
- Each language choosing its best approach (library, hand-rolled, or generated)

### Framework Wiring Examples

**Spring Boot** (Java): Register a custom `HttpMessageConverter<Object>` for `text/html` media type. Spring's content negotiation handles routing automatically — controllers don't change.

**Sinatra** (Ruby): `request.accept?('text/html')` check in route handler, delegate to transform.

**FastAPI** (Python): Check `request.headers["accept"]`, return `HTMLResponse` or default JSON.

**Gin** (Go): Middleware checks `Accept`, calls transform, sets `Content-Type`.

### Pipeline Integration

The `ServerEmitter` (or equivalent per language) generates:
1. The `ContentTransform` interface
2. A default implementation (`DefaultHtmlTransform`) using StringBuilder
3. The framework wiring (converter registration, middleware, etc.)

The static `generated-html/` output from `HtmlCrudGenerator` serves as the **reference** for what the HTML should look like — same structure, same field mappings, same navigation. The runtime transform produces the same HTML but with real data.

---

## Research Required (BLOCKING)

> **IMPORTANT**: Before implementing, thorough research is needed. There is a strong gut feeling that existing libraries or framework plugins solve this problem already. Building from scratch should be the LAST resort, not the first.

### Research Tasks

**Jeff (human) — framework-specific research:**
- [ ] Spring Boot: Does `ContentNegotiatingViewResolver` or an existing `HttpMessageConverter` library handle JSON→HTML out of the box? What about `spring-boot-starter-thymeleaf` with a JSON-to-model adapter?
- [ ] Sinatra/Ruby: Gems for automatic JSON→HTML rendering? (`respond_to` block patterns, `sinatra-contrib` media type handling)
- [ ] FastAPI/Python: Does `fastapi-responses` or similar handle content negotiation with HTML output?
- [ ] Go/Gin: Middleware for Accept-based response formatting?
- [ ] Generic: Are there OpenAPI-driven UI generators that read `openapi.yaml` and serve HTML forms automatically? (Swagger UI does read-only — is there a CRUD equivalent?)

**Claude (agent) — broad research:**
- [ ] Survey content negotiation libraries across Java, Python, Go, Ruby ecosystems
- [ ] Look for OpenAPI→HTML-CRUD generators (not just documentation — actual form-based CRUD)
- [ ] Check if `json-server` (Node.js) or similar mock servers have HTML output modes
- [ ] Investigate htmx + JSON API patterns — is 14KB of JS acceptable as a "no JS" compromise?
- [ ] Research `Hypermedia API` patterns (HAL, JSON-LD, Hydra) — do they solve this at the protocol level?

### Research Criteria

Any existing solution must be:
- Actively maintained (last release within 12 months)
- Lightweight (not a full framework — a library or plugin)
- Compatible with the target framework (Spring Boot, Sinatra, FastAPI, Gin)
- Replaceable behind the `ContentTransform` interface (no deep framework coupling)

### Decision Gate

After research, choose ONE of:
1. **Use existing library** — implement `ContentTransform` as a thin wrapper around the library
2. **Generate from scratch** — the pipeline generates transform code (like it generates everything else)
3. **Hybrid** — use a library for HTML rendering, generate the wiring code

Do NOT start implementation until this research is complete and a decision is made.

---

## MVP Scope

**In scope (text/html only):**
- JSON array → HTML `<table>` with headers from keys, rows from values
- JSON object → HTML `<dl>` (read-only detail)
- JSON object → HTML `<form>` (pre-filled edit form)
- Form POST (`application/x-www-form-urlencoded`) → JSON conversion
- Navigation (links between list/create/edit/detail pages)
- Business rules display on form pages (from specs.yaml)

**Out of scope (future):**
- XML support (`Accept: application/xml`) — same pattern, add later
- CSS styling beyond minimal inline
- JavaScript of any kind
- Pagination, sorting, filtering
- Authentication/session handling in HTML

---

## Relationship to Other Phases

| Phase | Relationship |
|-------|-------------|
| **0b (ServerEmitter)** | ContentTransform interface generated by emitter; wiring is framework-specific emitter code |
| **0c (gRPC/GraphQL)** | Independent — content negotiation is REST-only |
| **0d (HTML CRUD static)** | Static HTML is the **reference** for runtime HTML output structure |
| **0e (Multi-Industry)** | Verification harness should test HTML responses via Accept header, not just JSON |

---

## Key Files When Ready to Implement

| File | Relevance |
|------|-----------|
| `SpringBootEmitter.java` | Will gain `emitContentTransform()`, `emitHtmlMessageConverter()` methods |
| `ServerEmitter.java` | Interface gains content negotiation methods |
| `generated-html/` | Reference output — runtime HTML should match this structure |
| `HtmlCrudGenerator.java` | Reference for field→input mapping, page layout, nav structure |
| `tests/http-tests.yaml` | Add tests with `Accept: text/html` header expectations |
