package dev.appget.codegen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Models To Proto Converter Tests")
class ModelsToProtoConverterTest {

    private ModelsToProtoConverter converter;

    // Generated once per test class from ../schema.sql + ../views.sql
    private static Path tempModelsYaml;

    @BeforeAll
    static void generateModelsYaml() throws Exception {
        if (!new File("../schema.sql").exists()) {
            return;
        }
        tempModelsYaml = Files.createTempFile("models-test", ".yaml");
        new SQLSchemaParser().parseAndGenerate("../schema.sql", "../views.sql", tempModelsYaml.toString());
    }

    @BeforeEach
    void setUp() {
        converter = new ModelsToProtoConverter();
    }

    @Test
    @DisplayName("Converter should exist and be instantiable")
    void testConverterInstantiation() {
        assertNotNull(converter, "ModelsToProtoConverter should be instantiable");
    }

    @Test
    @DisplayName("Converter generates proto files from models.yaml")
    void testGeneratesProtoFiles(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        assertTrue(Files.exists(tempDir.resolve("auth_models.proto")), "auth_models.proto should be generated");
        assertTrue(Files.exists(tempDir.resolve("social_models.proto")), "social_models.proto should be generated");
        assertTrue(Files.exists(tempDir.resolve("admin_models.proto")), "admin_models.proto should be generated");
    }

    @Test
    @DisplayName("Generated proto has correct syntax and java_package for auth domain")
    void testAppgetProtoContent(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("auth_models.proto"));
        assertTrue(content.contains("syntax = \"proto3\""), "Should use proto3 syntax");
        assertTrue(content.contains("java_package = \"dev.appget.auth.model\""), "Should have correct java_package");
        assertTrue(content.contains("java_multiple_files = true"), "Should enable multiple files");
        assertTrue(content.contains("message Users"), "Should contain Users message");
        assertTrue(content.contains("message Sessions"), "Should contain Sessions message");
    }

    @Test
    @DisplayName("Users message has correct field types")
    void testEmployeeFieldTypes(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("auth_models.proto"));
        assertTrue(content.contains("string username"), "Users should have string username field");
        assertTrue(content.contains("string email"), "Users should have string email field");
        assertTrue(content.contains("bool is_verified"), "Users should have bool is_verified field");
    }

    @Test
    @DisplayName("social domain proto has correct java_package")
    void testHrProtoPackage(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("social_models.proto"));
        assertTrue(content.contains("java_package = \"dev.appget.social.model\""), "social domain should have correct java_package");
        assertTrue(content.contains("message Posts"), "Should contain Posts message");
        assertTrue(content.contains("message Comments"), "Should contain Comments message");
    }

    @Test
    @DisplayName("admin domain proto has correct field types")
    void testAdminFieldTypeMapping(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("admin_models.proto"));
        assertTrue(content.contains("int32 permission_level"), "INT should map to int32");
        assertTrue(content.contains("bool is_active"), "BOOLEAN should map to bool");
    }

    @Test
    @DisplayName("admin domain proto has correct content")
    void testAdminProtoContent(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("admin_models.proto"));
        assertTrue(content.contains("java_package = \"dev.appget.admin.model\""), "admin domain java_package");
        assertTrue(content.contains("message ModerationActions"), "Should contain ModerationActions message");
        assertTrue(content.contains("message Roles"), "Should contain Roles message");
    }

    @Test
    @DisplayName("View proto files are generated")
    void testViewProtoGeneration(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        assertTrue(Files.exists(tempDir.resolve("social_views.proto")), "social_views.proto should be generated");
    }

    @Test
    @DisplayName("View proto has correct java_package and message")
    void testViewProtoContent(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("social_views.proto"));
        assertTrue(content.contains("java_package = \"dev.appget.social.view\""), "View should have correct java_package");
        assertTrue(content.contains("message PostDetailView"), "Should contain PostDetailView message");
        assertTrue(content.contains("string post_content"), "Should have post_content field");
        assertTrue(content.contains("string author_username"), "Should have author_username field");
    }

    @Test
    @DisplayName("Generated proto has DO NOT EDIT comment")
    void testGeneratedComment(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("auth_models.proto"));
        assertTrue(content.contains("DO NOT EDIT MANUALLY"), "Should have do-not-edit warning");
    }

    @Test
    @DisplayName("Generated proto does not embed rule options")
    void testNoRuleOptionsInProto(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("auth_models.proto"));
        assertFalse(content.contains("import \"rules.proto\""), "Should not import rules.proto");
        assertFalse(content.contains("rule_set"), "Should not embed rule_set options");
        assertFalse(content.contains("(rules."), "Should not contain any rules custom options");
    }

    // ---- Phase C new feature tests ----

    @Test
    @DisplayName("Nullable fields use optional keyword in proto")
    void testOptionalKeywordForNullableFields(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("auth_models.proto"));
        // display_name and bio are nullable in users → should have 'optional' prefix
        assertTrue(content.contains("optional string display_name") || content.contains("optional string bio"),
                "Nullable string should have optional prefix");
    }

    @Test
    @DisplayName("Proto does not import timestamp when no date/datetime fields present")
    void testNoTimestampImport(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("auth_models.proto"));
        assertFalse(content.contains("import \"google/protobuf/timestamp.proto\""),
                "auth proto should not import timestamp when no TIMESTAMP fields exist");
    }

    @Test
    @DisplayName("appget_common.proto is generated when decimal fields present")
    void testCommonProtoGenerated(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        // Schema has DECIMAL fields (e.g. total_likes in user_stats_view), so appget_common.proto should be generated
        assertTrue(Files.exists(tempDir.resolve("appget_common.proto")),
                "appget_common.proto should be generated when decimal fields exist");
    }

    // ---- gRPC service generation tests ----

    @Test
    @DisplayName("Service proto files are generated for each domain")
    void testServiceProtoGeneration(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        assertTrue(Files.exists(tempDir.resolve("auth_services.proto")), "auth_services.proto should be generated");
        assertTrue(Files.exists(tempDir.resolve("social_services.proto")), "social_services.proto should be generated");
        assertTrue(Files.exists(tempDir.resolve("admin_services.proto")), "admin_services.proto should be generated");
    }

    @Test
    @DisplayName("Service proto has CRUD operations for Users")
    void testServiceCrudOperations(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String content = Files.readString(tempDir.resolve("auth_services.proto"));
        assertTrue(content.contains("service UsersService"), "Should have UsersService");
        assertTrue(content.contains("rpc CreateUsers"), "Should have CreateUsers RPC");
        assertTrue(content.contains("rpc GetUsers"), "Should have GetUsers RPC");
        assertTrue(content.contains("rpc UpdateUsers"), "Should have UpdateUsers RPC");
        assertTrue(content.contains("rpc DeleteUsers"), "Should have DeleteUsers RPC");
        assertTrue(content.contains("rpc ListUsers"), "Should have ListUsers RPC");
    }

    @Test
    @DisplayName("Service proto has correct java_package")
    void testServiceProtoPackage(@TempDir Path tempDir) throws Exception {
        if (tempModelsYaml == null) return;
        converter.convert(tempModelsYaml.toString(), tempDir.toString());

        String authContent = Files.readString(tempDir.resolve("auth_services.proto"));
        assertTrue(authContent.contains("java_package = \"dev.appget.auth.service\""), "auth service package");

        String socialContent = Files.readString(tempDir.resolve("social_services.proto"));
        assertTrue(socialContent.contains("java_package = \"dev.appget.social.service\""), "social service package");
    }
}
