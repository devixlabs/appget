# TODO: Extract NamingConvention Interface from Specification.java

> **For agentic workers:** Read all files referenced in this spec, understand the current state, then follow the phases in order. TDD applies — write failing tests before implementation.

**Goal:** Eliminate the duplicated `snakeToCamel` logic between `JavaUtils.java` and `Specification.java` by introducing a `NamingConvention` interface that each language subproject replicates as its standard pattern for field-name resolution at runtime.

**Problem:** `Specification.java` (in `dev.appget.specification`) has a private `snakeToCamel` method that duplicates `JavaUtils.snakeToCamel()` (in `dev.appget.codegen`). This duplication exists because the generated server's `build.gradle` excludes `**/codegen/**` from its classpath, so `Specification.java` cannot import `JavaUtils`. The classpath restriction is correct (generators don't belong in the server JAR), but the naming methods aren't generators — they're runtime utilities miscategorized in the codegen package.

**Why this matters beyond Java:** Java is the reference implementation. When future language subprojects (Go, Python, Rust) implement their own `Specification.*`, they should follow the same structural pattern — an interface/trait/protocol defining the naming contract, with a language-specific implementation. Without this, each language will solve the same problem differently for no reason. Even languages where the conversion is identity (Python, Rust) implement the interface — the structure is what's consistent.

---

## Current State

### Where naming logic lives today

| Location | Method | Used by | Problem |
|----------|--------|---------|---------|
| `dev.appget.codegen.JavaUtils` | `snakeToCamel()` | Generators (`SpecificationGenerator`, `AppServerGenerator`) | Correct — but not accessible at runtime |
| `dev.appget.codegen.JavaUtils` | `snakeToPascal()` | Generators | Same |
| `dev.appget.codegen.JavaUtils` | `snakeToHeaderCase()` | Generators | Same |
| `dev.appget.codegen.JavaUtils` | `JAVA_TO_PROTO_TYPE` | Generators only | Truly codegen-only — stays in codegen |
| `dev.appget.specification.Specification` | `snakeToCamel()` (private) | `getFieldValueViaReflection()` | **Duplicate** of JavaUtils version |

### How Specification uses naming today

`Specification.isSatisfiedBy(T target)` has two paths:
1. **Protobuf models** → `isSatisfiedByDescriptor()` → `descriptor.findFieldByName(field)` — proto fields ARE snake_case, so field name passes through unchanged
2. **Lombok metadata POJOs** → `isSatisfiedByReflection()` → `snakeToCamel(field)` → builds getter name (`getRoleLevel`) → invokes via reflection

Only path 2 needs naming conversion. The field name in `specs.yaml` is always snake_case (language-agnostic canonical form).

### Classpath restriction

`AppServerGenerator.java` line 483 generates `excludes = ['**/codegen/**']` in the server's `build.gradle`. This correctly prevents generators from being bundled into the server JAR. The problem is that `JavaUtils` is in `dev.appget.codegen` even though its naming methods are runtime utilities.

---

## Design

### New package: `dev.appget.naming`

```
dev.appget.naming/
├── NamingConvention.java       # Interface — the cross-language contract
└── JavaNaming.java             # Java implementation
```

This package is NOT excluded by the server's `build.gradle` (only `**/codegen/**` is excluded), so `Specification.java` can import it.

### NamingConvention interface (contract declaration)

```java
package dev.appget.naming;

/**
 * Cross-language contract for field-name resolution at runtime.
 *
 * Each language subproject defines this interface (or language equivalent)
 * and a static utility class that implements the logic:
 *   Java:   NamingConvention interface + JavaNaming static class
 *   Go:     NamingConvention interface + go_naming.go package-level function
 *   Python: NamingConvention protocol  + python_naming.py module-level function
 *   Rust:   NamingConvention trait     + rust_naming.rs module-level function
 *
 * Specification.* calls the static utility directly (no instance, no polymorphism).
 * This interface exists to document the contract, not for runtime dispatch.
 */
public interface NamingConvention {
    /**
     * Convert a snake_case field name to the language's field accessor form.
     * Used by Specification to resolve getter/field names via reflection.
     *
     * Java:   "role_level" → "roleLevel"  (camelCase for getter lookup)
     * Go:     "role_level" → "RoleLevel"  (PascalCase for exported fields)
     * Python: "role_level" → "role_level" (identity — getattr uses snake_case)
     * Rust:   "role_level" → "role_level" (identity — struct fields are snake_case)
     */
    String toFieldAccessor(String snakeCaseField);
}
```

