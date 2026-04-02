---
name: prompt-zero
description: "Load this skill when translating a high-level business description ('build me a Twitter-like app') into appget source files. Contains the elicitation interview template, abstract rule format, metadata category inference rules, common app archetypes, dependency ordering sequence, and the approval gate template. Essential for any 'creating from scratch' workflow."
allowed-tools: Read, Glob, Grep
---

# Prompt-Zero: From Business Description to Structured Domain

The transition from a human's natural-language business description to appget's four source files (schema.sql, views.sql, features/*.feature, metadata.yaml). This skill provides the reference material for that transition — elicitation questions, intermediate representations, inference heuristics, and approval templates.

---

## CRITICAL: Elicit Before Decomposing

A business description like "build me a Twitter-like app" contains implicit assumptions about actors, operations, authorization models, compliance requirements, multi-tenancy, and scale. Jumping directly to SQL tables and Gherkin rules without surfacing these assumptions produces incomplete or incorrect source files.

The elicitation interview (next section) surfaces these assumptions before any decomposition begins. Skipping it is the single most common source of rework in domain design.

**Indicators that elicitation was skipped**:
- Missing authorization rules for operations that clearly need them
- No views for cross-table operations the user described
- Metadata categories enabled without business justification
- Domains that overlap or duplicate each other
- Rules that reference fields not present in the schema

---

## Elicitation Template

Seven structured questions that surface the assumptions implicit in a business description. Each includes its purpose annotation — the type of source-file decision it informs.

### 1. Actors and Roles

> Who uses this system, and what distinguishes them?

**Purpose**: Informs `users` table design, `roles`/`permissions` metadata categories, and blocking authorization rules.

**Probe for**: Anonymous vs authenticated users, admin tiers, system/service accounts, external partners, role hierarchy depth.

### 2. Core Operations

> What are the 5-10 most important things a user does in this system?

**Purpose**: Informs table design (what entities exist), view design (which operations span multiple tables), and rule coverage (which operations need validation or authorization).

**Probe for**: CRUD operations, transactional multi-step flows, read-heavy vs write-heavy patterns, batch operations, real-time vs async.

### 3. Authorization Model

> How is access controlled? What determines whether someone can perform an operation?

**Purpose**: Informs metadata.yaml category selection, `Given ... context requires:` steps in feature files, and blocking rule design.

**Probe for**: Authentication method (SSO, OAuth, API key, JWT), role-based vs attribute-based access, resource ownership ("users can only edit their own posts"), tenant isolation, API rate limiting.

### 4. Compliance and Constraints

> Are there regulatory, legal, or business policy constraints?

**Purpose**: Informs blocking rules (hard constraints that prevent operations), audit metadata category, and field-level validation rules.

**Probe for**: Age verification, data residency (geo category), financial regulations (PCI-DSS → payments category), content moderation policies, data retention requirements.

### 5. Multi-Tenancy

> Is this a single-tenant or multi-tenant system? How are tenants isolated?

**Purpose**: Informs `tenant` metadata category enablement, tenant_id foreign keys in schema, and tenant-scoped authorization rules.

**Probe for**: Shared database vs per-tenant isolation, tenant-level feature flags, cross-tenant data visibility, tenant billing/subscription tiers.

### 6. Integrations

> What external systems does this interact with?

**Purpose**: Informs metadata categories (oauth for third-party auth, payments for payment processors), API-facing views, and integration-specific validation rules.

**Probe for**: Payment gateways, identity providers, notification services, analytics platforms, third-party APIs, webhook consumers.

### 7. Scale and Performance

> What are the expected traffic patterns and data volumes?

**Purpose**: Informs view design (aggregate views for dashboards vs real-time views for feeds), field type choices (INT vs BIGINT for counters), and whether rate-limiting rules (api category) are needed.

**Probe for**: Read:write ratio, concurrent users, data growth rate, reporting/analytics needs, real-time requirements.

---

## Abstract Rule Format

An intermediate representation between natural language and Gherkin. Each abstract rule captures the essential structure before committing to exact field names and SQL types.

### Format

```
RULE: <RuleName>
  TARGET: <entity or view>
  TYPE: blocking | informational
  COMPLEXITY: simple | compound-AND | compound-OR
  METADATA: <category.field> [, ...] | none
  CONDITIONS:
    - <field_name> (<inferred_sql_type>) <operator> <value>
    [- <field_name> (<inferred_sql_type>) <operator> <value>]
  STATUS PAIR: <positive> / <negative>
```

### Fields

| Field | Description |
|-------|-------------|
| `TARGET` | The entity or view this rule applies to (maps to `@target` tag; view targets also require a `@view` tag in Gherkin) |
| `TYPE` | blocking = prevents operation; informational = classifies/flags |
| `COMPLEXITY` | simple = one condition; compound-AND = all must match; compound-OR = any match |
| `METADATA` | Dot-notation references to metadata categories and fields (e.g., `roles.isAdmin`), or `none` |
| `CONDITIONS` | Each condition includes the inferred SQL type in parentheses for later schema verification. Metadata conditions use `(metadata)` as the type prefix. Every rule must include at least one non-metadata condition (maps to the Gherkin `When` clause) |
| `STATUS PAIR` | The Then / But otherwise outcome pair |

### Worked Example: Social Platform

```
RULE: AuthenticatedNewPost
  TARGET: posts
  TYPE: blocking
  COMPLEXITY: compound-AND
  METADATA: sso.authenticated
  CONDITIONS:
    - (metadata) authenticated equals true
    - content (TEXT) does not equal ""
  STATUS PAIR: AUTHORIZED / UNAUTHENTICATED

RULE: PostVisibilityCheck
  TARGET: posts
  TYPE: informational
  COMPLEXITY: simple
  METADATA: none
  CONDITIONS:
    - is_public (BOOLEAN) equals true
  STATUS PAIR: VISIBLE / RESTRICTED

RULE: VerifiedUserContentPromotion
  TARGET: posts
  TYPE: informational
  COMPLEXITY: compound-AND
  METADATA: none
  CONDITIONS:
    - is_public (BOOLEAN) equals true
    - like_count (INT) is greater than 100
  STATUS PAIR: PROMOTED / STANDARD

RULE: AdminContentModeration
  TARGET: posts
  TYPE: blocking
  COMPLEXITY: compound-AND
  METADATA: roles.isAdmin, sso.authenticated
  CONDITIONS:
    - (metadata) isAdmin equals true
    - (metadata) authenticated equals true
    - is_public (BOOLEAN) equals true
  STATUS PAIR: MODERATION_AUTHORIZED / INSUFFICIENT_PRIVILEGES
```

**Validation step**: Before converting abstract rules to Gherkin, verify each condition's inferred SQL type against the actual schema.sql column definition. If a condition references a TIMESTAMP/DATE/DATETIME column, redesign the rule to use a boolean flag or numeric field.

---

## Category Inference Rules

Mapping from keywords and concepts in a business description to the 14 built-in metadata.yaml categories. Use this table to determine which categories to enable.

### Keyword-to-Category Mapping

| Category | Keywords / Concepts | Enable When |
|----------|-------------------|-------------|
| `sso` | login, session, sign-in, authentication, logged in | Any app with authenticated users |
| `user` | user profile, identity, email, username, account | Any app with user accounts |
| `oauth` | third-party login, Google sign-in, social auth, OAuth, token exchange | External identity provider integration |
| `jwt` | JWT, token claims, stateless auth, bearer token, token validation | Stateless token-based authentication |
| `mfa` | two-factor, 2FA, TOTP, authenticator app, verification code | Multi-factor authentication requirement |
| `roles` | admin, moderator, role, permission level, staff, superuser | Role hierarchy or admin-vs-user distinction |
| `permissions` | fine-grained access, RBAC, can read, can write, resource-level | Per-resource or per-action permission checks |
| `api` | API key, rate limit, developer portal, public API, third-party access | External API consumers or rate limiting |
| `tenant` | multi-tenant, workspace, organization, team, white-label | Multiple isolated customer environments |
| `billing` | subscription, plan, freemium, upgrade, billing cycle | Subscription or plan-based feature gating |
| `payments` | payment, checkout, credit card, Stripe, payment method | Payment processing integration |
| `invoice` | invoice, receipt, billing record, charge, statement | Invoice generation or billing records |
| `audit` | audit log, compliance trail, request tracking, who did what | Regulatory compliance or activity logging |
| `geo` | geolocation, country restriction, region, timezone, data residency | Location-based rules or data residency |

### Inference Priority Rules

When multiple categories could apply, use these ordering heuristics:

1. **`sso`, `user`, and `roles` are almost always enabled** — any app with user accounts needs authentication context, user identity, and role-based access (all three are pre-enabled in metadata.yaml)
2. **`roles` before `permissions`** — start with role-based access; add fine-grained permissions only if the business description mentions per-resource or per-action controls
3. **`oauth` before `jwt`** — if the description mentions third-party login, enable oauth; add jwt only if stateless token validation is explicitly needed
4. **Commerce categories are independent** — `billing` (subscription management), `payments` (transaction processing), and `invoice` (billing records) serve different purposes; enable only the ones the business description actually references
5. **`audit` and `geo` are opt-in** — enable only when compliance, regulatory, or location-based requirements are explicitly stated

### Custom Categories

When a business description references authorization context that does not fit any built-in category, define a custom category at the bottom of metadata.yaml using the same format:

```yaml
# Example: Bitcoin/blockchain context
bitcoin:
  enabled: true
  description: "Bitcoin node and wallet context"
  fields:
    - name: address
      type: String
    - name: wallet
      type: String
```

Custom categories follow the same pipeline rules as built-in ones — they generate context POJOs, header extraction, and can be referenced in `Given ... context requires:` steps.

---

## Common App Archetypes

Eight common application patterns with their typical domain decomposition, metadata categories, view patterns, and rule characteristics.

### 1. SaaS Platform

- **Typical domains**: auth, billing, tenants, core-product, admin
- **Default enabled categories**: sso, user, roles, tenant, billing
- **Typical views**: tenant_usage_view (aggregate), user_subscription_view (JOIN), feature_access_view (JOIN)
- **Key rules**: Tenant isolation (blocking), subscription tier gating (blocking), usage limits (informational)

### 2. Social Platform

- **Typical domains**: auth, social, content, moderation, notifications
- **Default enabled categories**: sso, user, roles, oauth
- **Typical views**: post_detail_view (JOIN), user_feed_view (JOIN), user_stats_view (aggregate), follower_list_view (JOIN)
- **Key rules**: Authenticated operations (blocking), content visibility (informational), verified user privileges (informational), content moderation (blocking)

### 3. E-Commerce

- **Typical domains**: auth, catalog, orders, payments, inventory, shipping
- **Default enabled categories**: sso, user, roles, payments, billing, invoice
- **Typical views**: order_summary_view (subquery), product_detail_view (JOIN), cart_total_view (aggregate), revenue_report_view (aggregate)
- **Key rules**: Order validation (blocking), payment verification (blocking), inventory checks (blocking), pricing tiers (informational)

### 4. Developer / API Platform

- **Typical domains**: auth, api-management, billing, analytics
- **Default enabled categories**: sso, user, api, billing, roles
- **Typical views**: api_usage_view (aggregate), developer_dashboard_view (JOIN + aggregate), rate_limit_status_view (JOIN)
- **Key rules**: API key validation (blocking), rate limiting (blocking), usage tier classification (informational)

### 5. Content / Media Platform

- **Typical domains**: auth, content, media, monetization, moderation
- **Default enabled categories**: sso, user, roles, oauth
- **Typical views**: content_feed_view (JOIN), creator_stats_view (aggregate), trending_content_view (aggregate + JOIN)
- **Key rules**: Content ownership (blocking), publication status (informational), monetization eligibility (informational), content policy (blocking)

### 6. Healthcare / Regulated

- **Typical domains**: auth, patients, clinical, compliance, billing
- **Default enabled categories**: sso, user, roles, permissions, audit, geo
- **Typical views**: patient_record_view (JOIN), audit_trail_view (JOIN), compliance_report_view (aggregate)
- **Key rules**: Access authorization (blocking), data residency (blocking), audit logging (informational), consent verification (blocking)

### 7. Internal Tools

- **Typical domains**: auth, admin, workflows, reporting
- **Default enabled categories**: sso, user, roles, permissions
- **Typical views**: workflow_status_view (JOIN), team_activity_view (aggregate), admin_dashboard_view (aggregate + JOIN)
- **Key rules**: Role-based access (blocking), workflow state transitions (blocking), activity classification (informational)

### 8. IoT / Device Platform

- **Typical domains**: auth, devices, telemetry, alerts, admin
- **Default enabled categories**: sso, user, roles, api, tenant
- **Typical views**: device_status_view (JOIN), telemetry_summary_view (aggregate), alert_detail_view (JOIN)
- **Key rules**: Device authentication (blocking), alert thresholds (informational), tenant device isolation (blocking), telemetry validation (informational)

---

## Dependency Ordering

The six-step sequence for translating a business description into source files. Each step depends on the outputs of previous steps.

### Mandatory Sequence

```
Step 1: Domains
  ↓  (domains inform which entity groups exist)
Step 2: Entities
  ↓  (entities inform what fields are available)
Step 3: Fields
  ↓  (fields inform what views can project and what rules can reference)
Step 4: Views
  ↓  (views + entities together define the full set of rule targets)
Step 5: Metadata Categories
  ↓  (categories inform which authorization contexts rules can reference)
Step 6: Concrete Files (schema.sql, views.sql, features/*.feature, metadata.yaml)
```

### Step Details

| Step | Input | Output | Decision |
|------|-------|--------|----------|
| 1. Domains | Business description + elicitation answers | Domain list with boundaries | Which logical groupings exist |
| 2. Entities | Domain list | Table names per domain | What persistent objects each domain contains |
| 3. Fields | Entity list + business description | Column names, types, constraints | What data each entity stores (avoid TIMESTAMP in rule-eligible fields) |
| 4. Views | Entity list + fields + operations from elicitation Q2 | View definitions (JOIN, aggregate, subquery) | Which cross-table operations the API needs |
| 5. Metadata Categories | Elicitation Q3/Q4/Q5/Q6 + category inference table | Enabled categories list | Which authorization contexts are needed |
| 6. Concrete Files | All above | schema.sql, views.sql, features/*.feature, metadata.yaml | Translate abstract design into appget source files |

### Violations to Avoid

- **Writing schema.sql before identifying all domains** — leads to missing `-- domain` comments and orphaned tables
- **Writing feature files before finalizing field names** — leads to `When` conditions referencing non-existent columns
- **Enabling metadata categories before understanding authorization needs** — leads to unused categories or missing ones
- **Defining views before entities have their fields** — leads to views that project non-existent columns
- **Generating files before the approval gate** — leads to rework when the human disagrees with domain boundaries or rule strategy

---

## Approval Gate Template

A structured template for presenting the complete domain design to the human for review before generating any files. Fill in all sections, then present for explicit approval.

### Template

```
## Domain Design Review

### Domains and Entities

| Domain | Tables | Key Fields |
|--------|--------|------------|
| <domain> | <table1>, <table2> | <notable fields> |
| ... | ... | ... |

### Views and API Operations

| View | Pattern | API Operation |
|------|---------|--------------|
| <view_name> | JOIN / Aggregate / Subquery | <what endpoint this enables> |
| ... | ... | ... |

### Business Rules Summary

| Domain | Rule | Type | Target | Metadata |
|--------|------|------|--------|----------|
| <domain> | <RuleName> | blocking/informational | <entity/view> | <categories or none> |
| ... | ... | ... | ... | ... |

### Metadata Categories

| Category | Enabled | Justification |
|----------|---------|---------------|
| sso | yes | <why needed> |
| <category> | yes/no | <why or why not> |
| ... | ... | ... |

### Design Decisions

- <Decision 1>: <rationale>
- <Decision 2>: <rationale>
- ...

### What Happens Next

After approval:
1. Generate schema.sql with domain comments
2. Generate views.sql with domain comments
3. Generate features/*.feature (one per domain)
4. Update metadata.yaml (enable required categories)
5. Run `cd java && make all` to verify pipeline

**Approval requested**: Does this decomposition match your intent? Any domains, views, rules, or categories to add, remove, or change?
```

### Usage Notes

- Every enabled metadata category has an explicit justification (not just "might need it")
- Every view has a clear API operation it enables (not just "useful to have")
- Design decisions explain trade-offs, not just choices
- The "What Happens Next" section sets expectations for the file generation step
- The approval request is explicit — a clear yes/no question

---

## ML Classification Reference (Future — Not Implemented)

This section documents an alternative approach to category inference using machine learning. It is included for educational reference and future consideration. The keyword-based inference table above is the current recommended approach.

### The Problem with Keyword Matching

Keyword-to-category mapping works well for explicit mentions ("we need OAuth integration" → enable `oauth`) but struggles with implicit requirements:

- "Users can only see their own data" implies `tenant` or `permissions` but uses neither keyword
- "We need to comply with GDPR" implies `audit` + `geo` but mentions neither
- "Free users get 100 API calls per day" implies `api` + `billing` but describes a pricing model

### Proposed Architecture: Sentence Transformers + k-NN

**Model**: `all-MiniLM-L6-v2` (384-dimensional embeddings, fast inference, good semantic similarity)

**Approach**:
1. Embed each sentence from the elicitation answers using the sentence transformer
2. Compare against a labeled example bank (sentences pre-labeled with their implied categories)
3. Use k-nearest-neighbors (k=5) with cosine similarity to find the closest labeled examples
4. Aggregate category votes from the k nearest neighbors
5. Apply a confidence threshold (e.g., cosine similarity > 0.65) to filter low-confidence matches

**Labeled Example Bank** (illustrative):

| Sentence | Categories |
|----------|-----------|
| "Users log in with their Google account" | sso, oauth |
| "Admins can see all tenant data" | roles, tenant |
| "We charge $10/month for premium features" | billing |
| "Every API call is logged for compliance" | api, audit |
| "Users in the EU have data residency requirements" | geo, audit |

### Implementation Notes

- **Vector store**: FAISS (Facebook AI Similarity Search) for fast nearest-neighbor lookup
- **Training data**: Curate 200-500 labeled sentences covering all 14 categories plus common combinations
- **Re-ranking**: After k-NN retrieval, apply category co-occurrence rules (e.g., if `billing` is inferred, check whether `payments` or `invoice` should accompany it)
- **Fallback**: If no neighbor exceeds the confidence threshold, fall back to keyword matching
- **Evaluation metric**: Precision@k and recall@k against a held-out test set of 50+ business descriptions

### Why Not Implemented Now

1. **Training data does not exist yet** — the labeled example bank requires curating hundreds of sentences with correct category labels, which requires real-world usage data from domain-architect sessions
2. **Keyword matching is sufficient** for the current scope — most business descriptions use explicit terminology that the keyword table handles well
3. **Infrastructure cost** — embedding model inference adds a dependency (Python + torch/sentence-transformers) to what is currently a pure Java + YAML pipeline
4. **Diminishing returns** — the approval gate catches inference errors before they reach file generation, so the cost of a keyword-matching miss is one round of human correction, not a pipeline failure

When the volume of "creating from scratch" sessions grows and patterns emerge in what the keyword table misses, revisit this approach with real training data.
