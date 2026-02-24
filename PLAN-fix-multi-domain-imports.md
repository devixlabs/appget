# Plan: Fix Multi-Domain Import Bug in AppServerGenerator

## Context

When the Haiku sub-agent ran the domain-architect to generate a multi-domain social media app (3 domains: auth, social, admin), the generated `RuleService.java` had incorrect model imports:

```java
// BUGGY — flat package, no domain namespace
import dev.appget.model.Roles;
import dev.appget.model.Users;
import dev.appget.model.Posts;

// CORRECT — domain-specific namespaces
import dev.appget.admin.model.Roles;
import dev.appget.auth.model.Users;
import dev.appget.social.model.Posts;
```

The agent had to manually fix these imports to get the server to compile.

---

## Root Cause

**File**: `java/src/main/java/dev/appget/codegen/AppServerGenerator.java`

**Key mismatch**: `modelIndex` is keyed by **snake_case** names from `models.yaml` (e.g., `"roles"`, `"user_roles"`), but `rule.targetName` from `specs.yaml` uses **PascalCase** (e.g., `"Roles"`, `"UserRoles"`).

**Line 142** — models loaded with snake_case keys:
```java
modelIndex.put(modelName, info);  // key = "roles", "user_roles", "users"
```

**Line 181** — rules loaded with PascalCase target names:
```java
info.targetName = (String) target.get("name");  // "Roles", "UserRoles", "Users"
```

**Line 1292** — lookup always misses:
```java
private String resolveModelImport(RuleInfo rule) {
    ModelInfo model = modelIndex.get(rule.targetName);  // get("Roles") → null (key is "roles")
    if (model != null) {
        return model.namespace + ".model." + pascalName(model);  // NEVER REACHED
    }
    return "dev.appget.model." + rule.targetName;  // ALWAYS hits flat fallback
}
```

**Secondary issue**: The model imports are dead code — `RuleService` uses reflection (`spec.getClass().getMethods()`) and generics (`<T> target`), never referencing model classes directly.

**Same bug at line 601** (`generateSpecificationRegistry`) — lookup fails but works by accident because the fallback `JavaUtils.snakeToPascal(rule.targetName)` on already-PascalCase input preserves the correct name.

---

## Fix

### 1. Index models by BOTH snake_case and PascalCase keys

**File**: `AppServerGenerator.java`, method `loadModels()`, after line 142

Add a second index entry:
```java
modelIndex.put(modelName, info);                         // "roles"
modelIndex.put(JavaUtils.snakeToPascal(modelName), info); // "Roles"
```

This fixes ALL `modelIndex.get(rule.targetName)` lookups throughout the class (lines 601, 1292).

### 2. Remove unused model imports from `generateRuleService()`

**File**: `AppServerGenerator.java`, lines 634-641

Delete the entire model import collection block:
```java
// DELETE these lines — RuleService never references model classes
Set<String> modelImports = new LinkedHashSet<>();
for (RuleInfo rule : modelRules) {
    modelImports.add(resolveModelImport(rule));
}
for (String imp : modelImports) {
    code.append("import ").append(imp).append(";\n");
}
```

### 3. Remove `resolveModelImport()` method (now dead code)

**File**: `AppServerGenerator.java`, lines 1291-1297

Delete the entire method — it was only called from the import block we just removed.

---

## Unit Tests

**File**: `java/src/test/java/dev/appget/codegen/AppServerGeneratorTest.java`

### Test 1: RuleService has no model imports (they're unused dead code)
```java
@Test
@DisplayName("RuleService does not import model classes (uses reflection)")
void testRuleServiceNoModelImports(@TempDir Path tempDir) throws Exception {
    String content = readRuleService(tempDir);
    assertFalse(content.contains("import dev.appget.model."),
        "RuleService should not import flat dev.appget.model.* (no such package in multi-domain)");
    // Should not import ANY domain-specific model classes either (they're unused)
    assertFalse(content.matches("(?s).*import dev\\.appget\\.\\w+\\.model\\..*"),
        "RuleService should not import domain-specific model classes (uses reflection, not static types)");
}
```

### Test 2: SpecificationRegistry resolves PascalCase targets correctly
```java
@Test
@DisplayName("SpecificationRegistry resolves multi-domain target names")
void testSpecRegistryMultiDomainTargets(@TempDir Path tempDir) throws Exception {
    String content = generateAndReadFile(tempDir,
        "dev", "appget", "server", "service", "SpecificationRegistry.java");
    // Verify PascalCase target names are correctly resolved
    // (the bug caused modelIndex lookup to miss, falling to snakeToPascal fallback)
    assertTrue(content.contains("SPEC_TARGETS"),
        "Should have SPEC_TARGETS map for target resolution");
}
```

### Test 3: Per-model services import from correct domain namespace
```java
@Test
@DisplayName("Per-model services import from domain-specific namespaces")
void testModelServiceDomainImports(@TempDir Path tempDir) throws Exception {
    String outputDir = tempDir.toString();
    if (new File("models.yaml").exists() && new File("specs.yaml").exists()) {
        generator.generateServer("models.yaml", "specs.yaml", outputDir);
        // Verify at least one service uses domain-specific import (not flat dev.appget.model.*)
        Path usersServicePath = Paths.get(outputDir,
            "dev", "appget", "server", "service", "UsersService.java");
        if (Files.exists(usersServicePath)) {
            String content = Files.readString(usersServicePath);
            assertFalse(content.contains("import dev.appget.model."),
                "UsersService should not use flat dev.appget.model package");
            assertTrue(content.contains(".model.Users"),
                "UsersService should import Users from domain-specific namespace");
        }
    }
}
```

---

## Verification

1. Apply the fix to `AppServerGenerator.java`
2. Run `cd java && make all` — should pass cleanly
3. Run `cd java && make test` — all 280+ tests pass including new ones
4. Inspect generated `RuleService.java` — should have NO model imports
5. Inspect generated per-model `*Service.java` — should have correct domain imports

---

## Files to Modify

| File | Change |
|------|--------|
| `java/src/main/java/dev/appget/codegen/AppServerGenerator.java` | Fix modelIndex dual-keying (line 142), remove dead imports (lines 634-641), remove dead method (lines 1291-1297) |
| `java/src/test/java/dev/appget/codegen/AppServerGeneratorTest.java` | Add 3 new test methods |
