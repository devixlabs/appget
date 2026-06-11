package dev.appget.codegen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HtmlStructuralNormalizer#normalizeRuntime(String)}.
 *
 * <p>Verifies the two runtime-mode reductions:
 * <ol>
 *   <li>The {@code value} attribute is stripped from every {@code <input>} whose
 *       {@code name} is NOT {@code _method}. The {@code _method} input keeps
 *       {@code value=PUT} because it is the routing signal for edit forms.</li>
 *   <li>{@code <tbody>} children are collapsed to empty — data rows present in
 *       a live list page are dropped so the structure matches the golden's empty
 *       {@code <tbody>}.</li>
 * </ol>
 */
@DisplayName("HtmlStructuralNormalizer.normalizeRuntime() unit tests")
class HtmlStructuralNormalizerRuntimeTest {

    // ---- value stripping on inputs ----

    /**
     * An edit-form snippet with three inputs:
     *   1. hidden _method=PUT  — value must be kept (routing signal)
     *   2. hidden id=123        — value must be stripped (live data)
     *   3. text email           — value must be stripped (live data)
     */
    @Test
    @DisplayName("_method input keeps value=PUT; id and email inputs lose their value")
    void testEditFormInputValueStripping() {
        String html = "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<body>\n"
                + "<form action=\"/users\" method=\"POST\">\n"
                + "  <input type=\"hidden\" name=\"_method\" value=\"PUT\">\n"
                + "  <input type=\"hidden\" name=\"id\" value=\"123\">\n"
                + "  <input type=\"text\" name=\"email\" value=\"a@b.com\" required>\n"
                + "</form>\n"
                + "</body>\n"
                + "</html>\n";

        String result = HtmlStructuralNormalizer.normalizeRuntime(html);

        // _method keeps value=PUT
        assertTrue(result.contains("input [name=_method type=hidden value=PUT]"),
                "_method input must retain value=PUT.\nActual output:\n" + result);

        // id input: value stripped — only name and type remain (sorted alphabetically)
        assertTrue(result.contains("input [name=id type=hidden]"),
                "id input must have value stripped.\nActual output:\n" + result);

        // email input: value stripped — name, required, type remain
        assertTrue(result.contains("input [name=email required type=text]"),
                "email input must have value stripped.\nActual output:\n" + result);

        // Sanity: the live values must NOT appear anywhere in the output
        assertFalse(result.contains("123"),
                "Live id value '123' must not appear in runtime output.\nActual output:\n" + result);
        assertFalse(result.contains("a@b.com"),
                "Live email value must not appear in runtime output.\nActual output:\n" + result);
    }

    @Test
    @DisplayName("input with no value attribute is left unchanged")
    void testInputWithNoValueUnchanged() {
        String html = "<html><body><input type=\"text\" name=\"username\" required></body></html>";
        String result = HtmlStructuralNormalizer.normalizeRuntime(html);
        assertTrue(result.contains("input [name=username required type=text]"),
                "input without value must be left unchanged.\nActual output:\n" + result);
    }

    // ---- tbody child collapsing ----

    /**
     * A table where tbody has one data row. After normalizeRuntime the tbody must
     * be empty (no tr inside it), matching the golden's empty-tbody shape.
     */
    @Test
    @DisplayName("tbody with data rows is collapsed to empty tbody")
    void testTbodyCollapsedToEmpty() {
        String html = "<html><body>"
                + "<table>"
                + "<tbody>"
                + "<tr><td>x</td></tr>"
                + "</tbody>"
                + "</table>"
                + "</body></html>";

        String result = HtmlStructuralNormalizer.normalizeRuntime(html);

        // tbody must appear
        assertTrue(result.contains("tbody"), "tbody must appear in output.\nActual:\n" + result);

        // tr must NOT appear — it was inside tbody and was suppressed
        assertFalse(result.contains("tr"), "tr inside tbody must be suppressed.\nActual:\n" + result);
        assertFalse(result.contains("td"), "td inside tbody must be suppressed.\nActual:\n" + result);
        assertFalse(result.contains("#TEXT"), "text inside tbody must be suppressed.\nActual:\n" + result);
    }

    @Test
    @DisplayName("multiple data rows in tbody are all suppressed")
    void testMultipleRowsInTbodyAllSuppressed() {
        String html = "<html><body><table><tbody>"
                + "<tr><td>Alice</td><td>alice@x.com</td></tr>"
                + "<tr><td>Bob</td><td>bob@x.com</td></tr>"
                + "</tbody></table></body></html>";

        String result = HtmlStructuralNormalizer.normalizeRuntime(html);

        assertFalse(result.contains("tr"), "All tr rows must be suppressed.\nActual:\n" + result);
        assertFalse(result.contains("Alice"), "Live data must not appear.\nActual:\n" + result);
        assertFalse(result.contains("Bob"), "Live data must not appear.\nActual:\n" + result);
    }