**One method.** That's the contract. `Specification` needs exactly one naming operation at runtime: convert a snake_case field name to whatever the language uses for field access. Everything else (`snakeToPascal`, `snakeToHeaderCase`) is codegen-only and stays with the generators.

### JavaNaming static utility class

```java
package dev.appget.naming;

/**
 * Java naming conventions for field resolution.
 * Pure static functions — no instances, no state, thread-safe by design.
 */
public final class JavaNaming {
    private JavaNaming() {}

    public static String toFieldAccessor(String snakeCaseField) {
        // snakeToCamel: "role_level" → "roleLevel"
        if (snakeCaseField == null || snakeCaseField.isEmpty() || !snakeCaseField.contains("_")) {
            return snakeCaseField;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (int i = 0; i < snakeCaseField.length(); i++) {
            char c = snakeCaseField.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
```

**No instances. No singletons. No state.** Pure static functions, thread-safe by design. The `NamingConvention` interface documents the contract; `JavaNaming` is the static implementation that `Specification.java` calls directly.

### Changes to Specification.java

```java
// Before:
private static String snakeToCamel(String str) { ... }  // DELETE this

private <T> Object getFieldValueViaReflection(T target, String fieldName) throws Exception {
    String camelName = snakeToCamel(fieldName);
    ...
}

// After:
import dev.appget.naming.JavaNaming;

private <T> Object getFieldValueViaReflection(T target, String fieldName) throws Exception {
    String accessorName = JavaNaming.toFieldAccessor(fieldName);
    ...
}
```

**Constructor unchanged** — stays `Specification(String field, String operator, Object value)`. No new arguments, no fields. The only change is replacing the private `snakeToCamel` call with a static import.

**Minimal blast radius:** Handlebars templates, RuleEngine, and test files that construct `Specification` objects require zero changes.

### Changes to JavaUtils.java

`snakeToCamel` is removed from `JavaUtils` — callers that need it for codegen use `JavaNaming.toFieldAccessor()` instead.

`snakeToPascal` and `snakeToHeaderCase` stay in `JavaUtils` — they're codegen-only methods not needed at runtime.

`JAVA_TO_PROTO_TYPE` stays in `JavaUtils` — purely codegen.

---

## Phase 1: Explore and Verify Current State

- [ ] Read `Specification.java` — confirm the private `snakeToCamel` and its single call site in `getFieldValueViaReflection`
- [ ] Read `JavaUtils.java` — confirm `snakeToCamel`, `snakeToPascal`, `snakeToHeaderCase`, `JAVA_TO_PROTO_TYPE`
- [ ] Grep for all callers of `JavaUtils.snakeToCamel` in `src/main/java/` — understand which are codegen-time vs runtime
- [ ] Read `AppServerGenerator.java` line ~483 — confirm the `excludes = ['**/codegen/**']` pattern
- [ ] Run `make all` — confirm baseline (all 382+ tests pass)

## Phase 2: TDD — Write Failing Tests

- [ ] Create `src/test/java/dev/appget/naming/JavaNamingTest.java`:
  - `JavaNaming.toFieldAccessor("role_level")` → `"roleLevel"`
  - `JavaNaming.toFieldAccessor("is_admin")` → `"isAdmin"`
  - `JavaNaming.toFieldAccessor("authenticated")` → `"authenticated"` (no underscore, pass-through)
  - `JavaNaming.toFieldAccessor(null)` → `null`
  - `JavaNaming.toFieldAccessor("")` → `""`
  - `JavaNaming.toFieldAccessor("a_b_c")` → `"aBC"`
