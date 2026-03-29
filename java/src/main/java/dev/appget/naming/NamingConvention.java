package dev.appget.naming;

/**
 * Cross-language contract for field-name resolution at runtime.
 *
 * Each language subproject defines this interface (or language equivalent)
 * and a static utility class that implements the logic:
 *   Java:   NamingConvention interface + JavaNaming static class
 *   Go:     NamingConvention interface + go_naming.go package-level function
 *   Python: NamingConvention protocol  + python_naming.py module-level function
 *   Rust:   NamingConvention trait     + rust_naming.rs module-level function
 *
 * Specification.* calls the static utility directly (no instance, no polymorphism).
 * This interface exists to document the contract, not for runtime dispatch.
 */
public interface NamingConvention {
    /**
     * Convert a snake_case field name to the language's field accessor form.
     * Used by Specification to resolve getter/field names via reflection.
     *
     * Java:   "role_level" -> "roleLevel"  (camelCase for getter lookup)
     * Go:     "role_level" -> "RoleLevel"  (PascalCase for exported fields)
     * Python: "role_level" -> "role_level" (identity — getattr uses snake_case)
     * Rust:   "role_level" -> "role_level" (identity — struct fields are snake_case)
     */
    String toFieldAccessor(String snakeCaseField);
}
