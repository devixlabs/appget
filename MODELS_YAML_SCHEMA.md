# models.yaml Schema (Draft)

This schema defines the authoritative model contract across languages.
Types are Protobuf-aligned and include a custom Decimal type.

## Top-level shape
```
schema_version: 1
organization: appget

domains:
  appget:
    namespace: dev.appget
    models:
      - name: Employee
        source_table: employees
        resource: employees
        fields:
          - name: id
            type: string
            nullable: false
            field_number: 1
            primary_key: true
            primary_key_position: 1
          - name: salary
            type: decimal
            precision: 15
            scale: 2
            nullable: true
            field_number: 2
    views:
      - name: EmployeeSalaryView
        source_view: employee_salary_view
        resource: employee-salary-view
        fields:
          - name: salary_amount
            type: decimal
            precision: 15
            scale: 2
            nullable: false
            field_number: 1
```

## Required fields
- `schema_version` integer.
- `organization` string.
- `domains` map.

## Domain fields
- `namespace` string. Base namespace for generated code.
- `models` list. Optional.
- `views` list. Optional.

## Model and View fields
- `name` string. PascalCase model or view name.
- `source_table` string for models.
- `source_view` string for views.
- `resource` string. REST resource name. Use hyphenated form.
- `fields` list of field definitions.

## Field definition
Required
- `name` string. snake_case.
- `type` string from the type set below.
- `nullable` boolean.
- `field_number` integer. Stable across regenerations.

Optional
- `primary_key` boolean. True if part of the primary key.
- `primary_key_position` integer. Order for composite keys.
- `precision` integer. Required when `type` is `decimal`.
- `scale` integer. Required when `type` is `decimal`.
- `original_sql_type` string. Optional traceability.

## Type set
- `string`
- `int32`
- `int64`
- `double`
- `bool`
- `bytes`
- `timestamp` (maps to `google.protobuf.Timestamp`)
- `decimal` (maps to custom Decimal message)

## Ordering rules
1. Domains sorted by name.
2. Models and views preserve SQL declaration order.
3. Fields preserve SQL declaration order.

## Field number stability
- New fields get the next available `field_number`.
- Existing fields keep their original `field_number`.
- Removal does not renumber existing fields.
