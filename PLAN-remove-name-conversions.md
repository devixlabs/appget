# PLAN: Remove Name Conversions & Rename SpringBootServerGenerator

## Goal

Eliminate all name transformation logic (`singularize`, `toModelName`, `toViewModelName`) from the schema parser, pass SQL names through as-is, move `toResourceName` to the server generator where it belongs, derive domain mappings from feature files instead of hardcoded maps, and rename `SpringBootServerGenerator` to `AppServerGenerator`.

---

## Background

### Current flow (BROKEN for portability)
```
schema.sql          → SQLSchemaParser (singularize + PascalCase) → models.yaml (PascalCase names)
features/*.feature  → FeatureToSpecsConverter                    → specs.yaml  (PascalCase @target)
                                    ↓
              SpecificationGenerator matches specs.yaml target names
              against models.yaml model names — BOTH must be PascalCase
```

The PascalCase conversion is baked into the **parser** (language-agnostic layer) instead of the **generators** (language-specific layer). This blocks Python/Go/Rust targets that don't need PascalCase.

### Target flow (CORRECT for portability)
```
schema.sql          → SQLSchemaParser (NO conversion) → models.yaml (snake_case names as-is from SQL)
features/*.feature  → FeatureToSpecsConverter          → specs.yaml  (snake_case @target matching SQL)
                                    ↓
              SpecificationGenerator matches on snake_case names
              Java generators apply PascalCase only when emitting Java code
```

---

## Implementation Steps

### Step 1: Update feature files to use SQL table/view names in @target

**Files**: `features/social.feature`, `features/auth.feature`, `features/admin.feature`

Change all `@target` tags from PascalCase model names to exact SQL table/view names:

| Before | After |
|--------|-------|
| `@target:Post` | `@target:posts` |
| `@target:Comment` | `@target:comments` |
| `@target:Follow` | `@target:follows` |
| `@target:User` | `@target:users` |
| `@target:Session` | `@target:sessions` |
| `@target:ModerationFlag` | `@target:moderation_flags` |
| `@view @target:PostDetailView` | `@view @target:post_detail_view` |
| `@view @target:FeedPostView` | `@view @target:feed_post_view` |
| `@view @target:CommentDetailView` | `@view @target:comment_detail_view` |
| `@view @target:UserProfileView` | `@view @target:user_profile_view` |

**Rule**: `@target` value === SQL table name (for models) or SQL view name (for views). Exact match. Zero conversion.

---

### Step 2: Remove name conversion methods from SQLSchemaParser.java

**File**: `java/src/main/java/dev/appget/codegen/SQLSchemaParser.java`

**Delete these methods entirely:**
- `singularize()` (lines 781-794)
- `toModelName()` (lines 747-750)
- `toViewModelName()` (lines 752-767)
- `toResourceName()` (lines 776-779) — moves to AppServerGenerator in Step 7

**Modify `parseTables()` (around line 263):**
```java
// BEFORE:
String modelName = toModelName(tableName);
String sourceTable = tableName.toLowerCase();
String resource = toResourceName(tableName);

// AFTER:
String modelName = tableName.toLowerCase();
String sourceTable = modelName;
```

Remove the `resource` field from the model map — it moves to the server generator.

**Modify `parseViews()` (around line 336):**
```java
// BEFORE:
String viewModelName = toViewModelName(viewName);
...
viewModel.put("resource", toResourceName(viewName));

// AFTER:
String viewModelName = viewName.toLowerCase();
```

Remove the `resource` field from the view model map.

---

### Step 3: Remove hardcoded DOMAIN_MAPPING and VIEW_DOMAIN_MAPPING

**File**: `java/src/main/java/dev/appget/codegen/SQLSchemaParser.java`

**Delete:**
- `createDomainMapping()` method (lines 56-66) and its constant (line 26)
- `createViewDomainMapping()` method (lines 68-73) and its constant (line 27)

**Replace with**: Domain derived from SQL comments in schema.sql and views.sql.

The schema.sql already has domain comments:
```sql
-- auth domain
CREATE TABLE users ( ... );

-- social domain
CREATE TABLE posts ( ... );
```

**Add a `parseDomainFromComments()` method** that scans backwards from each `CREATE TABLE`/`CREATE VIEW` statement to find the nearest `-- <word> domain` comment. Use that word as the domain. Fallback to `"default"` if no comment found.

