package dev.appget.codegen;

import java.util.HashMap;
import java.util.Map;

/**
 * Java implementation of TypeRegistry.
 *
 * Maps language-neutral types (stored in models.yaml) to Java-specific types,
 * proto types, and OpenAPI types.
 *
 * This is the single source of truth for all type mappings in the Java subproject.
 * Use INSTANCE singleton to access this registry.
 *
 * Neutral type set: string, int32, int64, float64, bool, date, datetime, decimal
 */
public class JavaTypeRegistry implements TypeRegistry {

    /** Singleton instance — the single source of truth for Java type mappings. */
    public static final JavaTypeRegistry INSTANCE = new JavaTypeRegistry();

    private static final Map<String, String> NEUTRAL_TO_PROTO = createNeutralToProtoMapping();
    private static final Map<String, String[]> NEUTRAL_TO_OPENAPI = createNeutralToOpenApiMapping();
    private static final Map<String, String> NEUTRAL_TO_JAVA = createNeutralToJavaMapping();
    private static final Map<String, String> NEUTRAL_TO_JAVA_BOXED = createNeutralToJavaBoxedMapping();

    private JavaTypeRegistry() {
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

    // ---- TypeRegistry implementation ----

    @Override
    public String neutralToProto(String neutralType) {
        return NEUTRAL_TO_PROTO.getOrDefault(neutralType, "string");
    }

    @Override
    public String[] neutralToOpenApi(String neutralType) {
        String[] result = NEUTRAL_TO_OPENAPI.get(neutralType);
        if (result == null) {
            return new String[]{"string", null};
        }
        return result;
    }

    @Override
    public String neutralToJava(String neutralType) {
        return NEUTRAL_TO_JAVA.getOrDefault(neutralType, "String");
    }

    @Override
    public String neutralToJava(String neutralType, boolean nullable) {
        if (nullable) {
            return NEUTRAL_TO_JAVA_BOXED.getOrDefault(neutralType, "String");
        }
        return neutralToJava(neutralType);
    }

    @Override
    public boolean isTimestampType(String neutralType) {
        return "date".equals(neutralType) || "datetime".equals(neutralType);
    }

    @Override
    public boolean isDecimalType(String neutralType) {
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
