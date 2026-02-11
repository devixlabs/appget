package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Schema To Proto Converter Tests")
class SchemaToProtoConverterTest {

    private SchemaToProtoConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SchemaToProtoConverter();
    }

    @Test
    @DisplayName("Converter should exist and be instantiable")
    void testConverterInstantiation() {
        assertNotNull(converter, "SchemaToProtoConverter should be instantiable");
    }

    @Test
    @DisplayName("Converter generates proto files from schema.sql")
    void testGeneratesProtoFiles(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            assertTrue(Files.exists(tempDir.resolve("appget_models.proto")), "appget_models.proto should be generated");
            assertTrue(Files.exists(tempDir.resolve("hr_models.proto")), "hr_models.proto should be generated");
            assertTrue(Files.exists(tempDir.resolve("finance_models.proto")), "finance_models.proto should be generated");
        }
    }

    @Test
    @DisplayName("Generated proto has correct syntax and java_package for appget domain")
    void testAppgetProtoContent(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertTrue(content.contains("syntax = \"proto3\""), "Should use proto3 syntax");
            assertTrue(content.contains("java_package = \"dev.appget.model\""), "Should have correct java_package");
            assertTrue(content.contains("java_multiple_files = true"), "Should enable multiple files");
            assertTrue(content.contains("message Employee"), "Should contain Employee message");
            assertTrue(content.contains("message Role"), "Should contain Role message");
        }
    }

    @Test
    @DisplayName("Employee message has correct field types")
    void testEmployeeFieldTypes(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertTrue(content.contains("string name = 1"), "Employee should have string name field");
            assertTrue(content.contains("int32 age = 2"), "Employee should have int32 age field");
            assertTrue(content.contains("string role_id = 3"), "Employee should have string role_id field");
        }
    }

    @Test
    @DisplayName("HR domain proto has correct java_package")
    void testHrProtoPackage(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("hr_models.proto"));
            assertTrue(content.contains("java_package = \"dev.appget.hr.model\""), "HR domain should have correct java_package");
            assertTrue(content.contains("message Department"), "Should contain Department message");
            assertTrue(content.contains("message Salary"), "Should contain Salary message");
        }
    }

    @Test
    @DisplayName("Decimal SQL type maps to double proto type")
    void testDecimalTypeMapping(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("hr_models.proto"));
            assertTrue(content.contains("double budget"), "DECIMAL should map to double");
            assertTrue(content.contains("double amount"), "DECIMAL should map to double");
        }
    }

    @Test
    @DisplayName("Finance domain proto has correct content")
    void testFinanceProtoContent(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("finance_models.proto"));
            assertTrue(content.contains("java_package = \"dev.appget.finance.model\""), "Finance domain java_package");
            assertTrue(content.contains("message Invoice"), "Should contain Invoice message");
            assertTrue(content.contains("string issue_date"), "DATE should map to string");
        }
    }

    @Test
    @DisplayName("View proto files are generated")
    void testViewProtoGeneration(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists() && new File("views.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            assertTrue(Files.exists(tempDir.resolve("appget_views.proto")), "appget_views.proto should be generated");
            assertTrue(Files.exists(tempDir.resolve("hr_views.proto")), "hr_views.proto should be generated");
        }
    }

    @Test
    @DisplayName("View proto has correct java_package and message")
    void testViewProtoContent(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists() && new File("views.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("appget_views.proto"));
            assertTrue(content.contains("java_package = \"dev.appget.view\""), "View should have correct java_package");
            assertTrue(content.contains("message EmployeeSalaryView"), "Should contain EmployeeSalaryView message");
            assertTrue(content.contains("string employee_name"), "Should have employee_name field");
            assertTrue(content.contains("double salary_amount"), "Should have salary_amount field (double from DECIMAL)");
        }
    }

    @Test
    @DisplayName("Generated proto has DO NOT EDIT comment")
    void testGeneratedComment(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertTrue(content.contains("DO NOT EDIT MANUALLY"), "Should have do-not-edit warning");
        }
    }

    // ---- Phase 2: Rule embedding tests ----

    @Test
    @DisplayName("Proto with specs.yaml imports rules.proto")
    void testRulesImport(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists() && new File("specs.yaml").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertTrue(content.contains("import \"rules.proto\""), "Should import rules.proto when specs are provided");
        }
    }

    @Test
    @DisplayName("Employee message has embedded business rules")
    void testEmployeeRulesEmbedded(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists() && new File("specs.yaml").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertTrue(content.contains("option (rules.rule_set)"), "Employee should have rule_set option");
            assertTrue(content.contains("name: \"EmployeeAgeCheck\""), "Should contain EmployeeAgeCheck rule");
            assertTrue(content.contains("name: \"SeniorManagerCheck\""), "Should contain SeniorManagerCheck rule");
            assertTrue(content.contains("name: \"AuthenticatedApproval\""), "Should contain AuthenticatedApproval rule");
            assertTrue(content.contains("blocking: true"), "EmployeeAgeCheck should be blocking");
        }
    }

    @Test
    @DisplayName("Compound rule has correct AND/OR structure in proto")
    void testCompoundRuleStructure(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists() && new File("specs.yaml").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertTrue(content.contains("compound_conditions:"), "SeniorManagerCheck should have compound_conditions");
            assertTrue(content.contains("logic: \"AND\""), "SeniorManagerCheck should use AND logic");
        }
    }

    @Test
    @DisplayName("Metadata requirements embedded in proto options")
    void testMetadataRequirementsEmbedded(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists() && new File("specs.yaml").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertTrue(content.contains("metadata_requirements:"), "AuthenticatedApproval should have metadata_requirements");
            assertTrue(content.contains("category: \"sso\""), "Should require sso metadata");
            assertTrue(content.contains("category: \"roles\""), "Should require roles metadata");
        }
    }

    @Test
    @DisplayName("View proto has embedded rules when specs provided")
    void testViewRulesEmbedded(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists() && new File("specs.yaml").exists() && new File("views.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String content = Files.readString(tempDir.resolve("appget_views.proto"));
            assertTrue(content.contains("name: \"HighEarnerCheck\""), "EmployeeSalaryView should have HighEarnerCheck rule");
            assertTrue(content.contains("import \"rules.proto\""), "View proto should import rules.proto");
        }
    }

    @Test
    @DisplayName("Proto without specs.yaml has no rules import")
    void testNoRulesWithoutSpecs(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir);

            String content = Files.readString(tempDir.resolve("appget_models.proto"));
            assertFalse(content.contains("import \"rules.proto\""), "Should not import rules.proto without specs");
            assertFalse(content.contains("rule_set"), "Should not have rule_set without specs");
        }
    }

    // ---- Phase 3: gRPC service generation tests ----

    @Test
    @DisplayName("Service proto files are generated for each domain")
    void testServiceProtoGeneration(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            assertTrue(Files.exists(tempDir.resolve("appget_services.proto")), "appget_services.proto should be generated");
            assertTrue(Files.exists(tempDir.resolve("hr_services.proto")), "hr_services.proto should be generated");
            assertTrue(Files.exists(tempDir.resolve("finance_services.proto")), "finance_services.proto should be generated");
        }
    }

    @Test
    @DisplayName("Service proto has CRUD operations for Employee")
    void testServiceCrudOperations(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String content = Files.readString(tempDir.resolve("appget_services.proto"));
            assertTrue(content.contains("service EmployeeService"), "Should have EmployeeService");
            assertTrue(content.contains("rpc CreateEmployee"), "Should have CreateEmployee RPC");
            assertTrue(content.contains("rpc GetEmployee"), "Should have GetEmployee RPC");
            assertTrue(content.contains("rpc UpdateEmployee"), "Should have UpdateEmployee RPC");
            assertTrue(content.contains("rpc DeleteEmployee"), "Should have DeleteEmployee RPC");
            assertTrue(content.contains("rpc ListEmployees"), "Should have ListEmployees RPC");
        }
    }

    @Test
    @DisplayName("Service proto has correct java_package")
    void testServiceProtoPackage(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String appgetContent = Files.readString(tempDir.resolve("appget_services.proto"));
            assertTrue(appgetContent.contains("java_package = \"dev.appget.service\""), "Appget service package");

            String hrContent = Files.readString(tempDir.resolve("hr_services.proto"));
            assertTrue(hrContent.contains("java_package = \"dev.appget.hr.service\""), "HR service package");
        }
    }

    @Test
    @DisplayName("Service proto has method-level authorization options")
    void testServiceAuthOptions(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("schema.sql").exists()) {
            converter.convert("schema.sql", "views.sql", outputDir, "specs.yaml");

            String content = Files.readString(tempDir.resolve("appget_services.proto"));
            assertTrue(content.contains("required_role"), "Create/Update should require role");
            assertTrue(content.contains("check_ownership"), "Delete should check ownership");
        }
    }
}
