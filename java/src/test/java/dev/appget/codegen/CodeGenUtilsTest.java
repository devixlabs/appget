package dev.appget.codegen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CodeGenUtils Tests")
class CodeGenUtilsTest {

    // ---- lowerFirst ----

    @Test
    @DisplayName("lowerFirst: lowercases first character")
    void testLowerFirst() {
        assertEquals("hello", CodeGenUtils.lowerFirst("Hello"));
    }

    @Test
    @DisplayName("lowerFirst: only first character is affected")
    void testLowerFirstOnlyFirst() {
        assertEquals("hELLO", CodeGenUtils.lowerFirst("HELLO"));
    }

    @Test
    @DisplayName("lowerFirst: single character")
    void testLowerFirstSingleChar() {
        assertEquals("h", CodeGenUtils.lowerFirst("H"));
    }

    @Test
    @DisplayName("lowerFirst: already lowercase is unchanged")
    void testLowerFirstAlreadyLower() {
        assertEquals("hello", CodeGenUtils.lowerFirst("hello"));
    }

    @Test
    @DisplayName("lowerFirst: null returns null")
    void testLowerFirstNull() {
        assertNull(CodeGenUtils.lowerFirst(null));
    }

    @Test
    @DisplayName("lowerFirst: empty string returns empty string")
    void testLowerFirstEmpty() {
        assertEquals("", CodeGenUtils.lowerFirst(""));
    }

    // ---- capitalize ----

    @Test
    @DisplayName("capitalize: uppercases first character")
    void testCapitalize() {
        assertEquals("Hello", CodeGenUtils.capitalize("hello"));
    }

    @Test
    @DisplayName("capitalize: only first character is affected")
    void testCapitalizeOnlyFirst() {
        assertEquals("Hello world", CodeGenUtils.capitalize("hello world"));
    }

    @Test
    @DisplayName("capitalize: single character")
    void testCapitalizeSingleChar() {
        assertEquals("H", CodeGenUtils.capitalize("h"));
    }

    @Test
    @DisplayName("capitalize: already capitalized is unchanged")
    void testCapitalizeAlreadyUpper() {
        assertEquals("Hello", CodeGenUtils.capitalize("Hello"));
    }

    @Test
    @DisplayName("capitalize: null returns null")
    void testCapitalizeNull() {
        assertNull(CodeGenUtils.capitalize(null));
    }

    @Test
    @DisplayName("capitalize: empty string returns empty string")
    void testCapitalizeEmpty() {
        assertEquals("", CodeGenUtils.capitalize(""));
    }

    // ---- findMatchingParen ----

    @Test
    @DisplayName("findMatchingParen: finds closing paren for simple expression")
    void testFindMatchingParenSimple() {
        assertEquals(4, CodeGenUtils.findMatchingParen("(foo)", 0));
    }

    @Test
    @DisplayName("findMatchingParen: finds outer closing paren for nested expression")
    void testFindMatchingParenNested() {
        // "DECIMAL(15,2)" — outer parens at index 7 and 12
        String sql = "DECIMAL(15,2)";
        assertEquals(12, CodeGenUtils.findMatchingParen(sql, 7));
    }

    @Test
    @DisplayName("findMatchingParen: handles deeply nested parens")
    void testFindMatchingParenDeepNested() {
        // "(a(b(c)))" — outer parens at 0 and 8
        assertEquals(8, CodeGenUtils.findMatchingParen("(a(b(c)))", 0));
    }

    @Test
    @DisplayName("findMatchingParen: returns -1 when no closing paren found")
    void testFindMatchingParenNoClose() {
        assertEquals(-1, CodeGenUtils.findMatchingParen("(unclosed", 0));
    }

    // ---- smartSplit ----

    @Test
    @DisplayName("smartSplit: splits simple comma-separated string")
    void testSmartSplitSimple() {
        List<String> parts = CodeGenUtils.smartSplit("a,b,c", ',');
        assertEquals(List.of("a", "b", "c"), parts);
    }

    @Test
    @DisplayName("smartSplit: does not split inside parentheses")
    void testSmartSplitSkipsParens() {
        List<String> parts = CodeGenUtils.smartSplit("a,(b,c),d", ',');
        assertEquals(List.of("a", "(b,c)", "d"), parts);
    }

    @Test
    @DisplayName("smartSplit: single token with no delimiter")
    void testSmartSplitSingleToken() {
        List<String> parts = CodeGenUtils.smartSplit("foo", ',');
        assertEquals(List.of("foo"), parts);
    }

    @Test
    @DisplayName("smartSplit: empty string returns empty list")
    void testSmartSplitEmpty() {
        List<String> parts = CodeGenUtils.smartSplit("", ',');
        assertTrue(parts.isEmpty());
    }

    @Test
    @DisplayName("smartSplit: trims whitespace around tokens")
    void testSmartSplitTrims() {
        List<String> parts = CodeGenUtils.smartSplit("a , b , c", ',');
        assertEquals(List.of("a", "b", "c"), parts);
    }

    // ---- escapeString ----

    @Test
    @DisplayName("escapeString: escapes backslash")
    void testEscapeStringBackslash() {
        assertEquals("a\\\\b", CodeGenUtils.escapeString("a\\b"));
    }

    @Test
    @DisplayName("escapeString: escapes double quote")
    void testEscapeStringDoubleQuote() {
        assertEquals("say \\\"hello\\\"", CodeGenUtils.escapeString("say \"hello\""));
    }

    @Test
    @DisplayName("escapeString: escapes newline")
    void testEscapeStringNewline() {
        assertEquals("a\\nb", CodeGenUtils.escapeString("a\nb"));
    }

    @Test
    @DisplayName("escapeString: escapes carriage return")
    void testEscapeStringCarriageReturn() {
        assertEquals("a\\rb", CodeGenUtils.escapeString("a\rb"));
    }

    @Test
    @DisplayName("escapeString: escapes tab")
    void testEscapeStringTab() {
        assertEquals("a\\tb", CodeGenUtils.escapeString("a\tb"));
    }

    @Test
    @DisplayName("escapeString: plain string is unchanged")
    void testEscapeStringPlain() {
        assertEquals("hello world", CodeGenUtils.escapeString("hello world"));
    }

    @Test
    @DisplayName("escapeString: null returns null")
    void testEscapeStringNull() {
        assertNull(CodeGenUtils.escapeString(null));
    }
}
