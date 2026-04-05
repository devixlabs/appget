package dev.appget.codegen;

import java.util.List;
import java.util.Map;

/**
 * Immutable data carrier for a parsed domain model or view.
 *
 * Shared by {@link AppServerGenerator} and {@link HtmlCrudGenerator} to avoid
 * duplicating structurally similar inner classes. Each generator populates the
 * fields it needs; unused fields are {@code null} (e.g., {@code resource} is
 * unused by AppServerGenerator, {@code namespace} is unused by HtmlCrudGenerator).
 *
 * @param name      snake_case entity name from models.yaml (e.g., "users")
 * @param resource  kebab-case URL resource path (e.g., "users"), or null if not needed
 * @param domain    domain assignment from SQL comment (e.g., "auth")
 * @param namespace fully-qualified Java package (e.g., "dev.appget.auth"), or null
 * @param isView    true when this entity represents a database view
 * @param fields    field definitions from models.yaml; each map has at least "name" and "type"
 */
// Per EJ Item 17: immutable class
record ModelInfo(
        String name,
        String resource,
        String domain,
        String namespace,
        boolean isView,
        List<Map<String, Object>> fields) {
}
