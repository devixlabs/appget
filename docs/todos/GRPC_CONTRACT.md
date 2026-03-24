# gRPC Contract (Draft)

gRPC services are generated for models, not views.

## Service pattern
For each model `Users`:
- `message UsersId` contains primary key fields.
- `rpc CreateUsers(Users) returns (Users)`
- `rpc GetUsers(UsersId) returns (Users)`
- `rpc UpdateUsers(Users) returns (Users)`
- `rpc DeleteUsers(UsersId) returns (google.protobuf.Empty)`
- `rpc ListUsers(google.protobuf.Empty) returns (UsersList)`

`UsersList` has `repeated Users items`.

## Composite keys
- `UsersId` includes all primary key fields in order.

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
