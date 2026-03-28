package dev.appget;

import dev.appget.model.Rule;
import dev.appget.specification.MetadataContext;
import dev.appget.specification.Specification;
import dev.appget.specification.context.SsoContext;
import dev.appget.specification.context.RolesContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rule Engine Demo Tests")
class RuleEngineTest {

    @TempDir
    File tempDir;

    private File specsFile;

    @BeforeEach
    void setUp() throws Exception {
        specsFile = new File(tempDir, "specs.yaml");
    }

    private void writeSpecs(String content) throws Exception {
        try (FileWriter writer = new FileWriter(specsFile)) {
            writer.write(content);
        }
    }

    // ---- loadRulesFromSpecs: blocking flag ----

    @Test
    @DisplayName("loadRulesFromSpecs parses blocking flag as true")
    void testLoadRulesBlockingTrue() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: BlockingRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    blocking: true\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ACTIVE\"\n"
                + "    else:\n"
                + "      status: \"INACTIVE\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("users").get(0);
        assertTrue(rule.isBlocking(), "Rule should be marked as blocking");
    }

    @Test
    @DisplayName("loadRulesFromSpecs parses blocking flag as false when absent")
    void testLoadRulesBlockingFalseWhenAbsent() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: InfoRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: posts\n"
                + "      domain: social\n"
                + "    conditions:\n"
                + "      - field: is_public\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"PUBLIC\"\n"
                + "    else:\n"
                + "      status: \"PRIVATE\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("posts").get(0);
        assertFalse(rule.isBlocking(), "Rule without blocking flag should default to false");
    }

    @Test
    @DisplayName("loadRulesFromSpecs parses blocking: false explicitly")
    void testLoadRulesBlockingExplicitFalse() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: ExplicitNonBlocking\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: posts\n"
                + "      domain: social\n"
                + "    blocking: false\n"
                + "    conditions:\n"
                + "      - field: is_public\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"PUBLIC\"\n"
                + "    else:\n"
                + "      status: \"PRIVATE\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("posts").get(0);
        assertFalse(rule.isBlocking(), "Rule with blocking: false should be non-blocking");
    }

    // ---- loadRulesFromSpecs: requires block ----

    @Test
    @DisplayName("loadRulesFromSpecs parses single-category requires block")
    void testLoadRulesParsesSingleRequires() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: AuthRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    blocking: true\n"
                + "    requires:\n"
                + "      sso:\n"
                + "        - field: authenticated\n"
                + "          operator: \"==\"\n"
                + "          value: true\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ALLOWED\"\n"
                + "    else:\n"
                + "      status: \"DENIED\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("users").get(0);

        assertTrue(rule.hasMetadataRequirements(), "Rule should have metadata requirements");
        Map<String, List<Specification>> reqs = rule.getMetadataRequirements();
        assertNotNull(reqs, "Metadata requirements map should not be null");
        assertTrue(reqs.containsKey("sso"), "Should contain sso category");
        assertEquals(1, reqs.get("sso").size(), "Should have 1 sso requirement");
    }

    @Test
    @DisplayName("loadRulesFromSpecs parses multi-category requires block")
    void testLoadRulesParsesMultiCategoryRequires() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: AdminAuth\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    blocking: true\n"
                + "    requires:\n"
                + "      roles:\n"
                + "        - field: role_level\n"
                + "          operator: \">=\"\n"
                + "          value: 3\n"
                + "      sso:\n"
                + "        - field: authenticated\n"
                + "          operator: \"==\"\n"
                + "          value: true\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ADMIN_AUTHENTICATED\"\n"
                + "    else:\n"
                + "      status: \"ADMIN_DENIED\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("users").get(0);

        assertTrue(rule.hasMetadataRequirements(), "Rule should have metadata requirements");
        Map<String, List<Specification>> reqs = rule.getMetadataRequirements();
        assertEquals(2, reqs.size(), "Should have 2 metadata categories");
        assertTrue(reqs.containsKey("roles"), "Should contain roles category");
        assertTrue(reqs.containsKey("sso"), "Should contain sso category");
    }

    @Test
    @DisplayName("loadRulesFromSpecs: rule without requires has no metadata requirements")
    void testLoadRulesNoRequiresBlock() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: SimpleRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: posts\n"
                + "      domain: social\n"
                + "    conditions:\n"
                + "      - field: is_public\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"PUBLIC\"\n"
                + "    else:\n"
                + "      status: \"PRIVATE\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("posts").get(0);

        assertFalse(rule.hasMetadataRequirements(), "Rule without requires should have no metadata requirements");
    }

    // ---- loadRulesFromSpecs: metadata requirements integrate with Rule evaluation ----

    @Test
    @DisplayName("Loaded rule with requires block rejects when metadata is empty")
    void testLoadedRuleRejectsEmptyMetadata() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: SsoRequired\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    blocking: true\n"
                + "    requires:\n"
                + "      sso:\n"
                + "        - field: authenticated\n"
                + "          operator: \"==\"\n"
                + "          value: true\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ALLOWED\"\n"
                + "    else:\n"
                + "      status: \"DENIED\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("users").get(0);

        // Evaluate with empty metadata - should fail at metadata gate
        MetadataContext emptyMetadata = new MetadataContext();

        // We need a target object. Use a simple POJO stand-in that has is_active.
        SsoContext dummyTarget = SsoContext.builder().authenticated(true).build();
        // The spec checks "is_active" on target, but we're testing the metadata gate
        // which should reject before even checking conditions
        String result = rule.evaluate(dummyTarget, emptyMetadata);
        assertEquals("DENIED", result, "Rule with requires should reject when metadata category is missing");
    }

    @Test
    @DisplayName("Loaded rule with requires block passes when metadata is satisfied")
    void testLoadedRulePassesWithSatisfiedMetadata() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: SsoRequired\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    blocking: true\n"
                + "    requires:\n"
                + "      sso:\n"
                + "        - field: authenticated\n"
                + "          operator: \"==\"\n"
                + "          value: true\n"
                + "    conditions:\n"
                + "      - field: authenticated\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ALLOWED\"\n"
                + "    else:\n"
                + "      status: \"DENIED\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("users").get(0);

        SsoContext sso = SsoContext.builder().authenticated(true).sessionId("s1").build();
        MetadataContext metadata = new MetadataContext().with("sso", sso);

        // Use SsoContext as target too (it has authenticated field)
        SsoContext target = SsoContext.builder().authenticated(true).build();
        String result = rule.evaluate(target, metadata);
        assertEquals("ALLOWED", result, "Rule should pass when metadata is satisfied and condition passes");
    }

    // ---- loadRulesFromSpecs: targetType is set ----

    @Test
    @DisplayName("loadRulesFromSpecs sets targetType on loaded rules")
    void testLoadRulesSetsTargetType() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: TestRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ACTIVE\"\n"
                + "    else:\n"
                + "      status: \"INACTIVE\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        Rule rule = rules.get("users").get(0);
        assertEquals("users", rule.getTargetType(), "Target type should be set from specs.yaml");
    }

    // ---- loadRulesFromSpecs: missing file returns empty map ----

    @Test
    @DisplayName("loadRulesFromSpecs returns empty map when file does not exist")
    void testLoadRulesMissingFile() throws Exception {
        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs("/nonexistent/specs.yaml");
        assertTrue(rules.isEmpty(), "Should return empty map when specs file is missing");
    }

    // ---- parseRequires ----

    @Test
    @DisplayName("parseRequires returns null for null input")
    void testParseRequiresNull() {
        assertNull(RuleEngine.parseRequires(null), "Should return null for null input");
    }

    @Test
    @DisplayName("parseRequires returns null for non-Map input")
    void testParseRequiresNonMap() {
        assertNull(RuleEngine.parseRequires("not a map"), "Should return null for non-Map input");
    }

    // ---- buildDemoMetadata ----

    @Test
    @DisplayName("buildDemoMetadata returns MetadataContext with all 5 enabled categories")
    void testBuildDemoMetadataHasAllCategories() {
        MetadataContext metadata = RuleEngine.buildDemoMetadata();

        assertTrue(metadata.has("sso"), "Should have sso category");
        assertTrue(metadata.has("roles"), "Should have roles category");
        assertTrue(metadata.has("user"), "Should have user category");
        assertTrue(metadata.has("oauth"), "Should have oauth category");
        assertTrue(metadata.has("api"), "Should have api category");
    }

    @Test
    @DisplayName("buildDemoMetadata sso has authenticated=true")
    void testBuildDemoMetadataSsoAuthenticated() {
        MetadataContext metadata = RuleEngine.buildDemoMetadata();
        SsoContext sso = (SsoContext) metadata.get("sso");
        assertTrue(sso.isAuthenticated(), "SSO should be authenticated in demo metadata");
    }

    @Test
    @DisplayName("buildDemoMetadata roles has roleLevel>=4 and isAdmin=true")
    void testBuildDemoMetadataRolesAdmin() {
        MetadataContext metadata = RuleEngine.buildDemoMetadata();
        RolesContext roles = (RolesContext) metadata.get("roles");
        assertTrue(roles.getRoleLevel() >= 4, "Role level should be >= 4 to satisfy UserRoleAssignmentValid");
        assertTrue(roles.isAdmin(), "isAdmin should be true to satisfy ModerationAuthorizationCheck");
    }

    @Test
    @DisplayName("buildDemoMetadata satisfies AdminAuthenticationRequired requires block")
    void testBuildDemoMetadataSatisfiesAdminAuth() {
        MetadataContext metadata = RuleEngine.buildDemoMetadata();

        // AdminAuthenticationRequired requires: roles.role_level >= 3 AND sso.authenticated == true
        SsoContext sso = (SsoContext) metadata.get("sso");
        RolesContext roles = (RolesContext) metadata.get("roles");

        assertTrue(sso.isAuthenticated(), "SSO authenticated should be true");
        assertTrue(roles.getRoleLevel() >= 3, "Role level should be >= 3");
    }

    @Test
    @DisplayName("buildDemoMetadata satisfies ModerationAuthorizationCheck requires block")
    void testBuildDemoMetadataSatisfiesModAuth() {
        MetadataContext metadata = RuleEngine.buildDemoMetadata();

        // ModerationAuthorizationCheck requires: roles.is_admin == true AND sso.authenticated == true
        SsoContext sso = (SsoContext) metadata.get("sso");
        RolesContext roles = (RolesContext) metadata.get("roles");

        assertTrue(sso.isAuthenticated(), "SSO authenticated should be true");
        assertTrue(roles.isAdmin(), "isAdmin should be true");
    }

    @Test
    @DisplayName("buildDemoMetadata satisfies UserRoleAssignmentValid requires block")
    void testBuildDemoMetadataSatisfiesRoleAssignment() {
        MetadataContext metadata = RuleEngine.buildDemoMetadata();

        // UserRoleAssignmentValid requires: roles.role_level >= 4
        RolesContext roles = (RolesContext) metadata.get("roles");
        assertTrue(roles.getRoleLevel() >= 4, "Role level should be >= 4");
    }

    // ---- verifyAuthGates ----

    @Test
    @DisplayName("verifyAuthGates returns empty list when happy-path metadata satisfies all auth gates")
    void testVerifyAuthGatesPassesWithValidMetadata() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: AuthRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    requires:\n"
                + "      sso:\n"
                + "        - field: authenticated\n"
                + "          operator: \"==\"\n"
                + "          value: true\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ALLOWED\"\n"
                + "    else:\n"
                + "      status: \"DENIED\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        SsoContext sso = SsoContext.builder().authenticated(true).sessionId("s1").build();
        MetadataContext metadata = new MetadataContext().with("sso", sso);

        List<String> failures = RuleEngine.verifyAuthGates(rules, metadata);
        assertTrue(failures.isEmpty(), "Should have no auth gate failures when metadata satisfies all requires");
    }

    @Test
    @DisplayName("verifyAuthGates detects missing category in metadata")
    void testVerifyAuthGatesDetectsMissingCategory() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: AuthRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    requires:\n"
                + "      sso:\n"
                + "        - field: authenticated\n"
                + "          operator: \"==\"\n"
                + "          value: true\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ALLOWED\"\n"
                + "    else:\n"
                + "      status: \"DENIED\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        MetadataContext emptyMetadata = new MetadataContext();

        List<String> failures = RuleEngine.verifyAuthGates(rules, emptyMetadata);
        assertEquals(1, failures.size(), "Should detect missing sso category");
        assertTrue(failures.get(0).contains("sso"), "Error should mention missing category");
    }

    @Test
    @DisplayName("verifyAuthGates detects unsatisfied spec in metadata")
    void testVerifyAuthGatesDetectsUnsatisfiedSpec() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: AdminRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: users\n"
                + "      domain: auth\n"
                + "    requires:\n"
                + "      roles:\n"
                + "        - field: role_level\n"
                + "          operator: \">=\"\n"
                + "          value: 10\n"
                + "    conditions:\n"
                + "      - field: is_active\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"ALLOWED\"\n"
                + "    else:\n"
                + "      status: \"DENIED\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        RolesContext roles = RolesContext.builder().roleName("User").roleLevel(3).isAdmin(false).build();
        MetadataContext metadata = new MetadataContext().with("roles", roles);

        List<String> failures = RuleEngine.verifyAuthGates(rules, metadata);
        assertEquals(1, failures.size(), "Should detect role_level < 10");
        assertTrue(failures.get(0).contains("role_level"), "Error should mention the failing field");
    }

    @Test
    @DisplayName("verifyAuthGates skips rules without metadata requirements")
    void testVerifyAuthGatesSkipsNonMetadataRules() throws Exception {
        writeSpecs("rules:\n"
                + "  - name: SimpleRule\n"
                + "    target:\n"
                + "      type: model\n"
                + "      name: posts\n"
                + "      domain: social\n"
                + "    conditions:\n"
                + "      - field: is_public\n"
                + "        operator: \"==\"\n"
                + "        value: true\n"
                + "    then:\n"
                + "      status: \"PUBLIC\"\n"
                + "    else:\n"
                + "      status: \"PRIVATE\"\n");

        Map<String, List<Rule>> rules = RuleEngine.loadRulesFromSpecs(specsFile.getAbsolutePath());
        MetadataContext emptyMetadata = new MetadataContext();

        List<String> failures = RuleEngine.verifyAuthGates(rules, emptyMetadata);
        assertTrue(failures.isEmpty(), "Should have no failures for rules without metadata requirements");
    }
}
