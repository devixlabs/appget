package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Feature To Specs Converter Tests")
class FeatureToSpecsConverterTest {

    private FeatureToSpecsConverter converter;

    @BeforeEach
    void setUp() {
        converter = new FeatureToSpecsConverter();
    }

    @Test
    @DisplayName("Converter should exist and be instantiable")
    void testConverterInstantiation() {
        assertNotNull(converter, "FeatureToSpecsConverter should be instantiable");
    }

    // ---- Simple condition parsing ----

    @Test
    @DisplayName("Simple condition: age is greater than 18")
    void testSimpleConditionGreaterThan() {
        List<Map<String, Object>> conditions = converter.parseSimpleCondition("age is greater than 18");
        assertEquals(1, conditions.size());
        Map<String, Object> cond = conditions.get(0);
        assertEquals("age", cond.get("field"));
        assertEquals(">", cond.get("operator"));
        assertEquals(18, cond.get("value"));
    }

    @Test
    @DisplayName("Simple condition: role_id equals quoted string")
    void testSimpleConditionEqualsString() {
        List<Map<String, Object>> conditions = converter.parseSimpleCondition("role_id equals \"Manager\"");
        assertEquals(1, conditions.size());
        Map<String, Object> cond = conditions.get(0);
        assertEquals("role_id", cond.get("field"));
        assertEquals("==", cond.get("operator"));
        assertEquals("Manager", cond.get("value"));
    }

    @Test
    @DisplayName("Simple condition: is less than operator")
    void testSimpleConditionLessThan() {
        List<Map<String, Object>> conditions = converter.parseSimpleCondition("salary is less than 50000");
        assertEquals(1, conditions.size());
        assertEquals("<", conditions.get(0).get("operator"));
        assertEquals(50000, conditions.get(0).get("value"));
    }

    @Test
    @DisplayName("Simple condition: is at least operator")
    void testSimpleConditionAtLeast() {
        List<Map<String, Object>> conditions = converter.parseSimpleCondition("age is at least 25");
        assertEquals(1, conditions.size());
        assertEquals(">=", conditions.get(0).get("operator"));
        assertEquals(25, conditions.get(0).get("value"));
    }

    @Test
    @DisplayName("Simple condition: is at most operator")
    void testSimpleConditionAtMost() {
        List<Map<String, Object>> conditions = converter.parseSimpleCondition("count is at most 100");
        assertEquals(1, conditions.size());
        assertEquals("<=", conditions.get(0).get("operator"));
        assertEquals(100, conditions.get(0).get("value"));
    }

    @Test
    @DisplayName("Simple condition: does not equal operator")
    void testSimpleConditionNotEquals() {
        List<Map<String, Object>> conditions = converter.parseSimpleCondition("status does not equal \"REJECTED\"");
        assertEquals(1, conditions.size());
        assertEquals("!=", conditions.get(0).get("operator"));
        assertEquals("REJECTED", conditions.get(0).get("value"));
    }

    // ---- Value coercion ----

    @Test
    @DisplayName("Value coercion: integer")
    void testCoerceInteger() {
        Object result = converter.coerceValue("18");
        assertInstanceOf(Integer.class, result);
        assertEquals(18, result);
    }

    @Test
    @DisplayName("Value coercion: boolean true")
    void testCoerceBooleanTrue() {
        Object result = converter.coerceValue("true");
        assertInstanceOf(Boolean.class, result);
        assertEquals(true, result);
    }

    @Test
    @DisplayName("Value coercion: boolean false")
    void testCoerceBooleanFalse() {
        Object result = converter.coerceValue("false");
        assertInstanceOf(Boolean.class, result);
        assertEquals(false, result);
    }

    @Test
    @DisplayName("Value coercion: string")
    void testCoerceString() {
        Object result = converter.coerceValue("Manager");
        assertInstanceOf(String.class, result);
        assertEquals("Manager", result);
    }