This is a simple regex: `--\s+(\w+)\s+domain` applied to the text preceding each CREATE statement.

---

### Step 4: Update models.yaml contract

After steps 2-3, `models.yaml` will emit:
```yaml
domains:
  social:
    models:
      - name: posts              # was "Post" — now matches SQL table name
        source_table: posts
        fields: [...]
      - name: follows            # was "Follow" — no singularization
        source_table: follows
        fields: [...]
    views:
      - name: post_detail_view   # was "PostDetailView" — matches SQL view name
        source_view: post_detail_view
        fields: [...]
```

The `name` field is now identical to `source_table`/`source_view`. The `resource` field is removed (generated downstream).

**Note**: `source_table` and `name` are now redundant. Keep both for now to avoid breaking changes in models.yaml consumers. Can consolidate later.

---

### Step 5: Update SpecificationGenerator.java

**File**: `java/src/main/java/dev/appget/codegen/SpecificationGenerator.java`

The `targetImportMap` currently builds keys like `social:model:Post`. After the change, keys become `social:model:posts`. The `@target` in specs.yaml also becomes `posts`. These match — no logic change needed in the lookup.

**However**, the generated Java class needs a PascalCase type name for the import and method signature. Add a `snakeToPascal()` utility call when generating Java code:

```java
// Around line 212-216:
String targetName = (String) target.get("name");  // "posts" (snake_case)
String targetDomain = (String) target.get("domain");
String key = targetDomain + ":" + targetKind + ":" + targetName;
targetImport = targetImportMap.getOrDefault(key, guessImport(targetDomain, targetKind, targetName));

// NEW: Convert to PascalCase only for Java class name usage
targetTypeName = snakeToPascal(targetName);  // "posts" → "Posts"
```

Add `snakeToPascal()` as a static utility (it already partially exists as `JavaUtils.snakeToCamel`; may need a PascalCase variant or can reuse `toViewModelName` logic as a static utility).

**Key point**: The matching/lookup stays snake_case. Only the Java code emission uses PascalCase.

---

### Step 6: Update ModelsToProtoConverter.java

**File**: `java/src/main/java/dev/appget/codegen/ModelsToProtoConverter.java`

Protobuf message names should be PascalCase per protobuf style guide. Convert when emitting `.proto` files:

```java
// Around line 258:
// BEFORE:
sb.append("\nmessage ").append(msg.name()).append(" {\n");

// AFTER:
sb.append("\nmessage ").append(snakeToPascal(msg.name())).append(" {\n");
```

Apply the same conversion at:
- Line 293 (view messages)
- Line 323 (service ID messages: `PostsId`, `PostsList`)
- Line 327 (service list messages)
- Service names (line ~330)

Field names stay snake_case — that's already protobuf convention.

---

### Step 7: Rename SpringBootServerGenerator → AppServerGenerator

**File renames:**
- `java/src/main/java/dev/appget/codegen/SpringBootServerGenerator.java` → `AppServerGenerator.java`
- `java/src/test/java/dev/appget/codegen/SpringBootServerGeneratorTest.java` → `AppServerGeneratorTest.java`

**Internal changes in the renamed file:**
- Class name: `SpringBootServerGenerator` → `AppServerGenerator`
- Logger: Update class reference
- main() method: Update usage message
- All internal references

**Move `toResourceName()` here** (from deleted SQLSchemaParser methods). It generates REST paths, which is a server-generator concern:
```java
// In AppServerGenerator.java:
private static String toResourceName(String name) {
    return name.toLowerCase().replace('_', '-');
}
```

Apply PascalCase conversion when generating Java class names (controllers, services, etc.):
```java
// Around line 125:
String modelName = (String) model.get("name");  // "posts" (snake_case from models.yaml)
info.name = snakeToPascal(modelName);            // "Posts" for Java class names
info.resource = toResourceName(modelName);       // "posts" for REST paths
```

**Update build.gradle (line 185):**
```gradle
// BEFORE:
mainClass = 'dev.appget.codegen.SpringBootServerGenerator'
// AFTER:
mainClass = 'dev.appget.codegen.AppServerGenerator'
```

