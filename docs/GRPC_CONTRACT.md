# gRPC Contract (Draft)

gRPC services are generated for models, not views.

## Service pattern
For each model `Employee`:
- `message EmployeeKey` contains primary key fields.
- `rpc CreateEmployee(Employee) returns (Employee)`
- `rpc GetEmployee(EmployeeKey) returns (Employee)`
- `rpc UpdateEmployee(Employee) returns (Employee)`
- `rpc DeleteEmployee(EmployeeKey) returns (google.protobuf.Empty)`
- `rpc ListEmployees(google.protobuf.Empty) returns (EmployeeList)`

`EmployeeList` has `repeated Employee items`.

## Composite keys
- `EmployeeKey` includes all primary key fields in order.

## Blocking rules
- Rule evaluation mirrors REST behavior.
- If any blocking rule fails, return `INVALID_ARGUMENT`.
- If `google.rpc.Status` is available, attach rule outcomes as details.
- If not, return a minimal error message.

## Metadata
- Metadata keys mirror REST header names but must be lowercased for gRPC metadata.
- Example: `x-sso-authenticated`.

## Error mapping
- Metadata parse failure -> `INVALID_ARGUMENT`.
- Not found -> `NOT_FOUND`.
- Rule violations -> `INVALID_ARGUMENT`.
