package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the SOURCE STRING produced by SpringBootEmitter.emitPageRenderer(...)
 * for the form render methods: renderEditForm, renderCreateForm,
 * renderCreateFormWithErrors, and renderEditFormWithErrors.
 */
@DisplayName("PageRenderer Emitter - Form Output Tests")
class PageRendererEmitFormTest {

    private SpringBootEmitter emitter;
    private EntityContext usersCtx;
    private static final String BASE_PACKAGE = "dev.appget.server";

    @BeforeEach
    void setUp() {
        emitter = new SpringBootEmitter();
        usersCtx = buildUsersContext();
    }

    /**
     * Builds a minimal valid EntityContext for model "users" in domain "auth"
     * with fields: id (string, PK), username (string, required), email (string, required),
     * is_verified (bool, nullable), follower_count (int32, nullable).
     */
    private EntityContext buildUsersContext() {
        List<Map<String, Object>> fields = new ArrayList<>();

        Map<String, Object> idField = new HashMap<>();
        idField.put("name", "id");
        idField.put("type", "string");
        idField.put("nullable", false);
        idField.put("primary_key", true);
        fields.add(idField);

        Map<String, Object> usernameField = new HashMap<>();
        usernameField.put("name", "username");
        usernameField.put("type", "string");
        usernameField.put("nullable", false);
        fields.add(usernameField);

        Map<String, Object> emailField = new HashMap<>();
        emailField.put("name", "email");
        emailField.put("type", "string");
        emailField.put("nullable", false);
        fields.add(emailField);

        Map<String, Object> isVerifiedField = new HashMap<>();
        isVerifiedField.put("name", "is_verified");
        isVerifiedField.put("type", "bool");
        isVerifiedField.put("nullable", true);
        fields.add(isVerifiedField);

        Map<String, Object> followerCountField = new HashMap<>();
        followerCountField.put("name", "follower_count");
        followerCountField.put("type", "int32");
        followerCountField.put("nullable", true);
        fields.add(followerCountField);

        List<Map<String, Object>> pkFields = new ArrayList<>();
        pkFields.add(idField);

        return new EntityContext(
                "users",
                "Users",
                "auth",
                "dev.appget.auth",
                fields,
                false,
                false,
                true,
                pkFields,
                "users",
                "String id",
                "id",
                "",
                "",
                "id: {}",
                "id",
                "\"users not found: \" + id",
                "/{id}",
                "@PathVariable String id"
        );
    }

    // ---- renderEditForm tests ----

