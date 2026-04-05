package dev.appget.codegen;

/**
 * Immutable data carrier for a parsed business rule.
 *
 * Shared by {@link AppServerGenerator} and {@link HtmlCrudGenerator} to avoid
 * duplicating structurally similar inner classes. {@code requiresMetadata} is
 * only meaningful to AppServerGenerator; HtmlCrudGenerator passes {@code false}.
 *
 * @param name             unique rule name (PascalCase, e.g., "UserEmailValidation")
 * @param targetType       "model" or "view"
 * @param targetName       snake_case name of the target entity
 * @param targetDomain     domain of the target entity
 * @param requiresMetadata true when the rule's spec class needs a MetadataContext argument
 * @param blocking         true when an unsatisfied rule causes a 422 rejection
 */
// Per EJ Item 17: immutable class
record RuleInfo(
        String name,
        String targetType,
        String targetName,
        String targetDomain,
        boolean requiresMetadata,
        boolean blocking) {
}
