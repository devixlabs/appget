package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
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
    @DisplayName("Parse appget.feature produces 6 rules")
    void testParseAppgetFeature() throws Exception {
        Path featurePath = Path.of("features", "appget.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            assertEquals(6, rules.size(), "appget.feature should produce 6 rules");
        }
    }

    @Test
    @DisplayName("Parse hr.feature produces 1 rule")
    void testParseHrFeature() throws Exception {
        Path featurePath = Path.of("features", "hr.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            assertEquals(1, rules.size(), "hr.feature should produce 1 rule");
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("CountryOfOriginCheck rule has correct structure")
    void testCountryOfOriginRule() throws Exception {
        Path featurePath = Path.of("features", "appget.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(0);

            assertEquals("CountryOfOriginCheck", rule.get("name"));
            Map<String, Object> target = (Map<String, Object>) rule.get("target");
            assertEquals("model", target.get("type"));
            assertEquals("Employee", target.get("name"));
            assertEquals("appget", target.get("domain"));
            assertNull(rule.get("blocking"));

            List<Map<String, Object>> conditions = (List<Map<String, Object>>) rule.get("conditions");
            assertEquals(1, conditions.size());
            assertEquals("country_of_origin", conditions.get(0).get("field"));
            assertEquals("==", conditions.get(0).get("operator"));
            assertEquals("USA", conditions.get(0).get("value"));

            Map<String, String> thenMap = (Map<String, String>) rule.get("then");
            assertEquals("APPROVED", thenMap.get("status"));
            Map<String, String> elseMap = (Map<String, String>) rule.get("else");
            assertEquals("REJECTED", elseMap.get("status"));
        }
    }

    @Test
    @DisplayName("EmployeeAgeCheck rule has blocking flag")
    void testBlockingFlag() throws Exception {
        Path featurePath = Path.of("features", "appget.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(1); // EmployeeAgeCheck
            assertEquals("EmployeeAgeCheck", rule.get("name"));
            assertEquals(true, rule.get("blocking"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("SeniorManagerCheck has compound AND conditions")
    void testCompoundConditions() throws Exception {
        Path featurePath = Path.of("features", "appget.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(3); // SeniorManagerCheck
            assertEquals("SeniorManagerCheck", rule.get("name"));

            Map<String, Object> conditions = (Map<String, Object>) rule.get("conditions");
            assertEquals("AND", conditions.get("operator"));
            List<Map<String, Object>> clauses = (List<Map<String, Object>>) conditions.get("clauses");
            assertEquals(2, clauses.size());
            assertEquals("age", clauses.get(0).get("field"));
            assertEquals(">=", clauses.get(0).get("operator"));
            assertEquals(30, clauses.get(0).get("value"));
            assertEquals("role_id", clauses.get(1).get("field"));
            assertEquals("==", clauses.get(1).get("operator"));
            assertEquals("Manager", clauses.get(1).get("value"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("HighEarnerCheck targets a view")
    void testViewTarget() throws Exception {
        Path featurePath = Path.of("features", "appget.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(4); // HighEarnerCheck
            assertEquals("HighEarnerCheck", rule.get("name"));

            Map<String, Object> target = (Map<String, Object>) rule.get("target");
            assertEquals("view", target.get("type"));
            assertEquals("EmployeeSalaryView", target.get("name"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("AuthenticatedApproval has metadata requirements")
    void testMetadataRequirements() throws Exception {
        Path featurePath = Path.of("features", "appget.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(5); // AuthenticatedApproval
            assertEquals("AuthenticatedApproval", rule.get("name"));
            assertEquals(true, rule.get("blocking"));

            Map<String, List<Map<String, Object>>> requires =
                    (Map<String, List<Map<String, Object>>>) rule.get("requires");
            assertNotNull(requires);
            assertEquals(2, requires.size());

            // SSO requirement
            List<Map<String, Object>> ssoReqs = requires.get("sso");
            assertEquals(1, ssoReqs.size());
            assertEquals("authenticated", ssoReqs.get(0).get("field"));
            assertEquals("==", ssoReqs.get(0).get("operator"));
            assertEquals(true, ssoReqs.get(0).get("value"));

            // Roles requirement
            List<Map<String, Object>> rolesReqs = requires.get("roles");
            assertEquals(1, rolesReqs.size());
            assertEquals("roleLevel", rolesReqs.get(0).get("field"));
            assertEquals(">=", rolesReqs.get(0).get("operator"));
            assertEquals(3, rolesReqs.get(0).get("value"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("SalaryAmountCheck from hr.feature has correct domain")
    void testHrDomainRule() throws Exception {
        Path featurePath = Path.of("features", "hr.feature");
        if (Files.exists(featurePath)) {
            List<Map<String, Object>> rules = converter.parseFeatureFile(featurePath);
            Map<String, Object> rule = rules.get(0);
            assertEquals("SalaryAmountCheck", rule.get("name"));

            Map<String, Object> target = (Map<String, Object>) rule.get("target");
            assertEquals("model", target.get("type"));
            assertEquals("Salary", target.get("name"));
            assertEquals("hr", target.get("domain"));
        }
    }

    // ---- Full conversion / integration tests ----

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Full conversion produces valid specs.yaml with metadata and rules")
    void testFullConversion(@TempDir Path tempDir) throws Exception {
        Path featuresDir = Path.of("features");
        Path metadataFile = Path.of("metadata.yaml");
        if (Files.exists(featuresDir) && Files.exists(metadataFile)) {
            Path outputPath = tempDir.resolve("specs.yaml");
            converter.convert("features", "metadata.yaml", outputPath.toString());

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
            assertEquals(7, rules.size(), "Should have 7 rules total");

            // Verify rule names in order
            assertEquals("CountryOfOriginCheck", rules.get(0).get("name"));
            assertEquals("EmployeeAgeCheck", rules.get(1).get("name"));
            assertEquals("EmployeeRoleCheck", rules.get(2).get("name"));
            assertEquals("SeniorManagerCheck", rules.get(3).get("name"));
            assertEquals("HighEarnerCheck", rules.get(4).get("name"));
            assertEquals("AuthenticatedApproval", rules.get(5).get("name"));
            assertEquals("SalaryAmountCheck", rules.get(6).get("name"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Generated specs.yaml is structurally identical to original")
    void testStructuralEquivalence(@TempDir Path tempDir) throws Exception {
        Path featuresDir = Path.of("features");
        Path metadataFile = Path.of("metadata.yaml");
        Path originalSpecs = Path.of("specs.yaml");
        if (Files.exists(featuresDir) && Files.exists(metadataFile) && Files.exists(originalSpecs)) {
            // Generate new specs.yaml
            Path generatedPath = tempDir.resolve("specs.yaml");
            converter.convert("features", "metadata.yaml", generatedPath.toString());

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
