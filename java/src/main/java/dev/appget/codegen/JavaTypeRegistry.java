package dev.appget.codegen;

import java.util.HashMap;
import java.util.Map;

/**
 * Static utility class for Java type mappings.
 *
 * Maps language-neutral types (stored in models.yaml) to Java-specific types,
 * proto types, and OpenAPI types.
 *
 * This is the single source of truth for all type mappings in the Java subproject.
 * All methods are static — no instances.
 *
 * Neutral type set: string, int32, int64, float64, bool, date, datetime, decimal
 */
// Per EJ Item 4: noninstantiable utility class
public final class JavaTypeRegistry {

    private static final Map<String, String> NEUTRAL_TO_PROTO = createNeutralToProtoMapping();
    private static final Map<String, String[]> NEUTRAL_TO_OPENAPI = createNeutralToOpenApiMapping();
    private static final Map<String, String> NEUTRAL_TO_JAVA = createNeutralToJavaMapping();
    private static final Map<String, String> NEUTRAL_TO_JAVA_BOXED = createNeutralToJavaBoxedMapping();
    private static final Map<String, String> PROTO_TO_NEUTRAL = createProtoToNeutralMapping();

    /** Suppress default constructor — noninstantiable. */
    private JavaTypeRegistry() {
        throw new AssertionError("No instances");
    }

    // ---- Proto Mapping ----
    // Neutral → Proto type name