**Update Makefile references (lines 19, 75-77, 91-93):**
- Help text: `"Generate application server from models and specs"` (drop "Spring Boot" from user-facing text)
- Target name stays `generate-server` (no need to rename make targets)
- Logger/echo messages: `"Generating application server..."` instead of `"Generating Spring Boot server..."`

---

### Step 8: Update ProtoOpenAPIGenerator.java

**File**: `java/src/main/java/dev/appget/codegen/ProtoOpenAPIGenerator.java`

The generator reads protobuf message names (which are PascalCase after Step 6). No changes needed in how it processes message names — they arrive PascalCase from the proto parsing.

However, verify:
- Line 296: `schemas.put(msg.name(), buildSchema(msg))` — `msg.name()` comes from parsed proto, already PascalCase. OK.
- Line 305: `svc.name().replace("Service", "")` — service names from proto, already PascalCase. OK.

**No changes needed** — this generator consumes protobuf output, not models.yaml directly.

---

### Step 9: Update RuleEngine.java

**File**: `java/src/main/java/dev/appget/RuleEngine.java`

The RuleEngine loads from specs.yaml and looks up descriptors by model name.

- Line 45: `registry.getDescriptorByName(modelName)` — the descriptor registry indexes by protobuf message name (PascalCase). But specs.yaml now has snake_case target names.

**Fix**: Convert target name to PascalCase when looking up descriptors:
```java
// BEFORE:
Descriptors.Descriptor descriptor = registry.getDescriptorByName(modelName);

// AFTER:
Descriptors.Descriptor descriptor = registry.getDescriptorByName(snakeToPascal(modelName));
```

---

### Step 10: Update DescriptorRegistryGenerator.java

**File**: `java/src/main/java/dev/appget/codegen/DescriptorRegistryGenerator.java`

This generates the DescriptorRegistry class that maps model names to protobuf descriptors.

- Line 83: `String name = (String) model.get("name")` — now snake_case from models.yaml.

The registry should index by **snake_case** (matching specs.yaml targets) but look up **PascalCase** protobuf class names:

```java
String name = (String) model.get("name");          // "posts"
String protoName = snakeToPascal(name);             // "Posts"
// Register: snake_case key → PascalCase protobuf descriptor
sb.append("register(\"").append(name).append("\", ").append(protoName).append(".getDescriptor());\n");
```

---

### Step 11: Add snakeToPascal utility

**File**: `java/src/main/java/dev/appget/codegen/CodeGenUtils.java` (or new `NamingUtils.java`)

```java
/**
 * Converts snake_case to PascalCase.
 * "user_roles" → "UserRoles"
 * "posts" → "Posts"
 * "post_detail_view" → "PostDetailView"
 *
 * This is the ONLY name conversion in the pipeline.
 * It is deterministic and reversible (PascalCase → snake_case is unambiguous).
 * It is used ONLY by Java-specific generators, never by the parser.
 */
public static String snakeToPascal(String snake) {
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (char c : snake.toCharArray()) {
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
```

Note: `JavaUtils.snakeToCamel()` already exists but produces camelCase (first letter lowercase). The PascalCase variant capitalizes the first letter. Check if a simple wrapper works or if a new method is cleaner.

---

### Step 12: Update ALL tests

**Test files to update:**

1. **`FeatureToSpecsConverterTest.java`** — Change expected target names from PascalCase to snake_case:
   - `assertEquals("Employee", target.get("name"))` → `assertEquals("employees", target.get("name"))` (or whatever the test feature files use)
   - **Note**: The test feature files at `java/src/test/resources/` may still use the old HR/employee examples. These test fixture `.feature` files must ALSO be updated to use snake_case `@target` tags.

2. **`ProtoOpenAPIGeneratorTest.java`** — PascalCase expectations stay the same because OpenAPI consumes protobuf output (already PascalCase after Step 6).

3. **`SpecificationGeneratorTest.java`** — Update expected class names and imports. The generated Java classes will still be PascalCase (the generator converts), but the lookup keys change.

4. **`SpringBootServerGeneratorTest.java`** → rename to `AppServerGeneratorTest.java` — Same as above plus class name references.

5. **`SQLSchemaParserTest.java`** (if exists) — Update expected model names from PascalCase to snake_case.

