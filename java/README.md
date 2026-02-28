# appget.dev/java - Code Generation from Database Schema

**Write your database schema in SQL and business rules in Gherkin. Automatically generate Java models, validation logic, and REST API endpoints.**

> Production-ready code generation system: Gherkin-first business rules, schema-first protobuf design with SQL views, descriptor-based rule evaluation, compound business rules (AND/OR), metadata-aware authorization with toggle-based category registry, blocking/informational rule enforcement, and over 290 comprehensive unit tests.

---

## What Is This Project?

**appget.dev/java** is a code generator that converts your database schema into a complete Java backend with:

1. **Protobuf Models** - Auto-generated from `schema.sql` via `.proto` files (14 models + 10 views across 3 domains)
2. **gRPC Service Stubs** - Services across 3 domains with full CRUD
3. **REST API Server** - Spring Boot server with CRUD endpoints for all your models
4. **Business Rules** - Define rules in Gherkin `.feature` files, converted to `specs.yaml`, enforced automatically via generated Specification classes
5. **Authorization** - Rules can check headers/metadata to enforce access control
6. **Type Safety** - Everything generated from same schema source = perfect type alignment

**Think of it like**: Gherkin rules + SQL schema → Protocol Buffers → gRPC + REST + Business Rules, all from human-friendly sources of truth.

### The Complete Pipeline

```
YOU WRITE THIS:                SYSTEM GENERATES THIS:
┌──────────────────────┐
│  features/*.feature  │       ┌──────────────────────────────┐
│  (Business rules)    │──┐    │  specs.yaml (intermediate)   │
└──────────────────────┘  ├──▶ │  (YAML rules + metadata)     │
┌──────────────────────┐  │    └──────────────────────────────┘
│  metadata.yaml       │──┘            │
│  (Context POJOs)     │               │
└──────────────────────┘               │
                                       ▼
┌──────────────────┐         ┌──────────────────────────────┐
│  schema.sql      │         │  Java Domain Models          │
│  (Your DB)       │────────▶│  (Users, Posts, ModerationActions, etc) │
└──────────────────┘         └──────────────────────────────┘
                                     │
┌──────────────────┐                 │
│  views.sql       │─────────────────┘
│  (Read models)   │         ┌──────────────────────────────┐
└──────────────────┘────────▶│  REST API Specification      │
                             │  (OpenAPI 3.0)               │
                                     │
                            ┌────────┘
                            ▼
                     ┌──────────────────────────────┐
                     │  Spring Boot REST Server     │
                     │  - Controllers (endpoints)   │
                     │  - Services (rules + logic)  │
                     │  - Repositories (storage)    │
                     └──────────────────────────────┘
```

---

## File Organization: What You Edit vs What's Generated

### YOU EDIT THESE (Source Files - Commit to Git)

