# Contract Implementation Gaps

Tracked gaps in the cross-language contract docs that live in `docs/`. Each gap references the source doc and the fix location.

---

## All Java Gaps Resolved (2026-03-24)

GAP-M1, GAP-M2, GAP-S1, GAP-R1, GAP-R2, GAP-R3, GAP-D1 — all resolved in the Java reference implementation.

---

## Remaining

### GAP-P1: Non-Java language package options are aspirational
- **Spec**: Defines Go, Python, Ruby, Node package conventions
- **Current**: Only Java `java_package` options are generated
- **Status**: Blocked on Phase 5 (language implementations). No action needed until then.

### GAP-0F1: Create-form error re-render does not prefill submitted values — **RESOLVED** (2026-06-11)
- `HtmlCrudGenerator.generateCreateTemplate()` now uses a `{{CONTENT}}` slot inside `<form>` (no hidden fields). `renderCreateForm()` delegates to `renderCreateFormWithErrors(Map.of(), List.of())`. `renderCreateFormWithErrors()` fills the slot with optional error list + prefilled inputs from the submitted map via `form.getOrDefault(...)`, skipping the PK field. Verified: `make all` green (518 tests, 0 failures) + `make verify` green.

### GAP-0F2: HTML form DELETE returns 204, not a 303 redirect — **RESOLVED** (2026-06-11)
- `SpringBootEmitter.emitController()` now emits a separate `deleteForm` handler with `consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE` that returns `ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(resourcePath)).build()` (303 to list). JSON `@DeleteMapping` still returns 204. Verified: http-tests 5.3e (POST+`_method=DELETE` → 303) and 5.3f (GET after delete → 404) both green.

### GAP-0F3: Live HTML navigation links file-relative — **RESOLVED** (2026-06-01)
- Renderers/templates now emit server-route hrefs (Home `/`, Create `/{resource}?action=create`, View `/{resource}/{id}`, Back `/{resource}`); root index links `/{resource}` and `/views/{resource}`. New generated `RootController` serves `GET /` (text/html). Static pages + templates + 6 goldens regenerated together (chosen resolution: option 1 — absolute hrefs everywhere; `file://` preview of `generated-html/` now has absolute links, accepted).
- **Verified**: `make verify` green (testLive 5/5 vs new goldens) + Playwright walk (Home/Create/View/Back/root all resolve, no 404/500).
- **Edit-link follow-up — RESOLVED (2026-06-11)**: `HtmlCrudGenerator.generateDetailTemplate()` has `{{EDIT_LINK}}` placeholder after `</dl>`; `generateDetailHtml()` emits literal `/{resource}/{id}?action=edit`. `renderDetail()` fills `{{EDIT_LINK}}` with live-escaped PK. `HtmlStructuralNormalizer.normalizeRuntime()` normalizes live id → `{id}` in edit hrefs. Golden `users-view.structure.txt` updated. Verified: `make verify` golden diff green (5/5).

### GAP-0F4: `make all` did not compile `generated-server/` — **RESOLVED** (2026-06-01)
- Root cause: nothing in `make all` compiled the generated server (only `bootRun` did), so emitter-output bugs that produce uncompilable Java passed green builds + the substring-only `*EmitTest`s. A GAP-0F3 escaping bug (`href=\"/users/\")`) shipped this way and only surfaced at `make run-server`.
- Fix: added private `_compile-server` target (`cd generated-server && ../gradlew compileJava`, javac-only / server-free) to `make all` after `generate`. Now every emitter-output error fails the build. Also added a CLAUDE.md guard (java/) reminding to compile the generated server after any `emit*` change.

---

## Summary

| ID | Doc | Status |
|----|-----|--------|
| GAP-P1 | PROTO_CONVENTIONS | Blocked on Phase 5 |
| GAP-0F1 | SPEC-content-negotiation | **Resolved (2026-06-11)** — create-form prefill on 422 |
| GAP-0F2 | SPEC-content-negotiation | **Resolved (2026-06-11)** — HTML form DELETE → 303 PRG |
| GAP-0F3 | SPEC-content-negotiation | **Resolved (2026-06-01 + 2026-06-11)** — server-route hrefs + RootController + detail edit link |
| GAP-0F4 | build process | **Resolved (2026-06-01)** — `_compile-server` gate added to `make all` |