    @Test
    @DisplayName("thead rows outside tbody are NOT suppressed")
    void testTheadRowsNotSuppressed() {
        String html = "<html><body><table>"
                + "<thead><tr><th>Name</th></tr></thead>"
                + "<tbody><tr><td>Alice</td></tr></tbody>"
                + "</table></body></html>";

        String result = HtmlStructuralNormalizer.normalizeRuntime(html);

        // thead tr must appear
        assertTrue(result.contains("th"), "th inside thead must appear.\nActual:\n" + result);
        // tbody tr must NOT appear
        assertFalse(result.contains("td"), "td inside tbody must be suppressed.\nActual:\n" + result);
    }

    // ---- edit link href normalization ----

    @Test
    @DisplayName("normalizeRuntime replaces live id in edit href with {id}")
    void testNormalizeRuntimeStripsEditLinkId() {
        String html = "<!DOCTYPE html>\n"
                + "<html>\n<body>\n"
                + "<a href=\"/users/u-live-123?action=edit\">Edit</a>\n"
                + "</body>\n</html>\n";

        String result = HtmlStructuralNormalizer.normalizeRuntime(html);

        assertTrue(result.contains("a [href=/users/{id}?action=edit]"),
                "edit link href must have live id replaced with {id}.\nActual:\n" + result);
        assertFalse(result.contains("u-live-123"),
                "Live id must not appear in normalized output.\nActual:\n" + result);
    }

    // ---- normalize() is unchanged ----

    @Test
    @DisplayName("normalize() still keeps value on all inputs (unchanged behaviour)")
    void testNormalizeKeepsAllValues() {
        String html = "<html><body>"
                + "<input type=\"text\" name=\"email\" value=\"a@b.com\">"
                + "</body></html>";

        String result = HtmlStructuralNormalizer.normalize(html);

        assertTrue(result.contains("value=a@b.com"),
                "normalize() must still preserve value on data inputs.\nActual:\n" + result);
    }

    @Test
    @DisplayName("normalize() does NOT suppress tbody children")
    void testNormalizeKeepsTbodyChildren() {
        String html = "<html><body><table><tbody><tr><td>x</td></tr></tbody></table></body></html>";
        String result = HtmlStructuralNormalizer.normalize(html);
        assertTrue(result.contains("tr"), "normalize() must preserve tr in tbody.\nActual:\n" + result);
    }

    // ---- runtime strips live data the goldens don't carry: checked, dd/textarea text ----

    @Test
    @DisplayName("checked attribute on a prefilled checkbox is stripped")
    void testCheckedStripped() {
        String html = "<html><body>"
                + "<input type=\"checkbox\" name=\"is_active\" id=\"is_active\" checked>"
                + "</body></html>";
        String result = HtmlStructuralNormalizer.normalizeRuntime(html);
        assertFalse(result.contains("checked"),
                "checked (live checkbox state) must be stripped.\nActual:\n" + result);
        assertTrue(result.contains("input [id name=is_active type=checkbox]"),
                "checkbox structure must remain after stripping checked.\nActual:\n" + result);
    }

    @Test
    @DisplayName("<dd> field value text is emptied (detail page)")
    void testDdTextEmptied() {
        String html = "<html><body><dl>"
                + "<dt>Email</dt><dd>alice@x.com</dd>"
                + "</dl></body></html>";
        String result = HtmlStructuralNormalizer.normalizeRuntime(html);
        assertTrue(result.contains("dt"), "dt (static label) must remain.\nActual:\n" + result);
        assertFalse(result.contains("alice@x.com"),
                "Live dd value must not appear.\nActual:\n" + result);
        // dt keeps its #TEXT (static label); dd must have NO #TEXT child
        assertEquals(1, result.split("#TEXT", -1).length - 1,
                "Only the dt label keeps #TEXT; dd value text must be suppressed.\nActual:\n" + result);
    }

    @Test
    @DisplayName("<textarea> content is emptied (edit form)")
    void testTextareaContentEmptied() {
        String html = "<html><body><form>"
                + "<textarea name=\"bio\" id=\"bio\">some live bio</textarea>"
                + "</form></body></html>";
        String result = HtmlStructuralNormalizer.normalizeRuntime(html);
        assertTrue(result.contains("textarea [id name=bio]"),
                "textarea structure must remain.\nActual:\n" + result);
        assertFalse(result.contains("#TEXT"),
                "textarea live content must be suppressed.\nActual:\n" + result);
    }
}