| File | Purpose | Edit When |
|------|---------|-----------|
| **features/*.feature** | Business rules in Gherkin (BDD) | Adding or changing validation logic |
| **metadata.yaml** | Curated metadata registry with toggle model (14 built-in categories) | Enabling/disabling categories or adding custom ones |
| **schema.sql** | Database table definitions | Adding new models or tables |
| **views.sql** | SQL composite views (read models) | Creating read-optimized views |

### SYSTEM GENERATES THESE (Don't Edit - Git-Ignored)

| File/Directory | What It Is | Generated From |
|---|---|---|
| **specs.yaml** | Intermediate rules + metadata YAML | features/*.feature + metadata.yaml |
| **models.yaml** | Intermediate YAML representation | schema.sql + views.sql |
| **openapi.yaml** | REST API contract specification | models.yaml |
| **src/main/java-generated/** | All Java models, views, specs | models.yaml + specs.yaml |
| **generated-server/** | Complete Spring Boot REST server | models.yaml + specs.yaml |

---

## Getting Started: 5-Minute Quickstart

### The Fastest Path to a Working System

```bash
# 1. Generate everything from existing files (already in repo)
make all
# This runs: clean → generate (features-to-specs + proto + specs) → test → build
# Time: ~5 seconds
# Result: All 290+ tests pass

# 2. Run the rule engine demo
make run
# See rules evaluated on sample Users object
# Output shows which rules passed/failed

# 3. View test results
open build/reports/tests/test/index.html
# Detailed test report in your browser
```

**That's it!** You now have a working system. The models, rules, and specifications are already defined in the repo.

---

## Full Walkthrough: Starting from Scratch

### Step 1: Edit Your Database Schema (`schema.sql`)

This is where you define all your models. Edit `schema.sql`:

```sql
-- auth domain
CREATE TABLE users (
    id VARCHAR(50) NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    is_verified BOOLEAN NOT NULL,
    is_active BOOLEAN NOT NULL
);

-- social domain
CREATE TABLE posts (
    id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    like_count INT NOT NULL,
    is_public BOOLEAN NOT NULL,
    is_deleted BOOLEAN NOT NULL
);

-- admin domain
CREATE TABLE moderation_actions (
    id VARCHAR(50) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL
);
```

**Domains assigned from SQL comments (`-- <domain> domain` before each table group):**
- `users` → `dev.appget.auth.model.Users`
- `posts` → `dev.appget.social.model.Posts`
- `moderation_actions` → `dev.appget.admin.model.ModerationActions`

**Current domain breakdown** (14 models + 10 views across 3 domains):

| Domain | Models | Views |
|--------|--------|-------|
| **admin** | roles, user_roles, moderation_actions, company_settings | user_role_view, moderation_queue_view, company_health_view |
| **auth** | users, oauth_providers, oauth_tokens, api_keys, sessions | user_oauth_view, api_key_stats_view |
| **social** | posts, comments, likes, follows, feeds | post_detail_view, comment_detail_view, user_feed_view, user_stats_view, trending_posts_view |

### Step 2: Define Read Models (`views.sql` - Optional)

Create composite views for reporting/read-optimization:

```sql
-- social domain
CREATE VIEW post_detail_view AS
SELECT
    p.id AS post_id,
    p.content AS post_content,
    p.like_count AS like_count,
    u.username AS author_username,
    u.is_verified AS author_verified
FROM posts p
JOIN users u ON p.author_id = u.id;
```

Views generate Java view classes automatically. System resolves column types from source tables.

### Step 3: Parse Your Schema → Generate Intermediate YAML

```bash
make parse-schema
# INPUT:  schema.sql + views.sql
# OUTPUT: models.yaml (with all fields, types, domains)
```

**What happens**: SQLSchemaParser reads your SQL, parses tables/views, maps types to Java, generates models.yaml.

### Step 4: Generate Protobuf Models and Views

```bash
make generate-proto
# INPUT:  schema.sql + views.sql + specs.yaml (for rule embedding)
# OUTPUT: .proto files → protoc → build/generated/
#         ├── dev/appget/auth/model/Users.java (protobuf)
#         ├── dev/appget/social/model/Posts.java (protobuf)
#         ├── dev/appget/admin/model/ModerationActions.java (protobuf)
#         ├── dev/appget/social/view/PostDetailView.java (protobuf)
#         └── gRPC service stubs (8 services)
```

All models are protobuf `MessageOrBuilder` classes with Builder pattern.

### Step 5: Define Business Rules (`features/*.feature`)

This is where you write authorization and validation logic using human-friendly Gherkin syntax.

Edit `features/auth.feature`:

```gherkin
@domain:auth
Feature: Auth Domain Business Rules

  @target:users @blocking @rule:UserActivationCheck
  Scenario: User account must be active
    When is_active equals true
    Then status is "ACCOUNT_ACTIVE"
    But otherwise status is "ACCOUNT_INACTIVE"

  @target:users @rule:UserVerificationStatus
  Scenario: User can be verified badge holder
    When is_verified equals true
    Then status is "VERIFIED_USER"
    But otherwise status is "UNVERIFIED_USER"

  @target:users @blocking @rule:AdminAuthenticationRequired
  Scenario: User with admin role can manage system
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 3     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_active equals true
    Then status is "ADMIN_AUTHENTICATED"
    But otherwise status is "ADMIN_DENIED"

  @target:sessions @blocking @rule:SessionActivityCheck
  Scenario: Session must be active
    When is_active equals true
    Then status is "SESSION_ACTIVE"
    But otherwise status is "SESSION_EXPIRED"
```

**Gherkin Tags**:
- `@domain:auth` - Domain assignment (feature-level)
- `@target:users` - Target model/view name (snake_case plural, matches SQL table)
- `@rule:UserEmailValidation` - Rule name
- `@blocking` - Rule causes 422 rejection when unsatisfied
- `@view` - Target is a view (not a model)

**Operator Phrases** (natural language):
- `equals` → `==`, `does not equal` → `!=`
- `is greater than` → `>`, `is less than` → `<`
- `is at least` → `>=`, `is at most` → `<=`

**Rule Flow**:
1. Check `Given ... context requires:` metadata (if present) → if fails, return `else` status
2. Check `When` conditions on model/view fields → return `then` or `else` status

The system converts `.feature` files + `metadata.yaml` into `specs.yaml` automatically during the build.

### Step 6: Generate Specifications + Metadata Classes

```bash
make generate-specs
# INPUT:  specs.yaml + models.yaml
# OUTPUT: src/main/java-generated/
#         ├── dev/appget/specification/generated/ (Specification classes)
#         └── dev/appget/specification/context/ (Metadata POJOs)
```

### Step 7: Build Everything + Run Tests

```bash
make all
# Runs full pipeline:
#   1. clean (remove old builds)
#   2. features-to-specs (.feature + metadata → specs.yaml)
#   3. generate (proto + specs + registry + openapi)
#   4. test (run 290+ tests)
#   5. build (compile & package)
#
# Result: All 290+ tests passing
```

### Step 8: Run the Demo Rule Engine

```bash
make run
# Descriptor-driven evaluation: discovers all models with rules
# Shows which rules passed/failed with their status values
#
# Output:
# --- Rule Engine Evaluation (Descriptor-Based) ---
#
# Model: Users (7 rule(s))
#   Rule: UserEmailValidation           | Result: VALID_EMAIL
#   Rule: UserSuspensionCheck           | Result: ACTIVE
#   Rule: VerifiedUserRequirement       | Result: VERIFIED
#   Rule: UsernamePresence              | Result: USERNAME_PRESENT
#   Rule: UserAccountStatus             | Result: GOOD_STANDING
#   ...
#
# Model: ModerationActions (2 rule(s))
#   Rule: ModerationActionActive        | Result: ACTION_ENFORCED
#   Rule: ModerationAuthorizationCheck  | Result: MODERATION_AUTHORIZED
```

---

## When to Use What Command

### Development Workflow

```
You edit:              Command:                    Output:
──────────────────────────────────────────────────────────────
features/*.feature ──→ make features-to-specs ──→ specs.yaml
metadata.yaml     ──→ (included in features-to-specs)

schema.sql  ──→  make parse-schema  ──→  models.yaml
views.sql   ──→  (automatic in parse-schema)

(all above) ──→  make generate     ──→  protobuf classes + specs

All updated ──→  make test         ──→  290+ tests pass
```

### Quick Reference

| I Want To... | Use This | Then Run |
|---|---|---|
| See if everything works | `make all` | Nothing, tests run |
| After editing .feature files | `make features-to-specs && make generate && make test` | `make run` to see rules |
| After editing schema.sql | `make parse-schema && make generate && make test` | `make run` to see demo |
| Run only tests | `make test` | Repeat edits until pass |
| Generate REST API server | `make generate-server` | Check `generated-server/` |
| Start Spring Boot server | `make run-server` | Hit `http://localhost:8080` |
| Clean everything | `make clean` | Then `make all` |

---

## Generated Components: Where Things End Up

### Java Models (From schema.sql)

**Generated to**: `src/main/java-generated/dev/appget/*/model/`

```java
// Auto-generated via: schema.sql → .proto → protoc
// Protobuf message with Builder pattern
Users user = Users.newBuilder()
    .setUsername("alice")
    .setEmail("alice@example.com")
    .setIsVerified(true)
    .setIsSuspended(false)
    .build();
