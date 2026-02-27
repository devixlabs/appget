---
name: appget-feature-dsl
description: "Load this skill when writing, reviewing, or editing Gherkin .feature files for the appget platform. Contains the complete DSL reference: tags (@domain, @target, @rule, @blocking, @view), operator phrases, simple and compound conditions, metadata requirements, outcomes, non-comparable types, field validation rules, and common mistakes. Essential for any work on features/*.feature files."
allowed-tools: Read, Glob, Grep
---

# appget Feature DSL Reference

The appget Gherkin DSL defines business rules that drive the code generation pipeline. Feature files are parsed by `FeatureToSpecsConverter` into `specs.yaml`, which feeds downstream generators and the runtime rule engine.

**Source files**: `features/*.feature` (one per domain)
**Parser**: `java/src/main/java/dev/appget/codegen/FeatureToSpecsConverter.java`
**Output**: `java/specs.yaml` (generated, git-ignored)

---

## CRITICAL Gotchas (Read First)

1. **Non-comparable types**: `DATE`, `TIMESTAMP`, `DATETIME` columns CANNOT be used in `When` conditions. They map to `google.protobuf.Timestamp` (a protobuf message type, not a scalar). Rules that compare these will silently fail or always return false. Use boolean flags or string/numeric fields instead.

2. **No field-to-field comparisons**: `<value>` must ALWAYS be a literal (string, number, or boolean) — NEVER another field name. `When field_a does not equal field_b` parses `field_b` as the literal string `"field_b"`, creating a no-op rule.

3. **@target uses the SQL table name directly**: Table `follows` → `@target:follows`. Table `blog_posts` → `@target:blog_posts`. Use the exact snake_case plural table name — the pipeline applies `snakeToPascal` automatically.

4. **Metadata fields use camelCase**: Model fields use `snake_case` (`is_active`, `like_count`), but metadata fields use `camelCase` (`roleLevel`, `isAdmin`, `sessionId`) — matching Java getter conventions.

5. **View fields must be in SELECT clause**: For `@view` targets, every field in `When` conditions must exist in the view's `SELECT` clause, not just in its `WHERE` or `JOIN`. A column the view filters on but doesn't project is not accessible.

6. **`But otherwise` not `But`**: Gherkin requires a valid keyword before `otherwise`. Always use `But otherwise status is "..."`.

---

## File Organization

One `.feature` file per domain. File name matches domain name:
- `features/social.feature` for the `social` domain
- `features/auth.feature` for the `auth` domain
- `features/billing.feature` for the `billing` domain

### Feature-Level Structure

```gherkin
@domain:<domain_name>
Feature: <Domain Name> Domain Business Rules

  <scenarios go here>
```

The `@domain` tag assigns all scenarios in the file to that domain.

---

## Scenario Tags

Every scenario MUST have these tags on the line before `Scenario:`:

| Tag | Required | Purpose | Example |
|-----|----------|---------|---------|
| `@target:<table_name>` | Yes | Target model or view (snake_case plural matching SQL table name) | `@target:users` |
| `@rule:<RuleName>` | Yes | Unique rule name (PascalCase) | `@rule:UserAgeCheck` |
| `@blocking` | No | Rule causes 422 rejection if unsatisfied | `@blocking` |
| `@view` | No | Target is a view (not a model) | `@view` |

**Tag placement**: All tags go on a single line immediately before `Scenario:`.
**@target naming**: Use the exact SQL table name (snake_case plural, e.g., table `follows` → `@target:follows`, table `blog_posts` → `@target:blog_posts`). The pipeline applies `snakeToPascal` automatically.
**@rule naming**: PascalCase, descriptive of what the rule checks.

---

## Condition Patterns

### Simple Condition (single field check)

```gherkin
When <field_name> <operator_phrase> <value>
```

### Operator Phrases

| Phrase | Symbol | Example |
|--------|--------|---------|
| `equals` | `==` | `When status equals "ACTIVE"` |
| `does not equal` | `!=` | `When role does not equal "GUEST"` |
| `is greater than` | `>` | `When age is greater than 18` |
| `is less than` | `<` | `When score is less than 50` |
| `is at least` | `>=` | `When balance is at least 100` |
| `is at most` | `<=` | `When attempts is at most 5` |

