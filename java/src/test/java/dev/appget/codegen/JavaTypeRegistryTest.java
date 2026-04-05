package dev.appget.codegen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaTypeRegistry Tests")
class JavaTypeRegistryTest {

    // ---- neutralToProto ----

    @Test
    @DisplayName("string maps to string proto type")
    void testNeutralToProtoString() {
        assertEquals("string", JavaTypeRegistry.neutralToProto("string"));
    }

    @Test
    @DisplayName("int32 maps to int32 proto type")
    void testNeutralToProtoInt32() {
        assertEquals("int32", JavaTypeRegistry.neutralToProto("int32"));
    }

    @Test
    @DisplayName("int64 maps to int64 proto type")
    void testNeutralToProtoInt64() {
        assertEquals("int64", JavaTypeRegistry.neutralToProto("int64"));
    }

    @Test
    @DisplayName("float64 maps to double proto type")
    void testNeutralToProtoFloat64() {
        assertEquals("double", JavaTypeRegistry.neutralToProto("float64"));
    }

    @Test
    @DisplayName("bool maps to bool proto type")
    void testNeutralToProtoBool() {
        assertEquals("bool", JavaTypeRegistry.neutralToProto("bool"));
    }

    @Test
    @DisplayName("date maps to google.protobuf.Timestamp proto type")
    void testNeutralToProtoDate() {
        assertEquals("google.protobuf.Timestamp", JavaTypeRegistry.neutralToProto("date"));
    }

    @Test
    @DisplayName("datetime maps to google.protobuf.Timestamp proto type")
    void testNeutralToProtoDatetime() {
        assertEquals("google.protobuf.Timestamp", JavaTypeRegistry.neutralToProto("datetime"));
    }

    @Test
    @DisplayName("decimal maps to appget.common.Decimal proto type")
    void testNeutralToProtoDecimal() {
        assertEquals("appget.common.Decimal", JavaTypeRegistry.neutralToProto("decimal"));
    }

    @Test
    @DisplayName("Unknown neutral type falls back to string in proto")
    void testNeutralToProtoUnknown() {
        assertEquals("string", JavaTypeRegistry.neutralToProto("unknown_type"));
    }

    // ---- neutralToOpenApi ----

