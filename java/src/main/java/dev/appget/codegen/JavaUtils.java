package dev.appget.codegen;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java-specific type mappings and utilities for code generation.
 *
 * Counterpart to future PythonUtils, GoUtils, etc. — each language has its own
 * class holding the *_TO_PROTO_TYPE mapping and any other language-specific logic.
 */
public class JavaUtils {

    private JavaUtils() {
    }

    public static final Map<String, String> JAVA_TO_PROTO_TYPE = createJavaToProtoTypeMapping();

    private static Map<String, String> createJavaToProtoTypeMapping() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("String", "string");
        map.put("int", "int32");
        map.put("Integer", "int32");
        map.put("long", "int64");
        map.put("Long", "int64");
        map.put("BigDecimal", "double");
        map.put("double", "double");
        map.put("Double", "double");
        map.put("LocalDate", "string");
        map.put("LocalDateTime", "string");
        map.put("boolean", "bool");
        map.put("Boolean", "bool");
        return map;
    }

    public static String snakeToCamel(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static String snakeToPascal(String snake) {
        if (snake == null || snake.isEmpty()) {
            return snake;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
