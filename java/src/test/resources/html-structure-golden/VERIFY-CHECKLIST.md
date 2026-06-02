# HTML Structure Golden — Correctness Checklist

This checklist is for the human/orchestrator to confirm that the static HTML reference
pages produced by `HtmlCrudGenerator` are actually correct before 0f-core copies the
structural contract into runtime verification.

Run `make generate-html` (or `make all`) to refresh pages, then work through each item.

---

## 1. Page Count

- [ ] Every model has exactly 4 pages: `<resource>/index.html`, `create.html`, `edit.html`, `view.html`
- [ ] Every view (SQL view) has exactly 1 page: `views/<resource>/index.html`
- [ ] Root `index.html` exists at the output root
- [ ] Total HTML file count is **67** (1 root + 14 models × 4 + 10 views × 1)
  - Reference: `HtmlCrudGeneratorTest.testTotalHtmlFileCount`

## 2. Navigation / Form Routes

- [ ] Model `create.html` form `action` = `/<resource>` (e.g., `/roles`, `/users`)
- [ ] Model `edit.html` form `action` = `/<resource>` (same route as create — method override via hidden field)
- [ ] Model `edit.html` includes `<input type="hidden" name="_method" value="PUT">` (routing-structural)
- [ ] Model `edit.html` includes `<input type="hidden" name="id">` (PK scaffold)
- [ ] View `index.html` API reference = `/views/<resource>` (e.g., `/views/user-role`)
  - Reference: `HtmlCrudGeneratorTest.testCreateFormActionMatchesRoute`, `testEditFormHasHiddenMethodPut`, `testViewListApiRoute`

## 3. Field → Input Type Mapping

Verify against `HtmlCrudGenerator.fieldToInput()` logic:

| SQL Type        | Proto Type | Expected Input                          |
|-----------------|------------|-----------------------------------------|
| VARCHAR, CHAR   | string     | `<input type="text" ...>`               |
| TEXT            | string     | `<textarea ...></textarea>`             |
| INT, BIGINT     | int32/int64| `<input type="number" step="1" ...>`    |
| DECIMAL, NUMERIC| decimal    | `<input type="number" step="0.01" ...>` |
| FLOAT, DOUBLE   | float64    | `<input type="number" step="any" ...>`  |
| DATE            | date       | `<input type="date" ...>`               |
| DATETIME, TIMESTAMP | datetime | `<input type="datetime-local" ...>`   |
| BOOLEAN         | bool       | `<input type="checkbox" ...>`           |

- [ ] `roles/create.html`: `permission_level` (INT) → `type="number" step="1"`
- [ ] `users/create.html`: `username` (VARCHAR) → `type="text"`
- [ ] `users/create.html`: `bio` (TEXT) → `<textarea>`
- [ ] `users/create.html`: `follower_count` (INT) → `type="number" step="1"`
- [ ] `users/create.html`: `is_verified` (BOOLEAN) → `type="checkbox"` (no `required`)
- [ ] `posts/create.html`: `is_public` (BOOLEAN) → `type="checkbox"` (no `required`)
- [ ] Check any DECIMAL field if present in schema → `step="0.01"`
  - Reference: `HtmlCrudGeneratorTest.testIntFieldRendersAsNumberStepOne`, `testBooleanFieldRendersAsCheckbox`, `testTextFieldRendersAsTextarea`

## 4. Validation / `required` Attribute

- [ ] NOT NULL non-boolean fields carry `required` attribute
- [ ] Nullable fields do NOT carry `required`
- [ ] BOOLEAN/checkbox fields NEVER carry `required` (unchecked = false is a valid value)
  - Reference: `HtmlCrudGeneratorTest.testNotNullFieldHasRequiredAttribute`, `testNullableFieldHasNoRequiredAttribute`, `testCheckboxInputsNeverHaveRequired`

## 5. Primary Key Handling

- [ ] `create.html` for every model omits the PK (`id`) field entirely (no `name="id"` input)
- [ ] `edit.html` for every model renders PK as `<input type="hidden" name="id" id="id">`
  - Reference: `HtmlCrudGeneratorTest.testIdFieldOmittedOnCreate`, `testIdFieldHiddenOnEdit`

## 6. Business Rules Block

- [ ] `create.html` and `edit.html` include a `<details>` block when the model has rules in `specs.yaml`
- [ ] `create.html` and `edit.html` have NO `<details>` block when the model has no rules
- [ ] Blocking rules are prefixed with `[BLOCKING] ` in the list
- [ ] Non-blocking rules are listed without any prefix
- [ ] `index.html` and `view.html` never show a rules block (read-only pages)
  - Reference: `HtmlCrudGeneratorTest.testRulesBlockPresentWhenRulesExist`, `testBlockingRuleHasPrefix`, `testNonBlockingRuleHasNoPrefix`

## 7. View Pages

- [ ] View `index.html` pages have only a table (no create/edit/delete forms)
- [ ] View navigation links back to `../../index.html` (two levels up from `views/<resource>/`)
- [ ] No `create.html`, `edit.html`, or `view.html` exists under `views/`
  - Reference: `HtmlCrudGeneratorTest.testViewHasOnlyIndexPage`

---

---

## TODO_5 / Phase-0f Live Verification (5.6 decision)

**Approach chosen: (b) — strong body-shape assertions in http-tests.yaml (not a Java live test).**

Rationale: Option (a) — a tagged Java test using `java.net.http.HttpClient` — requires
wiring a `@Tag("live")` exclusion in `build.gradle`'s `test` task, adding a separate
`gradlew test -PincludeTags=live` call in `scripts/verify.sh`, and cannot be run or
validated without a live server. That is invasive for marginal benefit given that:

1. The emitter-output tests (PageRendererEmitListDetailTest, HtmlTemplateEmissionTest)
   already assert the generated HTML structure against the goldens at unit-test time.
2. The body-shape assertions in http-tests.yaml (tests 5.2b–5.2g) confirm that the
   runtime routes respond with HTML containing the required structural markers (`<table>`,
   `<dl>`, `<form>`, etc.) and absence of JSON content.
3. `HtmlStructuralNormalizer.normalizeRuntime` is already covered by
   HtmlStructuralNormalizerRuntimeTest (unit test).

Full normalize-diff parity (runtime HTML ↔ golden .structure.txt) is deferred and
tracked in docs/todos/. When implemented, the recommended approach is the tagged Java
test (option a) with `@Tag("live")` and a build.gradle exclusion in the default `test`
task, invoked by a dedicated Gradle task in `scripts/verify.sh`.

---

## Notes for 0f-core

- The `step` attribute is mandatory in golden snapshots: it is the discriminator between
  INT (`step="1"`), DECIMAL (`step="0.01"`), and FLOAT (`step="any"`). A renderer
  emitting the wrong numeric type would pass a diff that strips `step`.
- The `value=PUT` on the hidden `_method` input is structural routing — kept in goldens.
  When 0f-core diffs **runtime** HTML (which carries live data in `value=` for data inputs),
  it should strip `value` for data-bearing inputs but retain it for `name="_method"`.
  See `HtmlStructuralNormalizer` class Javadoc for the full nuance.
