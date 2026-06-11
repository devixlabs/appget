package dev.appget.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Reduces an HTML document to a canonical, structure-only form: tags, attributes
 * (sorted, values stripped except for the structural allow-list), and nesting.
 *
 * <p>Designed for snapshot-based structural tests of static CRUD pages produced
 * by {@code HtmlCrudGenerator}. Runtime HTML from PageRenderers will carry live
 * data in text nodes and attribute values — stripping those lets the diff focus
 * on structural contract violations.
 *
 * <h3>Allow-list rationale (attribute values kept verbatim):</h3>
 * <ul>
 *   <li>{@code type}   — distinguishes input types (text, number, checkbox, hidden, …)</li>
 *   <li>{@code step}   — discriminates numeric precision: {@code step="1"} (INT),
 *                        {@code step="0.01"} (DECIMAL), {@code step="any"} (FLOAT).
 *                        Without it a renderer emitting the wrong numeric type would pass.</li>
 *   <li>{@code method} — form HTTP method (POST)</li>
 *   <li>{@code action} — form REST route; structural routing contract</li>
 *   <li>{@code name}   — field binding identity</li>
 *   <li>{@code href}   — link destination</li>
 *   <li>{@code required} — presence/absence is validation contract; value is always "required"</li>
 *   <li>{@code value}  — the hidden {@code _method=PUT} input is what makes an edit form a PUT;
 *                        stripping it would hide routing-structural errors.
 *                        NOTE for 0f-core: when diffing runtime HTML, live data WILL appear in
 *                        {@code value=} for data inputs. 0f-core should strip {@code value} for
 *                        non-hidden inputs (or for inputs whose name != "_method") to avoid
 *                        treating live data as structural mismatches.</li>
 * </ul>
 *
 * <p>Text nodes between tags are replaced by {@code #TEXT} sentinel (non-empty runs only)
 * so "label present vs absent" is still detectable while the actual characters are ignored.
 *
 * <p>HTML comments are stripped entirely ({@code <!-- … -->} placeholders in static pages
 * are filled by the runtime; they must not cause structural mismatches).
 *
 * <p>Void/self-closing elements ({@code input}, {@code meta}, {@code br}, {@code link},
 * {@code img}, {@code hr}) are handled without pushing to the depth stack.
 *
 * <p>Output is one element per line, indented by 2 spaces per nesting level, making
 * snapshot diffs human-readable.
 *
 * <!-- TODO(0f-core): when PageRenderers exist, call normalize() on runtime HTML output
 *      and diff it against the golden snapshots committed under
 *      src/test/resources/html-structure-golden/. The #TEXT sentinel absorbs live data.
 *      See value= nuance in the class Javadoc above for the runtime-vs-static value handling. -->
 */
public final class HtmlStructuralNormalizer {

    /**
     * Attribute names whose values are preserved verbatim (structural contract).
     * All other attribute values are stripped (only the name is kept).
     */
    private static final Set<String> VALUE_KEEP_ATTRS = new HashSet<>(Arrays.asList(
            "type", "step", "method", "action", "name", "href", "required", "value"
    ));

    /**
     * HTML void elements — they cannot have children and do not push to the depth stack.
     */
    private static final Set<String> VOID_ELEMENTS = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
    ));

    private HtmlStructuralNormalizer() {
        // Static utility — no instances
    }

    /**
     * Normalizes {@code html} to a canonical structure-only representation.
     *
     * @param html raw HTML string (generator-controlled, well-formed)
     * @return indented, one-element-per-line structural string
     * @throws IllegalArgumentException if the input appears structurally malformed
     */
    public static String normalize(String html) {
        if (html == null || html.isEmpty()) {
            throw new IllegalArgumentException("normalize: input HTML must not be null or empty");
        }
        return normalizeInternal(html, false);
    }

    /**
     * Normalizes runtime HTML (from PageRenderers) to the same canonical structure
     * used by the static-page goldens, so the two can be diffed for structural
     * contract violations.
     *
     * <p>Does everything {@link #normalize(String)} does, plus:
     * <ul>
     *   <li><b>Strips {@code value} from data inputs</b> — any {@code <input>} whose
     *       {@code name} attribute is NOT {@code _method} has its {@code value}
     *       attribute dropped, so live field data injected by the renderer does not
     *       cause structural mismatches against the goldens.</li>
     *   <li><b>Collapses {@code <tbody>} children</b> — any {@code <tr>} rows inside
     *       a {@code <tbody>} are removed, reducing a runtime list page (which has
     *       real data rows) to the golden's empty {@code <tbody>}.</li>
     *   <li><b>Strips {@code checked}</b> from inputs — prefilled checkbox state is live
     *       data; the static goldens carry no {@code checked} attribute.</li>
     *   <li><b>Empties {@code <dd>} and {@code <textarea>} text</b> — detail-page field
     *       values and textarea contents are live data; the static goldens have these
     *       elements empty.</li>
     * </ul>
     *
     * @param html raw runtime HTML string produced by a PageRenderer
     * @return indented, one-element-per-line structural string matching the golden shape
     * @throws IllegalArgumentException if the input appears structurally malformed
     */
    public static String normalizeRuntime(String html) {
        if (html == null || html.isEmpty()) {
            throw new IllegalArgumentException("normalizeRuntime: input HTML must not be null or empty");
        }
        return normalizeInternal(html, true);
    }

    /**
     * Core normalization walk shared by {@link #normalize} and {@link #normalizeRuntime}.
     *
     * @param html        raw HTML string
     * @param runtimeMode when true, applies runtime-specific reductions:
     *                    strips {@code value} from non-{@code _method} inputs and
     *                    collapses {@code <tbody>} children.
     */
    private static String normalizeInternal(String html, boolean runtimeMode) {
        // 1. Strip HTML comments (<!-- ... -->) — may span lines
        String stripped = stripComments(html);

        // 1b. Runtime: normalize edit link hrefs — replace live id segment with {id}
        //     Pattern: href="/{resource}/{liveId}?action=edit" → href="/{resource}/{id}?action=edit"
        if (runtimeMode) {
            stripped = stripped.replaceAll(
                    "href=\"(/[^/?\"]+)/[^/?\"]+\\?action=edit\"",
                    "href=\"$1/{id}?action=edit\"");
        }

        // 2. Tokenize into tags and text runs
        List<String> tokens = tokenize(stripped);

        // 3. Walk tokens, maintain depth stack, emit canonical lines
        StringBuilder out = new StringBuilder();
        List<String> stack = new ArrayList<>();
        // runtimeMode: track tbody depth to suppress <tr> content inside tbody
        int tbodyDepth = -1; // -1 = not inside a tbody

        for (String token : tokens) {
            if (token.startsWith("<")) {
                if (token.startsWith("<!DOCTYPE") || token.startsWith("<!doctype")) {
                    // DOCTYPE is not an element — emit at depth 0
                    appendLine(out, 0, "!doctype");
                } else if (token.startsWith("</")) {
                    // Closing tag
                    String tagName = extractClosingTagName(token);
                    if (!VOID_ELEMENTS.contains(tagName)) {
                        // Pop stack — tolerate minor imbalance but don't go negative
                        if (!stack.isEmpty()) {
                            stack.remove(stack.size() - 1);
                        }
                    }
                    // In runtime mode: when closing tbody, clear the suppression depth
                    if (runtimeMode && "tbody".equals(tagName)) {
                        tbodyDepth = -1;
                    }
                    // Closing tags are NOT emitted — nesting is represented by indentation alone
                } else {
                    // Opening (or self-closing) tag
                    String tagName = extractOpenTagName(token);
                    boolean isSelfClosing = token.endsWith("/>") || VOID_ELEMENTS.contains(tagName);
                    int depth = stack.size();

                    // runtimeMode: suppress everything nested inside a tbody
                    if (runtimeMode && tbodyDepth >= 0 && depth > tbodyDepth) {
                        // Inside tbody — skip this tag (do not emit, do not push stack)
                        if (!isSelfClosing) {
                            stack.add(tagName);
                        }
                    } else {
                        String effectiveToken = token;
                        // runtimeMode: strip value from non-_method inputs
                        if (runtimeMode && "input".equals(tagName)) {
                            effectiveToken = stripValueIfNotMethod(token);
                            // Checkbox/radio state (`checked`) is live data — the static
                            // goldens have no `checked`; strip it so prefilled edit forms match.
                            effectiveToken = removeAttr(effectiveToken, "checked");
                        }
                        String attrStr = buildAttrString(effectiveToken, tagName);
                        appendLine(out, depth, tagName + attrStr);
                        if (!isSelfClosing) {
                            stack.add(tagName);
                            // runtimeMode: mark tbody entry depth for child suppression
                            if (runtimeMode && "tbody".equals(tagName)) {
                                tbodyDepth = depth;
                            }
                        }
                    }
                }
            } else {
                // Text token — replace non-empty (after trim) with #TEXT.
                // runtimeMode: suppress live data text inside tbody rows and inside
                // <dd>/<textarea> — these hold field data in runtime HTML but are empty
                // in the static-page goldens (detail values, textarea contents).
                boolean insideTbody = runtimeMode && tbodyDepth >= 0 && stack.size() > tbodyDepth;
                String parent = stack.isEmpty() ? "" : stack.get(stack.size() - 1);
                boolean insideDataText = runtimeMode
                        && ("dd".equals(parent) || "textarea".equals(parent));
                if (insideTbody || insideDataText) {
                    // inside a data-bearing container — skip live text
                } else {
                    String trimmed = token.trim();
                    if (!trimmed.isEmpty()) {
                        appendLine(out, stack.size(), "#TEXT");
                    }
                }
            }
        }

        return out.toString();
    }

    /**
     * Returns a copy of an {@code <input ...>} tag token with the {@code value}
     * attribute removed, unless the input's {@code name} attribute equals
     * {@code _method} (in which case the token is returned unchanged).
     *
     * <p>Used only in runtime normalization to strip live field data from edit forms.
     */
    private static String stripValueIfNotMethod(String token) {
        String nameVal = extractAttrValue(token, "name");
        if ("_method".equals(nameVal)) {
            return token;
        }
        return removeAttr(token, "value");
    }

    /**
     * Extracts the value of the named attribute from a tag token, or {@code null}
     * if the attribute is absent or has no value.
     */
    private static String extractAttrValue(String token, String targetAttr) {
        String tagName = extractOpenTagName(token);
        int nameEnd = 1 + tagName.length();
        int len = token.length();
        int pos = nameEnd;
        while (pos < len && Character.isWhitespace(token.charAt(pos))) {
            pos++;
        }
        int contentEnd = len - 1;
        if (token.endsWith("/>")) {
            contentEnd = len - 2;
        }
        String attrText = token.substring(pos, contentEnd).trim();
        int i = 0;
        int attrLen = attrText.length();
        while (i < attrLen) {
            while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            if (i >= attrLen) {
                break;
            }
            int nameStart = i;
            while (i < attrLen && attrText.charAt(i) != '=' && !Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            String attrName = attrText.substring(nameStart, i).toLowerCase();
            while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            if (i < attrLen && attrText.charAt(i) == '=') {
                i++;
                while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                    i++;
                }
                String attrValue = null;
                if (i < attrLen && (attrText.charAt(i) == '"' || attrText.charAt(i) == '\'')) {
                    char q = attrText.charAt(i);
                    i++;
                    int valStart = i;
                    while (i < attrLen && attrText.charAt(i) != q) {
                        i++;
                    }
                    attrValue = attrText.substring(valStart, i);
                    if (i < attrLen) {
                        i++;
                    }
                } else {
                    int valStart = i;
                    while (i < attrLen && !Character.isWhitespace(attrText.charAt(i))) {
                        i++;
                    }
                    attrValue = attrText.substring(valStart, i);
                }
                if (targetAttr.equals(attrName)) {
                    return attrValue;
                }
            } else {
                if (targetAttr.equals(attrName)) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Returns a copy of a tag token with the named attribute (and its value) removed.
     * If the attribute is not present, the original token is returned unchanged.
     */
    private static String removeAttr(String token, String targetAttr) {
        String tagName = extractOpenTagName(token);
        int nameEnd = 1 + tagName.length();
        int len = token.length();
        int pos = nameEnd;
        while (pos < len && Character.isWhitespace(token.charAt(pos))) {
            pos++;
        }
        int contentEnd = len - 1;
        boolean selfClosing = token.endsWith("/>");
        if (selfClosing) {
            contentEnd = len - 2;
        }
        String attrText = token.substring(pos, contentEnd).trim();

        // Rebuild attrText without the target attribute
        StringBuilder rebuilt = new StringBuilder();
        int i = 0;
        int attrLen = attrText.length();
        boolean removed = false;
        while (i < attrLen) {
            // Skip leading whitespace — but preserve separator between attributes
            int wsStart = i;
            while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            if (i >= attrLen) {
                break;
            }
            int nameStart = i;
            while (i < attrLen && attrText.charAt(i) != '=' && !Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            String attrName = attrText.substring(nameStart, i).toLowerCase();
            // Skip whitespace before '='
            int afterNameWs = i;
            while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            int attrEnd;
            if (i < attrLen && attrText.charAt(i) == '=') {
                i++;
                while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                    i++;
                }
                if (i < attrLen && (attrText.charAt(i) == '"' || attrText.charAt(i) == '\'')) {
                    char q = attrText.charAt(i);
                    i++;
                    while (i < attrLen && attrText.charAt(i) != q) {
                        i++;
                    }
                    if (i < attrLen) {
                        i++;
                    }
                } else {
                    while (i < attrLen && !Character.isWhitespace(attrText.charAt(i))) {
                        i++;
                    }
                }
                attrEnd = i;
            } else {
                attrEnd = afterNameWs;
                i = afterNameWs;
            }

            if (targetAttr.equals(attrName)) {
                // Skip this attribute (do not append to rebuilt)
                removed = true;
            } else {
                if (rebuilt.length() > 0) {
                    rebuilt.append(" ");
                }
                rebuilt.append(attrText, nameStart, attrEnd);
            }
        }

        if (!removed) {
            return token;
        }

        // Reconstruct the tag
        String suffix = selfClosing ? "/>" : ">";
        String prefix = "<" + token.substring(1, 1 + tagName.length());
        if (rebuilt.length() > 0) {
            return prefix + " " + rebuilt.toString() + suffix;
        }
        return prefix + suffix;
    }

    // ---- Private helpers ----

    /** Strips HTML comments (<!-- ... -->), which may span multiple lines. */
    private static String stripComments(String html) {
        StringBuilder sb = new StringBuilder(html.length());
        int i = 0;
        int len = html.length();
        while (i < len) {
            int commentStart = html.indexOf("<!--", i);
            if (commentStart < 0) {
                sb.append(html, i, len);
                break;
            }
            sb.append(html, i, commentStart);
            int commentEnd = html.indexOf("-->", commentStart + 4);
            if (commentEnd < 0) {
                // Unclosed comment — skip to end
                break;
            }
            i = commentEnd + 3;
        }
        return sb.toString();
    }

    /**
     * Splits the HTML string into a list of tokens: each token is either a full
     * tag string ({@code <...>}) or a text run between tags.
     */
    private static List<String> tokenize(String html) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int len = html.length();
        while (i < len) {
            int tagStart = html.indexOf('<', i);
            if (tagStart < 0) {
                // Trailing text
                if (i < len) {
                    tokens.add(html.substring(i));
                }
                break;
            }
            if (tagStart > i) {
                // Text before this tag
                tokens.add(html.substring(i, tagStart));
            }
            // Find end of tag — must handle quoted attribute values that may contain '>'
            int tagEnd = findTagEnd(html, tagStart);
            if (tagEnd < 0) {
                throw new IllegalArgumentException(
                        "normalize: unclosed tag starting at position " + tagStart
                        + ": " + html.substring(tagStart, Math.min(tagStart + 40, len)));
            }
            tokens.add(html.substring(tagStart, tagEnd + 1));
            i = tagEnd + 1;
        }
        return tokens;
    }

    /**
     * Finds the closing {@code >} of a tag starting at {@code startPos}, respecting
     * double-quoted attribute values that may contain {@code >}.
     */
    private static int findTagEnd(String html, int startPos) {
        int len = html.length();
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = startPos + 1; i < len; i++) {
            char c = html.charAt(i);
            if (inQuote) {
                if (c == quoteChar) {
                    inQuote = false;
                }
            } else if (c == '"' || c == '\'') {
                inQuote = true;
                quoteChar = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    /** Extracts the tag name from a closing tag token like {@code </div>}. */
    private static String extractClosingTagName(String token) {
        // token = </tagname ...>
        int start = 2; // skip '</'
        int end = start;
        while (end < token.length() && !Character.isWhitespace(token.charAt(end)) && token.charAt(end) != '>') {
            end++;
        }
        return token.substring(start, end).toLowerCase();
    }

    /** Extracts the tag name from an opening tag token like {@code <input type="text">}. */
    private static String extractOpenTagName(String token) {
        int start = 1; // skip '<'
        int end = start;
        while (end < token.length() && !Character.isWhitespace(token.charAt(end))
                && token.charAt(end) != '>' && token.charAt(end) != '/') {
            end++;
        }
        return token.substring(start, end).toLowerCase();
    }

    /**
     * Parses the attributes of the opening tag and returns a canonical string:
     * attributes sorted alphabetically, values kept only for allow-listed names.
     * Format: {@code [name1 name2=val2 name3]} (space-separated, inside brackets).
     * Returns empty string if no attributes.
     */
    private static String buildAttrString(String token, String tagName) {
        // Collect raw attribute text — everything after the tag name
        int nameEnd = 1 + tagName.length(); // skip '<' + tagName
        // Eat whitespace
        int len = token.length();
        int pos = nameEnd;
        while (pos < len && Character.isWhitespace(token.charAt(pos))) {
            pos++;
        }
        // End of content: before '>' or '/>'
        int contentEnd = len - 1; // position of '>'
        if (token.endsWith("/>")) {
            contentEnd = len - 2;
        }
        String attrText = token.substring(pos, contentEnd).trim();

        if (attrText.isEmpty()) {
            return "";
        }

        // Parse attributes: key, key="value", key='value'
        TreeMap<String, String> attrs = new TreeMap<>(); // sorted by key
        int i = 0;
        int attrLen = attrText.length();
        while (i < attrLen) {
            // Skip whitespace
            while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            if (i >= attrLen) {
                break;
            }
            // Read attribute name
            int nameStart = i;
            while (i < attrLen && attrText.charAt(i) != '='
                    && !Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            String attrName = attrText.substring(nameStart, i).toLowerCase();
            if (attrName.isEmpty()) {
                break;
            }
            // Skip whitespace
            while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                i++;
            }
            // Check for '='
            String attrValue = null;
            if (i < attrLen && attrText.charAt(i) == '=') {
                i++; // skip '='
                while (i < attrLen && Character.isWhitespace(attrText.charAt(i))) {
                    i++;
                }
                if (i < attrLen && (attrText.charAt(i) == '"' || attrText.charAt(i) == '\'')) {
                    char q = attrText.charAt(i);
                    i++;
                    int valStart = i;
                    while (i < attrLen && attrText.charAt(i) != q) {
                        i++;
                    }
                    attrValue = attrText.substring(valStart, i);
                    if (i < attrLen) {
                        i++; // skip closing quote
                    }
                } else {
                    // Unquoted value
                    int valStart = i;
                    while (i < attrLen && !Character.isWhitespace(attrText.charAt(i))) {
                        i++;
                    }
                    attrValue = attrText.substring(valStart, i);
                }
            }
            attrs.put(attrName, attrValue);
        }

        if (attrs.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(" [");
        boolean first = true;
        for (java.util.Map.Entry<String, String> entry : attrs.entrySet()) {
            if (!first) {
                sb.append(" ");
            }
            first = false;
            String attrName = entry.getKey();
            String attrValue = entry.getValue();
            if (attrValue == null) {
                // Boolean attribute (e.g. required, disabled)
                sb.append(attrName);
            } else if (VALUE_KEEP_ATTRS.contains(attrName)) {
                sb.append(attrName).append("=").append(attrValue);
            } else {
                // Strip value — keep name only
                sb.append(attrName);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static void appendLine(StringBuilder out, int depth, String content) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
        out.append(content).append("\n");
    }
}
