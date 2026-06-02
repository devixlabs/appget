package dev.appget.codegen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live structural diff test: GET each HTML page from the running server,
 * normalize the body with {@link HtmlStructuralNormalizer#normalizeRuntime(String)},
 * and assert equality with the committed golden under
 * {@code src/test/resources/html-structure-golden/}.
 *
 * <h3>Requirements</h3>
 * <ul>
 *   <li>Server must be running at {@code http://localhost:8080}
 *       (start with {@code make run-server}).</li>
 *   <li>Requires {@code make all} to have run first (generated server present).</li>
 * </ul>
 *
 * <h3>Run</h3>
 * <pre>
 *   make run-server           # terminal 1
 *   ./gradlew testLive        # terminal 2
 *   # or: make verify         # umbrella (API + HTTP + live structural diff)
 * </pre>
 *
 * <h3>Excluded from default test suite</h3>
 * This class is annotated {@code @Tag("live")} and excluded from the default
 * Gradle {@code test} task (and therefore from {@code make all}).  It runs only
 * via the dedicated {@code testLive} Gradle task invoked by {@code make verify}.
 *
 * <h3>Root-index golden</h3>
 * {@code root-index.structure.txt} has no runtime endpoint — the generated
 * server has no {@code GET /} controller.  That golden is static-only and is
 * intentionally omitted from this test.  The 5 pages covered are:
 * users-index, users-view, users-edit, users-create, and views-user-role-index.
 */
@Tag("live")
@DisplayName("Runtime HTML Structural Diff Tests (requires running server)")
class RuntimeHtmlStructureLiveTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String GOLDEN_RESOURCE_DIR = "html-structure-golden";
    private static final String SEED_USER_ID = "u-struct-1";

    /**
     * Metadata headers from {@code tests/http-tests.yaml} config.default_headers.
     * These are required so business rules (AdminAuthenticationRequired etc.) pass
     * on write endpoints during seeding.
     */
    private static final String[][] METADATA_HEADERS = {
        {"Content-Type",      "application/json"},
        {"X-Sso-Authenticated", "true"},
        {"X-Sso-Session-Id",  "test-session"},
        {"X-Roles-Role-Name", "admin"},
        {"X-Roles-Role-Level","10"},
        {"X-Roles-Is-Admin",  "true"},
        {"X-User-User-Id",    "test-user-1"},
        {"X-User-Email",      "test@appget.dev"},
        {"X-User-Username",   "testuser"}
    };

    private static HttpClient client;

    @BeforeAll
    static void seedDeterministicUser() throws IOException, InterruptedException {
        client = HttpClient.newHttpClient();

        // Build the JSON body for the deterministic seed user
        String body = "{"
            + "\"id\":\"" + SEED_USER_ID + "\","
            + "\"username\":\"structuser\","
            + "\"email\":\"struct@appget.dev\","
            + "\"displayName\":\"Struct User\","
            + "\"bio\":\"Seeded for structural live test\","
            + "\"isVerified\":true,"
            + "\"isActive\":true,"
            + "\"followerCount\":0"
            + "}";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/users"))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        for (String[] header : METADATA_HEADERS) {
            builder.header(header[0], header[1]);
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 201 = created fresh; 422 = rule violation (unexpected); 409 = already exists (idempotent ok)
        // We allow 201 or 200/2xx — if the user already exists from a prior run, a 409 or similar
        // conflict is also acceptable (idempotent seed).  Only hard-fail on 4xx that is NOT conflict.
        int status = response.statusCode();
        boolean seedOk = (status == 201) || (status == 200) || (status == 409);
        assertTrue(seedOk,
            "Seed user POST /users failed with status " + status
            + ". Response: " + response.body()
            + ". Is the server running at " + BASE_URL + "?");
    }

    // ---- Live structural diff tests ----

    @Test
    @DisplayName("users-index: GET /users matches golden")
    void testUsersIndexMatchesGolden() throws Exception {
        assertRuntimeMatchesGolden("/users", "users-index.structure.txt");
    }

    @Test
    @DisplayName("users-create: GET /users?action=create matches golden")
    void testUsersCreateMatchesGolden() throws Exception {
        assertRuntimeMatchesGolden("/users?action=create", "users-create.structure.txt");
    }

    @Test
    @DisplayName("users-view: GET /users/{id} matches golden")
    void testUsersViewMatchesGolden() throws Exception {
        assertRuntimeMatchesGolden("/users/" + SEED_USER_ID, "users-view.structure.txt");
    }

    @Test
    @DisplayName("users-edit: GET /users/{id}?action=edit matches golden")
    void testUsersEditMatchesGolden() throws Exception {
        assertRuntimeMatchesGolden("/users/" + SEED_USER_ID + "?action=edit", "users-edit.structure.txt");
    }

    @Test
    @DisplayName("views-user-role-index: GET /views/user-role matches golden")
    void testViewsUserRoleIndexMatchesGolden() throws Exception {
        assertRuntimeMatchesGolden("/views/user-role", "views-user-role-index.structure.txt");
    }

    // ---- Helper ----

    /**
     * GETs {@code path} from the running server with {@code Accept: text/html},
     * normalizes the body with {@link HtmlStructuralNormalizer#normalizeRuntime},
     * and asserts equality with the committed golden file.
     *
     * @param path        URL path (may include query string), e.g. {@code /users?action=create}
     * @param goldenFileName file name under {@code src/test/resources/html-structure-golden/}
     */
    private static void assertRuntimeMatchesGolden(String path, String goldenFileName)
            throws IOException, InterruptedException, URISyntaxException {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + path))
            .header("Accept", "text/html")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
            "Expected HTTP 200 from GET " + path + " but got " + response.statusCode()
            + ". Body: " + response.body().substring(0, Math.min(200, response.body().length())));

        String body = response.body();
        String normalized = HtmlStructuralNormalizer.normalizeRuntime(body);

        URL goldenUrl = RuntimeHtmlStructureLiveTest.class.getClassLoader()
            .getResource(GOLDEN_RESOURCE_DIR + "/" + goldenFileName);

        assertNotNull(goldenUrl,
            "Golden file not found on classpath: " + GOLDEN_RESOURCE_DIR + "/" + goldenFileName
            + ". Run 'make all' to ensure test resources are compiled.");

        String golden = Files.readString(Paths.get(goldenUrl.toURI()), StandardCharsets.UTF_8);

        assertEquals(golden.strip(), normalized.strip(),
            "Runtime HTML structural mismatch for GET " + path
            + " vs golden " + goldenFileName
            + ".\nIf the PageRenderer was intentionally changed, update the golden: "
            + "src/test/resources/html-structure-golden/" + goldenFileName);
    }
}