- [ ] Run `make test` — confirm new tests fail (class doesn't exist yet)

## Phase 3: Implement

- [ ] Create `src/main/java/dev/appget/naming/NamingConvention.java` — the interface (contract documentation)
- [ ] Create `src/main/java/dev/appget/naming/JavaNaming.java` — static utility class
- [ ] Update `Specification.java`:
  - Add `import dev.appget.naming.JavaNaming;`
  - Replace `snakeToCamel(fieldName)` call with `JavaNaming.toFieldAccessor(fieldName)`
  - Delete the private `snakeToCamel` method
  - (Constructor stays unchanged — no new arguments)
- [ ] Update `JavaUtils.java`:
  - Remove `snakeToCamel` method
- [ ] Update all `JavaUtils.snakeToCamel()` callers in generators to use `JavaNaming.toFieldAccessor()`
- [ ] Run `make test` — all tests pass (new + existing)

## Phase 4: Verify Full Pipeline

- [ ] Run `make clean && make all` — full pipeline passes
- [ ] Inspect a generated spec class — confirm `new Specification(...)` unchanged (no constructor change needed)
- [ ] Inspect generated `RolesContext.java` — confirm field names still `roleLevel` (SpecificationGenerator uses JavaNaming)
- [ ] Run `make generate-server` — confirm server generates without errors
- [ ] Verify no import of `dev.appget.codegen` exists in `dev.appget.specification` or `dev.appget.naming`

## Phase 5: Update Documentation

- [ ] Update `java/CLAUDE.md` — add "Naming Convention Pattern" section documenting the interface, its purpose as cross-language reference pattern, and the package structure
- [ ] Update the Language-Specific Utility Pattern section — note that `snakeToCamel` moved from `JavaUtils` to `JavaNaming` and that `JavaUtils` retains only codegen-specific methods (`snakeToPascal`, `snakeToHeaderCase`, `JAVA_TO_PROTO_TYPE`)

---

## Constraints

- Do NOT change `specs.yaml` format — field names remain snake_case
- Do NOT change the protobuf descriptor path in `Specification.java` — only the reflection path is affected
- Do NOT move `snakeToPascal` or `snakeToHeaderCase` — they're codegen-only, correctly placed
- All 382+ existing tests must continue to pass (after updating `Specification` constructor calls)
- The generated server must compile without importing `dev.appget.codegen`

## Files Involved

| File | Change |
|------|--------|
| `src/main/java/dev/appget/naming/NamingConvention.java` | **NEW** — interface (contract documentation) |
| `src/main/java/dev/appget/naming/JavaNaming.java` | **NEW** — static utility class |
| `src/main/java/dev/appget/specification/Specification.java` | Replace private `snakeToCamel` with `JavaNaming.toFieldAccessor()` static call |
| `src/main/java/dev/appget/codegen/JavaUtils.java` | Remove `snakeToCamel` (callers migrated) |
| `src/main/java/dev/appget/codegen/SpecificationGenerator.java` | Update `snakeToCamel` calls to `JavaNaming.toFieldAccessor()` |
| `src/main/java/dev/appget/codegen/AppServerGenerator.java` | Update `snakeToCamel` calls to `JavaNaming.toFieldAccessor()` |
| `src/test/java/dev/appget/naming/JavaNamingTest.java` | **NEW** — unit tests |
| `src/test/java/dev/appget/codegen/JavaUtilsTest.java` | Remove snakeToCamel tests (moved to JavaNamingTest) |
| `java/CLAUDE.md` | Document naming convention pattern |

**Not changed** (no constructor change = no blast radius):
- Handlebars templates (`*.hbs`) — `new Specification(...)` calls unchanged
- `RuleEngine.java` — `buildSpec()` unchanged
- Test files (`SpecificationTest`, `RuleTest`, `MetadataContextTest`) — unchanged

## Cross-Language Reference Pattern

Each language has a `naming` package with a contract definition and a static implementation. `Specification.*` calls the static function directly — no instances, no polymorphism.

When implementing `appget/go`:
```go
// naming/naming.go — contract (Go interface)
type NamingConvention interface {
    ToFieldAccessor(snakeCaseField string) string
}

// naming/go_naming.go — static implementation (package-level function)
func ToFieldAccessor(s string) string {
    return snakeToPascal(s) // Go exported fields are PascalCase
}
```

When implementing `appget/python`:
```python
# naming/naming.py — contract (Protocol)
class NamingConvention(Protocol):
    def to_field_accessor(self, snake_case_field: str) -> str: ...

# naming/python_naming.py — static implementation (module-level function)
def to_field_accessor(snake_case_field: str) -> str:
    return snake_case_field  # identity — Python uses snake_case
```

When implementing `appget/rust`:
```rust
// naming/mod.rs — contract (trait)
pub trait NamingConvention {
    fn to_field_accessor(snake_case_field: &str) -> String;
}

// naming/rust_naming.rs — static implementation (free function)
pub fn to_field_accessor(snake_case_field: &str) -> String {
    snake_case_field.to_string()  // identity — Rust uses snake_case
}
```

Same structure. Same contract. Static functions. No instances.