    @Test
    @DisplayName("string maps to OpenAPI [string, null]")
    void testNeutralToOpenApiString() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("string");
        assertEquals("string", result[0]);
        assertNull(result[1]);
    }

    @Test
    @DisplayName("int32 maps to OpenAPI [integer, int32]")
    void testNeutralToOpenApiInt32() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("int32");
        assertEquals("integer", result[0]);
        assertEquals("int32", result[1]);
    }

    @Test
    @DisplayName("int64 maps to OpenAPI [integer, int64]")
    void testNeutralToOpenApiInt64() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("int64");
        assertEquals("integer", result[0]);
        assertEquals("int64", result[1]);
    }

    @Test
    @DisplayName("float64 maps to OpenAPI [number, double]")
    void testNeutralToOpenApiFloat64() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("float64");
        assertEquals("number", result[0]);
        assertEquals("double", result[1]);
    }

    @Test
    @DisplayName("bool maps to OpenAPI [boolean, null]")
    void testNeutralToOpenApiBool() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("bool");
        assertEquals("boolean", result[0]);
        assertNull(result[1]);
    }

    @Test
    @DisplayName("date maps to OpenAPI [string, date]")
    void testNeutralToOpenApiDate() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("date");
        assertEquals("string", result[0]);
        assertEquals("date", result[1]);
    }

    @Test
    @DisplayName("datetime maps to OpenAPI [string, date-time]")
    void testNeutralToOpenApiDatetime() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("datetime");
        assertEquals("string", result[0]);
        assertEquals("date-time", result[1]);
    }

    @Test
    @DisplayName("decimal maps to OpenAPI [string, decimal]")
    void testNeutralToOpenApiDecimal() {
        String[] result = JavaTypeRegistry.neutralToOpenApi("decimal");
        assertEquals("string", result[0]);
        assertEquals("decimal", result[1]);
    }

    // ---- neutralToJava ----

    @Test
    @DisplayName("string maps to Java String")
    void testNeutralToJavaString() {
        assertEquals("String", JavaTypeRegistry.neutralToJava("string"));
    }

    @Test
    @DisplayName("int32 maps to Java int (non-nullable)")
    void testNeutralToJavaInt32() {
        assertEquals("int", JavaTypeRegistry.neutralToJava("int32"));
    }

    @Test
    @DisplayName("int32 nullable maps to Java Integer")
    void testNeutralToJavaInt32Nullable() {
        assertEquals("Integer", JavaTypeRegistry.neutralToJavaNullable("int32", true));
    }

    @Test
    @DisplayName("int64 maps to Java long (non-nullable)")
    void testNeutralToJavaInt64() {
        assertEquals("long", JavaTypeRegistry.neutralToJava("int64"));
    }

    @Test
    @DisplayName("int64 nullable maps to Java Long")
    void testNeutralToJavaInt64Nullable() {
        assertEquals("Long", JavaTypeRegistry.neutralToJavaNullable("int64", true));
    }

    @Test
    @DisplayName("float64 maps to Java double (non-nullable)")
    void testNeutralToJavaFloat64() {
        assertEquals("double", JavaTypeRegistry.neutralToJava("float64"));
    }

    @Test
    @DisplayName("float64 nullable maps to Java Double")
    void testNeutralToJavaFloat64Nullable() {
        assertEquals("Double", JavaTypeRegistry.neutralToJavaNullable("float64", true));
    }

    @Test
    @DisplayName("bool maps to Java boolean (non-nullable)")
    void testNeutralToJavaBool() {
        assertEquals("boolean", JavaTypeRegistry.neutralToJava("bool"));
    }

    @Test
    @DisplayName("bool nullable maps to Java Boolean")
    void testNeutralToJavaBoolNullable() {
        assertEquals("Boolean", JavaTypeRegistry.neutralToJavaNullable("bool", true));
    }

    @Test
    @DisplayName("date maps to Java LocalDate")
    void testNeutralToJavaDate() {
        assertEquals("LocalDate", JavaTypeRegistry.neutralToJava("date"));
    }

    @Test
    @DisplayName("datetime maps to Java LocalDateTime")
    void testNeutralToJavaDatetime() {
        assertEquals("LocalDateTime", JavaTypeRegistry.neutralToJava("datetime"));
    }

    @Test
    @DisplayName("decimal maps to Java BigDecimal")
    void testNeutralToJavaDecimal() {
        assertEquals("BigDecimal", JavaTypeRegistry.neutralToJava("decimal"));
    }

    // ---- isTimestampType ----

    @Test
    @DisplayName("date is a timestamp type")
    void testIsTimestampTypeDate() {
        assertTrue(JavaTypeRegistry.isTimestampType("date"));
    }

    @Test
    @DisplayName("datetime is a timestamp type")
    void testIsTimestampTypeDatetime() {
        assertTrue(JavaTypeRegistry.isTimestampType("datetime"));
    }

    @Test
    @DisplayName("string is not a timestamp type")
    void testIsTimestampTypeString() {
        assertFalse(JavaTypeRegistry.isTimestampType("string"));
    }

    @Test
    @DisplayName("decimal is not a timestamp type")
    void testIsTimestampTypeDecimal() {
        assertFalse(JavaTypeRegistry.isTimestampType("decimal"));
    }

    // ---- isDecimalType ----

    @Test
    @DisplayName("decimal is a decimal type")
    void testIsDecimalTypeDecimal() {
        assertTrue(JavaTypeRegistry.isDecimalType("decimal"));
    }

    @Test
    @DisplayName("string is not a decimal type")
    void testIsDecimalTypeString() {
        assertFalse(JavaTypeRegistry.isDecimalType("string"));
    }

    // ---- javaToNeutral (static method) ----

    @Test
    @DisplayName("javaToNeutral converts String to string")
    void testJavaToNeutralString() {
        assertEquals("string", JavaTypeRegistry.javaToNeutral("String"));
    }

    @Test
    @DisplayName("javaToNeutral converts int to int32")
    void testJavaToNeutralInt() {
        assertEquals("int32", JavaTypeRegistry.javaToNeutral("int"));
    }

    @Test
    @DisplayName("javaToNeutral converts Integer to int32")
    void testJavaToNeutralInteger() {
        assertEquals("int32", JavaTypeRegistry.javaToNeutral("Integer"));
    }

    @Test
    @DisplayName("javaToNeutral converts long to int64")
    void testJavaToNeutralLong() {
        assertEquals("int64", JavaTypeRegistry.javaToNeutral("long"));
    }

    @Test
    @DisplayName("javaToNeutral converts BigDecimal to decimal")
    void testJavaToNeutralBigDecimal() {
        assertEquals("decimal", JavaTypeRegistry.javaToNeutral("BigDecimal"));
    }

    @Test
    @DisplayName("javaToNeutral converts LocalDate to date")
    void testJavaToNeutralLocalDate() {
        assertEquals("date", JavaTypeRegistry.javaToNeutral("LocalDate"));
    }

    @Test
    @DisplayName("javaToNeutral converts LocalDateTime to datetime")
    void testJavaToNeutralLocalDateTime() {
        assertEquals("datetime", JavaTypeRegistry.javaToNeutral("LocalDateTime"));
    }

    @Test
    @DisplayName("javaToNeutral converts boolean to bool")
    void testJavaToNeutralBoolean() {
        assertEquals("bool", JavaTypeRegistry.javaToNeutral("boolean"));
    }
}
