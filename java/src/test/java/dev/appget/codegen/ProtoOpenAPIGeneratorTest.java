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
import dev.appget.codegen.JavaUtils;

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
    @DisplayName("snake_case to camelCase conversion works correctly")
    void testSnakeToCamelConversion() {
        assertEquals("roleId", JavaUtils.snakeToCamel("role_id"));
        assertEquals("invoiceNumber", JavaUtils.snakeToCamel("invoice_number"));
        assertEquals("yearsOfService", JavaUtils.snakeToCamel("years_of_service"));
        assertEquals("name", JavaUtils.snakeToCamel("name"));
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
