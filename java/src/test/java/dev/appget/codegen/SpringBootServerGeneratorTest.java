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

@DisplayName("Spring Boot Server Generator Tests")
class SpringBootServerGeneratorTest {

    private SpringBootServerGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SpringBootServerGenerator();
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
        String content = readRuleService(tempDir);
        assertTrue(content.contains("import dev.appget.specification.generated.EmployeeAgeCheck"),
            "Should import EmployeeAgeCheck");
        assertTrue(content.contains("import dev.appget.specification.generated.EmployeeRoleCheck"),
            "Should import EmployeeRoleCheck");
        assertTrue(content.contains("import dev.appget.specification.generated.AuthenticatedApproval"),
            "Should import AuthenticatedApproval");
        assertTrue(content.contains("import dev.appget.specification.generated.SalaryAmountCheck"),
            "Should import SalaryAmountCheck");
    }

    @Test
    @DisplayName("RuleService instantiates spec classes")
    void testRuleServiceInstantiatesSpecs(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertTrue(content.contains("new EmployeeAgeCheck()"), "Should instantiate EmployeeAgeCheck");
        assertTrue(content.contains("new EmployeeRoleCheck()"), "Should instantiate EmployeeRoleCheck");
        assertTrue(content.contains("new SeniorManagerCheck()"), "Should instantiate SeniorManagerCheck");
        assertTrue(content.contains("new AuthenticatedApproval()"), "Should instantiate AuthenticatedApproval");
        assertTrue(content.contains("new SalaryAmountCheck()"), "Should instantiate SalaryAmountCheck");
    }

    @Test
    @DisplayName("RuleService skips view-targeting rules")
    void testRuleServiceSkipsViewRules(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertFalse(content.contains("HighEarnerCheck"), "Should skip view-targeting HighEarnerCheck");
    }

    @Test
    @DisplayName("RuleService groups by instanceof")
    void testRuleServiceGroupsByInstanceof(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertTrue(content.contains("target instanceof Employee"), "Should check instanceof Employee");
        assertTrue(content.contains("target instanceof Salary"), "Should check instanceof Salary");
    }

    @Test
    @DisplayName("RuleService uses metadata-aware evaluate for AuthenticatedApproval")
    void testRuleServiceMetadataAwareEvaluate(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertTrue(content.contains("authenticatedApproval.evaluate(typedTarget, metadata)"),
            "Should use metadata-aware evaluate for AuthenticatedApproval");
        assertTrue(content.contains("authenticatedApproval.getResult(typedTarget, metadata)"),
            "Should use metadata-aware getResult for AuthenticatedApproval");
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
        // EmployeeAgeCheck is blocking - should set hasFailures
        assertTrue(content.contains("employeeAgeCheckSatisfied") && content.contains("hasFailures = true"),
            "Blocking rule EmployeeAgeCheck should set hasFailures");

        // Split content to check blocking logic per rule
        // EmployeeRoleCheck is NOT blocking - between its evaluate and the next rule, there should be no hasFailures
        int roleCheckIdx = content.indexOf("employeeRoleCheckSatisfied");
        int roleCheckOutcome = content.indexOf("\"EmployeeRoleCheck\"");
        assertTrue(roleCheckIdx > 0 && roleCheckOutcome > 0, "EmployeeRoleCheck should be evaluated");

        // Find the section between EmployeeRoleCheck outcome and the next rule
        int afterRoleCheck = content.indexOf(".build());", roleCheckOutcome);
        int nextSection = content.indexOf("Satisfied", afterRoleCheck);
        if (nextSection > 0) {
            String between = content.substring(afterRoleCheck, nextSection);
            assertFalse(between.contains("hasFailures = true"),
                "Non-blocking EmployeeRoleCheck should not set hasFailures");
        }
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
}
