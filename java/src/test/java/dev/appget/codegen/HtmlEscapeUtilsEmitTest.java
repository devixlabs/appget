package dev.appget.codegen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HtmlEscapeUtils Emitter Tests")
class HtmlEscapeUtilsEmitTest {

    // -------------------------------------------------------------------------
    // Reference implementation (byte-identical logic to the contract)
    // -------------------------------------------------------------------------

    private static String escapeReference(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == '\'') {
                sb.append("&#x27;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Fuzz: behavioral invariants on the reference implementation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Fuzz: output never contains raw < or >")
    void fuzzNoRawAngleBrackets() {
        Random rng = new Random(42L);
        char[] alphabet = "abcdefghijklmnopqrstuvwxyz0123456789&<>\"'é→世界".toCharArray();
        for (int trial = 0; trial < 5000; trial++) {
            int len = rng.nextInt(200);
            StringBuilder input = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                input.append(alphabet[rng.nextInt(alphabet.length)]);
            }
            String result = escapeReference(input.toString());
            assertFalse(result.contains("<"), "Raw < found in trial " + trial + " input: " + input);
            assertFalse(result.contains(">"), "Raw > found in trial " + trial + " input: " + input);
        }
    }

    @Test
    @DisplayName("Fuzz: every & in output begins a valid entity")
    void fuzzNoRawAmpersand() {
        Random rng = new Random(42L);
        char[] alphabet = "abcdefghijklmnopqrstuvwxyz0123456789&<>\"'é→世界".toCharArray();
        for (int trial = 0; trial < 5000; trial++) {
            int len = rng.nextInt(200);
            StringBuilder input = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                input.append(alphabet[rng.nextInt(alphabet.length)]);
            }
            String result = escapeReference(input.toString());
            int idx = 0;
            while ((idx = result.indexOf('&', idx)) != -1) {
                boolean validEntity =
                    result.startsWith("&amp;", idx) ||
                    result.startsWith("&lt;", idx) ||
                    result.startsWith("&gt;", idx) ||
                    result.startsWith("&quot;", idx) ||
                    result.startsWith("&#x27;", idx);
                assertTrue(validEntity,
                    "Bare & at index " + idx + " in output of trial " + trial + ": " + result);
                idx++;
            }
        }
    }

    @Test
    @DisplayName("Fuzz: escapeReference(null) returns empty string")
    void fuzzNullReturnsEmpty() {
        assertEquals("", escapeReference(null));
    }

    // -------------------------------------------------------------------------
    // Explicit edge cases on the reference implementation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Edge: empty string maps to empty string")
    void edgeCaseEmptyString() {
        assertEquals("", escapeReference(""));
    }

    @Test
    @DisplayName("Edge: string of only special chars escapes all five")
    void edgeCaseAllSpecialChars() {
        assertEquals("&amp;&lt;&gt;&quot;&#x27;", escapeReference("&<>\"'"));
    }

    @Test
    @DisplayName("Edge: Unicode passthrough (non-special chars unchanged)")
    void edgeCaseUnicodePassthrough() {
        String input = "héllo→世界";
        assertEquals(input, escapeReference(input));
    }

    @Test
    @DisplayName("Edge: long string (>= 10k chars) handled by StringBuilder")
    void edgeCaseLongString() {
        StringBuilder sb = new StringBuilder(10001);
        for (int i = 0; i < 10001; i++) {
            sb.append('a');
        }
        String result = escapeReference(sb.toString());
        assertEquals(10001, result.length());
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertFalse(result.contains("&"));
    }

    // -------------------------------------------------------------------------
    // Drift guard: emitted source matches reference
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Drift: emitted source contains all five entity literals")
    void driftEntityLiterals() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        assertTrue(src.contains("&amp;"), "Missing &amp; in emitted source");
        assertTrue(src.contains("&lt;"), "Missing &lt; in emitted source");
        assertTrue(src.contains("&gt;"), "Missing &gt; in emitted source");
        assertTrue(src.contains("&quot;"), "Missing &quot; in emitted source");
        assertTrue(src.contains("&#x27;"), "Missing &#x27; in emitted source");
    }

    @Test
    @DisplayName("Drift: emitted source contains null guard")
    void driftNullGuard() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        assertTrue(src.contains("return \"\";"), "Missing null guard 'return \"\";' in emitted source");
    }

    @Test
    @DisplayName("Drift: emitted source has private constructor")
    void driftPrivateConstructor() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        assertTrue(src.contains("private HtmlEscapeUtils()"), "Missing private constructor in emitted source");
    }

    @Test
    @DisplayName("Drift: emitted source is final class")
    void driftFinalClass() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        assertTrue(src.contains("final class"), "Emitted source is not a final class");
    }

    @Test
    @DisplayName("Drift: emitted source contains escape( method signature")
    void driftEscapeMethod() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        assertTrue(src.contains("escape("), "Missing escape( method signature in emitted source");
    }

    // -------------------------------------------------------------------------
    // Structural assertions
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Structural: emitted source declares package dev.appget.server.util")
    void structuralPackage() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        assertTrue(src.contains("package dev.appget.server.util;"),
            "Emitted source does not declare package dev.appget.server.util");
    }

    @Test
    @DisplayName("Structural: emitted source contains DO-NOT-EDIT header")
    void structuralDoNotEditHeader() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        assertTrue(src.contains("DO NOT EDIT MANUALLY"),
            "Emitted source is missing DO NOT EDIT MANUALLY header");
    }

    @Test
    @DisplayName("Structural: emitted source uses no switch expression (no 'case ->')")
    void structuralNoSwitchExpression() {
        String src = new SpringBootEmitter().emitHtmlEscapeUtils("dev.appget.server");
        // Check for switch arrow syntax: "case " immediately followed by "->"
        int caseIdx = 0;
        while ((caseIdx = src.indexOf("case ", caseIdx)) != -1) {
            int arrowIdx = src.indexOf("->", caseIdx);
            int nextNewline = src.indexOf('\n', caseIdx);
            // If -> appears before the next newline, it's a switch expression arrow
            if (arrowIdx != -1 && (nextNewline == -1 || arrowIdx < nextNewline)) {
                fail("Switch expression found in emitted source at position " + caseIdx);
            }
            caseIdx++;
        }
    }
}
