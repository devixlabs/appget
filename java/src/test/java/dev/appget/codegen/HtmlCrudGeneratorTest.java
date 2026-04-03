package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HTML CRUD Generator Tests")
class HtmlCrudGeneratorTest {

    private HtmlCrudGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HtmlCrudGenerator();
    }

    /**
     * Generates HTML to tempDir (with specs.yaml if available) and reads a file.
     */
    private String generateAndReadFile(Path tempDir, String relativePath) throws Exception {
        String outputDir = tempDir.toString();
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateHtml("models.yaml", specsPath, outputDir);
        Path filePath = Paths.get(outputDir, relativePath);
        assertTrue(Files.exists(filePath), "Expected file: " + filePath);
        return Files.readString(filePath);
    }

    // ---- Root Index Tests ----

    @Test
    @DisplayName("Root index exists and has APPGET title")
    void testRootIndexTitle(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "index.html");
        assertTrue(content.contains("APPGET"), "Root index should have 'APPGET' in title/heading");
    }

    @Test
    @DisplayName("Root index contains all 3 domain headings")
    void testRootIndexDomainHeadings(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "index.html");
        assertTrue(content.contains("<h2>admin</h2>"), "Root index should have admin domain heading");
        assertTrue(content.contains("<h2>auth</h2>"), "Root index should have auth domain heading");
        assertTrue(content.contains("<h2>social</h2>"), "Root index should have social domain heading");
    }

    @Test
    @DisplayName("Root index has model links for known resources")
    void testRootIndexModelLinks(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "index.html");
        assertTrue(content.contains("href=\"roles/index.html\""), "Root index should link to roles");
        assertTrue(content.contains("href=\"users/index.html\""), "Root index should link to users");
        assertTrue(content.contains("href=\"posts/index.html\""), "Root index should link to posts");
    }

    @Test
    @DisplayName("Root index has view links with views/ prefix")
    void testRootIndexViewLinks(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "index.html");
        assertTrue(content.contains("href=\"views/user-role/index.html\""),
                "Root index should link to user-role view");
        assertTrue(content.contains("href=\"views/post-detail/index.html\""),
                "Root index should link to post-detail view");
    }

    // ---- Page Existence Tests ----

    @Test
    @DisplayName("Model has 4 pages: index, create, edit, view")
    void testModelHasFourPages(@TempDir Path tempDir) throws Exception {
        generateAndReadFile(tempDir, "roles/index.html");
        Path outDir = tempDir;
        assertTrue(Files.exists(outDir.resolve("roles/index.html")), "roles/index.html should exist");
        assertTrue(Files.exists(outDir.resolve("roles/create.html")), "roles/create.html should exist");
        assertTrue(Files.exists(outDir.resolve("roles/edit.html")), "roles/edit.html should exist");
        assertTrue(Files.exists(outDir.resolve("roles/view.html")), "roles/view.html should exist");
    }

    @Test
    @DisplayName("View has only index.html, no create/edit/view pages")
    void testViewHasOnlyIndexPage(@TempDir Path tempDir) throws Exception {
        generateAndReadFile(tempDir, "views/user-role/index.html");
        Path outDir = tempDir;
        assertTrue(Files.exists(outDir.resolve("views/user-role/index.html")),
                "views/user-role/index.html should exist");
        assertFalse(Files.exists(outDir.resolve("views/user-role/create.html")),
                "views/user-role/create.html should NOT exist");
        assertFalse(Files.exists(outDir.resolve("views/user-role/edit.html")),
                "views/user-role/edit.html should NOT exist");
    }

    @Test
    @DisplayName("Total HTML file count is 67")
    void testTotalHtmlFileCount(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateHtml("models.yaml", specsPath, outputDir);

        try (Stream<Path> paths = Files.walk(tempDir)) {
            long count = paths
                    .filter(p -> p.toString().endsWith(".html"))
                    .count();
            assertEquals(67, count, "Total HTML file count should be 67");
        }
    }

    // ---- Form Action / Route Tests ----

    @Test
    @DisplayName("Create form action matches REST route for roles")
    void testCreateFormActionMatchesRoute(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "roles/create.html");
        assertTrue(content.contains("action=\"/roles\""),
                "Create form should post to /roles");
    }

    @Test
    @DisplayName("Edit form has hidden _method=PUT field")
    void testEditFormHasHiddenMethodPut(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "roles/edit.html");
        assertTrue(content.contains("type=\"hidden\" name=\"_method\" value=\"PUT\""),
                "Edit form should include hidden _method=PUT field");
    }

    @Test
    @DisplayName("View list page references correct API route")
    void testViewListApiRoute(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "views/post-detail/index.html");
        assertTrue(content.contains("/views/post-detail"),
                "View list page should reference /views/post-detail API route");
    }

    // ---- Field Type Mapping Tests ----

    @Test
    @DisplayName("VARCHAR field renders as type=text input")
    void testVarcharFieldRendersAsTextInput(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "users/create.html");
        // username is VARCHAR(100), NOT NULL
        assertTrue(content.contains("type=\"text\" name=\"username\""),
                "VARCHAR field 'username' should render as type=text");
    }

    @Test
    @DisplayName("TEXT field renders as textarea")
    void testTextFieldRendersAsTextarea(@TempDir Path tempDir) throws Exception {
        // users.bio is TEXT and nullable
        String content = generateAndReadFile(tempDir, "users/create.html");
        assertTrue(content.contains("<textarea name=\"bio\""),
                "TEXT field 'bio' should render as textarea");
    }

    @Test
    @DisplayName("INT field renders as type=number step=1")
    void testIntFieldRendersAsNumberStepOne(@TempDir Path tempDir) throws Exception {
        // roles.permission_level is INT, NOT NULL
        String content = generateAndReadFile(tempDir, "roles/create.html");
        assertTrue(content.contains("type=\"number\" step=\"1\" name=\"permission_level\""),
                "INT field 'permission_level' should render as type=number step=1");
    }

    @Test
    @DisplayName("BOOLEAN field renders as type=checkbox")
    void testBooleanFieldRendersAsCheckbox(@TempDir Path tempDir) throws Exception {
        // posts.is_public is BOOLEAN
        String content = generateAndReadFile(tempDir, "posts/create.html");
        assertTrue(content.contains("type=\"checkbox\" name=\"is_public\""),
                "BOOLEAN field 'is_public' should render as type=checkbox");
    }

    // ---- Validation Tests ----

    @Test
    @DisplayName("NOT NULL field has required attribute")
    void testNotNullFieldHasRequiredAttribute(@TempDir Path tempDir) throws Exception {
        // users.username is NOT NULL
        String content = generateAndReadFile(tempDir, "users/create.html");
        assertTrue(content.contains("name=\"username\" id=\"username\" required"),
                "NOT NULL field 'username' should have required attribute");
    }

    @Test
    @DisplayName("Nullable field does NOT have required attribute")
    void testNullableFieldHasNoRequiredAttribute(@TempDir Path tempDir) throws Exception {
        // users.display_name is nullable
        String content = generateAndReadFile(tempDir, "users/create.html");
        assertTrue(content.contains("name=\"display_name\" id=\"display_name\">"),
                "Nullable field 'display_name' should NOT have required attribute");
        assertFalse(content.contains("name=\"display_name\" id=\"display_name\" required"),
                "Nullable field 'display_name' should not have required");
    }

    @Test
    @DisplayName("Checkbox inputs never have required attribute")
    void testCheckboxInputsNeverHaveRequired(@TempDir Path tempDir) throws Exception {
        // posts has multiple BOOLEAN fields (is_public, is_deleted) — none should be required
        String content = generateAndReadFile(tempDir, "posts/create.html");
        // Extract all checkbox inputs and verify none have required
        assertFalse(content.contains("type=\"checkbox\" name=\"is_public\" id=\"is_public\" required"),
                "Checkbox 'is_public' should not have required attribute");
        assertFalse(content.contains("type=\"checkbox\" name=\"is_deleted\" id=\"is_deleted\" required"),
                "Checkbox 'is_deleted' should not have required attribute");
    }

    // ---- PK Field Handling Tests ----

    @Test
    @DisplayName("ID field is omitted on create page")
    void testIdFieldOmittedOnCreate(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "roles/create.html");
        assertFalse(content.contains("name=\"id\""),
                "Create page should not include any input with name='id'");
    }

    @Test
    @DisplayName("ID field is rendered as hidden input on edit page")
    void testIdFieldHiddenOnEdit(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "roles/edit.html");
        assertTrue(content.contains("type=\"hidden\" name=\"id\" id=\"id\""),
                "Edit page should render id as hidden input");
    }

    // ---- Business Rules Tests ----

    @Test
    @DisplayName("Rules block present on create page for model with rules")
    void testRulesBlockPresentWhenRulesExist(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadFile(tempDir, "roles/create.html");
        assertTrue(content.contains("<details>"),
                "Create page for roles should have a <details> rules block");
        assertTrue(content.contains("AdminRoleClassification"),
                "Rules block should contain rule name 'AdminRoleClassification'");
    }

    @Test
    @DisplayName("Blocking rule is marked with [BLOCKING] prefix")
    void testBlockingRuleHasPrefix(@TempDir Path tempDir) throws Exception {
        // posts has [BLOCKING] PostNotDeletedCheck
        String content = generateAndReadFile(tempDir, "posts/create.html");
        assertTrue(content.contains("[BLOCKING] PostNotDeletedCheck"),
                "Blocking rule 'PostNotDeletedCheck' should be prefixed with [BLOCKING]");
    }

    @Test
    @DisplayName("Non-blocking rule is listed without [BLOCKING] prefix")
    void testNonBlockingRuleHasNoPrefix(@TempDir Path tempDir) throws Exception {
        // posts has PostPublicityStatus which is not blocking
        String content = generateAndReadFile(tempDir, "posts/create.html");
        assertTrue(content.contains("<li>PostPublicityStatus</li>"),
                "Non-blocking rule 'PostPublicityStatus' should be listed without [BLOCKING] prefix");
    }

    @Test
    @DisplayName("Graceful degradation: null specs path still generates pages without rules block")
    void testGracefulDegradationNullSpecs(@TempDir Path tempDir) throws Exception {
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root");
        }
        // Generate with null specs path (no rules)
        generator.generateHtml("models.yaml", null, tempDir.toString());

        Path rolesCreate = tempDir.resolve("roles/create.html");
        assertTrue(Files.exists(rolesCreate), "roles/create.html should be generated even without specs");

        String content = Files.readString(rolesCreate);
        assertFalse(content.contains("<details>"),
                "Create page should have no rules block when specs not provided");
        // Form should still render properly
        assertTrue(content.contains("action=\"/roles\""),
                "Form action should still be correct without specs");
    }
}
