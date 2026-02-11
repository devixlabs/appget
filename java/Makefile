.PHONY: all help features-to-specs parse-schema generate-proto generate-specs generate-registry generate-openapi generate-server generate-test-script generate build test run run-server test-api clean

help:
	@echo "Available commands:"
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

# Default target - build everything
all: clean generate test build

# Convert .feature files + metadata.yaml to specs.yaml
features-to-specs:
	@echo "Converting .feature files + metadata.yaml to specs.yaml..."
	@/opt/gradle/gradle-9.3.1/bin/gradle featuresToSpecs

# Parse schema.sql + views.sql and generate models.yaml
parse-schema:
	@echo "Parsing schema.sql + views.sql and generating models.yaml..."
	@/opt/gradle/gradle-9.3.1/bin/gradle parseSchema

# Full build: compile manual code, generate from YAML, compile everything
build:
	@echo "Building with Gradle (includes parsing schema, generating specs and compiling)..."
	@/opt/gradle/gradle-9.3.1/bin/gradle build

# Generate protobuf models: schema.sql -> .proto -> protoc -> Java classes
generate-proto:
	@echo "Generating protobuf models from schema.sql..."
	@/opt/gradle/gradle-9.3.1/bin/gradle generateProto

# Generate specifications only
generate-specs:
	@echo "Generating specifications from YAML..."
	@/opt/gradle/gradle-9.3.1/bin/gradle generateSpecs -PspecsFile=specs.yaml

# Generate DescriptorRegistry from models.yaml
generate-registry:
	@echo "Generating DescriptorRegistry from models.yaml..."
	@/opt/gradle/gradle-9.3.1/bin/gradle generateDescriptorRegistry

# Generate OpenAPI spec only
generate-openapi:
	@echo "Generating OpenAPI spec from YAML..."
	@/opt/gradle/gradle-9.3.1/bin/gradle generateOpenAPI

# Generate models (protoc), specs, registry, and OpenAPI
generate: features-to-specs generate-proto generate-specs generate-registry generate-openapi

# Generate Spring Boot server only
generate-server:
	@echo "✓ Generating Spring Boot server from models and specs..."
	@/opt/gradle/gradle-9.3.1/bin/gradle generateServer

# Generate API test script from OpenAPI spec
generate-test-script:
	@echo "✓ Generating API test script from OpenAPI spec..."
	@/opt/gradle/gradle-9.3.1/bin/gradle generateTestScript

# Run the application
run: build
	@/opt/gradle/gradle-9.3.1/bin/gradle run

# Run unit tests
test:
	@/opt/gradle/gradle-9.3.1/bin/gradle test

# Build and run the Spring Boot server (requires make generate-server first)
run-server: build
	@echo "✓ Starting Spring Boot server..."
	@cd generated-server && /opt/gradle/gradle-9.3.1/bin/gradle bootRun

# Run API test script (requires server to be running)
test-api:
	@if [ ! -f test-api.sh ]; then \
		echo "✗ test-api.sh not found. Run 'make generate-test-script' first."; \
		exit 1; \
	fi
	@echo "✓ Running API tests..."
	@./test-api.sh

# Clean build artifacts and generated code
clean:
	@/opt/gradle/gradle-9.3.1/bin/gradle clean
