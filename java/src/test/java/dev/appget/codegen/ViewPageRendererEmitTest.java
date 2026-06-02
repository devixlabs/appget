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
 * Tests the SOURCE STRING produced by SpringBootEmitter.emitViewPageRenderer(...)
 * for a read-only view entity. Asserts list-only output and absence of
 * CRUD-specific elements.
 */
@DisplayName("View PageRenderer Emitter - Output Tests")
class ViewPageRendererEmitTest {

    private SpringBootEmitter emitter;
    private EntityContext userRoleViewCtx;
    private static final String BASE_PACKAGE = "dev.appget.server";

    @BeforeEach
    void setUp() {
        emitter = new SpringBootEmitter();
        userRoleViewCtx = buildUserRoleViewContext();
    }

    /**
     * Builds a minimal valid EntityContext for view "user_role" in domain "auth".
     * Fields: username (string), role_name (string), is_active (bool).
     * isView = true.
     */
    private EntityContext buildUserRoleViewContext() {
        List<Map<String, Object>> fields = new ArrayList<>();

        Map<String, Object> usernameField = new HashMap<>();
        usernameField.put("name", "username");
        usernameField.put("type", "string");
        usernameField.put("nullable", false);
        fields.add(usernameField);

        Map<String, Object> roleNameField = new HashMap<>();
        roleNameField.put("name", "role_name");
        roleNameField.put("type", "string");
        roleNameField.put("nullable", false);
        fields.add(roleNameField);

        Map<String, Object> isActiveField = new HashMap<>();
        isActiveField.put("name", "is_active");
        isActiveField.put("type", "bool");
        isActiveField.put("nullable", true);
        fields.add(isActiveField);

        List<Map<String, Object>> pkFields = new ArrayList<>();

        return new EntityContext(
                "user_role",
                "UserRole",
                "auth",
                "dev.appget.auth",
                fields,
                true,
                false,
                false,
                pkFields,
                "user-role",
                "String id",
                "id",
                "",
                "",
                "id: {}",
                "id",
                "\"user_role not found: \" + id",
                "/{id}",
                "@PathVariable String id"
        );
    }

    @Test
    @DisplayName("emitViewPageRenderer source contains @Component")
    void testHasComponentAnnotation() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertTrue(source.contains("@Component"),
                "@Component annotation must be present");
    }

    @Test
    @DisplayName("emitViewPageRenderer contains renderList method")
    void testHasRenderList() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertTrue(source.contains("renderList(List<UserRole> items)"),
                "renderList(List<UserRole> items) must be present");
    }

    @Test
    @DisplayName("renderList emits <td> per view field with HtmlEscapeUtils.escape")
    void testRenderListEmitsTdPerField() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        // username (string) — direct escape
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getUsername())"),
                "username field must be escaped directly");
        // role_name (string) — direct escape, camelCase getter getRoleName
        assertTrue(source.contains("HtmlEscapeUtils.escape(item.getRoleName())"),
                "role_name field must use getRoleName() getter escaped directly");
        // is_active (bool) — String.valueOf wrap
        assertTrue(source.contains("HtmlEscapeUtils.escape(String.valueOf(item.getIsActive()))"),
                "is_active bool field must use String.valueOf before escape");
        assertTrue(source.contains("rows.append(\"<td>\")"),
                "renderList must emit <td> elements");
    }

    @Test
    @DisplayName("emitViewPageRenderer does NOT contain getId(")
    void testNoGetId() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertFalse(source.contains("getId()"),
                "View renderer must not call getId() (no actions column)");
    }

    @Test
    @DisplayName("emitViewPageRenderer does NOT contain renderDetail")
    void testNoRenderDetail() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertFalse(source.contains("renderDetail"),
                "View renderer must not have renderDetail method");
    }

    @Test
    @DisplayName("emitViewPageRenderer does NOT contain renderEditForm")
    void testNoRenderEditForm() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertFalse(source.contains("renderEditForm"),
                "View renderer must not have renderEditForm method");
    }

    @Test
    @DisplayName("emitViewPageRenderer does NOT contain renderCreateForm")
    void testNoRenderCreateForm() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertFalse(source.contains("renderCreateForm"),
                "View renderer must not have renderCreateForm method");
    }

    @Test
    @DisplayName("emitViewPageRenderer does NOT contain Actions column or View link")
    void testNoActionsColumn() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertFalse(source.contains("View</a></td>"),
                "View renderer must not emit a View link in an Actions column");
        assertFalse(source.contains("Actions"),
                "View renderer must not reference an Actions column");
    }

    @Test
    @DisplayName("class is named UserRolePageRenderer")
    void testClassName() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertTrue(source.contains("public class UserRolePageRenderer"),
                "Class must be named UserRolePageRenderer");
    }

    @Test
    @DisplayName("template dir path uses views/ prefix and resource path")
    void testViewTemplateDirPath() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        // views/user-role/list.html (isView=true, resourcePath=user-role)
        assertTrue(source.contains("templates/views/user-role/list.html"),
                "View template path must be templates/views/user-role/list.html");
    }

    @Test
    @DisplayName("loadTemplate uses ClassName.class.getClassLoader() not getClass()")
    void testUsesStaticClassGetClassLoader() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertTrue(source.contains("UserRolePageRenderer.class.getClassLoader()"),
                "Static template load must use UserRolePageRenderer.class.getClassLoader()");
        assertFalse(source.contains("getClass().getClassLoader()"),
                "Must not use getClass().getClassLoader()");
    }

    @Test
    @DisplayName("renderList closes with listTemplate.replace(\"{{CONTENT}}\")")
    void testRenderListContentSlot() {
        String source = emitter.emitViewPageRenderer(BASE_PACKAGE, userRoleViewCtx);
        assertTrue(source.contains("listTemplate.replace(\"{{CONTENT}}\", rows.toString())"),
                "renderList must fill {{CONTENT}} slot");
    }
}