```

### Java Views (From views.sql)

**Generated to**: `src/main/java-generated/dev/appget/*/view/`

```java
// Auto-generated via: views.sql → .proto → protoc
// Protobuf message with Builder pattern
PostDetailView view = PostDetailView.newBuilder()
    .setPostId("post-1")
    .setPostContent("Hello world")
    .setLikeCount(42)
    .setAuthorUsername("alice")
    .setAuthorVerified(true)
    .build();
```

### Specification Classes (From specs.yaml)

**Generated to**: `src/main/java-generated/dev/appget/specification/generated/`

```java
// Auto-generated from: rules: [- name: UserEmailValidation ...]
public class UserEmailValidation implements Specification<Users> {
    public boolean isSatisfiedBy(Users target) {
        return !target.getEmail().isEmpty();
    }
}
```

### Spring Boot Server (From models.yaml + specs.yaml)

**Generated to**: `generated-server/dev/appget/server/`

```
generated-server/dev/appget/server/
├── Application.java                 (Spring Boot main class)
├── controller/                      (REST endpoints @RestController)
├── service/                         (Business logic @Service)
├── repository/                      (In-memory storage @Repository)
├── exception/                       (Error handling)
├── dto/                             (Request/response objects)
├── config/                          (MetadataExtractor for headers)
└── application.yaml                 (Spring Boot config)
```

---

## Spring Boot REST API Server

After running `make generate-server`, you get a complete REST API server with pre-compiled business rule enforcement.

### Generated REST Endpoints

```
POST   /users             - Create new user (validates rules)
GET    /users             - List all users
GET    /users/{id}        - Get specific user
PUT    /users/{id}        - Update user (validates rules)
DELETE /users/{id}        - Delete user

Same for: /sessions, /posts, /comments, /follows, /moderation-actions, /roles, /api-keys, etc.

# Views are read-only (GET only, no create/update/delete):
GET    /views/post-detail        - List post detail view entries
GET    /views/post-detail/{id}   - Get specific post detail view entry

Same for: /views/user-feed, /views/user-stats, /views/comment-detail,
          /views/trending-posts, /views/user-oauth, /views/api-key-stats,
          /views/user-role, /views/moderation-queue, /views/company-health
```

### Start the Server

```bash
make generate-server   # Generate Spring Boot code
make run-server        # Start server on http://localhost:8080
```

### Test an Endpoint

```bash
# Create user (will validate UserActivationCheck, UserVerificationStatus, etc.)
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "isVerified": true,
    "isActive": true
  }'

# Response:
# HTTP 201 Created
# {
#   "data": { ... },
#   "ruleResults": {
#     "outcomes": [
#       { "ruleName": "UserActivationCheck", "status": "ACCOUNT_ACTIVE" },
#       { "ruleName": "UserVerificationStatus", "status": "VERIFIED_USER" },
#       ...
#     ],
#     "hasFailures": false
#   }
# }
```

### Passing Metadata to Rules (Authorization)

Rules that check metadata (the `requires:` block) extract data from HTTP headers.
Headers follow the convention `X-{Category}-{Field-Name}`:

```bash
curl -X POST http://localhost:8080/moderation-actions \
  -H "Content-Type: application/json" \
  -H "X-Sso-Authenticated: true" \
  -H "X-Roles-Is-Admin: true" \
  -H "X-Roles-Role-Level: 5" \
  -d '{
    "reason": "Spam content",
    "isActive": true
  }'

# MetadataExtractor reads X-{Category}-{Field} headers
# Builds typed context POJOs (SsoContext, RolesContext, UserContext, OauthContext, ApiContext)
# Rules check both metadata AND model fields
```

### Blocking vs Informational Rules

Rules can be marked `@blocking` in `.feature` files. This affects HTTP responses:

- **Blocking rules** (`@blocking` tag): If unsatisfied, the request returns **422 Unprocessable Entity**
- **Informational rules** (default): Always reported in outcomes but never block the request

```gherkin
  # Blocking: causes 422 if user is inactive
  @target:users @blocking @rule:UserActivationCheck
  Scenario: User account must be active
    When is_active equals true
    Then status is "ACCOUNT_ACTIVE"
    But otherwise status is "ACCOUNT_INACTIVE"

  # Informational: reported but doesn't block
  @target:users @rule:UserVerificationStatus
  Scenario: User can be verified badge holder
    When is_verified equals true
    Then status is "VERIFIED_USER"
    But otherwise status is "UNVERIFIED_USER"
```

---

## System Architecture in Depth

### How Data Flows Through the System

```
0. You write business rules in Gherkin .feature files
   ↓
   FeatureToSpecsConverter parses features/*.feature + metadata.yaml
   ↓
   Generates specs.yaml (intermediate representation, git-ignored)
   ↓
1. You write SQL schema
   ↓
2. SQLSchemaParser reads schema.sql + views.sql
   - Parses table definitions
   - Resolves column types
   - Maps tables to domains
   - Resolves view column types from source tables
   ↓
3. Generates models.yaml
   - Intermediate representation
   - All models with fields + types
   - All views with fields + types
   ↓
4. ModelsToProtoConverter reads models.yaml
   - Creates .proto files per domain (schema only — no rules in proto)
   - protoc compiles .proto → Java protobuf classes + gRPC stubs
   - Output in build/generated/
   ↓
5. SpecificationGenerator reads specs.yaml + models.yaml
   - Creates Specification class for each rule
   - Creates metadata POJO classes (SsoContext, RolesContext, etc)
   ↓
6. ProtoOpenAPIGenerator reads .proto files
   - Creates OpenAPI 3.0.0 REST specification (full CRUD, security)
   ↓
7. AppServerGenerator reads models.yaml + specs.yaml
   - Creates Spring Boot REST API
   - Controllers (HTTP endpoints)
   - Services (business logic with rule evaluation)
   - Repositories (storage)
   - RuleService uses pre-compiled spec classes directly (no runtime YAML)
   - MetadataExtractor reads typed headers into context POJOs
   ↓
8. At runtime
   - RuleService evaluates pre-compiled specification classes
   - Blocking rules cause 422 rejection; informational rules are reported only
   - MetadataExtractor builds typed context POJOs from HTTP headers
   - Rules are evaluated when creating/updating entities
```

### Type Safety: SQL → Java → REST

Every field maps consistently through the entire pipeline:

```
SQL Type         Java Type        OpenAPI Type    TypeScript Type
─────────────────────────────────────────────────────────────────
VARCHAR(100)     String           string          string
INT              int (Integer*)   integer         number
DECIMAL(15,2)    BigDecimal       number          number
DATE             LocalDate        string(date)    Date
TIMESTAMP        LocalDateTime    string(date-time) Date
BOOLEAN          boolean          boolean         boolean

* Integer if nullable in SQL, int if NOT NULL
```

---

## Logging

All non-generated Java classes include **Log4j2 logging**:

- **INFO level** (default): File loading, rule evaluation results, important milestones
- **DEBUG level**: Method entry/exit, detailed execution flow (useful for troubleshooting)
- **Configuration**: `src/main/resources/log4j2.properties`

**Adjust logging verbosity**:

```properties
# Development (see everything)
logger.dev_appget_codegen.level = DEBUG

# Production (see only important events)
logger.dev_appget_codegen.level = INFO
```

---

## Testing & Quality Assurance

### Why Testing Matters

Every time you change `schema.sql` or `specs.yaml`, the system regenerates Java code. Tests verify:

- ✓ Models generated correctly from schema
- ✓ Rules evaluated correctly
- ✓ Type mappings are correct
- ✓ Views resolved properly
- ✓ REST endpoint specs are valid

### Over 290 Comprehensive Tests

The system includes over 290 unit tests across 16 test suites:

| Suite | Count | What It Tests |
|-------|-------|---|
| Java Type Registry | 44 | Type mappings: SQL → Java, Proto, OpenAPI; nullability rules |
| Code Gen Utils | 28 | Shared code generation utilities |
| Java Utils | 28 | Java-specific utility functions, name conversions |
| Feature To Specs Converter | 28 | Gherkin parsing, condition extraction, YAML generation, metadata validation |
| Proto-First OpenAPI Generator | 23 | Proto-first OpenAPI, CRUD, security, type mapping |
| Specification Patterns | 21 | All comparison operators, edge cases |
| Schema To Proto Converter | 18 | Proto generation, field mapping, services |
| App Server Generator | 17 | RuleService bridge, MetadataExtractor, blocking logic |
| Conformance | 16 | Proto file output matches expected golden files |
| Rule Engine | 15 | Rule evaluation, metadata, compound logic, type mismatch guard |
| Specification Generator | 13 | Rule generation, metadata, views |
| Descriptor Registry | 9 | Model discovery, lookup, field descriptors |
| gRPC Service Stubs | 7 | Service existence, CRUD method descriptors |
| Test Data Builder | 6 | DynamicMessage generation, default values |
| Compound Specifications | 6 | AND/OR logic combinations |
| Metadata Context | 5 | Authorization metadata storage |

### Run Tests

```bash
make test                # Run all 290+ tests (expect ~2s)
make all                 # Full pipeline (features → generate → test → build)
make clean && make test  # Fresh run
```

**All tests pass** → Your code is ready to use. **Some fail** → Check error messages, fix the issue, re-run.

### View Test Results

After running tests, detailed HTML report:

```bash
open build/reports/tests/test/index.html
```

Shows:
- Which test suites passed/failed
- Execution time per test
- Full stack traces for failures

### Performance Targets

| Task | Time |
|------|------|
| `make test` | ~2s (290+ tests) |
| `make all` | ~5-6s (full pipeline) |
| `make run` | ~1s (demo execution) |
| `make generate-server` | ~1s (server generation) |

## All Make Commands Reference

### Everyday Commands

| Command | When to Use | What It Does |
|---------|---|---|
| `make all` | After editing ANY file | Full pipeline: clean → generate → test → build |
| `make test` | To verify your changes | Run all 290+ tests (takes ~2s) |
| `make run` | To see rules in action | Build + execute demo rule engine |

### Code Generation Commands

| Command | When to Use | What It Does |
|---------|---|---|
| `make features-to-specs` | After editing .feature or metadata.yaml | Convert Gherkin → specs.yaml |
| `make parse-schema` | After editing schema.sql or views.sql | Parse SQL → Generate models.yaml |
| `make generate-proto` | After editing schema.sql | Protobuf models → build/generated/ |
| `make generate-specs` | After editing .feature files | Specification classes + metadata |
| `make generate-openapi` | After editing schema.sql | REST API specification → openapi.yaml (proto-first) |
| `make generate` | After editing schema, features, or metadata | All generation (features-to-specs + protoc + specs + openapi) |
| `make generate-server` | When ready for REST API | Spring Boot server → generated-server/ |

### Spring Boot Server Commands

| Command | What It Does |
|---------|---|
| `make generate-server` | Generate complete Spring Boot REST API server |
| `make run-server` | Build + start Spring Boot on port 8080 |

### System Commands

| Command | What It Does |
|---------|---|
| `make clean` | Remove all build artifacts + generated code |
| `make build` | Compile all code + create JAR |
| `make help` | Show all available commands |

### Typical Development Cycle

```bash
# 1. Edit your files
vim schema.sql
vim features/auth.feature

# 2. Regenerate + test
make all
# Output: Runs all steps, all 290+ tests should pass

# 3. See rules in action
make run
# Demo shows Users evaluated against all rules

# 4. When ready for REST API
make generate-server
make run-server
# Server runs on http://localhost:8080

# 5. Test an endpoint
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","isVerified":true,"isSuspended":false}'
```

## Key Features Explained

### 1. SQL Schema-First

**Your database schema defines everything.** No manual type definitions.

```sql
-- auth domain
CREATE TABLE users (
    id VARCHAR(50) NOT NULL,       -- String id
    username VARCHAR(100) NOT NULL, -- String username
    email VARCHAR(255) NOT NULL,    -- String email
    is_verified BOOLEAN NOT NULL,   -- boolean isVerified
    is_active BOOLEAN NOT NULL      -- boolean isActive
);
```

✓ Multi-dialect SQL support (MySQL, PostgreSQL, SQLite, Oracle, MSSQL)
✓ NOT NULL constraints honored (primitives stay primitive, nullable fields wrapped)
✓ Automatic snake_case → camelCase conversion

### 2. SQL Views (Composite Read Models)

**Define read-optimized views for reporting.** System auto-resolves column types.

```sql
-- social domain
CREATE VIEW post_detail_view AS
SELECT
    p.id AS post_id,
    p.like_count AS like_count,
    u.username AS author_username,
    u.is_verified AS author_verified
FROM posts p
JOIN users u ON p.author_id = u.id;
```

Generated as Java class:
```java
// PostDetailView (protobuf MessageOrBuilder)
public int getLikeCount()         // ✓ Type resolved from posts.like_count
public String getAuthorUsername() // ✓ Type resolved from users.username
public boolean getAuthorVerified()// ✓ Type resolved from users.is_verified
```

✓ View classes in `view/` subpackage
✓ Views can be targeted by rules (just like models)

### 3. Business Rules with Compound Logic

**Write rules in YAML. System enforces them automatically.**

```yaml
- name: HighEngagementPost
  conditions:
    operator: AND                # All must be true
    clauses:
      - field: like_count >= 1000
      - field: is_public == true
  then:
    status: "HIGH_ENGAGEMENT"
```

✓ Simple conditions (single field checks)
✓ Compound conditions (AND/OR multiple fields)
✓ Rules evaluated automatically on create/update
✓ Rule failures return specific status values

### 4. Metadata-Aware Authorization

**Rules can require caller context (roles, SSO, etc.) before evaluating model fields.**

Metadata categories are **cross-cutting concerns** — they represent "who is making this request?" context, not database tables. They have no corresponding tables in `schema.sql`.

**In a `.feature` file:**
```gherkin
@target:users @blocking @rule:AdminAuthenticationRequired
Scenario: User with admin role can manage system
  Given roles context requires:
    | field     | operator | value |
    | roleLevel | >=       | 3     |
  And sso context requires:
    | field         | operator | value |
    | authenticated | ==       | true  |
  When is_active equals true
  Then status is "ADMIN_AUTHENTICATED"
  But otherwise status is "ADMIN_DENIED"
```

**What happens at runtime:**
1. `MetadataExtractor` reads HTTP headers (`X-Roles-Role-Level`, `X-Sso-Authenticated`) into typed POJOs
2. Rule checks `roles.roleLevel >= 3` and `sso.authenticated == true` first
3. Only if metadata passes, evaluates `users.is_active == true` on the model
4. Returns `"ADMIN_AUTHENTICATED"` or `"ADMIN_DENIED"`

**HTTP headers follow the convention** `X-{Category}-{Field-Name}`:
```bash
curl -X POST http://localhost:8080/users \
  -H "X-Sso-Authenticated: true" \
  -H "X-Roles-Role-Level: 5" \
  -H "X-Roles-Is-Admin: true" \
  -d '{"username":"alice","email":"alice@example.com","isActive":true}'
```

### 5. Metadata Registry (Toggle Model)

`metadata.yaml` is a **curated registry** of 14 built-in categories, each with an `enabled: true/false` toggle. Only enabled categories flow through the pipeline. Categories are independent of `schema.sql` — they represent request-scoped context.

**Current categories** (3 pre-enabled by default, 2 additionally enabled for this project):

| Category | Enabled | Purpose | Fields |
|----------|---------|---------|--------|
| **sso** | yes (default) | Single sign-on session state | authenticated, sessionId, provider |
| **user** | yes (default) | Authenticated user identity | userId, email, username |
| **roles** | yes (default) | Role-based access control | roleName, roleLevel, isAdmin |
| **oauth** | yes (project) | OAuth 2.0 token context | accessToken, scope, expiresIn, provider |
| **api** | yes (project) | API key authentication | apiKey, rateLimitTier, isActive |
| jwt | no | JWT token claims | subject, issuer, audience, expiresAt |
| mfa | no | Multi-factor auth state | verified, method |
| permissions | no | Fine-grained permissions | permissionName, resourceType, canRead, canWrite |
| tenant | no | Multi-tenant isolation | tenantId, tenantName, plan, isActive |
| billing | no | Billing/subscription | customerId, plan, isActive, billingCycle |
| payments | no | Payment processing | paymentMethodId, provider, currency, isVerified |
| invoice | no | Invoice records | invoiceId, status, amount, isPaid |
| audit | no | Request audit trail | requestId, sourceIp, userAgent |
| geo | no | Geolocation context | country, region, timezone |

**To enable a built-in category**: Set `enabled: true` in `metadata.yaml`.

**To add a custom category**: Add a new entry at the bottom of `metadata.yaml`:
```yaml
  bitcoin:
    enabled: true
    description: "Bitcoin node and wallet context"
    fields:
      - name: address
        type: String
      - name: wallet
        type: String
```

**Build-time validation**: The pipeline validates all `Given <category> context requires:` references:
- Unknown category → build error
- Disabled category → build error with guidance to enable
- Unknown field in enabled category → build error

**How metadata flows through the pipeline:**

```
metadata.yaml                    features/*.feature
(14 categories,                  (Given roles context requires:)
 5 enabled)                      (And sso context requires:)
    │                                  │
    └──────────┬───────────────────────┘
               ▼
    FeatureToSpecsConverter
    (filters enabled-only, validates references)
               │
               ▼
           specs.yaml
    (metadata section: 5 categories)
    (rules with requires: blocks)
               │
       ┌───────┴───────────┐
       ▼                   ▼
SpecificationGenerator  AppServerGenerator
       │                   │
       ▼                   ▼
  5 Context POJOs     MetadataExtractor
  (SsoContext.java,   (reads X-{Category}-{Field}
   RolesContext.java,  HTTP headers into POJOs)
   UserContext.java,        │
   OauthContext.java,       ▼
   ApiContext.java)    RuleService
       │              (evaluates specs with
       ▼               metadata context)
  33 Spec classes
  (AdminAuthenticationRequired
   checks roles + sso before
   evaluating users.is_active)
```

### 6. Multi-Domain Organization

**Organize models by business domain. System auto-creates packages.**

```
schema.sql (with -- <domain> domain comments):
  admin:  roles, user_roles, moderation_actions, company_settings
  auth:   users, oauth_providers, oauth_tokens, api_keys, sessions
  social: posts, comments, likes, follows, feeds
```

✓ Logical separation of concerns
✓ Prevents naming conflicts
✓ Each domain gets own package namespace

### 7. Full REST API Generation

**Generate complete Spring Boot REST server from your schema.**

```bash
make generate-server
# Generates:
#   Controllers (REST endpoints)
#   Services (business logic + rule validation)
#   Repositories (in-memory storage)
#   DTOs (request/response objects)
#   Exception handling
```

✓ CRUD endpoints for all models
✓ Rules validated on create/update
✓ Metadata extracted from HTTP headers
✓ Proper HTTP status codes (201 Created, 422 Unprocessable Entity, etc)

### 8. Type Safety Everywhere

**Every type maps consistently from SQL → Java → REST → TypeScript.**

```
SQL                Java              OpenAPI          TypeScript
────────────────────────────────────────────────────────────────
VARCHAR(100)       String            string           string
INT NOT NULL       int               integer          number
DECIMAL(15,2)      BigDecimal        number           number
DATE               LocalDate         string(date)     Date
```

✓ No type mismatches between layers
✓ TypeScript client can be auto-generated from OpenAPI spec
✓ Full end-to-end type safety

### Comprehensive Testing

All 290+ tests pass automatically:
```
✓ Gherkin .feature file parsing and specs.yaml generation
✓ Proto generation correctness (schema → .proto → Java)
✓ Descriptor-based rule evaluation
✓ Type mapping validation
✓ View resolution
✓ REST endpoint specs (proto-first OpenAPI)
✓ gRPC service stubs
✓ Descriptor registry and test data builder
```

Run: `make test` to verify your changes at any time.

---

## File Structure: Where Everything Is

### Files YOU Edit (Commit These to Git)

```
appget.dev/java/
├── features/
│   ├── admin.feature       🖊️  Admin domain business rules (Gherkin)
│   ├── auth.feature        🖊️  Auth domain business rules (Gherkin)
│   └── social.feature      🖊️  Social domain business rules (Gherkin)
├── metadata.yaml           🖊️  Curated metadata registry (14 categories, toggle model)
├── schema.sql              🖊️  Your database schema (tables)
├── views.sql               🖊️  Your read models (views)
├── build.gradle            🖊️  Java build configuration
├── Makefile                🖊️  Build commands
└── src/main/java/dev/appget/
    ├── codegen/            📝  Code generators (do not modify)
    ├── model/
    │   └── Rule.java       📝  Rule engine (do not modify)
    ├── specification/      📝  Rule evaluation logic (do not modify)
    ├── util/               📝  DescriptorRegistry, DefaultDataBuilder
    └── RuleEngine.java     📝  Descriptor-driven demo app (do not modify)
```

### Files SYSTEM GENERATES (Don't Edit - Git-Ignored)

```
appget.dev/java/
├── specs.yaml              ✨  Auto-generated from features/*.feature + metadata.yaml
├── models.yaml             ✨  Auto-generated from schema.sql
├── openapi.yaml            ✨  Auto-generated REST spec
├── src/main/java-generated/✨  All generated Java models, specs
│   └── dev/appget/
│       ├── auth/model/     (Users, Sessions, OauthProviders, OauthTokens, ApiKeys)
│       ├── social/model/   (Posts, Comments, Likes, Follows, Feeds)
│       ├── social/view/    (PostDetailView, UserFeedView, TrendingPostsView, etc)
│       ├── admin/model/    (Roles, UserRoles, ModerationActions, CompanySettings)
│       └── specification/
│           ├── generated/  (Specification classes)
│           └── context/    (Metadata POJOs)
└── generated-server/       ✨  Spring Boot REST API
    └── dev/appget/server/
        ├── controller/     (REST endpoints)
        ├── service/        (Business logic)
        ├── repository/     (Storage)
        ├── dto/            (Request/response)
        └── exception/      (Error handling)
```

### Test Files (Don't Modify)

```
src/test/java/dev/appget/
├── codegen/
│   ├── AppServerGeneratorTest.java          (17 tests)
│   ├── CodeGenUtilsTest.java                (28 tests)
│   ├── FeatureToSpecsConverterTest.java     (24 tests)
│   ├── JavaTypeRegistryTest.java            (44 tests)
│   ├── JavaUtilsTest.java                   (28 tests)
│   ├── ModelsToProtoConverterTest.java      (18 tests)
│   ├── ProtoOpenAPIGeneratorTest.java       (23 tests)
│   └── SpecificationGeneratorTest.java      (13 tests)
├── conformance/
│   └── ConformanceTest.java                 (16 tests)
├── model/
│   └── RuleTest.java                        (15 tests)
├── service/
│   └── GrpcServiceTest.java                 (7 tests)
├── specification/
│   ├── CompoundSpecificationTest.java        (6 tests)
│   ├── MetadataContextTest.java              (5 tests)
│   └── SpecificationTest.java               (21 tests)
└── util/
    ├── DescriptorRegistryTest.java           (9 tests)
    └── DefaultDataBuilderTest.java           (6 tests)
```

---

## Rule Syntax: Comparison Operators

Define rules in `.feature` files using natural language phrases (`is greater than`, `equals`, etc.). The generated `specs.yaml` uses these operators in conditions:

### Numbers (int, long, double, BigDecimal)
```yaml
conditions:
  - field: age
    operator: ">"    # Greater than
    value: 18

  - field: salary
    operator: ">="   # Greater than or equal
    value: 50000

  - field: years
    operator: "<"    # Less than
    value: 5

  - field: bonus
    operator: "<="   # Less than or equal
    value: 10000

  - field: level
    operator: "=="   # Equals
    value: 3

  - field: status
    operator: "!="   # Not equals
    value: "INACTIVE"
```

### Strings
```yaml
conditions:
  - field: name
    operator: "=="   # Equals
    value: "Manager"

  - field: role
    operator: "!="   # Not equals
    value: "Guest"
```

### Booleans
```yaml
conditions:
  - field: active
    operator: "=="   # Must be true
    value: true

  - field: verified
    operator: "!="   # Must be false
    value: false
```

---

## Troubleshooting

### "FileNotFoundException: schema.sql"
**Problem**: System can't find your database schema
**Solution**: Create `schema.sql` at project root with table definitions

### "Tests are failing"
**Problem**: Some of the 290+ tests failed
**Solution**:
```bash
# 1. Check the error message
# 2. Look at what file caused it (schema.sql, specs.yaml, etc)
# 3. Fix that file
# 4. Run make all again
```

### "Generated code looks wrong"
**Problem**: Models generated incorrectly
**Solution**:
```bash
# Clean and regenerate everything
make clean
make all
```

### "Can't find class Users"
**Problem**: Java model not found
**Solution**:
```bash
# Make sure table exists in schema.sql
# Make sure you ran: make parse-schema && make generate
make all
```

### "Rule is not being evaluated"
**Problem**: Business rule not running
**Solution**:
1. Check `features/*.feature` - does scenario exist with correct tags?
2. Check rule `@target` tag - does model exist?
3. Check field names - match your model exactly?
4. Run `make features-to-specs && make generate-specs` to regenerate
5. Run `make run` to see demo

### "View column type is wrong"
**Problem**: View field has wrong Java type
**Solution**:
1. Check source table in `schema.sql` - is column type correct?
2. Check view SQL - is alias correct?
3. Make sure source table defined BEFORE view in SQL file
4. Run `make parse-schema` to re-parse

### Spring Boot server won't start
**Problem**: `make run-server` fails
**Solution**:
```bash
# Make sure server is generated
make generate-server

# Check generated code exists
ls -la generated-server/dev/appget/server/

# If missing, regenerate everything
make clean && make generate-server
```

### "Port 8080 already in use"
**Problem**: Another process using the port
**Solution**:
```bash
# Kill the other process
lsof -i :8080
kill -9 <PID>

# Or use different port
# Edit generated-server/src/main/resources/application.yaml:
# server:
#   port: 8081
```

---

## Dependencies & Environment

### Required

- **Java**: 25+ (OpenJDK 25 or newer)
- **Gradle**: 9.3.1+ (automatic wrapper download)

### Included (via build.gradle)

- **Gherkin**: 38.0.0 (Cucumber Gherkin parser for `.feature` files)
- **Protobuf**: 3.25.3 (protoc compiler + Java runtime)
- **gRPC-Java**: 1.62.2 (gRPC service stubs)
- **Lombok**: 1.18.38+ (metadata POJOs, Java 25 compatible)
- **JSQLParser**: 5.3 (multi-dialect SQL support)
- **SnakeYAML**: 2.2 (YAML parsing)
- **Log4j2**: 2.23.1 (logging)
- **JUnit 5**: 5.11.3 (testing)

### Check Your Setup

```bash
# Check Java version
java -version
# Output should show 25 or higher

# Check Gradle
./gradlew --version
# Should work (auto-installs if needed)

# Run a quick test
make test
# Should show 290+ tests passing
```

---

## Additional Documentation

For more details on implementation and architecture:

- **CLAUDE.md** - Technical implementation guide for Claude Code
- **PIPELINE.md** - Detailed pipeline architecture
- **ai.chat** - Original design discussion and concepts

---

## Quick Links

- [Gradle Documentation](https://gradle.org/)
- [Lombok Documentation](https://projectlombok.org/)
- [JSQLParser](https://github.com/JSQLParser/JSqlParser)
- [OpenAPI 3.0.0](https://spec.openapis.org/oas/v3.0.0)
- [Spring Boot](https://spring.io/projects/spring-boot)

---

**Last Updated**: 2026-02-27
**Status**: Production Ready
**Test Coverage**: 290+ tests, 100% passing (16 suites)
**Pipeline**: Gherkin → specs.yaml → Schema → Proto → Protoc → Specs → REST API → gRPC → Fully Typed
**Metadata**: 14 built-in categories with toggle model (5 enabled for this project)
**Java Version**: 25+ required