**Critical**: Read each test file fully before modifying. Some tests may use fixture files in `src/test/resources/` that also need updating.

---

### Step 13: Update domain-architect agent definition

**File**: `.claude/agents/domain-architect.md`

Update the agent to use snake_case `@target` tags:
- All examples change from `@target:Post` to `@target:posts`
- All examples change from `@view @target:PostDetailView` to `@view @target:post_detail_view`
- Remove singularization guidance (no longer relevant)
- Update the "Common Mistakes" section
- Simplify naming conventions section

---

### Step 14: Update documentation

**Files to update:**
- `java/CLAUDE.md` — All references to SpringBootServerGenerator → AppServerGenerator, update naming convention docs
- `java/README.md` — Same
- `java/PIPELINE.md` — Same, update pipeline flow diagrams
- `CLAUDE.md` (root) — Update if it references SpringBootServerGenerator
- `README.md` (root) — Update if needed
- `docs/*.md` — Search and update any references

---

## Verification

After all changes, run:
```bash
cd java && make all
```

This executes the full pipeline: parse → generate → test → build. All 250+ tests must pass.

**Key things to verify:**
1. `models.yaml` contains snake_case names (not PascalCase)
2. Generated `.proto` files still have PascalCase message names (converted by generator)
3. Generated Java classes still have PascalCase names (converted by generator)
4. `specs.yaml` target names are snake_case (matching feature files)
5. SpecificationGenerator correctly matches snake_case targets
6. RuleEngine correctly resolves snake_case targets to PascalCase descriptors
7. REST API paths still work (e.g., `/posts`, `/user-roles`)
8. No references to `SpringBootServerGenerator` remain anywhere in the codebase

---

## Summary of what changes where

| Layer | What changes | What stays the same |
|-------|-------------|-------------------|
| **Feature files** | `@target:posts` (snake_case) | Gherkin syntax, rule names, field names |
| **SQLSchemaParser** | No conversions, domain from SQL comments | Column parsing, type mapping, view resolution |
| **models.yaml** | `name: posts` (snake_case), no `resource` field | Field definitions, types, domain grouping |
| **specs.yaml** | `name: posts` in target (snake_case) | Rule conditions, metadata, outcomes |
| **ModelsToProtoConverter** | `snakeToPascal()` when emitting message names | Field names (already snake_case), proto structure |
| **SpecificationGenerator** | `snakeToPascal()` for Java class names only | Target matching logic (now both snake_case) |
| **AppServerGenerator** (renamed) | Class name, owns `toResourceName()`, `snakeToPascal()` for Java | All generation logic, REST structure |
| **ProtoOpenAPIGenerator** | Nothing | Consumes proto output (already PascalCase) |
| **RuleEngine** | `snakeToPascal()` for descriptor lookup | Rule evaluation logic |
| **Tests** | Expected names, fixture files, class references | Test structure, assertion patterns |

---

## Risk assessment

- **Low risk**: Rename SpringBootServerGenerator → AppServerGenerator (mechanical find-replace)
- **Low risk**: Delete singularize/toModelName/toViewModelName (removing code)
- **Medium risk**: Domain derivation from SQL comments (new parsing logic, but simple regex)
- **Medium risk**: Updating all test assertions and fixture files (many files, tedious but mechanical)
- **Highest risk**: Ensuring the snake_case ↔ PascalCase boundary is correctly placed in every generator. Miss one spot and names won't match. **Run `make all` frequently during implementation.**

---

## Implementation order recommendation

Do it in this order to stay green (passing tests) as long as possible:

1. Add `snakeToPascal()` utility (Step 11) — no tests break, just adding code
2. Rename SpringBootServerGenerator → AppServerGenerator (Step 7 partial — just the rename, not the logic changes) — update build.gradle, Makefile, docs
3. Move `toResourceName()` to AppServerGenerator (Step 7 partial)
4. Update feature files to snake_case @target (Step 1) — tests will start failing here
5. Remove conversion methods from SQLSchemaParser, add comment-based domain parsing (Steps 2-3)
6. Update all generators to use snakeToPascal where needed (Steps 5, 6, 8, 9, 10)
7. Update all tests and fixture files (Step 12)
8. Run `make all` — everything should pass
9. Update agent definition and docs (Steps 13-14)
