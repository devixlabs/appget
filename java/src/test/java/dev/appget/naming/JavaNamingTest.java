package dev.appget.naming;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaNaming Tests")
class JavaNamingTest {

    @Test
    @DisplayName("toFieldAccessor: converts snake_case to camelCase")
    void testSnakeToCamel() {
        assertEquals("snakeCase", JavaNaming.toFieldAccessor("snake_case"));
    }

    @Test
    @DisplayName("toFieldAccessor: multi-word snake_case")
    void testMultiWord() {
        assertEquals("snakeCaseString", JavaNaming.toFieldAccessor("snake_case_string"));
    }

    @Test
    @DisplayName("toFieldAccessor: no underscores is unchanged")
    void testNoUnderscore() {
        assertEquals("simple", JavaNaming.toFieldAccessor("simple"));
    }

    @Test
    @DisplayName("toFieldAccessor: null returns null")
    void testNull() {
        assertNull(JavaNaming.toFieldAccessor(null));
    }

    @Test
    @DisplayName("toFieldAccessor: empty string returns empty string")
    void testEmpty() {
        assertEquals("", JavaNaming.toFieldAccessor(""));
    }

    @Test
    @DisplayName("toFieldAccessor: triple segment")
    void testTripleSegment() {
        assertEquals("aBC", JavaNaming.toFieldAccessor("a_b_c"));
    }

    @Test
    @DisplayName("toFieldAccessor: domain field names")
    void testDomainFields() {
        assertEquals("roleLevel", JavaNaming.toFieldAccessor("role_level"));
        assertEquals("isAdmin", JavaNaming.toFieldAccessor("is_admin"));
        assertEquals("authenticated", JavaNaming.toFieldAccessor("authenticated"));
        assertEquals("roleId", JavaNaming.toFieldAccessor("role_id"));
        assertEquals("invoiceNumber", JavaNaming.toFieldAccessor("invoice_number"));
        assertEquals("yearsOfService", JavaNaming.toFieldAccessor("years_of_service"));
    }
}
