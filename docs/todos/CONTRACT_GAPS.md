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

### GAP-0F1: Create-form error re-render does not prefill submitted values
- **Spec**: [SPEC-content-negotiation.md](../SPEC-content-negotiation.md) — "Re-render form with inline error messages on blocking rule violation"
- **Current**: On a create-form 422, `renderCreateFormWithErrors` injects the error list but NOT the user's submitted values (the create template is fully static, no `{{CONTENT}}` slot). The user loses their input on a blocked create. Edit-form re-render prefills correctly (it has a slot).
- **Fix location**: `HtmlCrudGenerator.generateCreateTemplate` (add a `{{CONTENT}}` slot) + `SpringBootEmitter.emitPageRenderer` (`renderCreateFormWithErrors` fills it from the submitted map) + a new create golden.
- **Effort**: Small-Medium. MVP-acceptable (errors are shown; only prefill is missing).

### GAP-0F2: HTML form DELETE returns 204, not a 303 redirect
- **Spec**: [SPEC-content-negotiation.md](../SPEC-content-negotiation.md) — `DELETE` (HTML) → "Redirect to list (303)"
- **Current**: HTML form delete reuses the JSON `@DeleteMapping` (via `_method=DELETE` + HiddenHttpMethodFilter) → returns 204 No Content, so a browser shows a blank page instead of redirecting to the list.
- **Fix location**: `SpringBootEmitter.emitController` — add a form-delete handler returning 303 to the list (PRG).
- **Effort**: Small. Low priority (browser delete is rare without JS).

### GAP-0F3: Live HTML navigation links file-relative — **RESOLVED** (2026-06-01)
- Renderers/templates now emit server-route hrefs (Home `/`, Create `/{resource}?action=create`, View `/{resource}/{id}`, Back `/{resource}`); root index links `/{resource}` and `/views/{resource}`. New generated `RootController` serves `GET /` (text/html). Static pages + templates + 6 goldens regenerated together (chosen resolution: option 1 — absolute hrefs everywhere; `file://` preview of `generated-html/` now has absolute links, accepted).
- **Verified**: `make verify` green (testLive 5/5 vs new goldens) + Playwright walk (Home/Create/View/Back/root all resolve, no 404/500).
- **Remaining (separate, narrow)**: detail page still has no Edit (`?action=edit`) link — needs id-in-static-href handling. Tracked as a follow-up; low priority for MVP.

### GAP-0F4: `make all` did not compile `generated-server/` — **RESOLVED** (2026-06-01)
- Root cause: nothing in `make all` compiled the generated server (only `bootRun` did), so emitter-output bugs that produce uncompilable Java passed green builds + the substring-only `*EmitTest`s. A GAP-0F3 escaping bug (`href=\"/users/\")`) shipped this way and only surfaced at `make run-server`.
- Fix: added private `_compile-server` target (`cd generated-server && ../gradlew compileJava`, javac-only / server-free) to `make all` after `generate`. Now every emitter-output error fails the build. Also added a CLAUDE.md guard (java/) reminding to compile the generated server after any `emit*` change.

---

## Summary

| ID | Doc | Status |
|----|-----|--------|
| GAP-P1 | PROTO_CONVENTIONS | Blocked on Phase 5 |
| GAP-0F1 | SPEC-content-negotiation | Deferred (MVP-acceptable) — create-form prefill |
| GAP-0F2 | SPEC-content-negotiation | Deferred (low priority) — HTML delete PRG |
| GAP-0F3 | SPEC-content-negotiation | **Resolved (2026-06-01)** — server-route hrefs + RootController; verified live. Edit-link follow-up remains |
| GAP-0F4 | build process | **Resolved (2026-06-01)** — `_compile-server` gate added to `make all` |
