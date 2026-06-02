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
    @DisplayName("RuleService uses EvaluableRule interface for spec evaluation (no reflection)")
    void testRuleServiceUsesEvaluableRule(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        // RuleService should call through EvaluableRule interface, not reflection
        assertTrue(content.contains("spec.evaluate(target, metadata)"),
            "Should call evaluate through EvaluableRule interface");
        assertTrue(content.contains("spec.getResult(target, metadata)"),
            "Should call getResult through EvaluableRule interface");
        assertFalse(content.contains("getClass().getMethods()"),
            "Should not use reflection to invoke spec methods");
        assertFalse(content.contains("java.lang.reflect"),
            "Should not import java.lang.reflect");
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
        assertTrue(content.contains("public List<EvaluableRule> getByTarget(String modelName)"),
            "Should have getByTarget method returning EvaluableRule list");
        assertTrue(content.contains("SPEC_TARGETS"),
            "Should use static SPEC_TARGETS map for filtering");
    }

    @Test
    @DisplayName("SpecificationRegistry has get method for single spec lookup")
    void testSpecificationRegistryGetMethod(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "SpecificationRegistry.java");
        assertTrue(content.contains("public EvaluableRule get(String name)"),
            "Should have get method returning EvaluableRule");
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
    @DisplayName("RuleService does not import model classes (uses EvaluableRule interface)")
    void testRuleServiceNoModelImports(@TempDir Path tempDir) throws Exception {
        String content = readRuleService(tempDir);
        assertFalse(content.contains("import dev.appget.model."),
            "RuleService should not import flat dev.appget.model.* (no such package in multi-domain)");
        // Should not import ANY domain-specific model classes — EvaluableRule abstracts the target type
        assertFalse(content.matches("(?s).*import dev\\.appget\\.\\w+\\.model\\..*"),
            "RuleService should not import domain-specific model classes (uses EvaluableRule interface)");
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

            // Count model Controller files (exclude view controllers which contain "View" in name)
            Path controllerDir = Paths.get(outputDir, "dev", "appget", "server", "controller");
            long controllerCount;
            try (Stream<Path> walk = Files.list(controllerDir)) {
                controllerCount = walk.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith("Controller.java")
                        && !name.contains("View")
                        && !name.equals("RootController.java");
                }).count();
            }

            // Count model Service files (exclude RuleService, SpecificationRegistry, and view services)
            Path serviceDir = Paths.get(outputDir, "dev", "appget", "server", "service");
            long modelServiceCount;
            try (Stream<Path> walk = Files.list(serviceDir)) {
                modelServiceCount = walk.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith("Service.java")
                        && !name.equals("RuleService.java")
                        && !name.contains("View");
                }).count();
            }

            // Model controllers and model services should match 1:1 (each model gets exactly one of each)
            assertEquals(controllerCount, modelServiceCount,
                "Model controller count should equal model service count (no duplicates from dual-keying)");

            // With 14 models (3 domains), expect exactly 14 model controllers
            assertEquals(14, controllerCount,
                "Should generate exactly 14 model controllers (one per model, no duplicates)");
        }
    }

    // ---- Error handling and timestamp tests (GAP-R2, GAP-R3) ----

    @Test
    @DisplayName("MetadataExtractor uses safe parsing helpers for numeric headers")
    void testMetadataExtractorSafeParsing(@TempDir Path tempDir) throws Exception {
        String content = readMetadataExtractor(tempDir);
        assertTrue(content.contains("safeParseInt("),
            "Should use safeParseInt for integer header parsing");
        assertTrue(content.contains("MetadataParsingException"),
            "Should import MetadataParsingException");
        assertTrue(content.contains("private int safeParseInt(String value, String headerName)"),
            "Should generate safeParseInt helper method");
    }

    @Test
    @DisplayName("GlobalExceptionHandler handles MetadataParsingException with 400")
    void testGlobalExceptionHandlerMetadataParsing(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "GlobalExceptionHandler.java");
        assertTrue(content.contains("@ExceptionHandler(MetadataParsingException.class)"),
            "Should handle MetadataParsingException");
        assertTrue(content.contains("HttpStatus.BAD_REQUEST"),
            "MetadataParsingException should return 400 BAD_REQUEST");
        assertTrue(content.contains("INVALID_METADATA"),
            "Error code should be INVALID_METADATA");
    }

    @Test
    @DisplayName("MetadataParsingException class is generated")
    void testMetadataParsingExceptionExists(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "MetadataParsingException.java");
        assertTrue(content.contains("public class MetadataParsingException extends RuntimeException"),
            "MetadataParsingException should extend RuntimeException");
    }

    @Test
    @DisplayName("ErrorResponse uses OffsetDateTime for RFC 3339 compliance")
    void testErrorResponseUsesOffsetDateTime(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "dto", "ErrorResponse.java");
        assertTrue(content.contains("OffsetDateTime"),
            "ErrorResponse should use OffsetDateTime");
        assertFalse(content.contains("LocalDateTime"),
            "ErrorResponse should NOT use LocalDateTime");
    }

    @Test
    @DisplayName("GlobalExceptionHandler uses OffsetDateTime.now()")
    void testGlobalExceptionHandlerUsesOffsetDateTime(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "GlobalExceptionHandler.java");
        assertTrue(content.contains("OffsetDateTime.now()"),
            "Exception handler should use OffsetDateTime.now()");
        assertFalse(content.contains("LocalDateTime"),
            "Exception handler should NOT reference LocalDateTime");
    }

    // ---- View component tests ----

    @Test
    @DisplayName("View controller file exists in controller directory")
    void testViewControllerExists(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "controller", "PostDetailViewController.java");
        assertNotNull(content, "PostDetailViewController.java should be generated");
        assertTrue(content.contains("public class PostDetailViewController"),
            "Should declare PostDetailViewController class");
    }

    // ---- Generated infrastructure file tests ----

    @Test
    @DisplayName("Application.java has @SpringBootApplication")
    void testApplicationExists(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "Application.java");
        assertTrue(content.contains("@SpringBootApplication"),
            "Application.java should have @SpringBootApplication annotation");
        assertTrue(content.contains("public static void main"),
            "Application.java should have main method");
    }

    @Test
    @DisplayName("RuleViolationException is generated correctly")
    void testRuleViolationExceptionExists(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "RuleViolationException.java");
        assertTrue(content.contains("extends RuntimeException"),
            "Should extend RuntimeException");
        assertTrue(content.contains("RuleEvaluationResult"),
            "Should hold RuleEvaluationResult");
    }

    @Test
    @DisplayName("ResourceNotFoundException is generated correctly")
    void testResourceNotFoundExceptionExists(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "ResourceNotFoundException.java");
        assertTrue(content.contains("extends RuntimeException"),
            "Should extend RuntimeException");
    }

    @Test
    @DisplayName("GlobalExceptionHandler handles all five exception types")
    void testGlobalExceptionHandlerAllHandlers(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "GlobalExceptionHandler.java");
        assertTrue(content.contains("@ExceptionHandler(RuleViolationException.class)"),
            "Should handle RuleViolationException");
        assertTrue(content.contains("@ExceptionHandler(ResourceNotFoundException.class)"),
            "Should handle ResourceNotFoundException");
        assertTrue(content.contains("@ExceptionHandler(MetadataParsingException.class)"),
            "Should handle MetadataParsingException");
        assertTrue(content.contains("@ExceptionHandler(HttpMessageNotReadableException.class)"),
            "Should handle HttpMessageNotReadableException");
        assertTrue(content.contains("@ExceptionHandler(Exception.class)"),
            "Should have catch-all Exception handler");
        assertTrue(content.contains("HttpStatus.UNPROCESSABLE_ENTITY"),
            "RuleViolation should map to 422");
        assertTrue(content.contains("HttpStatus.NOT_FOUND"),
            "ResourceNotFound should map to 404");
        assertTrue(content.contains("HttpStatus.BAD_REQUEST"),
            "MetadataParsing should map to 400");
        assertTrue(content.contains("HttpStatus.INTERNAL_SERVER_ERROR"),
            "Catch-all should map to 500");
    }

    @Test
    @DisplayName("GlobalExceptionHandler catches bad request body with HttpMessageNotReadableException")
    void testGlobalExceptionHandlerBadRequestBody(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "GlobalExceptionHandler.java");
        assertTrue(content.contains("handleBadRequest(HttpMessageNotReadableException ex)"),
            "Should have handleBadRequest method for JSON parse errors");
        assertTrue(content.contains("BAD_REQUEST"),
            "Bad request body should return 400");
    }

    @Test
    @DisplayName("GlobalExceptionHandler has catch-all for unexpected exceptions")
    void testGlobalExceptionHandlerCatchAll(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "exception", "GlobalExceptionHandler.java");
        assertTrue(content.contains("handleGeneral(Exception ex)"),
            "Should have catch-all handleGeneral method");
        assertTrue(content.contains("INTERNAL_ERROR"),
            "Catch-all error code should be INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Application.java disables WRITE_DATES_AS_TIMESTAMPS for RFC 3339")
    void testApplicationDisablesTimestamps(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "Application.java");
        assertTrue(content.contains("SerializationFeature.WRITE_DATES_AS_TIMESTAMPS"),
            "ObjectMapper should disable WRITE_DATES_AS_TIMESTAMPS");
        assertTrue(content.contains("mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)"),
            "Should explicitly disable timestamp serialization");
    }

    @Test
    @DisplayName("Application.java registers all required Jackson modules")
    void testApplicationJacksonModules(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "Application.java");
        assertTrue(content.contains("new ProtobufModule()"),
            "Should register ProtobufModule");
        assertTrue(content.contains("new JavaTimeModule()"),
            "Should register JavaTimeModule");
        assertTrue(content.contains("new DecimalJacksonModule()"),
            "Should register DecimalJacksonModule");
        assertTrue(content.contains("FAIL_ON_UNKNOWN_PROPERTIES"),
            "Should disable FAIL_ON_UNKNOWN_PROPERTIES");
    }

    @Test
    @DisplayName("Model controller has all CRUD method annotations")
    void testModelControllerCrudAnnotations(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "controller", "UsersController.java");
        assertTrue(content.contains("@PostMapping"), "Model controller should have @PostMapping");
        assertTrue(content.contains("@GetMapping"), "Model controller should have @GetMapping");
        assertTrue(content.contains("@PutMapping"), "Model controller should have @PutMapping");
        assertTrue(content.contains("@DeleteMapping"), "Model controller should have @DeleteMapping");
    }

    @Test
    @DisplayName("Repository interface exists alongside InMemory implementation")
    void testRepositoryInterfaceAndImpl(@TempDir Path tempDir) throws Exception {
        String iface = generateAndReadFile(tempDir,
            "dev", "appget", "server", "repository", "UsersRepository.java");
        assertTrue(iface.contains("interface UsersRepository"),
            "Should generate UsersRepository interface");

        String impl = generateAndReadFile(tempDir,
            "dev", "appget", "server", "repository", "InMemoryUsersRepository.java");
        assertTrue(impl.contains("implements UsersRepository"),
            "InMemory implementation should implement the interface");
        assertTrue(impl.contains("@Component"),
            "InMemory implementation should be a @Component");
    }

    @Test
    @DisplayName("MetadataExtractor generates all safe parse helpers")
    void testMetadataExtractorAllSafeParseHelpers(@TempDir Path tempDir) throws Exception {
        String content = readMetadataExtractor(tempDir);
        assertTrue(content.contains("private int safeParseInt(String value, String headerName)"),
            "Should generate safeParseInt helper");
        assertTrue(content.contains("private long safeParseLong(String value, String headerName)"),
            "Should generate safeParseLong helper");
        assertTrue(content.contains("private double safeParseDouble(String value, String headerName)"),
            "Should generate safeParseDouble helper");
    }

    @Test
    @DisplayName("RuleEvaluationResult DTO has outcomes and hasFailures fields")
    void testRuleEvaluationResultDto(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "dto", "RuleEvaluationResult.java");
        assertTrue(content.contains("List<RuleOutcome> outcomes"),
            "Should have outcomes field");
        assertTrue(content.contains("boolean hasFailures"),
            "Should have hasFailures field");
    }

    @Test
    @DisplayName("RuleOutcome DTO has ruleName, status, and satisfied fields")
    void testRuleOutcomeDto(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "dto", "RuleOutcome.java");
        assertTrue(content.contains("String ruleName"), "Should have ruleName field");
        assertTrue(content.contains("String status"), "Should have status field");
        assertTrue(content.contains("boolean satisfied"), "Should have satisfied field");
    }

    @Test
    @DisplayName("RuleAwareResponse DTO wraps data and ruleResults")
    void testRuleAwareResponseDto(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "dto", "RuleAwareResponse.java");
        assertTrue(content.contains("ruleResults"), "Should have ruleResults field");
        assertTrue(content.contains("data"), "Should have data field");
    }

    // ---- View component tests (existing) ----

    @Test
    @DisplayName("View controller has GET mappings only (no POST, PUT, DELETE)")
    void testViewControllerGetOnly(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "controller", "PostDetailViewController.java");
        assertTrue(content.contains("@GetMapping"), "View controller should have @GetMapping");
        assertFalse(content.contains("@PostMapping"), "View controller should NOT have @PostMapping");
        assertFalse(content.contains("@PutMapping"), "View controller should NOT have @PutMapping");
        assertFalse(content.contains("@DeleteMapping"), "View controller should NOT have @DeleteMapping");
    }

    @Test
    @DisplayName("View controller does not reference ruleService (views have no rule evaluation)")
    void testViewControllerNoRuleService(@TempDir Path tempDir) throws Exception {
        String controllerContent = generateAndReadFile(tempDir,
            "dev", "appget", "server", "controller", "PostDetailViewController.java");
        assertFalse(controllerContent.contains("ruleService"),
            "View controller should not reference ruleService");

        String serviceContent = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "PostDetailViewService.java");
        assertFalse(serviceContent.contains("ruleService"),
            "View service should not reference ruleService");
    }

    @Test
    @DisplayName("View repository file exists in repository directory")
    void testViewRepositoryExists(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "repository", "PostDetailViewRepository.java");
        assertNotNull(content, "PostDetailViewRepository.java should be generated");
        assertTrue(content.contains("interface PostDetailViewRepository"),
            "Should declare PostDetailViewRepository interface");
    }

    @Test
    @DisplayName("View repository is read-only (no deleteById)")
    void testViewRepositoryReadOnly(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir,
            "dev", "appget", "server", "repository", "PostDetailViewRepository.java");
        assertFalse(content.contains("deleteById"),
            "View repository should not have deleteById");
        assertFalse(content.contains("existsById"),
            "View repository should not have existsById");
        assertTrue(content.contains("findById"),
            "View repository should have findById");
        assertTrue(content.contains("findAll"),
            "View repository should have findAll");
    }

    // ---- Composite primary key tests (GAP-R1) ----

    @Test
    @DisplayName("Single-PK model uses /{id} path variable (regression)")
    void testSinglePkModelUsesIdPathVariable(@TempDir Path tempDir) throws Exception {
        String controller = generateAndReadFile(tempDir,
            "dev", "appget", "server", "controller", "UsersController.java");
        assertTrue(controller.contains("value = \"/{id}\""),
            "Single-PK controller GET should use /{id}");
        assertTrue(controller.contains("@PutMapping(value = \"/{id}\""),
            "Single-PK controller PUT should use /{id}");
        assertTrue(controller.contains("@DeleteMapping(\"/{id}\")"),
            "Single-PK controller DELETE should use /{id}");
        assertTrue(controller.contains("@PathVariable String id"),
            "Single-PK controller should have @PathVariable String id");

        String service = generateAndReadFile(tempDir,
            "dev", "appget", "server", "service", "UsersService.java");
        assertTrue(service.contains("findById(String id)"),
            "Single-PK service findById should take String id");
        assertTrue(service.contains("deleteById(String id)"),
            "Single-PK service deleteById should take String id");

        String repoInterface = generateAndReadFile(tempDir,
            "dev", "appget", "server", "repository", "UsersRepository.java");
        assertTrue(repoInterface.contains("findById(String id)"),
            "Single-PK repository interface findById should take String id");
        assertTrue(repoInterface.contains("deleteById(String id)"),
            "Single-PK repository interface deleteById should take String id");
        assertTrue(repoInterface.contains("existsById(String id)"),
            "Single-PK repository interface existsById should take String id");
    }

    @Test
    @DisplayName("Composite-PK model generates multiple path variables")
    void testCompositePkModelGeneratesMultiplePathVariables(@TempDir Path tempDir) throws Exception {
        // Create a minimal models.yaml with a composite-key model
        String compositeModelsYaml =
            "schema_version: 1\n" +
            "organization: appget\n" +
            "domains:\n" +
            "  admin:\n" +
            "    namespace: dev.appget.admin\n" +
            "    models:\n" +
            "      - name: team_members\n" +
            "        source_table: team_members\n" +
            "        fields:\n" +
            "          - name: team_id\n" +
            "            type: string\n" +
            "            nullable: false\n" +
            "            field_number: 1\n" +
            "            primary_key: true\n" +
            "            primary_key_position: 1\n" +
            "          - name: user_id\n" +
            "            type: string\n" +
            "            nullable: false\n" +
            "            field_number: 2\n" +
            "            primary_key: true\n" +
            "            primary_key_position: 2\n" +
            "          - name: role\n" +
            "            type: string\n" +
            "            nullable: false\n" +
            "            field_number: 3\n";

        String minimalSpecsYaml =
            "metadata: {}\n" +
            "rules: []\n";

        Path modelsFile = tempDir.resolve("composite-models.yaml");
        Path specsFile = tempDir.resolve("composite-specs.yaml");
        Files.writeString(modelsFile, compositeModelsYaml);
        Files.writeString(specsFile, minimalSpecsYaml);

        Path outputDir = tempDir.resolve("output");
        Files.createDirectories(outputDir);

        AppServerGenerator compositeGenerator = new AppServerGenerator();
        compositeGenerator.generateServer(modelsFile.toString(), specsFile.toString(), outputDir.toString());

        // Verify controller has composite path variables
        String controller = Files.readString(Paths.get(outputDir.toString(),
            "dev", "appget", "server", "controller", "TeamMembersController.java"));
        assertTrue(controller.contains("value = \"/{teamId}/{userId}\""),
            "Composite-PK controller GET should use /{teamId}/{userId}");
        assertTrue(controller.contains("@PutMapping(value = \"/{teamId}/{userId}\""),
            "Composite-PK controller PUT should use /{teamId}/{userId}");
        assertTrue(controller.contains("@DeleteMapping(\"/{teamId}/{userId}\")"),
            "Composite-PK controller DELETE should use /{teamId}/{userId}");
        assertTrue(controller.contains("@PathVariable String teamId"),
            "Composite-PK controller should have @PathVariable String teamId");
        assertTrue(controller.contains("@PathVariable String userId"),
            "Composite-PK controller should have @PathVariable String userId");
        // Should NOT contain single /{id} for the get/put/delete endpoints
        assertFalse(controller.contains("@GetMapping(\"/{id}\")"),
            "Composite-PK controller should not use /{id}");

        // Verify service has composite parameters
        String service = Files.readString(Paths.get(outputDir.toString(),
            "dev", "appget", "server", "service", "TeamMembersService.java"));
        assertTrue(service.contains("findById(String teamId, String userId)"),
            "Composite-PK service findById should take two String params");
        assertTrue(service.contains("deleteById(String teamId, String userId)"),
            "Composite-PK service deleteById should take two String params");
        assertTrue(service.contains("update(String teamId, String userId,"),
            "Composite-PK service update should take two String params");

        // Verify repository interface has composite parameters
        String repoInterface = Files.readString(Paths.get(outputDir.toString(),
            "dev", "appget", "server", "repository", "TeamMembersRepository.java"));
        assertTrue(repoInterface.contains("findById(String teamId, String userId)"),
            "Composite-PK repo interface findById should take two String params");
        assertTrue(repoInterface.contains("deleteById(String teamId, String userId)"),
            "Composite-PK repo interface deleteById should take two String params");
        assertTrue(repoInterface.contains("existsById(String teamId, String userId)"),
            "Composite-PK repo interface existsById should take two String params");

        // Verify in-memory repository builds composite key
        String repoImpl = Files.readString(Paths.get(outputDir.toString(),
            "dev", "appget", "server", "repository", "InMemoryTeamMembersRepository.java"));
        assertTrue(repoImpl.contains("entity.getTeamId() + \":\" + entity.getUserId()"),
            "Composite-PK repo save should build composite key from entity getters");
        assertTrue(repoImpl.contains("teamId + \":\" + userId"),
            "Composite-PK repo findById/deleteById should build composite key from params");
    }
}
