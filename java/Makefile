# Note to LLMs, DO NOT MUDDY THIS FILE UP, instead create a bash script if you're hacking on something to please your user overlords.

.PHONY: all help check features-to-specs parse-schema generate-proto generate-specs generate-registry generate-openapi generate-server generate-html _generate-default-script generate build test run-server test-api verify clean

help:
	@echo "Available commands:"
	@echo "  make check             - Static analysis and tooling checks"
	@echo "  make help              - Show this help message"
	@echo "  make all               - Full build pipeline: clean → generate → test → build (no server needed)"
	@echo "  make features-to-specs - Convert .feature files + metadata.yaml to specs.yaml"
	@echo "  make parse-schema      - Parse schema.sql + views.sql and generate models.yaml"
	@echo "  make generate-proto    - schema.sql -> .proto -> protoc -> Java model classes"
	@echo "  make generate          - Generate models (protoc), specs, metadata, and OpenAPI"
	@echo "  make generate-specs    - Generate specification classes from YAML"
	@echo "  make generate-registry - Generate DescriptorRegistry from models.yaml"
	@echo "  make generate-openapi  - Generate OpenAPI spec from .proto files"
	@echo "  make generate-server   - Generate application server from models and specs"
	@echo "  make generate-html     - Generate static HTML CRUD pages from models + specs"
	@echo "  make build             - Full build: parse schema, generate, then compile"
	@echo "  make test              - Run unit tests"
	@echo "  make run-server        - Build and run the application server"
	@echo "  make verify            - All server-dependent tests: rule engine + API + HTTP (requires server running)"
	@echo "  make test-api          - Regenerate + run API test script (requires server running)"
	@echo "  make clean             - Remove build artifacts and generated code"

check:
	@shellcheck scripts/check.sh
	@shellcheck scripts/run-server.sh
	@shellcheck scripts/verify.sh
	@scripts/check.sh

# Clean build artifacts and generated code
clean:
	@echo "✓ Cleaning build artifacts..."
	@./gradlew clean

# Default target - full build pipeline (no server needed)
all: check clean generate test build _generate-default-script

# All server-dependent tests (requires server running on port 8080)
# Runs: rule engine verification + API integration tests + HTTP endpoint tests
# Pure live-server testing — no rebuild, no regen. The running server and the
# generated API-test script are prepared by 'make run-server' (or 'make all').
verify:
	@scripts/verify.sh

# Run API test script (requires server to be running)
test-api: check _generate-default-script
	@echo "✓ Running API tests..."
	@./generated-server/verify.sh

# Convert .feature files + metadata.yaml to specs.yaml
features-to-specs: check
	@echo "Converting .feature files + metadata.yaml to specs.yaml..."
	@./gradlew featuresToSpecs

# Parse schema.sql + views.sql and generate models.yaml
parse-schema: check
	@echo "Parsing schema.sql + views.sql and generating models.yaml..."
	@./gradlew parseSchema

# Full build: compile manual code, generate from YAML, compile everything
build: check
	@echo "Building with Gradle (includes parsing schema, generating specs and compiling)..."
	@./gradlew build

# Generate protobuf models: models.yaml -> .proto -> protoc -> Java classes
generate-proto: check
	@echo "Generating protobuf models from models.yaml..."
	@./gradlew generateProto

# Generate specifications only
generate-specs: check
	@echo "Generating specifications from YAML..."
	@./gradlew generateSpecs -PspecsFile=specs.yaml

# Generate DescriptorRegistry from models.yaml
generate-registry: check
	@echo "Generating DescriptorRegistry from models.yaml..."
	@./gradlew generateDescriptorRegistry

# Generate OpenAPI spec only
generate-openapi: check
	@echo "Generating OpenAPI spec from YAML..."
	@./gradlew generateOpenAPI

# Generate application server only
generate-server: check
	@echo "✓ Generating application server from models and specs..."
	@./gradlew generateServer

# Generate static HTML CRUD pages
generate-html: check
	@echo "✓ Generating HTML CRUD pages..."
	@./gradlew generateHtml

# Generate models (protoc), specs, registry, and OpenAPI
generate: features-to-specs generate-proto generate-specs generate-registry generate-openapi generate-server generate-html

# Run unit tests
test: build
	@./gradlew test

# Build, test, and run the application server (also generates the API-test script
# so 'make verify' from a second terminal has everything it needs)
run-server: test generate-server _generate-default-script
	@scripts/run-server.sh

# --- PRIVATE TARGETS (called by other targets / LLM agents) -----------------
# Internal implementation details. Do NOT call directly.

# Writes into generated-server/, so depend on generate-server creating that dir
_generate-default-script: generate-server
	@echo "✓ Generating API default script from OpenAPI spec..."
	@./gradlew generateDefaultScript
	@shellcheck generated-server/verify.sh

