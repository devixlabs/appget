# specs.yaml Schema (Draft)

This schema defines the authoritative rules contract across languages.

## Top-level shape
```
schema_version: 1
metadata:
  sso:
    fields:
      - name: authenticated
        type: bool
rules:
  - name: UserEmailValidation
    target:
      type: model
      name: users
      domain: auth
    blocking: true
    requires:
      sso:
        - field: authenticated
          operator: "=="
          value: true
          value_type: bool
    conditions:
      - field: email
        operator: "!="
        value: ""
        value_type: string
    then:
      status: "VALID_EMAIL"
    else:
      status: "INVALID_EMAIL"
```

## Metadata section
- `metadata` map of categories.
- Each category has `fields` with `name` and `type`.
- Type set is identical to `models.yaml`.

## Rules section
Each rule includes:
- `name` string. Used as class name.
- `target` object with `type`, `name`, `domain`.
- `blocking` boolean. Optional, default false.
- `requires` map from metadata category to conditions list.
- `conditions` list or compound object.
- `then` status object with `status` string.
- `else` status object with `status` string.

## Condition object
Required
- `field` string. snake_case.
- `operator` string.

Optional
- `value` scalar or null.
- `value_type` string. If missing, infer from `models.yaml` or metadata types.

## Operators
- `==` and `!=`
- `>` `>=` `<` `<=`
- `IS_NULL` and `IS_NOT_NULL`

## Null comparisons
- Either use `operator: IS_NULL` or `operator: ==` with `value: null`.
- Both forms must be supported.

## Compound conditions
Compound form is an object:
```
conditions:
  operator: AND
  clauses:
    - field: age
      operator: ">="
      value: 30
```
Operator is `AND` or `OR`.

## Evaluation semantics
1. Evaluate `requires` first.
2. If any metadata requirement fails, rule fails.
3. Evaluate main `conditions` only if metadata passes.
