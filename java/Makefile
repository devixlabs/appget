# Note to LLMs, DO NOT MUDDY THIS FILE UP, instead create a bash script if you're hacking on something to please your user overlords.

.PHONY: all help setup features-to-specs parse-schema generate-proto generate-specs generate-registry generate-openapi generate-server generate-test-script generate build test run run-server test-api clean

GRADLE_VERSION := 9.3.1

help:
	@echo "Available commands:"
	@echo "  make setup             - Initialize Gradle wrapper (Java 25 support)"
	@echo "  make help              - Show this help message"
	@echo "  make all               - Build everything (clean, generate, test, build)"
	@echo "  make features-to-specs - Convert .feature files + metadata.yaml to specs.yaml"
	@echo "  make parse-schema      - Parse schema.sql + views.sql and generate models.yaml"
	@echo "  make generate-proto    - schema.sql -> .proto -> protoc -> Java model classes"
	@echo "  make generate          - Generate models (protoc), specs, metadata, and OpenAPI"
	@echo "  make generate-specs    - Generate specification classes from YAML"
	@echo "  make generate-registry - Generate DescriptorRegistry from models.yaml"
	@echo "  make generate-openapi  - Generate OpenAPI spec from .proto files"
	@echo "  make generate-server   - Generate Spring Boot server from models and specs"
	@echo "  make generate-test-script - Generate API test script from OpenAPI spec"
	@echo "  make build             - Full build: parse schema, generate, then compile"
	@echo "  make test              - Run unit tests"
	@echo "  make run               - Build and run the Java Rule Engine POC"
	@echo "  make run-server        - Build and run the Spring Boot server"
	@echo "  make test-api          - Run the generated API test script (requires server running)"
	@echo "  make clean             - Remove build artifacts and generated code"

# Initialize gradle wrapper (required for Java 25 support)
setup:
	@if [ ! -f "gradlew" ]; then \
		echo "Initializing Gradle wrapper with version $(GRADLE_VERSION)..."; \
		gradle wrapper --gradle-version=$(GRADLE_VERSION); \
		chmod +x gradlew; \
	fi

# Default target - build everything
all: setup clean generate test build

# Convert .feature files + metadata.yaml to specs.yaml
features-to-specs: setup
	@echo "Converting .feature files + metadata.yaml to specs.yaml..."
	@./gradlew featuresToSpecs

# Parse schema.sql + views.sql and generate models.yaml
parse-schema: setup
	@echo "Parsing schema.sql + views.sql and generating models.yaml..."
	@./gradlew parseSchema

# Full build: compile manual code, generate from YAML, compile everything
build: setup
	@echo "Building with Gradle (includes parsing schema, generating specs and compiling)..."
	@./gradlew build

# Generate protobuf models: models.yaml -> .proto -> protoc -> Java classes
generate-proto: setup
	@echo "Generating protobuf models from models.yaml..."
	@./gradlew generateProto

# Generate specifications only
generate-specs: setup
	@echo "Generating specifications from YAML..."
	@./gradlew generateSpecs -PspecsFile=specs.yaml

# Generate DescriptorRegistry from models.yaml
generate-registry: setup
	@echo "Generating DescriptorRegistry from models.yaml..."
	@./gradlew generateDescriptorRegistry

# Generate OpenAPI spec only
generate-openapi: setup
	@echo "Generating OpenAPI spec from YAML..."
	@./gradlew generateOpenAPI

# Generate Spring Boot server only
generate-server: setup
	@echo "✓ Generating Spring Boot server from models and specs..."
	@./gradlew generateServer

# Generate models (protoc), specs, registry, and OpenAPI
generate: features-to-specs generate-proto generate-specs generate-registry generate-openapi generate-server

# Run the application
run: build
	@./gradlew run

# Run unit tests
test: setup
	@./gradlew test

# Build and run the Spring Boot server
run-server: build generate-server
	@echo "✓ Starting Spring Boot server..."
	@cd generated-server && ../gradlew bootRun

# Generate API test script from OpenAPI spec
generate-test-script: setup
	@echo "✓ Generating API test script from OpenAPI spec..."
	@./gradlew generateTestScript

# Run API test script (requires server to be running)
test-api: generate-test-script
	@echo "✓ Running API tests..."
	@./test-api.sh

# Clean build artifacts and generated code
clean: setup
	@./gradlew clean
