package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HtmlCrudGenerator#generateTemplates} — the Phase-0f Spec C
 * template emission feature.
 *
 * <p>Uses its own {@code @TempDir} that is completely separate from
 * {@code HtmlCrudGeneratorTest}'s temp dir. The existing
 * {@code testTotalHtmlFileCount} assertion (67 files in the static tree) is
 * not affected: {@code generateHtml} is never called here, and templates are
 * written only into the {@code @TempDir} passed to {@code generateTemplates}.
 *
 * <p>Anchor model: {@code auth/users} — confirmed present by the existing
 * {@code HtmlCrudGeneratorTest} assertions on domain headings and model links.
 */
@DisplayName("HTML Template Emission Tests (Spec C)")
class HtmlTemplateEmissionTest {

    private HtmlCrudGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HtmlCrudGenerator();
    }

    /**
     * Runs {@code generateTemplates} into the given temp dir and returns the content
     * of the requested relative path.
     */
    private String generateAndReadTemplate(Path tempDir, String relativePath) throws Exception {
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root — run 'make all' first");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateTemplates("models.yaml", specsPath, tempDir.toString());
        Path filePath = tempDir.resolve(relativePath);
        assertTrue(Files.exists(filePath), "Expected template file: " + filePath);
        return Files.readString(filePath);
    }

    // ---- Root Index ----

    @Test
    @DisplayName("Root index.html is fully static — no {{CONTENT}} placeholder")
    void testRootIndexIsFullyStatic(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "index.html");
        assertFalse(content.contains("{{CONTENT}}"),
                "Root index template must be fully static — no {{CONTENT}} placeholder");
        assertTrue(content.contains("APPGET"), "Root index should contain APPGET heading");
    }

    // ---- Anchor Model: auth/users ----

    @Test
    @DisplayName("All four template files exist for auth/users model")
    void testAnchorModelTemplatesExist(@TempDir Path tempDir) throws Exception {
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root — run 'make all' first");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateTemplates("models.yaml", specsPath, tempDir.toString());

        // Anchor model: auth/users — confirmed by HtmlCrudGeneratorTest domain assertions
        assertTrue(Files.exists(tempDir.resolve("auth/users/list.html")),
                "auth/users/list.html must exist");
        assertTrue(Files.exists(tempDir.resolve("auth/users/detail.html")),
                "auth/users/detail.html must exist");
        assertTrue(Files.exists(tempDir.resolve("auth/users/edit.html")),
                "auth/users/edit.html must exist");
        assertTrue(Files.exists(tempDir.resolve("auth/users/create.html")),
                "auth/users/create.html must exist");
    }

    // ---- list.html ----

    @Test
    @DisplayName("list.html contains exactly one {{CONTENT}} inside <tbody>")
    void testListTemplateContentPlaceholder(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/list.html");

        // Exactly one occurrence
        int count = countOccurrences(content, "{{CONTENT}}");
        assertEquals(1, count, "list.html must contain exactly one {{CONTENT}} placeholder");

        // Must be inside <tbody>
        assertTrue(content.contains("<tbody>{{CONTENT}}</tbody>"),
                "{{CONTENT}} must be inside <tbody> in list.html");
    }

    @Test
    @DisplayName("list.html retains trailing <th>Actions</th> column header")
    void testListTemplateHasActionsColumn(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/list.html");
        assertTrue(content.contains("<th>Actions</th>"),
                "Model list.html must retain trailing <th>Actions</th> header");
    }

    @Test
    @DisplayName("list.html <thead> column headers match static page headers")
    void testListTemplateHeadersMatchStaticPage(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/list.html");
        // users fields should be present as <th> elements (structural parity with generateListHtml)
        assertTrue(content.contains("<thead>"), "list.html must have a <thead>");
        assertTrue(content.contains("<th>"), "list.html must have at least one <th>");
        // Verify Actions is last (after field columns)
        int lastFieldTh = content.lastIndexOf("<th>");
        int actionsTh = content.indexOf("<th>Actions</th>");
        assertTrue(actionsTh >= 0, "<th>Actions</th> must be present");
        // Actions column must appear after at least one other column
        assertTrue(content.indexOf("<th>") < actionsTh,
                "<th>Actions</th> must follow at least one field column");
    }

    // ---- detail.html ----

    @Test
    @DisplayName("detail.html contains {{CONTENT}} inside <dl>")
    void testDetailTemplateContentPlaceholder(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/detail.html");

        int count = countOccurrences(content, "{{CONTENT}}");
        assertEquals(1, count, "detail.html must contain exactly one {{CONTENT}} placeholder");

        // {{CONTENT}} must be within the <dl>...</dl> block
        assertTrue(content.contains("<dl>"), "detail.html must have a <dl>");
        int dlStart = content.indexOf("<dl>");
        int dlEnd = content.indexOf("</dl>");
        int placeholder = content.indexOf("{{CONTENT}}");
        assertTrue(placeholder > dlStart && placeholder < dlEnd,
                "{{CONTENT}} must be inside the <dl> block in detail.html");
    }

    // ---- edit.html ----

    @Test
    @DisplayName("edit.html contains exactly one {{CONTENT}} placeholder")
    void testEditTemplateContentPlaceholder(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/edit.html");

        int count = countOccurrences(content, "{{CONTENT}}");
        assertEquals(1, count, "edit.html must contain exactly one {{CONTENT}} placeholder");
    }

    @Test
    @DisplayName("edit.html retains hidden _method=PUT input verbatim")
    void testEditTemplateHiddenMethodPut(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/edit.html");
        assertTrue(content.contains("<input type=\"hidden\" name=\"_method\" value=\"PUT\">"),
                "edit.html must contain hidden _method=PUT input");
    }

    @Test
    @DisplayName("edit.html retains hidden id input with NO value= attribute")
    void testEditTemplateHiddenIdNoValue(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/edit.html");
        // Must contain the id input
        assertTrue(content.contains("<input type=\"hidden\" name=\"id\" id=\"id\">"),
                "edit.html must contain hidden id input");
        // Must NOT have value= on the id hidden input (mirrors static page, NOT parent spec example)
        assertFalse(content.contains("name=\"id\" id=\"id\" value="),
                "edit.html id hidden input must NOT have a value= attribute");
    }

    @Test
    @DisplayName("edit.html submit button reads 'Update' (not 'Save')")
    void testEditTemplateSubmitButtonText(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/edit.html");
        assertTrue(content.contains("Update"),
                "edit.html submit button must read 'Update'");
        assertFalse(content.contains(">Save<"),
                "edit.html submit button must NOT read 'Save'");
    }

    // ---- create.html ----

    @Test
    @DisplayName("create.html is fully static — no {{CONTENT}} placeholder")
    void testCreateTemplateIsFullyStatic(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/create.html");
        assertFalse(content.contains("{{CONTENT}}"),
                "create.html must be fully static — no {{CONTENT}} placeholder");
    }

    @Test
    @DisplayName("create.html contains real input elements (reuses fieldToInput)")
    void testCreateTemplateHasRealInputs(@TempDir Path tempDir) throws Exception {
        String content = generateAndReadTemplate(tempDir, "auth/users/create.html");
        // users.username is VARCHAR NOT NULL → type=text required
        assertTrue(content.contains("<input type=\"text\" name=\"username\""),
                "create.html must contain a text input for 'username'");
        // users.bio is TEXT nullable → textarea without required
        assertTrue(content.contains("<textarea name=\"bio\""),
                "create.html must contain a textarea for 'bio' (TEXT field)");
    }

    // ---- View list template ----

    @Test
    @DisplayName("View emits views/{view-resource}/list.html with {{CONTENT}}")
    void testViewListTemplateExists(@TempDir Path tempDir) throws Exception {
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root — run 'make all' first");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateTemplates("models.yaml", specsPath, tempDir.toString());

        // user-role-view → views/user-role/list.html (confirmed by HtmlCrudGeneratorTest)
        Path viewList = tempDir.resolve("views/user-role/list.html");
        assertTrue(Files.exists(viewList), "views/user-role/list.html must exist");

        String content = Files.readString(viewList);
        assertTrue(content.contains("<tbody>{{CONTENT}}</tbody>"),
                "View list.html must have {{CONTENT}} inside <tbody>");
    }

    @Test
    @DisplayName("View list.html has NO trailing Actions column")
    void testViewListTemplateNoActionsColumn(@TempDir Path tempDir) throws Exception {
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root — run 'make all' first");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateTemplates("models.yaml", specsPath, tempDir.toString());

        String content = Files.readString(tempDir.resolve("views/user-role/list.html"));
        assertFalse(content.contains("<th>Actions</th>"),
                "View list.html must NOT have an Actions column (views are read-only)");
    }

    @Test
    @DisplayName("View emits NO create/edit/detail templates (read-only)")
    void testViewHasNoCreateEditDetailTemplates(@TempDir Path tempDir) throws Exception {
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root — run 'make all' first");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateTemplates("models.yaml", specsPath, tempDir.toString());

        assertFalse(Files.exists(tempDir.resolve("views/user-role/create.html")),
                "Views must NOT have a create.html template");
        assertFalse(Files.exists(tempDir.resolve("views/user-role/edit.html")),
                "Views must NOT have an edit.html template");
        assertFalse(Files.exists(tempDir.resolve("views/user-role/detail.html")),
                "Views must NOT have a detail.html template");
    }

    // ---- Isolation: generateTemplates writes ONLY into templatesDir ----

    @Test
    @DisplayName("generateTemplates writes only into the explicit templatesDir (not generated-html/)")
    void testGenerateTemplatesDoesNotTouchGeneratedHtml(@TempDir Path tempDir) throws Exception {
        // Verify that the templates dir is a fresh subdir of tempDir
        // and that we only write files there (not into a sibling/parent).
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root — run 'make all' first");
        }
        Path myTemplatesDir = tempDir.resolve("my-templates");
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        generator.generateTemplates("models.yaml", specsPath, myTemplatesDir.toString());

        // index.html must be inside myTemplatesDir
        assertTrue(Files.exists(myTemplatesDir.resolve("index.html")),
                "index.html must be written inside the explicit templatesDir");

        // Nothing should be written outside myTemplatesDir
        long filesOutside;
        try (java.util.stream.Stream<Path> s = Files.walk(tempDir)) {
            filesOutside = s.filter(p -> !p.startsWith(myTemplatesDir))
                           .filter(Files::isRegularFile)
                           .count();
        }
        assertEquals(0, filesOutside,
                "generateTemplates must not write any file outside the explicit templatesDir");
    }

    // ---- Utility ----

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) != -1) {
            count++;
            idx += token.length();
        }
        return count;
    }
}
