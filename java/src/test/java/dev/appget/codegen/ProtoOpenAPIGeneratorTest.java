package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import dev.appget.naming.JavaNaming;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Proto-First OpenAPI Generator Tests")
class ProtoOpenAPIGeneratorTest {

    private ProtoOpenAPIGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ProtoOpenAPIGenerator();
    }

    @Test
    @DisplayName("Generator should instantiate successfully")
    void testInstantiation() {
        assertNotNull(generator);
    }

    @Test
    @DisplayName("Generates openapi.yaml from proto files")
    void testGeneratesOpenAPIFromProto(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("openapi.yaml");
        if (Files.exists(Path.of("src/main/proto/appget_models.proto"))) {
            generator.generate("src/main/proto", outputFile.toString());
            assertTrue(Files.exists(outputFile), "openapi.yaml should be generated");
            String content = Files.readString(outputFile);
            assertTrue(content.contains("openapi: 3.0.0"), "Should have OpenAPI 3.0.0");
        }
    }

    @Test
    @DisplayName("Spec has correct info section")
    void testInfoSection(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> info = (Map<String, Object>) spec.get("info");
        assertNotNull(info, "Should have info section");
        assertEquals("AppGet API", info.get("title"));
        assertEquals("1.0.0", info.get("version"));
        assertTrue(((String) info.get("description")).contains("protobuf"),
                "Description should reference protobuf");
    }

    @Test
    @DisplayName("All model schemas generated from proto messages")
    void testAllModelSchemas(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> schemas = getSchemas(spec);
        String[] expectedModels = {"Roles", "Employees", "Departments", "Salaries", "Invoices"};
        for (String model : expectedModels) {
            assertNotNull(schemas.get(model), "Schema for " + model + " should exist");
        }
    }

    @Test
    @DisplayName("View schemas generated from proto view messages")
    void testViewSchemas(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> schemas = getSchemas(spec);
        assertNotNull(schemas.get("EmployeeSalaryView"), "EmployeeSalaryView schema should exist");
        assertNotNull(schemas.get("DepartmentBudgetView"), "DepartmentBudgetView schema should exist");
    }

    @Test
    @DisplayName("Employee schema has correct fields from proto")
    void testEmployeeSchemaFields(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> schemas = getSchemas(spec);
        Map<String, Object> employee = (Map<String, Object>) schemas.get("Employees");
        Map<String, Object> props = (Map<String, Object>) employee.get("properties");

        assertTrue(props.containsKey("name"), "Should have name field");
        assertTrue(props.containsKey("age"), "Should have age field");
        assertTrue(props.containsKey("roleId"), "Proto snake_case role_id -> camelCase roleId");
    }

    @Test
    @DisplayName("Proto types map correctly to OpenAPI types")
    void testProtoTypeMapping(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> schemas = getSchemas(spec);
        Map<String, Object> employee = (Map<String, Object>) schemas.get("Employees");
        Map<String, Object> props = (Map<String, Object>) employee.get("properties");

        Map<String, Object> age = (Map<String, Object>) props.get("age");
        assertEquals("integer", age.get("type"), "int32 -> integer");
        assertEquals("int32", age.get("format"), "int32 -> format int32");

        Map<String, Object> name = (Map<String, Object>) props.get("name");
        assertEquals("string", name.get("type"), "string -> string");
    }

    @Test
    @DisplayName("Double proto type maps to number/double")
    void testDoubleTypeMapping(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> schemas = getSchemas(spec);
        // Location has double fields (longitude, latitude) which remain proto double
        Map<String, Object> location = (Map<String, Object>) schemas.get("Locations");
        Map<String, Object> props = (Map<String, Object>) location.get("properties");

        Map<String, Object> longitude = (Map<String, Object>) props.get("longitude");
        assertEquals("number", longitude.get("type"), "double -> number");
        assertEquals("double", longitude.get("format"), "double -> format double");
    }

    @Test
    @DisplayName("Full CRUD collection paths generated for all models")
    void testCollectionPaths(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        String[] expectedPaths = {"/roles", "/employees", "/departments", "/salaries", "/invoices"};
        for (String path : expectedPaths) {
            assertNotNull(paths.get(path), "Collection path " + path + " should exist");
        }
    }

    @Test
    @DisplayName("Item paths with {id} generated for all models")
    void testItemPaths(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        String[] expectedPaths = {"/roles/{id}", "/employees/{id}", "/departments/{id}",
                "/salaries/{id}", "/invoices/{id}"};
        for (String path : expectedPaths) {
            assertNotNull(paths.get(path), "Item path " + path + " should exist");
        }
    }

    @Test
    @DisplayName("Collection paths have POST and GET operations")
    void testCollectionOperations(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> employees = (Map<String, Object>) paths.get("/employees");
        assertNotNull(employees.get("post"), "/employees should have POST");
        assertNotNull(employees.get("get"), "/employees should have GET");
    }

    @Test
    @DisplayName("Item paths have GET, PUT, DELETE operations")
    void testItemOperations(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> employeeItem = (Map<String, Object>) paths.get("/employees/{id}");
        assertNotNull(employeeItem.get("get"), "/employees/{id} should have GET");
        assertNotNull(employeeItem.get("put"), "/employees/{id} should have PUT");
        assertNotNull(employeeItem.get("delete"), "/employees/{id} should have DELETE");
    }

    @Test
    @DisplayName("POST returns 201 and 422 responses")
    void testPostResponses(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> employees = (Map<String, Object>) paths.get("/employees");
        Map<String, Object> post = (Map<String, Object>) employees.get("post");
        Map<String, Object> responses = (Map<String, Object>) post.get("responses");

        assertNotNull(responses.get("201"), "POST should have 201 response");
        assertNotNull(responses.get("422"), "POST should have 422 response");
    }

    @Test
    @DisplayName("DELETE returns 204 and 404 responses")
    void testDeleteResponses(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> employeeItem = (Map<String, Object>) paths.get("/employees/{id}");
        Map<String, Object> delete = (Map<String, Object>) employeeItem.get("delete");
        Map<String, Object> responses = (Map<String, Object>) delete.get("responses");

        assertNotNull(responses.get("204"), "DELETE should have 204 response");
        assertNotNull(responses.get("404"), "DELETE should have 404 response");
    }

    @Test
    @DisplayName("No securitySchemes in proto-only OpenAPI (auth policy lives in specs.yaml, not proto)")
    void testNoSecuritySchemesInProtoOnlyOpenAPI(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        if (components == null) return;
        // Proto files no longer embed auth options; security policy lives in specs.yaml.
        // The OpenAPI generator should not emit securitySchemes when no required_role is present.
        Map<String, Object> secSchemes = (Map<String, Object>) components.get("securitySchemes");
        assertNull(secSchemes, "Proto-only OpenAPI should have no securitySchemes; auth belongs in specs.yaml");
    }

    @Test
    @DisplayName("Endpoints have no security requirement when proto has no auth options")
    void testNoSecurityOnEndpointsWithoutAuthOptions(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> employees = (Map<String, Object>) paths.get("/employees");
        Map<String, Object> post = (Map<String, Object>) employees.get("post");

        // Auth policy lives in specs.yaml; proto has no required_role options.
        assertNull(post.get("security"), "POST /employees should have no security requirement when proto has no auth options");
    }

    @Test
    @DisplayName("GET endpoints have no per-endpoint security requirement")
    void testNoSecurityOnGetEndpoints(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> employees = (Map<String, Object>) paths.get("/employees");
        Map<String, Object> get = (Map<String, Object>) employees.get("get");

        assertNull(get.get("security"), "GET list /employees should not require security");
    }

    @Test
    @DisplayName("Operations have operationId and tags")
    void testOperationMetadata(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> employees = (Map<String, Object>) paths.get("/employees");
        Map<String, Object> post = (Map<String, Object>) employees.get("post");

        assertEquals("createEmployees", post.get("operationId"));
        List<String> tags = (List<String>) post.get("tags");
        assertTrue(tags.contains("Employees"), "Should be tagged with Employees");
    }

    @Test
    @DisplayName("View schema has correct fields from view proto")
    void testViewSchemaFields(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> schemas = getSchemas(spec);
        Map<String, Object> view = (Map<String, Object>) schemas.get("EmployeeSalaryView");
        assertNotNull(view, "EmployeeSalaryView should be in schemas");

        Map<String, Object> props = (Map<String, Object>) view.get("properties");
        assertTrue(props.containsKey("employeeName"), "Should have employeeName");
        assertTrue(props.containsKey("salaryAmount"), "Should have salaryAmount");
    }

    @Test
    @DisplayName("View collection paths exist under /views/")
    void testViewCollectionPaths(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        // PostDetailView → strip View → PostDetail → camelToKebab → post-detail → /views/post-detail
        assertNotNull(paths.get("/views/post-detail"), "/views/post-detail should exist");
        assertNotNull(paths.get("/views/user-feed"), "/views/user-feed should exist");
    }

    @Test
    @DisplayName("View item paths with {id} exist under /views/")
    void testViewItemPaths(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        assertNotNull(paths.get("/views/post-detail/{id}"), "/views/post-detail/{id} should exist");
        assertNotNull(paths.get("/views/user-feed/{id}"), "/views/user-feed/{id} should exist");
    }

    @Test
    @DisplayName("View collection paths have GET only (no POST)")
    void testViewCollectionGetOnly(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> postDetailCollection = (Map<String, Object>) paths.get("/views/post-detail");
        assertNotNull(postDetailCollection, "/views/post-detail should exist");
        assertNotNull(postDetailCollection.get("get"), "/views/post-detail should have GET");
        assertNull(postDetailCollection.get("post"), "/views/post-detail should NOT have POST");
    }

    @Test
    @DisplayName("View item paths have GET only (no PUT or DELETE)")
    void testViewItemGetOnly(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> postDetailItem = (Map<String, Object>) paths.get("/views/post-detail/{id}");
        assertNotNull(postDetailItem, "/views/post-detail/{id} should exist");
        assertNotNull(postDetailItem.get("get"), "/views/post-detail/{id} should have GET");
        assertNull(postDetailItem.get("put"), "/views/post-detail/{id} should NOT have PUT");
        assertNull(postDetailItem.get("delete"), "/views/post-detail/{id} should NOT have DELETE");
    }

    @Test
    @DisplayName("View GET list returns 200 response with array schema ref")
    void testViewGetListResponse(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> collection = (Map<String, Object>) paths.get("/views/post-detail");
        Map<String, Object> get = (Map<String, Object>) collection.get("get");
        Map<String, Object> responses = (Map<String, Object>) get.get("responses");
        Map<String, Object> response200 = (Map<String, Object>) responses.get("200");

        assertNotNull(response200, "GET list should have 200 response");
        Map<String, Object> content = (Map<String, Object>) response200.get("content");
        Map<String, Object> json = (Map<String, Object>) content.get("application/json");
        Map<String, Object> schema = (Map<String, Object>) json.get("schema");
        assertEquals("array", schema.get("type"), "GET list response schema should be array");
        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        assertEquals("#/components/schemas/PostDetailView", items.get("$ref"),
                "Array items should reference PostDetailView schema");
    }

    @Test
    @DisplayName("View GET by ID returns 200 and 404 responses")
    void testViewGetByIdResponse(@TempDir Path tempDir) throws Exception {
        Map<String, Object> spec = generateAndLoad(tempDir);
        if (spec == null) return;

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> item = (Map<String, Object>) paths.get("/views/post-detail/{id}");
        Map<String, Object> get = (Map<String, Object>) item.get("get");
        Map<String, Object> responses = (Map<String, Object>) get.get("responses");

        assertNotNull(responses.get("200"), "GET by ID should have 200 response");
        assertNotNull(responses.get("404"), "GET by ID should have 404 response");
    }

    @Test
    @DisplayName("snake_case to camelCase conversion works correctly")
    void testSnakeToCamelConversion() {
        assertEquals("roleId", JavaNaming.toFieldAccessor("role_id"));
        assertEquals("invoiceNumber", JavaNaming.toFieldAccessor("invoice_number"));
        assertEquals("yearsOfService", JavaNaming.toFieldAccessor("years_of_service"));
        assertEquals("name", JavaNaming.toFieldAccessor("name"));
    }

    @Test
    @DisplayName("Proto message parsing extracts correct fields")
    void testMessageParsing() {
        String proto = """
                syntax = "proto3";
                package test;
                message Foo {
                  string bar = 1;
                  int32 count = 2;
                  double amount = 3;
                }
                """;
        var messages = generator.parseMessages(proto, "test");
        assertEquals(1, messages.size(), "Should parse 1 message");
        assertEquals("Foo", messages.get(0).name());
        assertEquals(3, messages.get(0).fields().size(), "Should have 3 fields");
    }

    @Test
    @DisplayName("Proto service parsing extracts RPC methods")
    void testServiceParsing() {
        String proto = """
                syntax = "proto3";
                service FooService {
                  rpc CreateFoo(Foo) returns (Foo) {
                    option (rules.required_role) = "ROLE_USER";
                  }
                  rpc GetFoo(FooId) returns (Foo) {}
                  rpc ListFoos(google.protobuf.Empty) returns (FooList) {}
                }
                """;
        var services = generator.parseServices(proto);
        assertEquals(1, services.size());
        assertEquals("FooService", services.get(0).name());
        assertEquals(3, services.get(0).methods().size(), "Should have 3 methods");

        var create = services.get(0).methods().get(0);
        assertEquals("CreateFoo", create.name());
        assertEquals("ROLE_USER", create.requiredRole());

        var get = services.get(0).methods().get(1);
        assertNull(get.requiredRole(), "GetFoo has no required role");
    }

    @Test
    @DisplayName("Schema builder converts proto types correctly")
    void testSchemaBuild() {
        var msg = new ProtoOpenAPIGenerator.ProtoMessage("Test",
                List.of(
                        new ProtoOpenAPIGenerator.ProtoField("name", "string", 1),
                        new ProtoOpenAPIGenerator.ProtoField("count", "int32", 2),
                        new ProtoOpenAPIGenerator.ProtoField("value", "double", 3)
                ), "test", false);

        Map<String, Object> schema = generator.buildSchema(msg);
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertEquals("string", ((Map<String, Object>) props.get("name")).get("type"));
        assertEquals("integer", ((Map<String, Object>) props.get("count")).get("type"));
        assertEquals("int32", ((Map<String, Object>) props.get("count")).get("format"));
        assertEquals("number", ((Map<String, Object>) props.get("value")).get("type"));
        assertEquals("double", ((Map<String, Object>) props.get("value")).get("format"));
    }

    @Test
    @DisplayName("Decimal fields include x-precision and x-scale when models.yaml is loaded")
    void testDecimalFieldsHavePrecisionAndScale(@TempDir Path tempDir) throws Exception {
        // Write a minimal models.yaml with decimal precision/scale
        Path modelsYaml = tempDir.resolve("models.yaml");
        Files.writeString(modelsYaml, """
                schema_version: 1
                organization: test
                domains:
                  hr:
                    namespace: dev.test.hr
                    models:
                      - name: salaries
                        source_table: salaries
                        fields:
                          - name: employee_id
                            type: string
                            nullable: false
                            field_number: 1
                          - name: amount
                            type: decimal
                            nullable: false
                            field_number: 2
                            precision: 15
                            scale: 2
                """);

        // Load decimal precision lookup
        var lookup = generator.loadDecimalPrecision(modelsYaml.toString());
        assertNotNull(lookup.get("Salaries"), "Should have Salaries in lookup");
        assertNotNull(lookup.get("Salaries").get("amount"), "Should have amount field");
        assertEquals(15, lookup.get("Salaries").get("amount")[0], "Precision should be 15");
        assertEquals(2, lookup.get("Salaries").get("amount")[1], "Scale should be 2");

        // Write a minimal proto file
        Path protoDir = tempDir.resolve("proto");
        Files.createDirectories(protoDir);
        Files.writeString(protoDir.resolve("hr_models.proto"), """
                syntax = "proto3";
                import "appget_common.proto";
                option java_package = "dev.test.hr.model";
                option java_multiple_files = true;
                package hr;
                message Salaries {
                  string employee_id = 1;
                  appget.common.Decimal amount = 2;
                }
                """);

        Path outputFile = tempDir.resolve("openapi.yaml");
        generator.generateWithModelsYaml(protoDir.toString(), outputFile.toString(), modelsYaml.toString());

        Yaml yaml = new Yaml();
        Map<String, Object> spec = yaml.load(Files.readString(outputFile));
        Map<String, Object> schemas = getSchemas(spec);
        Map<String, Object> salaries = (Map<String, Object>) schemas.get("Salaries");
        Map<String, Object> props = (Map<String, Object>) salaries.get("properties");
        Map<String, Object> amountField = (Map<String, Object>) props.get("amount");

        assertEquals("string", amountField.get("type"), "Decimal type should be string");
        assertEquals("decimal", amountField.get("format"), "Decimal format should be decimal");
        assertEquals(15, amountField.get("x-precision"), "x-precision should be 15");
        assertEquals(2, amountField.get("x-scale"), "x-scale should be 2");
    }

    @Test
    @DisplayName("Non-decimal fields do NOT include x-precision or x-scale")
    void testNonDecimalFieldsHaveNoPrecisionOrScale(@TempDir Path tempDir) throws Exception {
        // Write a models.yaml with a decimal field and a non-decimal field
        Path modelsYaml = tempDir.resolve("models.yaml");
        Files.writeString(modelsYaml, """
                schema_version: 1
                organization: test
                domains:
                  hr:
                    namespace: dev.test.hr
                    models:
                      - name: salaries
                        source_table: salaries
                        fields:
                          - name: employee_id
                            type: string
                            nullable: false
                            field_number: 1
                          - name: amount
                            type: decimal
                            nullable: false
                            field_number: 2
                            precision: 15
                            scale: 2
                          - name: years_of_service
                            type: int32
                            nullable: true
                            field_number: 3
                """);

        // Write a proto file with string, decimal, and int32 fields
        Path protoDir = tempDir.resolve("proto");
        Files.createDirectories(protoDir);
        Files.writeString(protoDir.resolve("hr_models.proto"), """
                syntax = "proto3";
                import "appget_common.proto";
                option java_package = "dev.test.hr.model";
                option java_multiple_files = true;
                package hr;
                message Salaries {
                  string employee_id = 1;
                  appget.common.Decimal amount = 2;
                  optional int32 years_of_service = 3;
                }
                """);

        Path outputFile = tempDir.resolve("openapi.yaml");
        generator.generateWithModelsYaml(protoDir.toString(), outputFile.toString(), modelsYaml.toString());

        Yaml yaml = new Yaml();
        Map<String, Object> spec = yaml.load(Files.readString(outputFile));
        Map<String, Object> schemas = getSchemas(spec);
        Map<String, Object> salaries = (Map<String, Object>) schemas.get("Salaries");
        Map<String, Object> props = (Map<String, Object>) salaries.get("properties");

        // String field: no x-precision/x-scale
        Map<String, Object> employeeIdField = (Map<String, Object>) props.get("employeeId");
        assertNull(employeeIdField.get("x-precision"), "String field should not have x-precision");
        assertNull(employeeIdField.get("x-scale"), "String field should not have x-scale");

        // Int32 field: no x-precision/x-scale
        Map<String, Object> yearsField = (Map<String, Object>) props.get("yearsOfService");
        assertNull(yearsField.get("x-precision"), "Int32 field should not have x-precision");
        assertNull(yearsField.get("x-scale"), "Int32 field should not have x-scale");

        // Decimal field: HAS x-precision/x-scale
        Map<String, Object> amountField = (Map<String, Object>) props.get("amount");
        assertEquals(15, amountField.get("x-precision"), "Decimal field should have x-precision");
        assertEquals(2, amountField.get("x-scale"), "Decimal field should have x-scale");
    }

    @Test
    @DisplayName("Decimal fields without models.yaml have no x-precision or x-scale (backward compatible)")
    void testDecimalFieldsWithoutModelsYaml() {
        // Build schema WITHOUT loading any models.yaml (backward compatible)
        var msg = new ProtoOpenAPIGenerator.ProtoMessage("Salaries",
                List.of(
                        new ProtoOpenAPIGenerator.ProtoField("employee_id", "string", 1),
                        new ProtoOpenAPIGenerator.ProtoField("amount", "appget.common.Decimal", 2)
                ), "hr", false);

        Map<String, Object> schema = generator.buildSchema(msg);
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> amountField = (Map<String, Object>) props.get("amount");

        assertEquals("string", amountField.get("type"), "Decimal type should be string");
        assertEquals("decimal", amountField.get("format"), "Decimal format should be decimal");
        assertNull(amountField.get("x-precision"), "No x-precision without models.yaml");
        assertNull(amountField.get("x-scale"), "No x-scale without models.yaml");
    }

    @Test
    @DisplayName("Conformance: decimal fields in views also get x-precision and x-scale")
    void testViewDecimalFieldsPrecisionScale(@TempDir Path tempDir) throws Exception {
        // Write a models.yaml with a view that has decimal precision/scale
        Path modelsYaml = tempDir.resolve("models.yaml");
        Files.writeString(modelsYaml, """
                schema_version: 1
                organization: test
                domains:
                  appget:
                    namespace: dev.test
                    views:
                      - name: employee_salary_view
                        source_view: employee_salary_view
                        fields:
                          - name: employee_name
                            type: string
                            nullable: false
                            field_number: 1
                          - name: salary_amount
                            type: decimal
                            nullable: false
                            field_number: 2
                            precision: 15
                            scale: 2
                """);

        Path protoDir = tempDir.resolve("proto");
        Files.createDirectories(protoDir);
        Files.writeString(protoDir.resolve("appget_views.proto"), """
                syntax = "proto3";
                import "appget_common.proto";
                option java_package = "dev.test.view";
                option java_multiple_files = true;
                package appget_views;
                message EmployeeSalaryView {
                  string employee_name = 1;
                  appget.common.Decimal salary_amount = 2;
                }
                """);

        Path outputFile = tempDir.resolve("openapi.yaml");
        generator.generateWithModelsYaml(protoDir.toString(), outputFile.toString(), modelsYaml.toString());

        Yaml yaml = new Yaml();
        Map<String, Object> spec = yaml.load(Files.readString(outputFile));
        Map<String, Object> schemas = getSchemas(spec);
        Map<String, Object> view = (Map<String, Object>) schemas.get("EmployeeSalaryView");
        Map<String, Object> props = (Map<String, Object>) view.get("properties");
        Map<String, Object> salaryField = (Map<String, Object>) props.get("salaryAmount");

        assertEquals("string", salaryField.get("type"), "View decimal type should be string");
        assertEquals("decimal", salaryField.get("format"), "View decimal format should be decimal");
        assertEquals(15, salaryField.get("x-precision"), "View decimal x-precision should be 15");
        assertEquals(2, salaryField.get("x-scale"), "View decimal x-scale should be 2");
    }

    @Test
    @DisplayName("loadDecimalPrecision converts snake_case names to PascalCase correctly")
    void testSnakeToPascalConversion(@TempDir Path tempDir) throws Exception {
        Path modelsYaml = tempDir.resolve("models.yaml");
        Files.writeString(modelsYaml, """
                schema_version: 1
                organization: test
                domains:
                  hr:
                    namespace: dev.test.hr
                    models:
                      - name: departments
                        source_table: departments
                        fields:
                          - name: budget
                            type: decimal
                            nullable: false
                            field_number: 1
                            precision: 10
                            scale: 4
                    views:
                      - name: department_budget_view
                        source_view: department_budget_view
                        fields:
                          - name: department_budget
                            type: decimal
                            nullable: false
                            field_number: 1
                            precision: 15
                            scale: 2
                """);

        var lookup = generator.loadDecimalPrecision(modelsYaml.toString());

        // Simple name: departments -> Departments
        assertNotNull(lookup.get("Departments"), "Should have Departments");
        assertEquals(10, lookup.get("Departments").get("budget")[0], "Departments.budget precision");
        assertEquals(4, lookup.get("Departments").get("budget")[1], "Departments.budget scale");

        // Multi-word name: department_budget_view -> DepartmentBudgetView
        assertNotNull(lookup.get("DepartmentBudgetView"), "Should have DepartmentBudgetView");
        assertEquals(15, lookup.get("DepartmentBudgetView").get("department_budget")[0], "DepartmentBudgetView precision");
        assertEquals(2, lookup.get("DepartmentBudgetView").get("department_budget")[1], "DepartmentBudgetView scale");
    }

    // ---- Helper Methods ----

    @SuppressWarnings("unchecked")
    private Map<String, Object> generateAndLoad(Path tempDir) throws Exception {
        if (!Files.exists(Path.of("src/main/proto/appget_models.proto"))) {
            return null; // Proto files not generated yet
        }
        Path outputFile = tempDir.resolve("openapi.yaml");
        generator.generate("src/main/proto", outputFile.toString());
        Yaml yaml = new Yaml();
        return yaml.load(Files.readString(outputFile));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSchemas(Map<String, Object> spec) {
        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        return (Map<String, Object>) components.get("schemas");
    }
}