    @Test
    @DisplayName("Value coercion: empty string in quotes returns empty string")
    void testCoerceEmptyString() {
        Object result = converter.coerceValue("\"\"");
        assertInstanceOf(String.class, result);
        assertEquals("", result, "Quoted empty string should coerce to empty string, not remain as \"\"");
    }

    // ---- YAML value formatting ----

    @Test
    @DisplayName("Format YAML value: integer unquoted")
    void testFormatIntegerValue() {
        assertEquals("18", converter.formatYamlValue(18));
    }

    @Test
    @DisplayName("Format YAML value: boolean unquoted")
    void testFormatBooleanValue() {
        assertEquals("true", converter.formatYamlValue(true));
    }

    @Test
    @DisplayName("Format YAML value: string quoted")
    void testFormatStringValue() {
        assertEquals("\"Manager\"", converter.formatYamlValue("Manager"));
    }

    // ---- Feature file parsing ----

    @Test
    @DisplayName("Parse auth.feature produces 9 rules")
    void testParseAuthFeature() throws Exception {
        Path featurePath = Path.of("../features", "auth.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            assertEquals(9, rules.size(), "auth.feature should produce 9 rules");
        }
    }

    @Test
    @DisplayName("Parse admin.feature produces 10 rules")
    void testParseAdminFeature() throws Exception {
        Path featurePath = Path.of("../features", "admin.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            assertEquals(10, rules.size(), "admin.feature should produce 10 rules");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("UserActivationCheck rule has correct structure")
    void testFirstAuthRule() throws Exception {
        Path featurePath = Path.of("../features", "auth.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(0);

            assertEquals("UserActivationCheck", rule.get("name"));
            Map<String, Object> target = (Map<String, Object>) rule.get("target");
            assertEquals("model", target.get("type"));
            assertEquals("users", target.get("name"));
            assertEquals("auth", target.get("domain"));
            assertEquals(true, rule.get("blocking"));

            List<Map<String, Object>> conditions = (List<Map<String, Object>>) rule.get("conditions");
            assertEquals(1, conditions.size());
            assertEquals("is_active", conditions.get(0).get("field"));
            assertEquals("==", conditions.get(0).get("operator"));
            assertEquals(true, conditions.get(0).get("value"));

            Map<String, String> thenMap = (Map<String, String>) rule.get("then");
            assertEquals("ACCOUNT_ACTIVE", thenMap.get("status"));
            Map<String, String> elseMap = (Map<String, String>) rule.get("else");
            assertEquals("ACCOUNT_INACTIVE", elseMap.get("status"));
        }
    }

    @Test
    @DisplayName("OAuthTokenValidity rule has blocking flag")
    void testBlockingFlag() throws Exception {
        Path featurePath = Path.of("../features", "auth.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(2); // OAuthTokenValidity
            assertEquals("OAuthTokenValidity", rule.get("name"));
            assertEquals(true, rule.get("blocking"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("ViralPostDetection has compound AND conditions")
    void testCompoundConditions() throws Exception {
        Path featurePath = Path.of("../features", "social.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(2); // ViralPostDetection
            assertEquals("ViralPostDetection", rule.get("name"));

            Map<String, Object> conditions = (Map<String, Object>) rule.get("conditions");
            assertEquals("AND", conditions.get("operator"));
            List<Map<String, Object>> clauses = (List<Map<String, Object>>) conditions.get("clauses");
            assertEquals(2, clauses.size());
            assertEquals("like_count", clauses.get(0).get("field"));
            assertEquals(">=", clauses.get(0).get("operator"));
            assertEquals(1000, clauses.get(0).get("value"));
            assertEquals("is_deleted", clauses.get(1).get("field"));
            assertEquals("==", clauses.get(1).get("operator"));
            assertEquals(false, clauses.get(1).get("value"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("PublicPostViewable targets a view")
    void testViewTarget() throws Exception {
        Path featurePath = Path.of("../features", "social.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(8); // PublicPostViewable
            assertEquals("PublicPostViewable", rule.get("name"));

            Map<String, Object> target = (Map<String, Object>) rule.get("target");
            assertEquals("view", target.get("type"));
            assertEquals("post_detail_view", target.get("name"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("ModerationAuthorizationCheck has metadata requirements")
    void testMetadataRequirements() throws Exception {
        Path featurePath = Path.of("../features", "admin.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(3); // ModerationAuthorizationCheck
            assertEquals("ModerationAuthorizationCheck", rule.get("name"));
            assertEquals(true, rule.get("blocking"));

            Map<String, List<Map<String, Object>>> requires =
                    (Map<String, List<Map<String, Object>>>) rule.get("requires");
            assertNotNull(requires);
            assertEquals(2, requires.size());

            // Roles requirement
            List<Map<String, Object>> rolesReqs = requires.get("roles");
            assertEquals(1, rolesReqs.size());
            assertEquals("isAdmin", rolesReqs.get(0).get("field"));
            assertEquals("==", rolesReqs.get(0).get("operator"));
            assertEquals(true, rolesReqs.get(0).get("value"));

            // Sso requirement
            List<Map<String, Object>> ssoReqs = requires.get("sso");
            assertEquals(1, ssoReqs.size());
            assertEquals("authenticated", ssoReqs.get(0).get("field"));
            assertEquals("==", ssoReqs.get(0).get("operator"));
            assertEquals(true, ssoReqs.get(0).get("value"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("AdminRoleClassification from admin.feature has correct domain")
    void testAdminDomainRule() throws Exception {
        Path featurePath = Path.of("../features", "admin.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(0);
            assertEquals("AdminRoleClassification", rule.get("name"));

            Map<String, Object> target = (Map<String, Object>) rule.get("target");
            assertEquals("model", target.get("type"));
            assertEquals("roles", target.get("name"));
            assertEquals("admin", target.get("domain"));
        }
    }

    // ---- Full conversion / integration tests ----

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Full conversion produces valid specs.yaml with metadata and rules")
    void testFullConversion(@TempDir Path tempDir) throws Exception {
        Path featuresDir = Path.of("../features");
        Path metadataFile = Path.of("../metadata.yaml");
        if (Files.exists(featuresDir) && Files.exists(metadataFile)) {
            Path outputPath = tempDir.resolve("specs.yaml");
            converter.convert("../features", "../metadata.yaml", outputPath.toString());

            assertTrue(Files.exists(outputPath), "specs.yaml should be generated");

            // Parse the output with SnakeYAML for structural verification
            Yaml yaml = new Yaml();
            Map<String, Object> data;
            try (InputStream in = new FileInputStream(outputPath.toFile())) {
                data = yaml.load(in);
            }

            assertNotNull(data.get("metadata"), "Output should have metadata section");
            List<Map<String, Object>> rules = (List<Map<String, Object>>) data.get("rules");
            assertNotNull(rules, "Output should have rules section");
            assertEquals(33, rules.size(), "Should have 33 rules total (10 admin + 9 auth + 14 social)");

            // Files processed in alphabetical order: admin, auth, social
            // Verify first rule from admin.feature
            assertEquals("AdminRoleClassification", rules.get(0).get("name"));
            // Verify first rule from auth.feature (after admin's 10 rules)
            assertEquals("UserActivationCheck", rules.get(10).get("name"));
        }
    }

    // ---- Metadata toggle model validation tests ----

    @Test
    @DisplayName("Referencing unknown metadata category produces error")
    void testUnknownMetadataCategoryError(@TempDir Path tempDir) throws Exception {
        Path featuresDir = tempDir.resolve("features");
        Files.createDirectories(featuresDir);
        Files.writeString(featuresDir.resolve("test.feature"),
                "@domain:test\n" +
                "Feature: Test Rules\n" +
                "\n" +
                "  @target:users @blocking @rule:TestRule\n" +
                "  Scenario: Test rule with unknown metadata\n" +
                "    Given unknown_category context requires:\n" +
                "      | field | operator | value |\n" +
                "      | foo   | ==       | true  |\n" +
                "    When is_active equals true\n" +
                "    Then status is \"PASS\"\n" +
                "    But otherwise status is \"FAIL\"\n");

        Path metadataFile = tempDir.resolve("metadata.yaml");
        Files.writeString(metadataFile,
                "metadata:\n" +
                "  sso:\n" +
                "    enabled: true\n" +
                "    fields:\n" +
                "      - name: authenticated\n" +
                "        type: boolean\n");

        Path outputPath = tempDir.resolve("specs.yaml");
        IOException ex = assertThrows(IOException.class, () ->
                converter.convert(featuresDir.toString(), metadataFile.toString(), outputPath.toString()));
        assertTrue(ex.getMessage().contains("does not exist"),
                "Error should mention category does not exist: " + ex.getMessage());
    }

    @Test
    @DisplayName("Referencing disabled metadata category produces error")
    void testDisabledMetadataCategoryError(@TempDir Path tempDir) throws Exception {
        Path featuresDir = tempDir.resolve("features");
        Files.createDirectories(featuresDir);
        Files.writeString(featuresDir.resolve("test.feature"),
                "@domain:test\n" +
                "Feature: Test Rules\n" +
                "\n" +
                "  @target:users @blocking @rule:TestRule\n" +
                "  Scenario: Test rule with disabled metadata\n" +
                "    Given oauth context requires:\n" +
                "      | field       | operator | value |\n" +
                "      | accessToken | !=       | \"\"    |\n" +
                "    When is_active equals true\n" +
                "    Then status is \"PASS\"\n" +
                "    But otherwise status is \"FAIL\"\n");

        Path metadataFile = tempDir.resolve("metadata.yaml");
        Files.writeString(metadataFile,
                "metadata:\n" +
                "  oauth:\n" +
                "    enabled: false\n" +
                "    fields:\n" +
                "      - name: accessToken\n" +
                "        type: String\n");

        Path outputPath = tempDir.resolve("specs.yaml");
        IOException ex = assertThrows(IOException.class, () ->
                converter.convert(featuresDir.toString(), metadataFile.toString(), outputPath.toString()));
        assertTrue(ex.getMessage().contains("disabled"),
                "Error should mention category is disabled: " + ex.getMessage());
    }

    @Test
    @DisplayName("Referencing unknown field in enabled metadata category produces error")
    void testUnknownMetadataFieldError(@TempDir Path tempDir) throws Exception {
        Path featuresDir = tempDir.resolve("features");
        Files.createDirectories(featuresDir);
        Files.writeString(featuresDir.resolve("test.feature"),
                "@domain:test\n" +
                "Feature: Test Rules\n" +
                "\n" +
                "  @target:users @blocking @rule:TestRule\n" +
                "  Scenario: Test rule with unknown metadata field\n" +
                "    Given sso context requires:\n" +
                "      | field            | operator | value |\n" +
                "      | nonExistentField | ==       | true  |\n" +
                "    When is_active equals true\n" +
                "    Then status is \"PASS\"\n" +
                "    But otherwise status is \"FAIL\"\n");

        Path metadataFile = tempDir.resolve("metadata.yaml");
        Files.writeString(metadataFile,
                "metadata:\n" +
                "  sso:\n" +
                "    enabled: true\n" +
                "    fields:\n" +
                "      - name: authenticated\n" +
                "        type: boolean\n");

        Path outputPath = tempDir.resolve("specs.yaml");
        IOException ex = assertThrows(IOException.class, () ->
                converter.convert(featuresDir.toString(), metadataFile.toString(), outputPath.toString()));
        assertTrue(ex.getMessage().contains("not found in metadata category"),
                "Error should mention field not found: " + ex.getMessage());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Only enabled categories appear in specs.yaml output")
    void testOnlyEnabledCategoriesInOutput(@TempDir Path tempDir) throws Exception {
        Path featuresDir = tempDir.resolve("features");
        Files.createDirectories(featuresDir);
        Files.writeString(featuresDir.resolve("test.feature"),
                "@domain:test\n" +
                "Feature: Test Rules\n" +
                "\n" +
                "  @target:users @blocking @rule:SimpleRule\n" +
                "  Scenario: Simple rule without metadata\n" +
                "    When is_active equals true\n" +
                "    Then status is \"ACTIVE\"\n" +
                "    But otherwise status is \"INACTIVE\"\n");

        Path metadataFile = tempDir.resolve("metadata.yaml");
        Files.writeString(metadataFile,
                "metadata:\n" +
                "  sso:\n" +
                "    enabled: true\n" +
                "    description: \"Single sign-on\"\n" +
                "    fields:\n" +
                "      - name: authenticated\n" +
                "        type: boolean\n" +
                "  oauth:\n" +
                "    enabled: false\n" +
                "    description: \"OAuth 2.0\"\n" +
                "    fields:\n" +
                "      - name: accessToken\n" +
                "        type: String\n" +
                "  roles:\n" +
                "    enabled: true\n" +
                "    description: \"Role-based access\"\n" +
                "    fields:\n" +
                "      - name: isAdmin\n" +
                "        type: boolean\n");

        Path outputPath = tempDir.resolve("specs.yaml");
        converter.convert(featuresDir.toString(), metadataFile.toString(), outputPath.toString());

        // Parse the output and verify metadata section
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(outputPath.toFile())) {
            data = yaml.load(in);
        }

        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        assertNotNull(metadata, "Output should have metadata section");
        assertTrue(metadata.containsKey("sso"), "Enabled category 'sso' should be in output");
        assertTrue(metadata.containsKey("roles"), "Enabled category 'roles' should be in output");
        assertFalse(metadata.containsKey("oauth"), "Disabled category 'oauth' should NOT be in output");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Generated specs.yaml is structurally identical to original")
    void testStructuralEquivalence(@TempDir Path tempDir) throws Exception {
        Path featuresDir = Path.of("../features");
        Path metadataFile = Path.of("../metadata.yaml");
        Path originalSpecs = Path.of("specs.yaml");
        if (Files.exists(featuresDir) && Files.exists(metadataFile) && Files.exists(originalSpecs)) {
            // Generate new specs.yaml
            Path generatedPath = tempDir.resolve("specs.yaml");
            converter.convert("../features", "../metadata.yaml", generatedPath.toString());

            // Parse both with SnakeYAML
            Yaml yaml = new Yaml();
            Map<String, Object> original;
            try (InputStream in = new FileInputStream(originalSpecs.toFile())) {
                original = yaml.load(in);
            }
            Map<String, Object> generated;
            try (InputStream in = new FileInputStream(generatedPath.toFile())) {
                generated = yaml.load(in);
            }

            // Compare metadata sections
            assertEquals(original.get("metadata"), generated.get("metadata"),
                    "Metadata sections should be identical");

            // Compare rules count
            List<Map<String, Object>> origRules = (List<Map<String, Object>>) original.get("rules");
            List<Map<String, Object>> genRules = (List<Map<String, Object>>) generated.get("rules");
            assertEquals(origRules.size(), genRules.size(), "Rule count should match");

            // Compare each rule
            for (int i = 0; i < origRules.size(); i++) {
                assertEquals(origRules.get(i).get("name"), genRules.get(i).get("name"),
                        "Rule " + i + " name should match");
                assertEquals(origRules.get(i).get("target"), genRules.get(i).get("target"),
                        "Rule " + i + " target should match");
            }
        }
    }
}
