# Proto Conventions (Draft)

Proto files are generated from `models.yaml` and must follow deterministic naming and packaging rules.

## File naming
- `${domain}_models.proto` for models
- `${domain}_views.proto` for views
- `${domain}_services.proto` for services
- `appget_common.proto` for shared types like Decimal

## Proto package names
- Models: `package ${domain};`
- Views: `package ${domain}_views;`
- Services: `package ${domain}_services;`

## Language package options
Language-specific package options are derived by convention from repo layout.

Java
- `java_package = "dev.appget[.<domain>].model"` for models
- `java_package = "dev.appget[.<domain>].view"` for views
- `java_package = "dev.appget[.<domain>].service"` for services

Go
- Use module path from `go/go.mod` if present
- go_package = `${module_path}/gen/${domain};${domain}pb`

Python
- Base package from `python/pyproject.toml` or `setup.cfg`
- Output under `<base>.gen.<domain>`

Ruby
- Base module `Appget::Gen::<Domain>`
- File path `appget/gen/<domain>`

Node
- Base package from `node/package.json` if present
- Output under `gen/<domain>`

If metadata is missing, generators should fall back to `appget` as the base name.