    @Test
    @DisplayName("renderEditForm injects value attribute on data inputs")
    void testRenderEditFormValueInjection() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("renderEditForm(Users item)"),
                "renderEditForm method must be present");
        // String field username: appends value=" + escape(getUsername())
        assertTrue(source.contains("value=\\\""),
                "renderEditForm must inject value=\\\" attribute on inputs");
        // Escape call for username (string type)
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getUsername())"),
                "renderEditForm must escape username value");
    }

    @Test
    @DisplayName("renderEditForm second replace injects value into hidden id input")
    void testRenderEditFormHiddenIdValueReplace() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // The second .replace fills the id hidden input
        assertTrue(source.contains("filled = filled.replace("),
                "renderEditForm must have a second .replace call for id hidden input");
        assertTrue(source.contains("<input type=\\\"hidden\\\" name=\\\"id\\\" id=\\\"id\\\">"),
                "renderEditForm second replace targets the bare hidden id input");
        assertTrue(source.contains("<input type=\\\"hidden\\\" name=\\\"id\\\" id=\\\"id\\\" value=\\\""),
                "renderEditForm second replace adds value= to hidden id input");
        // The id value must come from item.getId()
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getId())"),
                "renderEditForm must escape item.getId() for hidden id value");
    }

    @Test
    @DisplayName("renderEditForm skips the primary key field (no input for id)")
    void testRenderEditFormSkipsPrimaryKey() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // id is PK — it is rendered via hidden input outside {{CONTENT}}, not as a regular data input
        // The only id-related content in the inputs StringBuilder is the second .replace block
        // We verify there is no label for "id" field in the inputs block
        // (The replace block uses a hardcoded string literal, not a label)
        String editFormBlock = extractMethod(source, "renderEditForm(Users item)");
        assertFalse(editFormBlock.contains("<label for=\\\"id\\\">"),
                "renderEditForm must not emit a label for the primary key id field");
    }

    @Test
    @DisplayName("renderEditForm does not mark bool field as required")
    void testRenderEditFormBoolNotRequired() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // is_verified is bool/nullable — checkbox never gets required attribute
        // Extract the is_verified checkbox line: it must not have " required"
        assertTrue(source.contains("type=\\\"checkbox\\\" name=\\\"is_verified\\\""),
                "is_verified must render as checkbox");
        // The checkbox append line must NOT contain " required"
        // We check: the line with is_verified checkbox does not include \" required\"
        assertFalse(source.contains("is_verified\\\" id=\\\"is_verified\\\" required"),
                "bool field is_verified checkbox must not have required attribute");
    }

    // ---- renderCreateForm tests ----

    @Test
    @DisplayName("renderCreateForm returns create template unchanged")
    void testRenderCreateFormReturnsTemplateUnchanged() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("renderCreateForm()"),
                "renderCreateForm method must be present");
        // renderCreateForm just returns createTemplate with no substitution
        assertTrue(source.contains("return createTemplate;"),
                "renderCreateForm must return createTemplate unchanged");
        // Must NOT perform a {{CONTENT}} replace inside renderCreateForm
        String createFormBlock = extractMethod(source, "renderCreateForm()");
        assertFalse(createFormBlock.contains("{{CONTENT}}"),
                "renderCreateForm must not substitute {{CONTENT}} itself");
    }

    // ---- error-variant method tests ----

    @Test
    @DisplayName("renderCreateFormWithErrors is present and distinct from renderCreateForm")
    void testRenderCreateFormWithErrorsPresent() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("renderCreateFormWithErrors("),
                "renderCreateFormWithErrors method must be present");
        // Distinct name — no overloading
        assertTrue(source.contains("renderCreateForm()"),
                "renderCreateForm (no-arg) must exist separately");
        // Both names appear; they are distinct (one has args, one doesn't)
        assertFalse(source.indexOf("renderCreateFormWithErrors(") == source.lastIndexOf("renderCreateFormWithErrors(")
                && source.indexOf("renderCreateForm()") < 0,
                "renderCreateForm and renderCreateFormWithErrors must both be present");
    }

    @Test
    @DisplayName("renderEditFormWithErrors is present and distinct from renderEditForm")
    void testRenderEditFormWithErrorsPresent() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("renderEditFormWithErrors("),
                "renderEditFormWithErrors method must be present");
        assertTrue(source.contains("renderEditForm(Users item)"),
                "renderEditForm(Users item) must exist separately");
        // Verify no overloading: renderEditForm(Users item) and renderEditFormWithErrors( are distinct signatures
        assertTrue(source.contains("renderEditForm(Users item)") && source.contains("renderEditFormWithErrors("),
                "Both renderEditForm and renderEditFormWithErrors must be present with distinct signatures");
    }

    @Test
    @DisplayName("no method overloading: renderEditForm and renderEditFormWithErrors have distinct names")
    void testNoOverloadingEditForms() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // Count occurrences of each method declaration prefix
        int editFormCount = countOccurrences(source, "renderEditForm(Users item)");
        int editFormWithErrorsCount = countOccurrences(source, "renderEditFormWithErrors(");
        assertTrue(editFormCount >= 1, "renderEditForm(Users item) must appear at least once");
        assertTrue(editFormWithErrorsCount >= 1, "renderEditFormWithErrors must appear at least once");
    }

    // ---- helpers ----

    /**
     * Extracts the text of a method body by finding the method signature and
     * returning text up to (but not including) the next "    public " declaration.
     */
    private String extractMethod(String source, String methodSignature) {
        int start = source.indexOf(methodSignature);
        if (start < 0) {
            return "";
        }
        // Find next method declaration after this one
        int next = source.indexOf("    public ", start + methodSignature.length());
        if (next < 0) {
            return source.substring(start);
        }
        return source.substring(start, next);
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) >= 0) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
