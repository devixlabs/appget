# TODO: Enhance `make run` Rule Engine Demo

> **For agentic workers:** Explore the current implementation, reason about the gap, and write an implementation plan before coding.

**Goal:** Make `make run` a meaningful diagnostic tool that exercises the full rule engine — including metadata-aware authorization rules — using auto-generated demo data.

**Current state:** `RuleEngine.main()` loads all rules from `specs.yaml`, builds sample protobuf messages via `DefaultDataBuilder`, and evaluates every rule. However, `buildDemoMetadata()` returns an empty `MetadataContext()`, so all rules with `requires:` blocks (metadata authorization) silently fail without exercising the auth path.

**What the server does differently:** `make run-server` generates a Spring Boot app where `MetadataExtractor` reads typed HTTP headers (`X-Sso-Authenticated`, `X-Roles-Role-Level`, etc.) into context POJOs. The CLI demo has no equivalent.

---

## Phase 1: Explore and Understand

- [ ] Read `RuleEngine.java` — understand the full `main()` flow, `loadRulesFromSpecs()`, `buildSpec()`, and `buildDemoMetadata()`
- [ ] Read `DefaultDataBuilder.java` — understand how sample DynamicMessages are built per descriptor
- [ ] Read `MetadataContext.java` — understand `with(category, pojo)` API and how rules evaluate metadata
- [ ] Read `specs.yaml` (generate first with `make features-to-specs`) — identify which rules have `requires:` blocks and what metadata categories/fields they reference
- [ ] Read the generated context POJOs (`SsoContext`, `RolesContext`, `UserContext`, `OauthContext`, `ApiContext`) — understand builder pattern and field types
- [ ] Read `metadata.yaml` — understand the 5 enabled categories and their fields
- [ ] Check `Specification.java` `getFieldValueViaReflection()` — confirm how metadata POJO fields are accessed (snake_case → snakeToCamel → getter)

## Phase 2: Reason About the Design

Key questions to answer before coding:

1. **What should demo metadata look like?** Build a "happy path" MetadataContext that satisfies all `requires:` blocks so the demo shows rules actually passing their auth gates. Consider also showing a "denied" path.
2. **Should the demo show metadata-pass vs metadata-fail side by side?** Running each rule twice (once with good metadata, once without) would demonstrate the auth gate clearly.
3. **Should `buildDemoMetadata()` read from `specs.yaml`?** It could auto-discover which categories are referenced in `requires:` blocks and build matching POJOs dynamically. This would stay in sync as rules change. Or it could use hardcoded "sensible defaults" (simpler but fragile).
4. **Output formatting** — The current printf is minimal. Consider a summary table showing: rule name, target model, has-metadata-requirement (Y/N), metadata-satisfied (Y/N), main-condition result, final status.
5. **Blocking indicator** — The demo doesn't distinguish blocking vs informational rules. The `blocking` flag is in `specs.yaml` but not loaded into `Rule.java` by `loadRulesFromSpecs()`. Should it be?

## Phase 3: Implementation Plan (write before coding)

Based on exploration, write a concrete implementation plan covering:

- [ ] Changes to `RuleEngine.java`:
  - `buildDemoMetadata()` — populate with realistic values from `metadata.yaml` categories
  - `loadRulesFromSpecs()` — consider loading `blocking` flag and `requires` metadata into `Rule` objects
  - Output formatting — clearer table with metadata/blocking indicators
- [ ] Changes to `Rule.java` (if needed):
  - Add `blocking` field if not already present
  - Add metadata requirement tracking for display purposes
- [ ] Test coverage:
  - Existing `RuleTest` suite covers `evaluate(target, metadata)` — verify no regressions
  - Consider adding a test for `loadRulesFromSpecs()` with metadata requirements
- [ ] TDD: Write failing tests first for any new behavior

## Constraints

- Do NOT change the generated server (`AppServerGenerator`, `generated-server/`)
- Do NOT change `specs.yaml` format or `FeatureToSpecsConverter` output
- Keep `make run` as a standalone CLI demo — no Spring dependencies
- Follow existing patterns: snake_case in intermediates, `JavaUtils.snakeToCamel()` at codegen boundary
- All 382+ existing tests must continue to pass

## Files Likely Involved

| File | Role |
|------|------|
| `src/main/java/dev/appget/RuleEngine.java` | Main demo entry point — primary changes here |
| `src/main/java/dev/appget/model/Rule.java` | May need `blocking` field addition |
| `src/main/java/dev/appget/specification/MetadataContext.java` | Used by demo, probably no changes |
| `src/main/java/dev/appget/util/DefaultDataBuilder.java` | Sample data generation, probably no changes |
| `metadata.yaml` | Reference for enabled categories and fields |
| `specs.yaml` | Generated — reference for `requires:` blocks |
| `src/test/java/dev/appget/model/RuleTest.java` | Verify/extend metadata evaluation tests |