### Value Types

- **String values** must be quoted: `When role_id equals "Manager"`
- **Numeric values** are unquoted: `When age is greater than 18`
- **Boolean values** are unquoted: `When is_active equals true`

**CRITICAL**: Values must ALWAYS be literals. Never use another field name as a value (see Gotcha #2 above).

### Comparable Types (safe for When conditions)

`VARCHAR`, `TEXT`, `INT`, `BIGINT`, `DECIMAL`, `FLOAT`, `DOUBLE`, `BOOLEAN`

### Non-Comparable Types (NEVER use in When conditions)

`DATE`, `TIMESTAMP`, `DATETIME` — use these in schema.sql for storage only. Add boolean flags or numeric fields for rule comparisons instead.

### Compound AND Condition (all must be true)

```gherkin
When all conditions are met:
  | field   | operator | value   |
  | age     | >=       | 30      |
  | role_id | ==       | Manager |
```

### Compound OR Condition (at least one must be true)

```gherkin
When any condition is met:
  | field     | operator | value |
  | is_admin  | ==       | true  |
  | role_id   | ==       | Owner |
```

**Data table format**: Three columns — `field`, `operator`, `value`. Operators use symbols (`>=`, `==`), not natural language phrases.

---

## Metadata Requirements (Authorization)

Metadata requirements are evaluated BEFORE the main condition. If any metadata requirement fails, the rule fails without evaluating the main `When` condition.

```gherkin
Given sso context requires:
  | field         | operator | value |
  | authenticated | ==       | true  |
And roles context requires:
  | field     | operator | value |
  | roleLevel | >=       | 3     |
```

**Category names** must match categories defined in `metadata.yaml` (e.g., `sso`, `roles`, `user`, `oauth`, `api`, `tenant`).

**Field names** must match field names in that category's `fields` list in `metadata.yaml`. Use **camelCase** (e.g., `roleLevel`, `isAdmin`), NOT snake_case.

At runtime, metadata values come from HTTP headers: `X-{Category}-{Field}` (e.g., `X-Sso-Authenticated: true`, `X-Roles-Role-Level: 3`).

---

## Outcomes

Every scenario MUST have both a success and failure outcome:

```gherkin
Then status is "APPROVED"
But otherwise status is "REJECTED"
```

### Status Value Guidelines

- Use `SCREAMING_SNAKE_CASE`
- Be descriptive: `APPROVED`, `AGE_RESTRICTED`, `PREMIUM_TIER`, `FRAUD_FLAGGED`
- Avoid generic values: `OK`, `FAIL`, `YES`, `NO`
- `Then` = positive/satisfied outcome, `But otherwise` = negative/unsatisfied outcome

---

## Complete Scenario Examples

**Simple blocking rule**:
```gherkin
  @target:users @blocking @rule:UserAgeVerification
  Scenario: User must be at least 13 years old
    When age is at least 13
    Then status is "VERIFIED"
    But otherwise status is "AGE_RESTRICTED"
```

**Compound rule (non-blocking, informational)**:
```gherkin
  @target:posts @rule:ViralPostCheck
  Scenario: Post is considered viral with high engagement
    When all conditions are met:
      | field      | operator | value |
      | like_count | >=       | 10000 |
      | is_public  | ==       | true  |
    Then status is "VIRAL"
    But otherwise status is "STANDARD"
```

**View-targeting rule**:
```gherkin
  @view @target:post_detail_view @rule:VerifiedAuthorCheck
  Scenario: Post by verified author gets priority
    When author_verified equals true
    Then status is "PRIORITY"
    But otherwise status is "NORMAL"
```

**Metadata-aware authorization rule**:
```gherkin
  @target:posts @blocking @rule:AuthenticatedPostCreation
  Scenario: Only authenticated users with sufficient role can create posts
    Given sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    And roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 2     |
    When is_public equals true
    Then status is "ALLOWED"
    But otherwise status is "DENIED"
```

---

## Common Mistakes

### Mistake: Field on VIEW but targeting MODEL

```gherkin
  # WRONG: user_roles table has: id, user_id, role_id, assigned_at
  # role_name does NOT exist on user_roles -- it exists on user_role_view
  @target:user_roles @rule:DeveloperCheck
  Scenario: Check developer role
    When role_name equals "developer"

  # CORRECT: target the VIEW where role_name actually exists
  @view @target:user_role_view @rule:DeveloperCheck
  Scenario: Check developer role
    When role_name equals "developer"
    Then status is "DEVELOPER"
    But otherwise status is "NOT_DEVELOPER"
```

### Mistake: Comparing a TIMESTAMP column

```gherkin
  # WRONG: expires_at is TIMESTAMP, not comparable
  @target:sessions @rule:SessionCheck
  Scenario: Session not expired
    When expires_at is greater than 0

  # CORRECT: use a comparable field instead
  @target:sessions @rule:SessionCheck
  Scenario: Session has valid token
    When token does not equal ""
    Then status is "ACTIVE"
    But otherwise status is "INVALID"
```

### Mistake: PascalCase in @target instead of snake_case

```gherkin
  # WRONG: @target must use the exact SQL table name (snake_case), not PascalCase
  @target:Follows @rule:ActiveFollow
  Scenario: Follow is active

  # CORRECT: table is "follows" → @target:follows
  @target:follows @rule:ActiveFollow
  Scenario: Follow is active
    When is_active equals true
    Then status is "ACTIVE"
    But otherwise status is "INACTIVE"
```

### Mistake: Field-to-field comparison

```gherkin
  # WRONG: DSL only compares fields to LITERAL values, not to other fields
  @target:follows @rule:SelfFollowPrevention
  Scenario: User cannot follow themselves
    When follower_id does not equal following_id

  # The parser treats "following_id" as the literal string "following_id",
  # NOT as a reference to the following_id column. This rule is a no-op.

  # CORRECT: use a boolean/string/int field with a literal value
  @target:follows @rule:ActiveFollowValidation
  Scenario: Follow relationship must be active
    When is_active equals true
    Then status is "VALID"
    But otherwise status is "INVALID"
```

### Mistake: View column not in SELECT clause

```gherkin
  # WRONG: user_feed_view uses WHERE u.is_suspended = false
  # but does NOT include author_suspended in its SELECT clause
  @view @target:user_feed_view @rule:FeedCheck
  Scenario: Feed post check
    When author_suspended equals false

  # CORRECT: only reference columns in the view's SELECT clause
  @view @target:user_feed_view @rule:FeedCheck
  Scenario: Feed post check
    When is_public equals true
    Then status is "ELIGIBLE"
    But otherwise status is "FILTERED"
```

### Mistake: Tautological OR condition

```gherkin
  # WRONG: bio == "" OR bio != "" is ALWAYS true
  When any condition is met:
    | field | operator | value |
    | bio   | ==       | ""    |
    | bio   | !=       | ""    |

  # CORRECT: test for the meaningful condition directly
  When bio does not equal ""
  Then status is "PROFILE_COMPLETE"
  But otherwise status is "PROFILE_INCOMPLETE"
```

---

## Validation Checklist

Before finalizing any feature file, verify:

- [ ] Every `@target` references a model in `schema.sql` or view in `views.sql`
- [ ] `@target` uses the exact SQL table name (snake_case plural, e.g., `users`, `blog_posts`)
- [ ] Every field in `When` conditions exists on the EXACT target model/view
- [ ] For `@view` targets: field exists in the view's `SELECT` clause (not just WHERE/JOIN)
- [ ] No `When` conditions reference `DATE`, `TIMESTAMP`, or `DATETIME` columns
- [ ] No field-to-field comparisons (values must be literals)
- [ ] No tautological OR conditions (e.g., `field == x OR field != x`)
- [ ] Every metadata category in `Given ... context requires:` exists in `metadata.yaml`
- [ ] Every metadata field name exists in that category's `fields` list
- [ ] Every scenario has both `Then status is "..."` and `But otherwise status is "..."`
- [ ] `@rule` names are unique across all feature files
- [ ] Status values are descriptive SCREAMING_SNAKE_CASE (not generic OK/FAIL)
