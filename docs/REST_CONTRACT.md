# REST Contract (Draft)

REST endpoints are generated for models, not views.

## Resource naming
- Base name comes from the SQL table name.
- Convert snake_case to kebab-case for URLs.
- Pluralization rules are deterministic:
  - If ending with `y` and not a vowel, replace with `ies`.
  - If ending with `s`, `x`, `z`, `ch`, `sh`, or `o`, add `es`.
  - Otherwise add `s`.

Examples
- `employee` -> `/employees`
- `salary` -> `/salaries`
- `role` -> `/roles`

## CRUD endpoints
- `POST /{resource}` create
- `GET /{resource}` list
- `GET /{resource}/{pk...}` get by key
- `PUT /{resource}/{pk...}` update
- `DELETE /{resource}/{pk...}` delete

Composite keys use multiple path params in primary key order.

## Request and response shapes
- Create and update responses wrap data and rule outcomes.
- List returns array of model objects.

Rule response
```
RuleEvaluationResult:
  outcomes: [RuleOutcome]
  hasFailures: boolean

RuleOutcome:
  ruleName: string
  status: string
  satisfied: boolean
```

Rule-aware response
```
RuleAwareResponse<T>:
  data: T
  ruleResults: RuleEvaluationResult
```

## Blocking rules
If any blocking rule fails, return 422 with `RuleEvaluationResult`.

## Metadata headers
Headers follow `X-{Category}-{Field}` with case preserved.
Example: `X-Sso-Authenticated: true`.

## Errors
- Invalid metadata or type parsing returns 400.
- Not found returns 404.
- Rule violations return 422.

Error response
```
ErrorResponse:
  errorCode: string
  message: string
  timestamp: string (RFC3339)
```
