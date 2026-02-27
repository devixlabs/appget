# Gherkin Business Rules Guide — University Application Example

This guide teaches the appget Gherkin DSL through a complete University application. By the end you can write your own `.feature` business rule files from scratch, or understand and edit what the **domain-architect** agent generates.

**Prerequisites**: `.feature` files do not stand alone. Before writing rules:

1. **`schema.sql` and `views.sql`** — Every field in a `When` condition must be a column in these files. Every `@target` model must match a table or view they define. Run `make parse-schema` to confirm they parse correctly.
2. **`metadata.yaml`** — Every category name in a `Given … context requires:` step must be declared here. If a category or field is missing, the pipeline fails. See [Writing metadata.yaml](#writing-metadatayaml) below.

For the DSL specification in full technical detail, see the [appget Feature DSL Reference](../.claude/skills/appget-feature-dsl/SKILL.md). For Gherkin syntax beyond what appget uses, see the [Gherkin Authoring Reference](../.claude/skills/gherkin-authoring/SKILL.md).

---

## The University Domain

The examples throughout this guide model a full university application. The schema below defines the tables and views used in the `.feature` files that follow.

### Tables by Domain

**`auth` domain** — identity, credentials, and sessions:

| Table | Type | Key Fields for Rules |
|-------|------|----------------------|
| `users` | base | `is_active BOOLEAN`, `is_verified BOOLEAN` |
| `credentials` | base | `credential_type VARCHAR`, `is_active BOOLEAN` |
| `oauth_tokens` | base | `provider VARCHAR`, `is_valid BOOLEAN` |
| `sessions` | base | `is_active BOOLEAN` |

**`academic` domain** — courses, enrollment, and grading:

| Table | Type | Key Fields for Rules |
|-------|------|----------------------|
| `students` | base | `gpa DECIMAL`, `credit_hours_completed INT`, `is_enrolled BOOLEAN`, `is_on_probation BOOLEAN`, `enrollment_status VARCHAR` |
| `teachers` | base | `is_tenured BOOLEAN`, `is_active BOOLEAN`, `rank VARCHAR` |
| `colleges` | base | `is_accredited BOOLEAN` |
| `departments` | base | `is_active BOOLEAN` |
| `courses` | base | `credit_hours INT`, `enrollment_count INT`, `capacity INT`, `is_active BOOLEAN`, `is_graduate_level BOOLEAN` |
| `semesters` | base | `year INT`, `is_active BOOLEAN`, `is_registration_open BOOLEAN`, `is_grades_published BOOLEAN` |
| `enrollments` | base | `grade_points DECIMAL`, `is_dropped BOOLEAN`, `is_completed BOOLEAN`, `is_waitlisted BOOLEAN`, `enrollment_status VARCHAR` |

**`admissions` domain** — applicant tracking:

| Table | Type | Key Fields for Rules |
|-------|------|----------------------|
| `admissions` | base | `gpa_submitted DECIMAL`, `test_score INT`, `is_international BOOLEAN`, `is_complete BOOLEAN`, `application_status VARCHAR`, `program_applied VARCHAR` |

**`finance` domain** — tuition billing and financial aid:

| Table | Type | Key Fields for Rules |
|-------|------|----------------------|
| `tuition_accounts` | base | `amount_due DECIMAL`, `amount_paid DECIMAL`, `is_paid BOOLEAN`, `is_past_due BOOLEAN`, `financial_hold_count INT` |
| `financial_aid` | base | `aid_type VARCHAR`, `aid_amount DECIMAL`, `is_disbursed BOOLEAN`, `is_active BOOLEAN` |

**`intranet` domain** — internal systems and access control:

| Table | Type | Key Fields for Rules |
|-------|------|----------------------|
| `intranet_systems` | base | `system_name VARCHAR`, `system_type VARCHAR`, `is_active BOOLEAN`, `required_access_level INT` |
| `system_access` | base | `access_level INT`, `is_active BOOLEAN` |

### Views (from `views.sql`)

Views are read-only projections — often JOINs or computed columns — defined in `views.sql`. Only the columns in each view's `SELECT` clause are available in `@view`-targeted rules.

| View | SELECT Fields Available for Rules |
|------|-----------------------------------|
| `course_availability_view` | `is_active`, `is_graduate_level`, `available_seats` *(derived: `capacity − enrollment_count`)* |
| `student_enrollment_view` | `gpa`, `credit_hours_completed`, `is_enrolled`, `is_on_probation`, `is_paid`, `financial_hold_count` |
| `student_transcript_view` | `grade_points`, `credit_hours`, `is_completed`, `is_dropped` |
| `tuition_balance_view` | `amount_due`, `amount_paid`, `is_paid`, `is_past_due`, `financial_hold_count`, `aid_disbursed` |
| `admission_review_view` | `gpa_submitted`, `test_score`, `is_international`, `is_complete`, `application_status` |

### Authorization Metadata

Metadata context is declared in `metadata.yaml` — a **curated registry** of built-in categories with an `enabled: true/false` toggle. Each category represents a cross-cutting concern (authentication, authorization, billing, etc.) independent of any specific `schema.sql`. Users enable the categories they need; disabled categories are excluded from the pipeline output.

The university application enables `sso` and `roles`:

| Category | Field | Type | HTTP Header |
|----------|-------|------|-------------|
| `sso` | `authenticated` | boolean | `X-Sso-Authenticated` |
| `sso` | `sessionId` | string | `X-Sso-Session-Id` |
| `sso` | `provider` | string | `X-Sso-Provider` |
| `roles` | `roleLevel` | int | `X-Roles-Role-Level` |
| `roles` | `isAdmin` | boolean | `X-Roles-Is-Admin` |
| `roles` | `roleName` | string | `X-Roles-Role-Name` |

**University role level convention** used throughout these examples:

| `roleLevel` | Role |
|-------------|------|
| 1 | Student |
| 2 | Staff |
| 3 | Faculty |
| 4 | Department Admin / Admissions Officer |
| 5 | System Administrator |

---

## Writing `metadata.yaml`

`metadata.yaml` is a **curated registry** of authorization context categories. It ships with 14 built-in categories covering standard application concerns (SSO, roles, OAuth, billing, audit, etc.). Each category has an `enabled: true/false` toggle — only enabled categories are emitted into `specs.yaml` and available to `Given … context requires:` steps.

The pipeline validates all metadata references at build time:
- Referencing a **non-existent** category → build error
- Referencing a **disabled** category → build error with guidance to enable it
- Referencing a **non-existent field** in an enabled category → build error

### Structure

```yaml
metadata:
  <category_name>:          # lowercase, matches "Given <category> context requires:" label
    enabled: true            # true = active in pipeline; false = dormant in registry
    description: "..."       # documentation-only, not emitted to specs.yaml
    fields:
      - name: <fieldName>   # camelCase — becomes a Java getter; used in data table "field" column
        type: <type>         # boolean | String | int
```

Field names must be **camelCase** and match exactly what you write in the `Given` data table. They are read from HTTP request headers at runtime: a field `roleLevel` in category `roles` is delivered via the `X-Roles-Role-Level` header.

### Supported types

| Type | Use for | Example value in `Given` step |
|------|---------|-------------------------------|
| `boolean` | flags | `authenticated == true` |
| `String` | identifiers, names | `provider == "google"` |
| `int` | levels, counts | `roleLevel >= 3` |

### Built-in categories

The registry ships with 14 built-in categories. Three are pre-enabled (`sso`, `user`, `roles`) as the most universal. Enable others by setting `enabled: true`.

| Group | Category | Pre-enabled | Fields |
|-------|----------|-------------|--------|
| Identity | `sso` | yes | authenticated, sessionId, provider |
| Identity | `user` | yes | userId, email, username |
| Identity | `oauth` | no | accessToken, scope, expiresIn, provider |
| Identity | `jwt` | no | subject, issuer, audience, expiresAt |
| Identity | `mfa` | no | verified, method |
| Authorization | `roles` | yes | roleName, roleLevel, isAdmin |
| Authorization | `permissions` | no | permissionName, resourceType, canRead, canWrite |
| API | `api` | no | apiKey, rateLimitTier, isActive |
| Multi-tenancy | `tenant` | no | tenantId, tenantName, plan, isActive |
| Commerce | `billing` | no | customerId, plan, isActive, billingCycle |
| Commerce | `payments` | no | paymentMethodId, provider, currency, isVerified |
| Commerce | `invoice` | no | invoiceId, status, amount, isPaid |
| Compliance | `audit` | no | requestId, sourceIp, userAgent |
| Compliance | `geo` | no | country, region, timezone |

### University application example

The university examples in this guide use the `sso` and `roles` categories (both pre-enabled):

```yaml
metadata:
  sso:
    enabled: true
    description: "Single sign-on session state"
    fields:
      - name: authenticated
        type: boolean
      - name: sessionId
        type: String
      - name: provider
        type: String
  roles:
    enabled: true
    description: "Role-based access control"
    fields:
      - name: roleName
        type: String
      - name: roleLevel
        type: int
      - name: isAdmin
        type: boolean
```

### Adding a custom category

Custom categories use the same format as built-ins. Add them at the bottom of `metadata.yaml`:

```yaml
  # ─── Custom Categories ───
  university:
    enabled: true
    description: "University-specific role context"
    fields:
      - name: isStudent
        type: boolean
      - name: isFaculty
        type: boolean
      - name: isStaff
        type: boolean
```

Then use it in a `Given` step just like any built-in category:

```gherkin
Given university context requires:
  | field     | operator | value |
  | isFaculty | ==       | true  |
```

### Enabling a built-in category

To start using a built-in category that's currently disabled, just set `enabled: true`:

```yaml
  jwt:
    enabled: true      # was false
    description: "JWT token claims"
    fields:
      ...
```

### Checklist for adding or enabling a category

- [ ] Category name is lowercase (`university`, not `University`)
- [ ] `enabled: true` is set
- [ ] Field names are camelCase (`roleLevel`, `isFaculty` — not `role_level`, `is_faculty`)
- [ ] Each field type is one of `boolean`, `String`, or `int`
- [ ] Run `make features-to-specs` after updating to confirm the pipeline still passes

---

## Feature File Structure

Each domain gets exactly one `.feature` file. The `@domain` tag on the line before `Feature:` assigns all rules in that file to their domain.

```gherkin
@domain:<domain_name>
Feature: <Domain Name> Domain Business Rules

  @target:<ModelName> @rule:<RuleName>
  Scenario: <Human-readable description of the rule>
    When <field> <operator_phrase> <value>
    Then status is "POSITIVE_OUTCOME"
    But otherwise status is "NEGATIVE_OUTCOME"
```

**Every scenario requires:**

| Tag / Keyword | Required | Purpose |
|---------------|----------|---------|
| `@target:<Model>` | Yes | Plural PascalCase table or view name |
| `@rule:<Name>` | Yes | Unique rule name across **all** feature files (PascalCase) |
| `@blocking` | When rejecting | Adds HTTP 422 on rule failure; omit for informational rules |
| `@view` | When targeting a view | Required alongside `@target` for any view from `views.sql` |
| `Then status is "..."` | Yes | Outcome when condition passes |
| `But otherwise status is "..."` | Yes | Outcome when condition fails |

---

## Rule Patterns

### Simple condition

One field compared to a literal value using a natural-language operator phrase:

```gherkin
When <field> <operator phrase> <value>
```

`<field>` must be an exact column name from `schema.sql` for the target table, or from the `SELECT` clause of the target view in `views.sql`. Misspelled or non-existent field names produce a no-op rule with no parse error.

> **Column type restriction**: Only `VARCHAR`, `TEXT`, `INT`, `BIGINT`, `DECIMAL`, `FLOAT`, `DOUBLE`, and `BOOLEAN` columns are valid in `When` conditions. Never use `DATE`, `TIMESTAMP`, or `DATETIME` columns — they map to a non-scalar protobuf type and will silently evaluate as false on every call. Use a boolean flag in your schema instead (e.g., `is_registration_open BOOLEAN` rather than comparing `registration_deadline TIMESTAMP`).

| Operator phrase | Symbol | Value types |
|-----------------|--------|-------------|
| `equals` | `==` | string (quoted), number, boolean |
| `does not equal` | `!=` | string (quoted), number, boolean |
| `is greater than` | `>` | number |
| `is less than` | `<` | number |
| `is at least` | `>=` | number |
| `is at most` | `<=` | number |

```gherkin
  @target:Students @blocking @rule:StudentEnrollmentCheck
  Scenario: Student must be actively enrolled
    When is_enrolled equals true
    Then status is "ENROLLED"
    But otherwise status is "NOT_ENROLLED"
```

### Compound AND condition

All listed conditions must be true. Uses a three-column data table. **Operators in data tables use symbols** (`==`, `!=`, `>=`, `<=`, `>`, `<`) — not the natural language phrases above.

```gherkin
  @target:TuitionAccounts @blocking @rule:FullTuitionClearance
  Scenario: Account must be paid in full with no financial holds
    When all conditions are met:
      | field                | operator | value |
      | is_paid              | ==       | true  |
      | financial_hold_count | <=       | 0     |
    Then status is "ACCOUNT_CLEARED"
    But otherwise status is "ACCOUNT_HOLD"
```

### Compound OR condition

At least one condition must be true:

```gherkin
  @target:Admissions @rule:FastTrackEligibility
  Scenario: High GPA or high test score qualifies for fast-track review
    When any condition is met:
      | field         | operator | value |
      | gpa_submitted | >=       | 3.8   |
      | test_score    | >=       | 1400  |
    Then status is "FAST_TRACK"
    But otherwise status is "STANDARD_REVIEW"
```

### Metadata-gated rule

Authorization context from HTTP headers is checked in `Given` steps **before** the `When` condition. If metadata requirements fail, the rule fails immediately without evaluating `When`.

```gherkin
  @target:Admissions @blocking @rule:AdmissionsStaffOnly
  Scenario: Only admissions staff can review applications
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 4     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_complete equals true
    Then status is "REVIEW_PERMITTED"
    But otherwise status is "REVIEW_DENIED"
```

### View-targeting rule

Add `@view` when the field you need is a computed or joined column from `views.sql`. The field must exist in the view's `SELECT` clause — not just in its `WHERE` or `JOIN`.

```gherkin
  @view @target:CourseAvailabilityView @blocking @rule:OpenSeatRequired
  Scenario: Course must have open seats before enrollment is allowed
    When available_seats is greater than 0
    Then status is "SEATS_AVAILABLE"
    But otherwise status is "COURSE_FULL"
```

---

## Complete Domain Feature Files

### `features/auth.feature`

```gherkin
@domain:auth
Feature: Auth Domain Business Rules

  @target:Users @blocking @rule:UserActivationCheck
  Scenario: User account must be active to access the system
    When is_active equals true
    Then status is "ACCOUNT_ACTIVE"
    But otherwise status is "ACCOUNT_INACTIVE"

  @target:Users @rule:UserVerificationStatus
  Scenario: Verified users receive a verified badge
    When is_verified equals true
    Then status is "VERIFIED_USER"
    But otherwise status is "UNVERIFIED_USER"

  @target:Credentials @blocking @rule:CredentialActiveStatus
  Scenario: Credential must be active to authenticate
    When is_active equals true
    Then status is "CREDENTIAL_VALID"
    But otherwise status is "CREDENTIAL_REVOKED"

  @target:OauthTokens @blocking @rule:OauthTokenValidity
  Scenario: OAuth token must be valid for API access
    When is_valid equals true
    Then status is "TOKEN_VALID"
    But otherwise status is "TOKEN_INVALID"

  @target:Sessions @blocking @rule:SessionActivityCheck
  Scenario: Session must be active to serve requests
    When is_active equals true
    Then status is "SESSION_ACTIVE"
    But otherwise status is "SESSION_EXPIRED"

  @target:Users @blocking @rule:AdminAuthenticationRequired
  Scenario: Admin operations require an authenticated user with elevated role
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 5     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_active equals true
    Then status is "ADMIN_AUTHENTICATED"
    But otherwise status is "ADMIN_DENIED"
```

---

### `features/academic.feature`

```gherkin
@domain:academic
Feature: Academic Domain Business Rules

  @target:Students @blocking @rule:StudentEnrollmentCheck
  Scenario: Student must be actively enrolled to access academic services
    When is_enrolled equals true
    Then status is "ENROLLED"
    But otherwise status is "NOT_ENROLLED"

  @target:Students @rule:AcademicProbationStatus
  Scenario: Student GPA below threshold is placed on academic probation
    When gpa is less than 2.0
    Then status is "ON_PROBATION"
    But otherwise status is "GOOD_STANDING"

  @target:Students @rule:SeniorClassification
  Scenario: Student with enough credit hours is classified as a senior
    When credit_hours_completed is at least 90
    Then status is "SENIOR"
    But otherwise status is "NOT_SENIOR"

  @target:Teachers @blocking @rule:TeacherActiveCheck
  Scenario: Teacher must be active to be assigned courses
    When is_active equals true
    Then status is "FACULTY_ACTIVE"
    But otherwise status is "FACULTY_INACTIVE"

  @target:Teachers @rule:TenuredFacultyStatus
  Scenario: Tenured faculty receive tenure classification
    When is_tenured equals true
    Then status is "TENURED"
    But otherwise status is "NON_TENURED"

  @target:Colleges @blocking @rule:CollegeAccreditationCheck
  Scenario: College must be accredited to issue degrees
    When is_accredited equals true
    Then status is "ACCREDITED"
    But otherwise status is "UNACCREDITED"

  @target:Courses @blocking @rule:CourseActiveCheck
  Scenario: Course must be active to accept enrollments
    When is_active equals true
    Then status is "COURSE_ACTIVE"
    But otherwise status is "COURSE_INACTIVE"

  @target:Courses @rule:GraduateLevelClassification
  Scenario: Graduate-level courses receive graduate classification
    When is_graduate_level equals true
    Then status is "GRADUATE_COURSE"
    But otherwise status is "UNDERGRADUATE_COURSE"

  @target:Semesters @blocking @rule:RegistrationWindowOpen
  Scenario: Registration can only occur during an open registration window
    When is_registration_open equals true
    Then status is "REGISTRATION_OPEN"
    But otherwise status is "REGISTRATION_CLOSED"

  @target:Semesters @rule:GradePublicationStatus
  Scenario: Semester grades are available after publication
    When is_grades_published equals true
    Then status is "GRADES_PUBLISHED"
    But otherwise status is "GRADES_PENDING"

  @target:Enrollments @blocking @rule:EnrollmentNotDropped
  Scenario: Dropped enrollment cannot receive grade submission
    When is_dropped equals false
    Then status is "ENROLLMENT_ACTIVE"
    But otherwise status is "ENROLLMENT_DROPPED"

  @target:Enrollments @rule:WaitlistStatus
  Scenario: Waitlisted enrollment is pending seat availability
    When is_waitlisted equals true
    Then status is "WAITLISTED"
    But otherwise status is "ENROLLED"

  @target:Enrollments @rule:HighAchievementRecognition
  Scenario: Enrollment with high grade points qualifies for dean's list consideration
    When grade_points is at least 3.7
    Then status is "DEANS_LIST_ELIGIBLE"
    But otherwise status is "STANDARD_PERFORMANCE"

  @target:Courses @blocking @rule:CourseFacultyManagement
  Scenario: Only authenticated faculty can modify course configuration
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 3     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_active equals true
    Then status is "FACULTY_EDIT_PERMITTED"
    But otherwise status is "FACULTY_EDIT_DENIED"

  @view @target:CourseAvailabilityView @blocking @rule:OpenSeatRequired
  Scenario: Course must have open seats before enrollment is allowed
    When available_seats is greater than 0
    Then status is "SEATS_AVAILABLE"
    But otherwise status is "COURSE_FULL"

  @view @target:StudentEnrollmentView @blocking @rule:RegistrationEligibilityCheck
  Scenario: Student must be enrolled and have no financial holds to register
    When all conditions are met:
      | field                | operator | value |
      | is_enrolled          | ==       | true  |
      | financial_hold_count | <=       | 0     |
    Then status is "ELIGIBLE_TO_REGISTER"
    But otherwise status is "REGISTRATION_BLOCKED"

  @view @target:StudentTranscriptView @rule:CourseCompletionStatus
  Scenario: Completed enrollments appear on official transcript
    When is_completed equals true
    Then status is "COURSE_COMPLETED"
    But otherwise status is "COURSE_IN_PROGRESS"
```

---

### `features/admissions.feature`

```gherkin
@domain:admissions
Feature: Admissions Domain Business Rules

  @target:Admissions @blocking @rule:ApplicationCompletionRequired
  Scenario: Application must be complete before entering review
    When is_complete equals true
    Then status is "APPLICATION_COMPLETE"
    But otherwise status is "APPLICATION_INCOMPLETE"

  @target:Admissions @blocking @rule:MinimumGpaStandard
  Scenario: Applicant GPA must meet the minimum for admission consideration
    When gpa_submitted is at least 2.5
    Then status is "GPA_MEETS_STANDARD"
    But otherwise status is "GPA_BELOW_MINIMUM"

  @target:Admissions @rule:InternationalStudentClassification
  Scenario: International applicants receive international classification
    When is_international equals true
    Then status is "INTERNATIONAL_APPLICANT"
    But otherwise status is "DOMESTIC_APPLICANT"

  @target:Admissions @rule:FastTrackEligibility
  Scenario: High GPA or high test score qualifies for fast-track review
    When any condition is met:
      | field         | operator | value |
      | gpa_submitted | >=       | 3.8   |
      | test_score    | >=       | 1400  |
    Then status is "FAST_TRACK"
    But otherwise status is "STANDARD_REVIEW"

  @target:Admissions @rule:HonorsAdmissionCheck
  Scenario: Exceptional academics qualify for honors program consideration
    When all conditions are met:
      | field         | operator | value |
      | gpa_submitted | >=       | 3.9   |
      | test_score    | >=       | 1450  |
    Then status is "HONORS_ELIGIBLE"
    But otherwise status is "STANDARD_ADMISSION"

  @target:Admissions @blocking @rule:AdmissionsStaffOnly
  Scenario: Only admissions staff with sufficient role can review applications
    Given roles context requires:
      | field     | operator | value |
      | roleLevel | >=       | 4     |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_complete equals true
    Then status is "REVIEW_PERMITTED"
    But otherwise status is "REVIEW_DENIED"

  @view @target:AdmissionReviewView @rule:AdmissionDecisionPending
  Scenario: Application in pending status awaits committee decision
    When application_status equals "PENDING"
    Then status is "AWAITING_DECISION"
    But otherwise status is "DECISION_RENDERED"

  @view @target:AdmissionReviewView @rule:InternationalHighAchiever
  Scenario: International applicant with strong GPA receives priority routing
    When all conditions are met:
      | field         | operator | value |
      | is_international | ==    | true  |
      | gpa_submitted    | >=    | 3.5   |
    Then status is "INTERNATIONAL_PRIORITY"
    But otherwise status is "INTERNATIONAL_STANDARD"
```

---

### `features/finance.feature`

```gherkin
@domain:finance
Feature: Finance Domain Business Rules

  @target:TuitionAccounts @blocking @rule:TuitionPaidCheck
  Scenario: Tuition must be paid to access academic records and registration
    When is_paid equals true
    Then status is "TUITION_PAID"
    But otherwise status is "TUITION_OUTSTANDING"

  @target:TuitionAccounts @blocking @rule:FinancialHoldBlock
  Scenario: Financial holds block registration and official document release
    When financial_hold_count is at most 0
    Then status is "NO_FINANCIAL_HOLD"
    But otherwise status is "FINANCIAL_HOLD_ACTIVE"

  @target:TuitionAccounts @blocking @rule:FullTuitionClearance
  Scenario: Account must be fully paid with no holds for semester clearance
    When all conditions are met:
      | field                | operator | value |
      | is_paid              | ==       | true  |
      | financial_hold_count | <=       | 0     |
    Then status is "ACCOUNT_CLEARED"
    But otherwise status is "ACCOUNT_HOLD"

  @target:TuitionAccounts @rule:PastDueAlert
  Scenario: Past-due accounts are flagged for collections outreach
    When is_past_due equals true
    Then status is "PAST_DUE"
    But otherwise status is "CURRENT"

  @target:FinancialAid @rule:AidDisbursementStatus
  Scenario: Disbursed aid is applied to the student account balance
    When is_disbursed equals true
    Then status is "AID_DISBURSED"
    But otherwise status is "AID_PENDING"

  @target:FinancialAid @rule:ActiveAidAward
  Scenario: Only active aid awards contribute to balance calculations
    When is_active equals true
    Then status is "AID_ACTIVE"
    But otherwise status is "AID_INACTIVE"

  @target:FinancialAid @rule:PremiumAidClassification
  Scenario: Large aid awards qualify for priority disbursement processing
    When aid_amount is at least 10000
    Then status is "PRIORITY_DISBURSEMENT"
    But otherwise status is "STANDARD_DISBURSEMENT"

  @view @target:TuitionBalanceView @blocking @rule:TuitionBalanceCritical
  Scenario: Outstanding balance with no disbursed aid blocks degree clearance
    When all conditions are met:
      | field         | operator | value |
      | is_paid       | ==       | false |
      | aid_disbursed | ==       | false |
    Then status is "BALANCE_CRITICAL"
    But otherwise status is "BALANCE_MANAGEABLE"
```

---

### `features/intranet.feature`

```gherkin
@domain:intranet
Feature: Intranet Domain Business Rules

  @target:IntranetSystems @blocking @rule:IntranetSystemActive
  Scenario: Intranet system must be active to accept connections
    When is_active equals true
    Then status is "SYSTEM_ONLINE"
    But otherwise status is "SYSTEM_OFFLINE"

  @target:SystemAccess @blocking @rule:SystemAccessActive
  Scenario: System access grant must be active to allow login
    When is_active equals true
    Then status is "ACCESS_GRANTED"
    But otherwise status is "ACCESS_REVOKED"

  @target:SystemAccess @blocking @rule:MinimumAccessLevel
  Scenario: Access level must meet minimum threshold for intranet use
    When access_level is at least 1
    Then status is "ACCESS_SUFFICIENT"
    But otherwise status is "ACCESS_INSUFFICIENT"

  @target:SystemAccess @rule:ElevatedAccessClassification
  Scenario: Users with elevated access level are classified as privileged
    When access_level is at least 5
    Then status is "PRIVILEGED_ACCESS"
    But otherwise status is "STANDARD_ACCESS"

  @target:SystemAccess @rule:HighPrivilegeClassification
  Scenario: High-privilege access grants receive special classification
    When access_level is at least 8
    Then status is "HIGH_PRIVILEGE"
    But otherwise status is "STANDARD_PRIVILEGE"

  @target:IntranetSystems @blocking @rule:AdminSystemAccessOnly
  Scenario: High-security systems require admin role and active SSO session
    Given roles context requires:
      | field   | operator | value |
      | isAdmin | ==       | true  |
    And sso context requires:
      | field         | operator | value |
      | authenticated | ==       | true  |
    When is_active equals true
    Then status is "ADMIN_ACCESS_PERMITTED"
    But otherwise status is "ADMIN_ACCESS_DENIED"

  @target:SystemAccess @blocking @rule:SeniorSystemAccessRequired
  Scenario: Senior administrative systems require faculty-level access or above
    When all conditions are met:
      | field        | operator | value |
      | is_active    | ==       | true  |
      | access_level | >=       | 3     |
    Then status is "SENIOR_ACCESS_GRANTED"
    But otherwise status is "SENIOR_ACCESS_DENIED"
```

---

## Critical Constraints

These mistakes cause silent failures or parser errors. Check against this list before committing any `.feature` file.

### 1. Date and Timestamp columns cannot be compared

`DATE`, `TIMESTAMP`, and `DATETIME` columns map to `google.protobuf.Timestamp` — not a comparable scalar. A rule comparing them silently returns false on every evaluation.

```gherkin
# WRONG: enrolled_at is TIMESTAMP — not comparable
When enrolled_at is greater than 0

# CORRECT: use a boolean flag stored in the schema instead
When is_enrolled equals true
```

**University example**: Use `is_registration_open BOOLEAN` on `semesters` instead of comparing `registration_deadline TIMESTAMP` to a date value.

### 2. Values must be literals — never field names

The DSL compares one field to a **literal** (string, number, boolean). Using another field name as the value silently creates a no-op rule — the parser treats the field name as a literal string.

```gherkin
# WRONG: amount_paid is treated as the literal string "amount_paid", not a field
When amount_due does not equal amount_paid

# CORRECT: use a boolean flag, or compare to a meaningful literal
When is_paid equals false
```

### 3. `@target` uses plural PascalCase — matching the table name

**Conversion rule**: split the snake_case table name on underscores, capitalize only the first letter of each word (leave the rest lowercase), then join. Do not singularize, do not use acronym-style all-caps.

```
students              →  Students
tuition_accounts      →  TuitionAccounts
oauth_tokens          →  OauthTokens        ← "oauth", not "OAuth"
intranet_systems      →  IntranetSystems
system_access         →  SystemAccess
course_availability_view  →  CourseAvailabilityView
```

| SQL table | Correct `@target` | Common mistake |
|-----------|-------------------|----------------|
| `students` | `@target:Students` | `@target:Student` (singularized) |
| `tuition_accounts` | `@target:TuitionAccounts` | `@target:TuitionAccount` |
| `oauth_tokens` | `@target:OauthTokens` | `@target:OAuthTokens` (acronym caps) |
| `intranet_systems` | `@target:IntranetSystems` | `@target:IntranetSystem` |
| `system_access` | `@target:SystemAccess` | `@target:SystemAccesses` |
| `course_availability_view` | `@target:CourseAvailabilityView` | `@target:CourseAvailabilityView` ✓ |

### 4. View fields must be in the SELECT clause

A view's `WHERE` clause filters rows, but only `SELECT` columns are accessible in `When` conditions. A column used in filtering that is not projected is invisible to the rule engine.

```gherkin
# WRONG: is_dropped filters student_transcript_view rows but is not in its SELECT
@view @target:StudentTranscriptView @rule:ActiveEnrollmentsOnly
Scenario: Only active enrollments appear in transcript
  When is_dropped equals false    ← not in SELECT, evaluates as not-found

# CORRECT: is_completed IS in the view's SELECT clause
  When is_completed equals true
  Then status is "COURSE_COMPLETED"
  But otherwise status is "COURSE_IN_PROGRESS"
```

### 5. Metadata fields use camelCase

Model fields use `snake_case`, but metadata fields use `camelCase` — matching Java getter conventions.

```gherkin
# WRONG: snake_case for a metadata field
Given roles context requires:
  | field      | operator | value |
  | role_level | >=       | 3     |

# CORRECT: camelCase for metadata fields
Given roles context requires:
  | field     | operator | value |
  | roleLevel | >=       | 3     |
```

### 6. Data table operators use symbols, not natural language

In compound condition tables, use `==`, `!=`, `>=`, `<=`, `>`, `<`. Natural language phrases (`equals`, `is at least`) only parse correctly in single-field `When` steps.

```gherkin
# WRONG: natural language phrase in data table
When all conditions are met:
  | field     | operator   | value |
  | is_active | equals     | true  |   ← fails to parse

# CORRECT: symbol operator in data table
When all conditions are met:
  | field     | operator | value |
  | is_active | ==       | true  |
```

### 7. `@rule` names must be unique across all feature files

A rule name that appears in two different `.feature` files causes a collision in `specs.yaml`. Use a **domain-prefixed PascalCase** convention to make uniqueness automatic:

| Domain | Prefix convention | Example |
|--------|-------------------|---------|
| `auth` | `Auth` or describe the entity | `UserActivationCheck`, `OauthTokenValidity` |
| `academic` | `Student`, `Course`, `Enrollment`, etc. | `StudentEnrollmentCheck`, `CourseActiveCheck` |
| `admissions` | `Admission` or `Application` | `AdmissionCompletionRequired`, `ApplicationGpaStandard` |
| `finance` | `Tuition`, `Aid` | `TuitionPaidCheck`, `AidDisbursementStatus` |
| `intranet` | `Intranet`, `System`, `Access` | `IntranetSystemActive`, `SystemAccessActive` |

A generic name like `ActiveCheck`, `StatusCheck`, or `ValidationRule` will collide across domains. Names that include the entity (`StudentEnrollmentCheck`, `CourseActiveCheck`) are inherently unique.

---

## Validation Checklist

Before committing any `.feature` file, verify each scenario against this list:

- [ ] `schema.sql` (and `views.sql` for any `@view` rules) exist and parse cleanly via `make parse-schema`
- [ ] File opens with `@domain:<name>` on its own line before `Feature:`
- [ ] Every scenario has `@target`, `@rule`, and (if needed) `@blocking` and `@view`
- [ ] `@target` is the plural PascalCase of the table or view name (not singularized)
- [ ] Every field in `When` conditions exists on the exact target table or view
- [ ] For `@view` targets: every field appears in the view's `SELECT` clause
- [ ] No `When` condition references a `DATE`, `TIMESTAMP`, or `DATETIME` column
- [ ] All values in `When` conditions are literals — never another field name
- [ ] Compound data table operators use symbols (`==`, `>=`, etc.)
- [ ] Metadata fields in `Given … context requires:` use `camelCase`
- [ ] Every metadata category name (e.g., `roles`, `sso`) exists in `metadata.yaml`
- [ ] Every metadata field name exists in that category's `fields` list in `metadata.yaml`
- [ ] Every scenario has both `Then status is "…"` and `But otherwise status is "…"`
- [ ] Status values are descriptive `SCREAMING_SNAKE_CASE` (not `OK`, `YES`, `FAIL`)
- [ ] `@rule` names are unique across **all** `.feature` files in the project

---

## Planned: Deriving `schema.sql` from `.feature` Files

> **TODO**: Write this section.
>
> Cover the workflow for teams who want to start from business rules rather than a database schema — BDD-first design. Topics to include:
>
> - Reading `When` conditions to infer table names (`@target`), column names (field), and column types (value literal type)
> - Identifying `BOOLEAN` columns (fields compared to `true`/`false`), `DECIMAL` (float literals), `INT` (integer literals), `VARCHAR` (quoted string literals)
> - Inferring views from `@view @target` usage — these become `CREATE VIEW … AS SELECT` stubs in `views.sql`
> - Limitations: feature files express conditions, not constraints (`NOT NULL`, `UNIQUE`, `REFERENCES`) — those must still be hand-authored
> - Suggested workflow: draft `.feature` scenarios → extract column inventory → write `schema.sql` → run `make parse-schema` → verify `models.yaml` matches expectations → iterate

---

**Last Updated**: 2026-02-26
**Status**: Active — University domain reference for the appget Gherkin DSL
