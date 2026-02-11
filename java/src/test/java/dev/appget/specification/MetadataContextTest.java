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

        Specification spec = new Specification("roleLevel", ">=", 3);
        assertTrue(spec.isSatisfiedBy(roles), "RoleLevel 5 >= 3 should be true");
    }
}
