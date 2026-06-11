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
 * for the list and detail render methods. Does not execute the generated class.
 */
@DisplayName("PageRenderer Emitter - List and Detail Output Tests")
class PageRendererEmitListDetailTest {

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
     * with fields: id (string, PK), username (string), email (string),
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

    @Test
    @DisplayName("emitPageRenderer source contains @Component")
    void testHasComponentAnnotation() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("@Component"),
                "@Component annotation must be present");
    }

    @Test
    @DisplayName("loadTemplate uses ClassName.class.getClassLoader() not getClass()")
    void testUsesStaticClassGetClassLoader() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("UsersPageRenderer.class.getClassLoader()"),
                "Static template load must use UsersPageRenderer.class.getClassLoader()");
        // getClass() in a static method would be a compile error; assert the safe form only
        assertFalse(source.contains("getClass().getClassLoader()"),
                "Must not use getClass().getClassLoader() (invalid in static context)");
    }

    @Test
    @DisplayName("renderList emits <tr> and <td> for each field with HtmlEscapeUtils.escape")
    void testRenderListEmitsTdPerField() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("renderList(List<Users> items)"),
                "renderList method must be present");
        assertTrue(source.contains("rows.append(\"<tr>\")"),
                "renderList must open <tr>");
        assertTrue(source.contains("rows.append(\"<td>\")"),
                "renderList must emit <td>");
        assertTrue(source.contains("HtmlEscapeUtils.escape("),
                "renderList must use HtmlEscapeUtils.escape for cell values");
    }

    @Test
    @DisplayName("renderList wraps String field directly in escape (no String.valueOf)")
    void testRenderListStringFieldNoStringValueOf() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // username is type "string" — escape directly
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getUsername())"),
                "String field username must be escaped directly without String.valueOf");
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getEmail())"),
                "String field email must be escaped directly without String.valueOf");
    }

    @Test
    @DisplayName("renderList wraps non-String fields with String.valueOf before escape")
    void testRenderListNonStringFieldsUseStringValueOf() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // is_verified is bool — must use String.valueOf
        assertTrue(source.contains("HtmlEscapeUtils.escape(String.valueOf(item.getIsVerified()))"),
                "bool field is_verified must use String.valueOf before escape");
        // follower_count is int32 — must use String.valueOf
        assertTrue(source.contains("HtmlEscapeUtils.escape(String.valueOf(item.getFollowerCount()))"),
                "int32 field follower_count must use String.valueOf before escape");
    }

    @Test
    @DisplayName("renderList emits Actions <td> with View link using getId()")
    void testRenderListActionsColumn() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // The actions column appends a View link with the escaped id
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getId())"),
                "Actions column must escape item.getId()");
        assertTrue(source.contains("View</a></td>"),
                "Actions column must contain View link text");
    }

    @Test
    @DisplayName("renderList closes with listTemplate.replace(\"{{CONTENT}}\"")
    void testRenderListContentSlot() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("listTemplate.replace(\"{{CONTENT}}\", rows.toString())"),
                "renderList must fill {{CONTENT}} slot in list template");
    }

    @Test
    @DisplayName("renderDetail builds <dl> with <dt>/<dd> pairs for each field")
    void testRenderDetailBuildsDl() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("renderDetail(Users item)"),
                "renderDetail method must be present");
        assertTrue(source.contains("dl.append(\"  <dt>\")"),
                "renderDetail must emit <dt> elements");
        assertTrue(source.contains("dl.append(\"  <dd>\")"),
                "renderDetail must emit <dd> elements");
    }

    @Test
    @DisplayName("renderDetail closes with detailTemplate.replace(\"{{CONTENT}}\"")
    void testRenderDetailContentSlot() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("detailTemplate.replace(\"{{CONTENT}}\", dl.toString())"),
                "renderDetail must fill {{CONTENT}} slot in detail template");
    }

    @Test
    @DisplayName("renderDetail fills {{EDIT_LINK}} with ?action=edit href")
    void testRenderDetailFillsEditLink() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // Verify the replace() call targets the {{EDIT_LINK}} slot (not just that the literal appears)
        assertTrue(source.contains("result.replace(\"{{EDIT_LINK}}\","),
                "renderDetail must call result.replace(\"{{EDIT_LINK}}\", ...) to fill the slot");
        // Verify the PK value is HTML-escaped (id is a string PK in usersCtx)
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getId())"),
                "renderDetail must escape the PK via HtmlEscapeUtils.escape(item.getId())");
        // Verify the edit href contains the query param
        assertTrue(source.contains("?action=edit"),
                "renderDetail must produce a ?action=edit href");
    }

    @Test
    @DisplayName("emitted source imports HtmlEscapeUtils from util package")
    void testImportsHtmlEscapeUtils() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("import " + BASE_PACKAGE + ".util.HtmlEscapeUtils;"),
                "Source must import HtmlEscapeUtils from server util package");
    }

    @Test
    @DisplayName("class is named UsersPageRenderer")
    void testClassName() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        assertTrue(source.contains("public class UsersPageRenderer"),
                "Class must be named UsersPageRenderer");
    }

    @Test
    @DisplayName("template dir path uses domain and resource path")
    void testTemplateDirPath() {
        String source = emitter.emitPageRenderer(BASE_PACKAGE, usersCtx);
        // templates/auth/users/list.html (domain=auth, resourcePath=users)
        assertTrue(source.contains("templates/auth/users/list.html"),
                "list template path must be templates/auth/users/list.html");
        assertTrue(source.contains("templates/auth/users/detail.html"),
                "detail template path must be templates/auth/users/detail.html");
    }
}