    private static Map<String, String> createNeutralToProtoMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("string",   "string");
        map.put("int32",    "int32");
        map.put("int64",    "int64");
        map.put("float64",  "double");
        map.put("bool",     "bool");
        map.put("date",     "google.protobuf.Timestamp");
        map.put("datetime", "google.protobuf.Timestamp");
        map.put("decimal",  "appget.common.Decimal");
        return map;
    }

    // ---- OpenAPI Mapping ----
    // Neutral → [openapi_type, openapi_format] (format may be null)

    private static Map<String, String[]> createNeutralToOpenApiMapping() {
        Map<String, String[]> map = new HashMap<>();
        map.put("string",   new String[]{"string",  null});
        map.put("int32",    new String[]{"integer", "int32"});
        map.put("int64",    new String[]{"integer", "int64"});
        map.put("float64",  new String[]{"number",  "double"});
        map.put("bool",     new String[]{"boolean", null});
        map.put("date",     new String[]{"string",  "date"});
        map.put("datetime", new String[]{"string",  "date-time"});
        map.put("decimal",  new String[]{"string",  "decimal"});
        return map;
    }

    // ---- Java Mapping (non-nullable / primitive forms) ----

    private static Map<String, String> createNeutralToJavaMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("string",   "String");
        map.put("int32",    "int");
        map.put("int64",    "long");
        map.put("float64",  "double");
        map.put("bool",     "boolean");
        map.put("date",     "LocalDate");
        map.put("datetime", "LocalDateTime");
        map.put("decimal",  "BigDecimal");
        return map;
    }

    // ---- Java Mapping (nullable / boxed forms for primitives) ----

    private static Map<String, String> createNeutralToJavaBoxedMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("string",   "String");
        map.put("int32",    "Integer");
        map.put("int64",    "Long");
        map.put("float64",  "Double");
        map.put("bool",     "Boolean");
        map.put("date",     "LocalDate");
        map.put("datetime", "LocalDateTime");
        map.put("decimal",  "BigDecimal");
        return map;
    }

    // ---- Proto-to-Neutral Reverse Mapping ----
    // Derived from NEUTRAL_TO_PROTO with special-case handling for proto types
    // that have no neutral equivalent (float, bytes) and for Timestamp which maps
    // to two neutral types (date, datetime) — we prefer "datetime" for date-time format.

    private static Map<String, String> createProtoToNeutralMapping() {
        Map<String, String> map = new HashMap<>();
        // Invert the neutral-to-proto map; last-write-wins for duplicates,
        // so insert "date" first, then "datetime" overwrites for Timestamp -> datetime.
        for (Map.Entry<String, String> entry : NEUTRAL_TO_PROTO.entrySet()) {
            map.put(entry.getValue(), entry.getKey());
        }
        // Guarantee that google.protobuf.Timestamp maps to "datetime" (not "date"),
        // producing OpenAPI format "date-time" — matching the original behavior.
        map.put("google.protobuf.Timestamp", "datetime");
        return map;
    }

    // ---- Public static methods ----

    public static String neutralToProto(String neutralType) {
        return NEUTRAL_TO_PROTO.getOrDefault(neutralType, "string");
    }

    public static String[] neutralToOpenApi(String neutralType) {
        String[] result = NEUTRAL_TO_OPENAPI.get(neutralType);
        if (result == null) {
            return new String[]{"string", null};
        }
        return result;
    }

    public static String neutralToJava(String neutralType) {
        return NEUTRAL_TO_JAVA.getOrDefault(neutralType, "String");
    }

    // No method overloading — use distinct method names (per java/CLAUDE.md)
    public static String neutralToJavaNullable(String neutralType, boolean nullable) {
        if (nullable) {
            return NEUTRAL_TO_JAVA_BOXED.getOrDefault(neutralType, "String");
        }
        return neutralToJava(neutralType);
    }

    /**
     * Map a proto type directly to its OpenAPI [type, format] pair.
     *
     * Chain: proto type -> neutral type (via reverse map) -> OpenAPI pair.
     * Handles proto-only types (float, bytes) that have no neutral equivalent.
     * Falls back to ["string", null] for unknown types.
     *
     * @param protoType the protobuf type name (e.g. "int32", "double", "appget.common.Decimal")
     * @return a two-element array [openApiType, openApiFormat]; format may be null
     */
    public static String[] protoToOpenApi(final String protoType) {
        // Proto-only types with no neutral equivalent
        if ("float".equals(protoType)) {
            return new String[]{"number", "float"};
        }
        if ("bytes".equals(protoType)) {
            return new String[]{"string", "byte"};
        }
        final String neutralType = PROTO_TO_NEUTRAL.get(protoType);
        if (neutralType == null) {
            return new String[]{"string", null};
        }
        final String[] result = NEUTRAL_TO_OPENAPI.get(neutralType);
        if (result == null) {
            return new String[]{"string", null};
        }
        return result;
    }

    /**
     * Check if the given type is a known neutral type in the registry.
     * Delegates to the NEUTRAL_TO_PROTO map — the canonical set of neutral types.
     */
    public static boolean isKnownNeutralType(String type) {
        return type != null && NEUTRAL_TO_PROTO.containsKey(type);
    }

    public static boolean isTimestampType(String neutralType) {
        return "date".equals(neutralType) || "datetime".equals(neutralType);
    }

    public static boolean isDecimalType(String neutralType) {
        return "decimal".equals(neutralType);
    }

    /**
     * Convenience: map a legacy Java type (from old models.yaml) to a neutral type.
     * Used for backward compatibility during migration.
     */
    public static String javaToNeutral(String javaType) {
        if (javaType == null) return "string";
        if ("String".equals(javaType)) return "string";
        if ("int".equals(javaType) || "Integer".equals(javaType)) return "int32";
        if ("long".equals(javaType) || "Long".equals(javaType)) return "int64";
        if ("double".equals(javaType) || "Double".equals(javaType)) return "float64";
        if ("boolean".equals(javaType) || "Boolean".equals(javaType)) return "bool";
        if ("LocalDate".equals(javaType)) return "date";
        if ("LocalDateTime".equals(javaType)) return "datetime";
        if ("BigDecimal".equals(javaType)) return "decimal";
        if ("float".equals(javaType) || "Float".equals(javaType)) return "float64";
        return "string"; // fallback
    }
}
