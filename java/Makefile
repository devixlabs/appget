# Note to LLMs, DO NOT MUDDY THIS FILE UP, instead create a bash script if you're hacking on something to please your user overlords.

.PHONY: all help check features-to-specs parse-schema generate-proto generate-specs generate-registry generate-openapi generate-server _generate-test-script generate build test run run-server test-api clean

GRADLE_VERSION := 9.3.1

help:
	@echo "Available commands:"
	@echo "  make check             - Static analysis and tooling checks"
	@echo "  make help              - Show this help message"
	@echo "  make all               - Build everything (clean, generate, test, build)"
	@echo "  make features-to-specs - Convert .feature files + metadata.yaml to specs.yaml"
	@echo "  make parse-schema      - Parse schema.sql + views.sql and generate models.yaml"
	@echo "  make generate-proto    - schema.sql -> .proto -> protoc -> Java model classes"
	@echo "  make generate          - Generate models (protoc), specs, metadata, and OpenAPI"
	@echo "  make generate-specs    - Generate specification classes from YAML"
	@echo "  make generate-registry - Generate DescriptorRegistry from models.yaml"
	@echo "  make generate-openapi  - Generate OpenAPI spec from .proto files"
	@echo "  make generate-server   - Generate application server from models and specs"
	@echo "  make build             - Full build: parse schema, generate, then compile"
	@echo "  make test              - Run unit tests"
	@echo "  make run               - Build and run the Java Rule Engine POC"
	@echo "  make run-server        - Build and run the application server"
	@echo "  make test-api          - Run the generated API test script (requires server running)"
	@echo "  make clean             - Remove build artifacts and generated code"

check:
	@if [ ! -f "gradlew" ]; then \
		echo "Initializing Gradle wrapper with version $(GRADLE_VERSION)..."; \
		gradle wrapper --gradle-version=$(GRADLE_VERSION); \
		chmod +x gradlew; \
	fi

# Default target - build everything
all: check clean generate test build

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

# Generate models (protoc), specs, registry, and OpenAPI
generate: features-to-specs generate-proto generate-specs generate-registry generate-openapi generate-server

# Run the application
run: build
	@./gradlew run

# Run unit tests
test: build
	@./gradlew test

# Build, test, and run the application server
run-server: test generate-server
	@echo "✓ Starting application server..."
	@cd generated-server && ../gradlew bootRun

_generate-test-script:
	@echo "✓ Generating API test script from OpenAPI spec..."
	@./gradlew generateTestScript
	@shellcheck generated-server/test-api.sh

# Run API test script (requires server to be running)
test-api: _generate-test-script
	@echo "✓ Running API tests..."
	@./generated-server/test-api.sh

# Clean build artifacts and generated code
clean: check
	@./gradlew clean
