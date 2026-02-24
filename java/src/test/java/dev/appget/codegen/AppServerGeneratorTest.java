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

@DisplayName("Application Server Generator Tests")
class AppServerGeneratorTest {

    private AppServerGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new AppServerGenerator();
    }

    private String generateAndReadFile(Path tempDir, String... pathParts) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("models.yaml").exists() && new File("specs.yaml").exists()) {
            generator.generateServer("models.yaml", "specs.yaml", outputDir);
            Path filePath = Paths.get(outputDir, pathParts);
            assertTrue(Files.exists(filePath), "Expected file: " + filePath);
            return Files.readString(filePath);
        }
        fail("models.yaml and specs.yaml must exist at project root");
        return null;
    }

    private String readRuleService(Path tempDir) throws Exception {
        return generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "RuleService.java");
    }

    private String readMetadataExtractor(Path tempDir) throws Exception {
        return generateAndReadFile(tempDir,
            "dev", "appget", "server", "config", "MetadataExtractor.java");
    }

    @Test
    @DisplayName("RuleService has no TODO stubs")
    void testRuleServiceNoTodos(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertFalse(content.contains("TODO"), "RuleService should have no TODO stubs");
    }

    @Test
    @DisplayName("MetadataExtractor has no TODO stubs")
    void testMetadataExtractorNoTodos(@TempDir Path tempDir) throws Exception {
        String content = readMetadataExtractor(tempDir);
        assertFalse(content.contains("TODO"), "MetadataExtractor should have no TODO stubs");
    }

    @Test
    @DisplayName("RuleService imports spec classes")
    void testRuleServiceImportsSpecs(@TempDir Path tempDir) throws Exception {
        String ruleServiceContent = readRuleService(tempDir);
        String registryContent = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");

        // SpecificationRegistry should import the spec classes
        assertTrue(registryContent.contains("import dev.appget.specification.generated.UserEmailValidation"),
            "Registry should import UserEmailValidation");
        assertTrue(registryContent.contains("import dev.appget.specification.generated.UserSuspensionCheck"),
            "Registry should import UserSuspensionCheck");
        assertTrue(registryContent.contains("import dev.appget.specification.generated.SeverityLevelValidation"),
            "Registry should import SeverityLevelValidation");

        // RuleService should inject SpecificationRegistry instead
        assertTrue(ruleServiceContent.contains("SpecificationRegistry"),
            "RuleService should use SpecificationRegistry");
    }

    @Test
    @DisplayName("RuleService instantiates spec classes")
    void testRuleServiceInstantiatesSpecs(@TempDir Path tempDir) throws Exception {
        String registryContent = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");

        // SpecificationRegistry should instantiate the spec classes
        assertTrue(registryContent.contains("new UserEmailValidation()"), "Registry should instantiate UserEmailValidation");
        assertTrue(registryContent.contains("new UserSuspensionCheck()"), "Registry should instantiate UserSuspensionCheck");
        assertTrue(registryContent.contains("new SeverityLevelValidation()"), "Registry should instantiate SeverityLevelValidation");
        assertTrue(registryContent.contains("new AdminAuthorizationRequired()"), "Registry should instantiate AdminAuthorizationRequired");
        assertTrue(registryContent.contains("new UserAccountStatus()"), "Registry should instantiate UserAccountStatus");
    }

    @Test
    @DisplayName("RuleService skips view-targeting rules")
    void testRuleServiceSkipsViewRules(@TempDir Path tempDir) throws Exception {
        String registryContent = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        assertFalse(registryContent.contains("HighEngagementPost"), "Should skip view-targeting HighEngagementPost");
        assertFalse(registryContent.contains("VerifiedAuthorPriority"), "Should skip view-targeting VerifiedAuthorPriority");
        assertFalse(registryContent.contains("FeedPostEligibility"), "Should skip view-targeting FeedPostEligibility");
        assertFalse(registryContent.contains("AuthorVerificationInComments"), "Should skip view-targeting AuthorVerificationInComments");
    }

    @Test
    @DisplayName("RuleService groups by instanceof")
    void testRuleServiceGroupsByInstanceof(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        // RuleService now uses registry.getByTarget() instead of instanceof
        assertTrue(content.contains("registry.getByTarget(modelName)"),
            "Should use registry.getByTarget to filter specs by target");
    }

    @Test
    @DisplayName("RuleService uses metadata-aware evaluate for AdminAuthorizationRequired")
    void testRuleServiceMetadataAwareEvaluate(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        // RuleService should use getMethods() iteration to handle typed spec params
        assertTrue(content.contains("spec.getClass().getMethods()"),
            "Should use getMethods() iteration to invoke evaluate method");
        assertTrue(content.contains("BLOCKING_RULES"),
            "Should have BLOCKING_RULES static map");
    }

    @Test
    @DisplayName("RuleService has no PostConstruct")
    void testRuleServiceNoPostConstruct(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertFalse(content.contains("@PostConstruct"), "Should not use @PostConstruct");
    }

    @Test
    @DisplayName("RuleService only blocks for blocking rules")
    void testRuleServiceBlockingLogic(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        // RuleService should check BLOCKING_RULES map
        assertTrue(content.contains("BLOCKING_RULES"), "Should have BLOCKING_RULES static map");
        assertTrue(content.contains("BLOCKING_RULES.put(\"UserEmailValidation\", true)"),
            "UserEmailValidation should be marked as blocking");
        assertTrue(content.contains("BLOCKING_RULES.put(\"AdminAuthorizationRequired\", true)"),
            "AdminAuthorizationRequired should be marked as blocking");
        assertTrue(content.contains("BLOCKING_RULES.put(\"UserFollowingStats\", false)"),
            "UserFollowingStats should be marked as non-blocking");

        // Verify blocking logic
        assertTrue(content.contains("if (isBlocking && !outcome.isSatisfied()) {"),
            "Should only set hasFailures for blocking rules that are unsatisfied");
    }

    @Test
    @DisplayName("MetadataExtractor imports context POJOs")
    void testMetadataExtractorImportsPojos(@TempDir Path tempDir) throws Exception {
        String content = readMetadataExtractor(tempDir);
        assertTrue(content.contains("import dev.appget.specification.context.SsoContext"),
            "Should import SsoContext");
        assertTrue(content.contains("import dev.appget.specification.context.RolesContext"),
            "Should import RolesContext");
        assertTrue(content.contains("import dev.appget.specification.context.UserContext"),
            "Should import UserContext");
    }

    @Test
    @DisplayName("MetadataExtractor reads correct headers")
    void testMetadataExtractorReadsHeaders(@TempDir Path tempDir) throws Exception {
        String content = readMetadataExtractor(tempDir);
        assertTrue(content.contains("X-Sso-Authenticated"), "Should read X-Sso-Authenticated header");
        assertTrue(content.contains("X-Sso-Session-Id"), "Should read X-Sso-Session-Id header");
        assertTrue(content.contains("X-Roles-Role-Name"), "Should read X-Roles-Role-Name header");
        assertTrue(content.contains("X-Roles-Role-Level"), "Should read X-Roles-Role-Level header");
        assertTrue(content.contains("X-User-User-Id"), "Should read X-User-User-Id header");
        assertTrue(content.contains("X-User-Clearance-Level"), "Should read X-User-Clearance-Level header");
    }

    @Test
    @DisplayName("MetadataExtractor uses builder pattern with context POJOs")
    void testMetadataExtractorUsesBuilder(@TempDir Path tempDir) throws Exception {
        String content = readMetadataExtractor(tempDir);
        assertTrue(content.contains("SsoContext.builder()"), "Should use SsoContext.builder()");
        assertTrue(content.contains("RolesContext.builder()"), "Should use RolesContext.builder()");
        assertTrue(content.contains("UserContext.builder()"), "Should use UserContext.builder()");
        assertTrue(content.contains("context.with(\"sso\""), "Should add sso to context");
        assertTrue(content.contains("context.with(\"roles\""), "Should add roles to context");
        assertTrue(content.contains("context.with(\"user\""), "Should add user to context");
    }

    @Test
    @DisplayName("SpecificationRegistry exists and is annotated with @Component")
    void testSpecificationRegistryExists(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        assertTrue(content.contains("public class SpecificationRegistry"),
            "SpecificationRegistry class should exist");
        assertTrue(content.contains("@Component"),
            "SpecificationRegistry should be annotated with @Component");
    }

    @Test
    @DisplayName("SpecificationRegistry registers all spec classes")
    void testSpecificationRegistryRegistersAllSpecs(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        assertTrue(content.contains("register(\"UserEmailValidation\""),
            "Should register UserEmailValidation");
        assertTrue(content.contains("register(\"UserSuspensionCheck\""),
            "Should register UserSuspensionCheck");
        assertTrue(content.contains("register(\"AdminAuthorizationRequired\""),
            "Should register AdminAuthorizationRequired");
        assertTrue(content.contains("register(\"SeverityLevelValidation\""),
            "Should register SeverityLevelValidation");
    }

    @Test
    @DisplayName("SpecificationRegistry has getByTarget method")
    void testSpecificationRegistryGetByTarget(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        assertTrue(content.contains("public List<Object> getByTarget(String modelName)"),
            "Should have getByTarget method");
        assertTrue(content.contains("SPEC_TARGETS"),
            "Should use static SPEC_TARGETS map for filtering");
    }

    @Test
    @DisplayName("SpecificationRegistry has get method for single spec lookup")
    void testSpecificationRegistryGetMethod(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        assertTrue(content.contains("public Object get(String name)"),
            "Should have get method for single spec lookup");
    }

    @Test
    @DisplayName("RuleService injects SpecificationRegistry")
    void testRuleServiceInjectsRegistry(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertTrue(content.contains("private final SpecificationRegistry registry"),
            "RuleService should inject SpecificationRegistry");
        assertTrue(content.contains("public RuleService(SpecificationRegistry registry)"),
            "RuleService constructor should accept SpecificationRegistry");
    }
}
