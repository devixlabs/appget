package dev.appget.codegen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden snapshot tests for the structural shape of HTML pages produced by
 * {@link HtmlCrudGenerator}.
 *
 * <h3>What these tests verify</h3>
 * Each test normalizes a generated HTML page with {@link HtmlStructuralNormalizer}
 * and compares the result against a committed golden file under
 * {@code src/test/resources/html-structure-golden/}. A mismatch means the
 * generator changed the structure of a page without a deliberate golden update.
 *
 * <h3>Golden file update workflow</h3>
 * If {@code HtmlCrudGenerator} intentionally changes page structure:
 * <ol>
 *   <li>Run {@code make generate-html} to regenerate the static pages.</li>
 *   <li>Delete the stale golden(s) under {@code src/test/resources/html-structure-golden/}.</li>
 *   <li>Run the tests once — they will fail with the new normalized form in the failure message.</li>
 *   <li>Copy the printed normalized form into the golden file and commit.</li>
 * </ol>
 *
 * <h3>Extension point for 0f-core</h3>
 * When PageRenderers exist, 0f-core will call {@code HtmlStructuralNormalizer.normalize()}
 * on runtime HTML and diff it against these same goldens. The {@code #TEXT} sentinel
 * absorbs live data so only structural mismatches cause failures. See the class
 * Javadoc on {@link HtmlStructuralNormalizer} for the {@code value=} nuance relevant
 * to 0f-core's runtime comparison.
 */
@DisplayName("HTML Structural Snapshot Tests")
class HtmlStructuralSnapshotTest {

    // The model chosen to anchor the snapshots: "users" (auth domain) exercises
    // text, textarea, checkbox, and number inputs, plus business rules.
    // "roles" (admin domain) is included as a second model to capture number step=1
    // without textarea. "views/user-role" is included for the view page type.
    private static final String GOLDEN_RESOURCE_DIR = "html-structure-golden";

    // Shared generated output — all snapshot tests reuse this directory via
    // BeforeAll so we only invoke the generator once per test class run.
    @TempDir
    static Path sharedTempDir;

    @BeforeAll
    static void generateHtml() throws Exception {
        if (!new File("models.yaml").exists()) {
            fail("models.yaml must exist at project root (run 'make all' before bare test runs)");
        }
        String specsPath = new File("specs.yaml").exists() ? "specs.yaml" : null;
        new HtmlCrudGenerator().generateHtml("models.yaml", specsPath, sharedTempDir.toString());
    }

    // ---- Snapshot tests ----

    @Test
    @DisplayName("Root index structural snapshot")
    void testRootIndexSnapshot() throws Exception {
        assertStructuralSnapshot("index.html", "root-index.structure.txt");
    }

    @Test
    @DisplayName("users/create.html structural snapshot")
    void testUsersCreateSnapshot() throws Exception {
        assertStructuralSnapshot("users/create.html", "users-create.structure.txt");
    }

    @Test
    @DisplayName("users/edit.html structural snapshot")
    void testUsersEditSnapshot() throws Exception {
        assertStructuralSnapshot("users/edit.html", "users-edit.structure.txt");
    }

    @Test
    @DisplayName("users/index.html (list) structural snapshot")
    void testUsersIndexSnapshot() throws Exception {
        assertStructuralSnapshot("users/index.html", "users-index.structure.txt");
    }

    @Test
    @DisplayName("users/view.html structural snapshot")
    void testUsersViewSnapshot() throws Exception {
        assertStructuralSnapshot("users/view.html", "users-view.structure.txt");
    }

    @Test
    @DisplayName("views/user-role/index.html structural snapshot")
    void testUserRoleViewSnapshot() throws Exception {
        assertStructuralSnapshot("views/user-role/index.html", "views-user-role-index.structure.txt");
    }

    // ---- Helper ----

    /**
     * Normalizes the generated page at {@code relativePagePath} and compares it
     * against the golden file named {@code goldenFileName} in the test resources.
     *
     * <p>If the golden file does not exist, the test fails with the current
     * normalized form printed — copy that output into a new golden file.
     */
    private static void assertStructuralSnapshot(String relativePagePath, String goldenFileName)
            throws IOException, URISyntaxException {
        Path pagePath = sharedTempDir.resolve(relativePagePath);
        assertTrue(Files.exists(pagePath),
                "Generated page not found: " + pagePath);

        String html = Files.readString(pagePath, StandardCharsets.UTF_8);
        String actual = HtmlStructuralNormalizer.normalize(html);

        URL goldenUrl = HtmlStructuralSnapshotTest.class.getClassLoader()
                .getResource(GOLDEN_RESOURCE_DIR + "/" + goldenFileName);

        if (goldenUrl == null) {
            fail("Golden file not found: " + GOLDEN_RESOURCE_DIR + "/" + goldenFileName
                    + "\nCreate it with this content:\n---\n" + actual + "---");
        }

        String expected = Files.readString(Paths.get(goldenUrl.toURI()), StandardCharsets.UTF_8);
        assertEquals(expected, actual,
                "Structural snapshot mismatch for " + relativePagePath
                        + "\nIf the change is intentional, update: src/test/resources/"
                        + GOLDEN_RESOURCE_DIR + "/" + goldenFileName);
    }
}
