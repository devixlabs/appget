# Research: Content Negotiation Survey for Code Generation

> **Date**: 2026-04-02
> **Author**: Claude (agent research per SPEC-content-negotiation.md)
> **Status**: Complete -- feeds into SPEC-content-negotiation.md decision gate

---

## 1. OpenAPI CRUD Generators

### OpenAPI Generator (openapi-generator.tech)

**Content negotiation status: Partial, with known gaps.**

- The Spring generator has a `singleContentTypes` option (default: `false`) that controls whether generated controllers preserve multiple `produces`/`consumes` media types per operation. When false, the `@RequestMapping` annotation can include `produces = {"application/json", "application/xml"}`.
- The `withXml` option (default: `false`) adds `application/xml` support and XML annotations to models.
- **However**, the generator uses a single-method-per-operation approach. It does NOT generate separate handler methods for each content type. Multiple content types in the spec result in a single method with multiple media types in the `produces` attribute, relying on Spring's built-in content negotiation to select the format at runtime.
- Known bug reports: request bodies with multiple content types only generate one method ([#2932](https://github.com/OpenAPITools/openapi-generator/issues/2932), [#17877](https://github.com/OpenAPITools/openapi-generator/issues/17877)). Multiple response types have the same limitation ([#1096](https://github.com/OpenAPITools/openapi-generator/issues/1096)).
- **HTML is not a supported response type.** The generator handles JSON and XML serialization (via Jackson annotations), but has no concept of rendering HTML from the same endpoint.

**Code generation relevance**: The `produces` annotation approach is directly usable -- appget could generate `@RequestMapping(produces = {"application/json", "text/html", "application/xml"})` and let the framework handle routing. But the actual transform logic (JSON-to-HTML) is not something OpenAPI Generator addresses at all.

### Swagger Codegen

- Fork ancestor of OpenAPI Generator; same architectural approach.
- Template-driven (Handlebars/Mustache) code generation from OpenAPI specs.
- Same limitation: generates stubs with `produces`/`consumes` annotations but no runtime content negotiation logic beyond what the framework provides natively.
- No HTML rendering capability.

### API Platform (PHP/Symfony)

**The most complete content negotiation implementation found in any code generation framework.**

- Natively supports JSON-LD, JSON:API, HAL, GraphQL, YAML, CSV, HTML, XML, and plain JSON from the same endpoints.
- Format selection via Accept header or URL extension (e.g., `/api/books.jsonld`).
- Configuration in `api_platform.yaml` maps MIME types to formats.
- First configured format is the default when no Accept header is present.
- **However**, this is PHP/Symfony-specific and not portable to other languages.

**Code generation relevance**: API Platform proves the pattern works at scale. Its architecture (registered serializers per media type, framework-level routing) maps directly to the `ContentTransform` interface pattern in the spec. But the implementation is not reusable.

### Other Generators

- **openapi-processor** (Java): Documents the multi-content-type challenge explicitly. Notes that mapping multiple content types to separate Java methods is the "correct" approach but most generators don't do it.
- **Goa Design** (Go): Code generation framework with content negotiation support, but 404 on current docs -- may be unmaintained.
- **Speakeasy**: Commercial OpenAPI SDK generator. Focuses on client SDK generation, not server content negotiation.

### Section Verdict

No existing OpenAPI-based code generator handles JSON+HTML+XML content negotiation out of the box. They all handle the annotation/declaration side (`produces`) but leave the actual format transformation to the developer. API Platform is the exception but is locked to PHP. **This confirms that appget needs to generate the transform logic itself.**

---

## 2. htmx and Hypermedia API Patterns

### htmx

**Key mechanism: `HX-Request: true` header, NOT Accept header.**

- htmx sends `HX-Request: true` on every AJAX request. This is the primary way servers distinguish htmx requests from normal browser navigation.
- For the Accept header, htmx sends `text/html, */*` -- it expects HTML fragments, not JSON.
- htmx does NOT use content negotiation in the traditional sense. It always expects HTML back.

**Carson Gross (htmx creator) explicitly argues against content negotiation from the same endpoint:**
- JSON APIs need stable, versioned endpoints.
- Hypermedia APIs should evolve freely based on UI needs.
- These goals conflict -- serving both from the same endpoint creates maintenance pressure.
- His recommendation: separate paths (`/api/v1/contacts` for JSON, `/contacts` for HTML).

Source: [Why I Tend Not To Use Content Negotiation](https://htmx.org/essays/why-tend-not-to-use-content-negotiation/)

**Code generation relevance**: The `HX-Request` header pattern is simpler than Accept-header parsing and could be used as an ADDITIONAL signal (not a replacement). However, htmx's philosophy of separate API paths conflicts with appget's single-endpoint content negotiation approach. The htmx argument is worth noting but does not change the spec -- appget's use case is different (generated servers where both formats are equally "stable" because they come from the same schema).

### Turbo / Hotwire

**Key mechanism: Custom MIME type `text/vnd.turbo-stream.html` in Accept header.**

- Turbo injects `text/vnd.turbo-stream.html` into the Accept header on form submissions (POST/PUT/PATCH/DELETE).
- Does NOT add this header on GET requests by default (requires `data-turbo-stream` attribute).
- Rails `respond_to` block handles format selection:
  ```ruby
  respond_to do |format|
    format.turbo_stream { render turbo_stream: ... }
    format.html { redirect_to ... }
  end
  ```
- Turbo Streams are HTML fragments with `<turbo-stream>` wrapper elements -- not raw HTML.

**Code generation relevance**: The custom MIME type approach is interesting but Turbo-specific. The `respond_to` block pattern in Rails is the closest to a portable content negotiation pattern -- it maps directly to a switch statement on accepted media type, which is exactly what appget would generate.

### Unpoly

**Key mechanism: `X-Up-Version` header (detects Unpoly) + `X-Up-Target` header (CSS selector for target fragment).**

- Unpoly sends `X-Up-Version` on every request (equivalent to htmx's `HX-Request`).
- `X-Up-Target` tells the server which CSS selector is being updated, allowing the server to optionally render only that fragment.
- Server protocol is explicitly documented as **entirely optional** -- server can always return full HTML pages and Unpoly will extract the relevant fragment client-side.
- No custom Accept header manipulation. Unpoly works with standard HTML responses.

**Code generation relevance**: Unpoly's approach requires NO server-side content negotiation at all. The server always returns HTML; Unpoly handles fragment extraction client-side. This is the simplest server-side pattern but only works for HTML (not JSON or XML).

### Intercooler.js

- Predecessor to htmx. Sends `X-IC-Request: true` header.
- Same pattern as htmx: server checks for the header, returns HTML fragment vs. full page.
- Effectively deprecated in favor of htmx.

### Summary Table: Hypermedia Libraries

| Library | Detection Header | Accept Header Behavior | Server Must Return |
|---------|-----------------|----------------------|-------------------|
| htmx | `HX-Request: true` | `text/html, */*` | HTML fragment |
| Turbo | (none specific) | Injects `text/vnd.turbo-stream.html` on POST | Turbo Stream HTML or redirect |
| Unpoly | `X-Up-Version` | Standard browser Accept | Full HTML (client extracts fragment) |
| Intercooler | `X-IC-Request: true` | Standard | HTML fragment |

### Section Verdict

All four hypermedia libraries expect HTML responses, not JSON. None of them implement true multi-format content negotiation. The `HX-Request`-style header pattern (a boolean flag to distinguish AJAX from full-page) is a useful supplementary signal but does not replace Accept-header-based format selection. The htmx creator's argument against shared endpoints is noted but does not apply to appget's use case where both formats are generated from the same schema and equally stable.

---

## 3. Multi-Format Response Patterns

### Established Pattern Names

- **Content negotiation** (conneg): The HTTP-standard term. RFC 7231 Section 5.3.
- **Server-driven negotiation**: Server selects format based on request headers (Accept, Accept-Language, Accept-Encoding).
- **Agent-driven negotiation**: Server returns a list of available representations; client chooses. Rarely used in practice.
- **Reactive negotiation**: Synonym for agent-driven.
- **Multi-representation endpoints**: Informal term for endpoints that serve multiple formats.

### Three Approaches Compared

#### A. Accept Header (HTTP Standard)

**How it works**: Client sends `Accept: application/json` or `Accept: text/html`. Server parses the header, selects the best matching format, sets `Content-Type` on the response.

**Pros**:
- HTTP standard (RFC 7231)
- Clean URLs (no format info in the URL)
- Works with any HTTP client
- Quality factors (`q=`) allow preference ordering

**Cons**:
- Browser default Accept headers are messy and inconsistent (see below)
- Requires `Vary: Accept` response header for correct caching
- `Vary: Accept` effectively disables shared caching in many CDNs and browsers
- Debugging is harder (can't see format in URL/logs)
- Some client libraries don't set Accept properly

**Browser default Accept headers (navigation requests)**:
- Firefox 132+: `text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8`
- Chrome 131+: `text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7`
- Safari 18+: `text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8`

**Critical gotcha**: All browsers include `*/*` with a quality factor, which means a naive implementation that just checks "does the Accept header contain application/json" will match browser navigation requests. Proper quality-factor parsing is essential.

#### B. Query Parameter (`?format=json`)

**How it works**: Format specified as a URL query parameter. `GET /users?format=html` vs `GET /users?format=json`.

**Pros**:
- Simple to implement
- Visible in URLs and logs
- Easy to test in browser address bar
- No caching issues (different URLs = different cache entries)
- Works with any client, no header manipulation needed

**Cons**:
- Not HTTP-standard for this purpose
- Pollutes the URL with rendering concerns
- Can conflict with other query parameters
- Less RESTful (format is not a resource property)

**Framework support**:
- Spring Boot: `ContentNegotiationConfigurer.favorParameter(true).parameterName("format")`
- Django REST Framework: `?format=json` supported via `format_suffix_patterns`
- Sinatra: `sinatra-respond_to` gem supports this

#### C. URL Extension (`.json`, `.html`)

**How it works**: Format appended as a file extension. `GET /users.json` vs `GET /users.html`.

**Pros**:
- Extremely clear and debuggable
- Different cache entries naturally
- Works in browser address bar
- Used by Twitter API, GitHub API (historically), Rails conventions

**Cons**:
- Security risk: Reflected File Download (RFD) attacks. Spring Framework documentation explicitly warns against this and recommends query parameters over extensions.
- Complicates routing (need to strip extension before matching route)
- Conflicts with actual file extensions in resource paths
- Less RESTful

**Framework support**:
- Spring Boot: `ContentNegotiationConfigurer.favorPathExtension(true)` (deprecated, security concern)
- Django REST Framework: `format_suffix_patterns` in URL configuration
- Sinatra: `sinatra-respond_to` gem supports `.json`/`.html` extensions
- Rails: Built-in via `respond_to` blocks and route extensions

### Framework-Specific Content Negotiation Support

#### Spring Boot (Java)

**Built-in support: Excellent.**

- `ContentNegotiationConfigurer` configures strategy globally.
- Supports Accept header (default), query parameter, and URL extension (deprecated).
- `@RequestMapping(produces = {"application/json", "text/html"})` declares supported types.
- `HttpMessageConverter` interface is the extension point -- register a converter for `text/html` and Spring handles routing automatically.
- `ContentNegotiatingViewResolver` resolves views based on Accept header.
- Controller code does NOT change -- format selection is infrastructure.

**Appget implication**: Generate a `HtmlHttpMessageConverter implements HttpMessageConverter<Object>` that wraps the `ContentTransform` interface. Register it in a `@Configuration` class. Controllers remain format-unaware. This is the cleanest approach for Java.

#### Django REST Framework (Python)

**Built-in support: Excellent.**

- Renderer classes: `JSONRenderer`, `BrowsableAPIRenderer`, `TemplateHTMLRenderer`, `StaticHTMLRenderer`.
- `@renderer_classes([JSONRenderer, TemplateHTMLRenderer])` on a view enables both formats.
- `DefaultContentNegotiation` class selects renderer based on Accept header.
- Renderer ordering determines default (first renderer wins when `Accept: */*`).
- **Note**: DRF intentionally ignores `q` quality values in Accept headers.
- `format_suffix_patterns` adds URL extension support (`/users.json`, `/users.api`).
- `request.accepted_renderer` available in view code for conditional logic.

**Appget implication**: Generate custom renderer classes. DRF's architecture maps perfectly to `ContentTransform` -- each renderer IS a transform.

#### Gin (Go)

**Built-in support: Good.**

- `c.Negotiate(200, gin.Negotiate{Offered: []string{...}, Data: data})` handles format selection.
- `c.NegotiateFormat(gin.MIMEHTML, gin.MIMEJSON)` returns the best matching format.
- Built-in MIME constants: `MIMEJSON`, `MIMEHTML`, `MIMEXML`, `MIMEXML2`, `MIMEPlain`, `MIMEYAML`, `MIMETOML`.
- Switch on `NegotiateFormat()` result to render different responses.
- Can also be implemented as middleware.

**Appget implication**: Generate middleware that calls `NegotiateFormat()` and delegates to `ContentTransform`. Alternatively, generate a switch block in each handler (but middleware is cleaner).

#### Sinatra (Ruby)

**Built-in support: Good (via sinatra-contrib).**

- `respond_to` block from `sinatra-respond_to` gem:
  ```ruby
  respond_to do |wants|
    wants.html { haml :posts }
    wants.json { posts.to_json }
    wants.xml  { posts.to_xml }
  end
  ```
- Supports Accept header parsing, URL extensions (`.json`), and `default_content` configuration.
- `request.accept?('text/html')` for manual checking.

**Appget implication**: Generate `respond_to` blocks in route handlers. The gem handles Accept header parsing. Transforms plug into the format-specific blocks.

### Middleware / Library Approaches

Several framework-agnostic content negotiation libraries exist:

| Library | Language | Approach | Maintained |
|---------|----------|----------|------------|
| `willdurand/Negotiation` | PHP | Standalone Accept parser, used by Symfony & Slim | Yes |
| `rack-conneg` | Ruby | Rack middleware, injects `negotiated_type` into request | Minimal activity |
| `@middy/http-content-negotiation` | Node.js | AWS Lambda middleware for Accept parsing | Yes |
| `content-negotiation-go` | Go | Standalone library for parsing Accept headers | Yes |
| `ptlis/conneg` | PHP | PSR-7 middleware for full conneg | Yes |
| `django-conneg` | Python | Django extension for content negotiation | Minimal activity |

**Key insight**: These libraries handle Accept header PARSING (determining which format the client wants), NOT the actual format TRANSFORMATION (converting data to that format). The parsing is the easy part. The transform is where the work is.

### Caching Gotchas

**The `Vary: Accept` problem is the biggest operational gotcha with Accept-header content negotiation.**

- When a server returns different content based on the Accept header, it MUST include `Vary: Accept` in the response.
- This tells caches (CDNs, proxies, browsers) to store separate cached copies per Accept value.
- **CDN impact**: Most CDNs respect `Vary: Accept` and will cache separate versions. This multiplies cache storage and reduces hit rates.
- **Browser impact**: Browsers historically handle `Vary` poorly. Chrome treats `Vary` as a cache validator (not a key), meaning it may serve stale cached responses of the wrong format.
- **Mitigation**: For appget's use case (development/internal tools, not high-traffic CDN-served APIs), this is unlikely to matter. But it should be documented.

---

## 4. Recommendations for appget

### Primary Approach: Accept Header (with fallback)

Use the Accept header as the primary mechanism, matching the HTTP standard. Add query parameter (`?format=html`) as a fallback for easy browser testing and debugging.

**Rationale**:
- Accept header is the HTTP standard and what every framework supports natively.
- Query parameter fallback costs almost nothing to implement and solves the browser-testing problem.
- URL extensions are not recommended due to security concerns (RFD) and routing complexity.

### Accept Header Parsing Strategy

Do NOT hand-parse the Accept header. Each target framework has built-in parsing:
- **Spring Boot**: `ContentNegotiationConfigurer` + `HttpMessageConverter` (zero custom parsing)
- **Django REST Framework**: Renderer classes (zero custom parsing)
- **Gin**: `c.NegotiateFormat()` (zero custom parsing)
- **Sinatra**: `sinatra-respond_to` gem or `request.accept?()` (zero custom parsing)

The generated code should use the framework's native content negotiation mechanism, not reimplement Accept header parsing.

### Transform Architecture Confirmed

No existing tool generates JSON-to-HTML transforms. The `ContentTransform` interface in the spec is the right approach. The wiring layer should use each framework's native content negotiation mechanism (not custom middleware).

### What to Generate Per Framework

| Framework | Wiring Approach | Transform Integration |
|-----------|----------------|----------------------|
| Spring Boot | `HttpMessageConverter<Object>` for `text/html` | Converter delegates to `ContentTransform` |
| DRF (Python) | Custom `Renderer` class | Renderer delegates to `ContentTransform` |
| Gin (Go) | Middleware with `NegotiateFormat()` | Middleware delegates to `ContentTransform` |
| Sinatra (Ruby) | `respond_to` block in routes | Block delegates to `ContentTransform` |

### Decision Gate Answer

**Option 3: Hybrid.**
- Use each framework's built-in content negotiation for the WIRING (Accept header parsing, format routing).
- Generate the TRANSFORM logic (JSON-to-HTML) since no existing library does this.
- The `ContentTransform` interface remains the abstraction boundary.

---

## Sources

### OpenAPI and Code Generators
- [OpenAPI Generator Spring Documentation](https://openapi-generator.tech/docs/generators/spring/)
- [API Platform Content Negotiation](https://api-platform.com/docs/core/content-negotiation/)
- [OpenAPI Specification Issue #146 - Multiple request/response models](https://github.com/OAI/OpenAPI-Specification/issues/146)
- [OpenAPI Generator Issue #2932 - Multiple media types not supported](https://github.com/OpenAPITools/openapi-generator/issues/2932)
- [OpenAPI Generator Issue #1096 - Can't handle multiple responses](https://github.com/OpenAPITools/openapi-generator/issues/1096)
- [Bump.sh - Multiple Content Types with OpenAPI](https://docs.bump.sh/guides/openapi/specification/v3.1/advanced/multiple-content-types/)

### htmx and Hypermedia
- [htmx - Why I Tend Not To Use Content Negotiation](https://htmx.org/essays/why-tend-not-to-use-content-negotiation/)
- [htmx Issue #1229 - AJAX Accept header](https://github.com/bigskysoftware/htmx/issues/1229)
- [Turbo Streams Handbook](https://turbo.hotwired.dev/handbook/streams)
- [Unpoly Server Protocol](https://unpoly.com/up.protocol)
- [Unpoly X-Up-Target](https://unpoly.com/X-Up-Target)

### Framework Content Negotiation
- [Spring Framework Content Types](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-config/content-negotiation.html)
- [Django REST Framework Content Negotiation](https://www.django-rest-framework.org/api-guide/content-negotiation/)
- [Django REST Framework Renderers](https://www.django-rest-framework.org/api-guide/renderers/)
- [Gin Content Negotiation Issue #59](https://github.com/gin-gonic/gin/issues/59)
- [Sinatra respond_to](https://github.com/cehoffman/sinatra-respond_to)

### Spring Boot Content Negotiation (Jeff's research)
- [Multi-Format API Output in Spring Boot](https://alexanderobregon.substack.com/p/multi-format-api-output-in-spring) — ContentNegotiationConfigurer, custom HttpMessageConverter for CSV, `?format=` param
- [Return XML or JSON in Spring MVC](https://www.appsdeveloperblog.com/return-xml-json-spring-mvc/) — `produces` attribute array, jackson-dataformat-xml, Accept header routing

### HTTP Standards and Patterns
- [MDN - Content Negotiation](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Content_negotiation)
- [MDN - Default Accept Header Values](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Content_negotiation/List_of_default_Accept_values)
- [RESTful API - Content Negotiation](https://restfulapi.net/content-negotiation/)
- [Smashing Magazine - Understanding the Vary Header](https://www.smashingmagazine.com/2017/11/understanding-vary-header/)
- [Spring Blog - Content Negotiation Using Spring MVC](https://spring.io/blog/2013/05/11/content-negotiation-using-spring-mvc/)
