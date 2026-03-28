package dev.appget.specification;

import dev.appget.specification.context.SsoContext;
import dev.appget.specification.context.RolesContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Metadata Context Tests")
class MetadataContextTest {

    @Test
    @DisplayName("MetadataContext stores and retrieves categories")
    void testStoreAndRetrieve() {
        SsoContext sso = SsoContext.builder()
                .authenticated(true)
                .sessionId("abc123")
                .build();

        MetadataContext ctx = new MetadataContext().with("sso", sso);
        assertNotNull(ctx.get("sso"));
        assertEquals(sso, ctx.get("sso"));
    }

    @Test
    @DisplayName("MetadataContext returns null for unknown category")
    void testUnknownCategory() {
        MetadataContext ctx = new MetadataContext();
        assertNull(ctx.get("nonexistent"));
    }

    @Test
    @DisplayName("MetadataContext has() checks category existence")
    void testHasCategory() {
        MetadataContext ctx = new MetadataContext().with("sso", "dummy");
        assertTrue(ctx.has("sso"));
        assertFalse(ctx.has("other"));
    }

    @Test
    @DisplayName("Specification works with metadata POJO via reflection")
    void testSpecificationWithMetadataPojo() {
        SsoContext sso = SsoContext.builder()
                .authenticated(true)
                .sessionId("session-xyz")
                .build();

        // SsoContext has isAuthenticated() (Lombok generates is* for boolean)
        Specification spec = new Specification("authenticated", "==", true);
        assertTrue(spec.isSatisfiedBy(sso), "SsoContext.isAuthenticated() should be true");
    }

    @Test
    @DisplayName("Specification works with RolesContext via reflection")
    void testSpecificationWithRolesContext() {
        RolesContext roles = RolesContext.builder()
                .roleName("Admin")
                .roleLevel(5)
                .build();

        Specification spec = new Specification("role_level", ">=", 3);
        assertTrue(spec.isSatisfiedBy(roles), "RoleLevel 5 >= 3 should be true");
    }

    // ---- Overwriting a category ----

    @Test
    @DisplayName("Overwriting a category replaces the previous value")
    void testOverwriteCategory() {
        SsoContext sso1 = SsoContext.builder().authenticated(true).sessionId("old").build();
        SsoContext sso2 = SsoContext.builder().authenticated(false).sessionId("new").build();

        MetadataContext ctx = new MetadataContext().with("sso", sso1).with("sso", sso2);
        assertEquals(sso2, ctx.get("sso"), "Second with() should overwrite first");
    }

    // ---- Multiple categories ----

    @Test
    @DisplayName("Multiple categories coexist in context")
    void testMultipleCategories() {
        SsoContext sso = SsoContext.builder().authenticated(true).sessionId("s1").build();
        RolesContext roles = RolesContext.builder().roleName("Admin").roleLevel(5).build();

        MetadataContext ctx = new MetadataContext().with("sso", sso).with("roles", roles);

        assertTrue(ctx.has("sso"));
        assertTrue(ctx.has("roles"));
        assertEquals(sso, ctx.get("sso"));
        assertEquals(roles, ctx.get("roles"));
    }

    // ---- String field via reflection ----

    @Test
    @DisplayName("Specification works with string field via reflection (roleName)")
    void testSpecificationWithStringFieldReflection() {
        RolesContext roles = RolesContext.builder()
                .roleName("Admin")
                .roleLevel(5)
                .build();

        Specification spec = new Specification("role_name", "==", "Admin");
        assertTrue(spec.isSatisfiedBy(roles), "roleName 'Admin' == 'Admin' should be true");
    }

    // ---- Invalid field via reflection ----

    @Test
    @DisplayName("Specification with non-existent field on POJO returns false")
    void testSpecificationInvalidFieldReflection() {
        SsoContext sso = SsoContext.builder().authenticated(true).sessionId("s1").build();

        Specification spec = new Specification("nonexistent", "==", true);
        assertFalse(spec.isSatisfiedBy(sso), "Non-existent field should return false, not throw");
    }

    // ---- isAdmin-style boolean field (Lombok "is" prefix handling) ----

    @Test
    @DisplayName("Specification handles isAdmin-style boolean field via reflection")
    void testIsAdminStyleBooleanField() {
        RolesContext roles = RolesContext.builder()
                .roleName("Admin")
                .roleLevel(5)
                .isAdmin(true)
                .build();

        Specification spec = new Specification("is_admin", "==", true);
        assertTrue(spec.isSatisfiedBy(roles), "isAdmin true == true should be true via reflection");
    }
}
