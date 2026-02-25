package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        assertTrue(registryContent.contains("import dev.appget.specification.generated.UserActivationCheck"),
            "Registry should import UserActivationCheck");
        assertTrue(registryContent.contains("import dev.appget.specification.generated.OAuthTokenValidity"),
            "Registry should import OAuthTokenValidity");
        assertTrue(registryContent.contains("import dev.appget.specification.generated.PostNotDeletedCheck"),
            "Registry should import PostNotDeletedCheck");

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
        assertTrue(registryContent.contains("new UserActivationCheck()"), "Registry should instantiate UserActivationCheck");
        assertTrue(registryContent.contains("new OAuthTokenValidity()"), "Registry should instantiate OAuthTokenValidity");
        assertTrue(registryContent.contains("new ModerationActionActive()"), "Registry should instantiate ModerationActionActive");
        assertTrue(registryContent.contains("new AdminAuthenticationRequired()"), "Registry should instantiate AdminAuthenticationRequired");
        assertTrue(registryContent.contains("new PostNotDeletedCheck()"), "Registry should instantiate PostNotDeletedCheck");
    }

    @Test
    @DisplayName("RuleService skips view-targeting rules")
    void testRuleServiceSkipsViewRules(@TempDir Path tempDir) throws Exception {
        String registryContent = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        assertFalse(registryContent.contains("PublicPostViewable"), "Should skip view-targeting PublicPostViewable");
        assertFalse(registryContent.contains("VerifiedAuthorPriority"), "Should skip view-targeting VerifiedAuthorPriority");
        assertFalse(registryContent.contains("UserHasAdminRole"), "Should skip view-targeting UserHasAdminRole");
        assertFalse(registryContent.contains("ActiveContentCreator"), "Should skip view-targeting ActiveContentCreator");
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
        assertTrue(content.contains("BLOCKING_RULES.put(\"UserActivationCheck\", true)"),
            "UserActivationCheck should be marked as blocking");
        assertTrue(content.contains("BLOCKING_RULES.put(\"AdminAuthenticationRequired\", true)"),
            "AdminAuthenticationRequired should be marked as blocking");
        assertTrue(content.contains("BLOCKING_RULES.put(\"UserVerificationStatus\", false)"),
            "UserVerificationStatus should be marked as non-blocking");

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
        assertTrue(content.contains("X-User-Email"), "Should read X-User-Email header");
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
        assertTrue(content.contains("register(\"UserActivationCheck\""),
            "Should register UserActivationCheck");
        assertTrue(content.contains("register(\"OAuthTokenValidity\""),
            "Should register OAuthTokenValidity");
        assertTrue(content.contains("register(\"AdminAuthenticationRequired\""),
            "Should register AdminAuthenticationRequired");
        assertTrue(content.contains("register(\"ModerationActionActive\""),
            "Should register ModerationActionActive");
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

    @Test
    @DisplayName("RuleService does not import model classes (uses reflection)")
    void testRuleServiceNoModelImports(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertFalse(content.contains("import dev.appget.model."),
            "RuleService should not import flat dev.appget.model.* (no such package in multi-domain)");
        // Should not import ANY domain-specific model classes either (they're unused)
        assertFalse(content.matches("(?s).*import dev\\.appget\\.\\w+\\.model\\..*"),
            "RuleService should not import domain-specific model classes (uses reflection, not static types)");
    }

    @Test
    @DisplayName("SpecificationRegistry resolves multi-domain target names")
    void testSpecRegistryMultiDomainTargets(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        // Verify PascalCase target names are correctly resolved
        assertTrue(content.contains("SPEC_TARGETS"),
            "Should have SPEC_TARGETS map for target resolution");
    }

    @Test
    @DisplayName("Per-model services import from domain-specific namespaces")
    void testModelServiceDomainImports(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("models.yaml").exists() && new File("specs.yaml").exists()) {
            generator.generateServer("models.yaml", "specs.yaml", outputDir);
            // Check a per-model service for correct domain-specific imports
            Path usersServicePath = Paths.get(outputDir,
                "dev", "appget", "server", "service", "UsersService.java");
            if (Files.exists(usersServicePath)) {
                String content = Files.readString(usersServicePath);
                assertFalse(content.contains("import dev.appget.model."),
                    "UsersService should not use flat dev.appget.model package");
                assertTrue(content.contains(".model.Users"),
                    "UsersService should import Users from domain-specific namespace");
            }
        }
    }

    // ---- Dual-keying and deduplication tests (modelIndex fix) ----

    @Test
    @DisplayName("No generated file uses flat dev.appget.model.* import")
    void testNoGeneratedFileUsesFlatModelImport(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("models.yaml").exists() && new File("specs.yaml").exists()) {
            generator.generateServer("models.yaml", "specs.yaml", outputDir);

            // Scan ALL generated .java files for the flat (wrong) import pattern
            Path serverRoot = Paths.get(outputDir, "dev", "appget", "server");
            List<Path> javaFiles;
            try (Stream<Path> walk = Files.walk(serverRoot)) {
                javaFiles = walk.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            }
            assertTrue(javaFiles.size() > 10, "Should have generated many .java files");

            for (Path javaFile : javaFiles) {
                String content = Files.readString(javaFile);
                assertFalse(content.contains("import dev.appget.model."),
                    javaFile.getFileName() + " should not use flat dev.appget.model.* import");
            }
        }
    }

    @Test
    @DisplayName("All three domains have correct namespace imports in generated services")
    void testAllDomainNamespaceImports(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("models.yaml").exists() && new File("specs.yaml").exists()) {
            generator.generateServer("models.yaml", "specs.yaml", outputDir);

            // auth domain: Users → dev.appget.auth.model.Users
            String usersService = Files.readString(Paths.get(outputDir,
                "dev", "appget", "server", "service", "UsersService.java"));
            assertTrue(usersService.contains("import dev.appget.auth.model.Users"),
                "UsersService should import from auth domain namespace");

            // admin domain: Roles → dev.appget.admin.model.Roles
            String rolesService = Files.readString(Paths.get(outputDir,
                "dev", "appget", "server", "service", "RolesService.java"));
            assertTrue(rolesService.contains("import dev.appget.admin.model.Roles"),
                "RolesService should import from admin domain namespace");

            // social domain: Posts → dev.appget.social.model.Posts
            String postsService = Files.readString(Paths.get(outputDir,
                "dev", "appget", "server", "service", "PostsService.java"));
            assertTrue(postsService.contains("import dev.appget.social.model.Posts"),
                "PostsService should import from social domain namespace");
        }
    }

    @Test
    @DisplayName("SPEC_TARGETS maps rule names to PascalCase model names")
    void testSpecTargetsUsesPascalCaseModelNames(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");

        // The dual-keying fix ensures modelIndex.get(rule.targetName) resolves correctly,
        // producing PascalCase model names (not snake_case) in SPEC_TARGETS
        assertTrue(content.contains("SPEC_TARGETS.put(\"UserActivationCheck\", \"Users\")"),
            "UserActivationCheck should target PascalCase 'Users'");
        assertTrue(content.contains("SPEC_TARGETS.put(\"AdminRoleClassification\", \"Roles\")"),
            "AdminRoleClassification should target PascalCase 'Roles'");
        assertTrue(content.contains("SPEC_TARGETS.put(\"PostNotDeletedCheck\", \"Posts\")"),
            "PostNotDeletedCheck should target PascalCase 'Posts'");
        assertTrue(content.contains("SPEC_TARGETS.put(\"ModerationActionActive\", \"ModerationActions\")"),
            "ModerationActionActive should target PascalCase 'ModerationActions'");

        // Verify no snake_case target values leaked into SPEC_TARGETS
        assertFalse(content.contains("SPEC_TARGETS.put(\"UserActivationCheck\", \"users\")"),
            "SPEC_TARGETS should not have snake_case target 'users'");
        assertFalse(content.contains("SPEC_TARGETS.put(\"AdminRoleClassification\", \"roles\")"),
            "SPEC_TARGETS should not have snake_case target 'roles'");
    }

    @Test
    @DisplayName("Per-model generation produces exactly one set of files per model (no duplicates)")
    void testNoDuplicatePerModelFiles(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (new File("models.yaml").exists() && new File("specs.yaml").exists()) {
            generator.generateServer("models.yaml", "specs.yaml", outputDir);

            // Count Controller files (one per model, views excluded)
            Path controllerDir = Paths.get(outputDir, "dev", "appget", "server", "controller");
            long controllerCount;
            try (Stream<Path> walk = Files.list(controllerDir)) {
                controllerCount = walk.filter(p -> p.toString().endsWith("Controller.java")).count();
            }

            // Count Service files (minus RuleService and SpecificationRegistry which are infrastructure)
            Path serviceDir = Paths.get(outputDir, "dev", "appget", "server", "service");
            long modelServiceCount;
            try (Stream<Path> walk = Files.list(serviceDir)) {
                modelServiceCount = walk.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith("Service.java")
                        && !name.equals("RuleService.java");
                }).count();
            }

            // Controllers and model services should match 1:1 (each model gets exactly one of each)
            assertEquals(controllerCount, modelServiceCount,
                "Controller count should equal model service count (no duplicates from dual-keying)");

            // With 14 models (3 domains, views excluded), expect exactly 14 controllers
            assertEquals(14, controllerCount,
                "Should generate exactly 14 controllers (one per model, no duplicates)");
        }
    }
}
