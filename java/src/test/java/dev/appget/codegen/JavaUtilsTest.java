package dev.appget.codegen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JavaUtils Tests")
class JavaUtilsTest {

    @Test
    @DisplayName("JAVA_TO_PROTO_TYPE map is not null")
    void testMapNotNull() {
        assertNotNull(JavaUtils.JAVA_TO_PROTO_TYPE);
    }

    @Test
    @DisplayName("JAVA_TO_PROTO_TYPE map has correct size")
    void testMapSize() {
        assertEquals(12, JavaUtils.JAVA_TO_PROTO_TYPE.size());
    }

    @Test
    @DisplayName("String maps to string")
    void testStringType() {
        assertEquals("string", JavaUtils.JAVA_TO_PROTO_TYPE.get("String"));
    }

    @Test
    @DisplayName("int maps to int32")
    void testIntType() {
        assertEquals("int32", JavaUtils.JAVA_TO_PROTO_TYPE.get("int"));
    }

    @Test
    @DisplayName("Integer maps to int32")
    void testIntegerType() {
        assertEquals("int32", JavaUtils.JAVA_TO_PROTO_TYPE.get("Integer"));
    }

    @Test
    @DisplayName("long maps to int64")
    void testLongType() {
        assertEquals("int64", JavaUtils.JAVA_TO_PROTO_TYPE.get("long"));
    }

    @Test
    @DisplayName("Long maps to int64")
    void testLongWrapperType() {
        assertEquals("int64", JavaUtils.JAVA_TO_PROTO_TYPE.get("Long"));
    }

    @Test
    @DisplayName("BigDecimal maps to double")
    void testBigDecimalType() {
        assertEquals("double", JavaUtils.JAVA_TO_PROTO_TYPE.get("BigDecimal"));
    }

    @Test
    @DisplayName("double maps to double")
    void testDoubleType() {
        assertEquals("double", JavaUtils.JAVA_TO_PROTO_TYPE.get("double"));
    }

    @Test
    @DisplayName("Double maps to double")
    void testDoubleWrapperType() {
        assertEquals("double", JavaUtils.JAVA_TO_PROTO_TYPE.get("Double"));
    }

    @Test
    @DisplayName("LocalDate maps to string")
    void testLocalDateType() {
        assertEquals("string", JavaUtils.JAVA_TO_PROTO_TYPE.get("LocalDate"));
    }

    @Test
    @DisplayName("LocalDateTime maps to string")
    void testLocalDateTimeType() {
        assertEquals("string", JavaUtils.JAVA_TO_PROTO_TYPE.get("LocalDateTime"));
    }

    @Test
    @DisplayName("boolean maps to bool")
    void testBooleanType() {
        assertEquals("bool", JavaUtils.JAVA_TO_PROTO_TYPE.get("boolean"));
    }

    @Test
    @DisplayName("Boolean maps to bool")
    void testBooleanWrapperType() {
        assertEquals("bool", JavaUtils.JAVA_TO_PROTO_TYPE.get("Boolean"));
    }

    @Test
    @DisplayName("Unknown type falls back to string via getOrDefault")
    void testUnknownTypeFallback() {
        assertEquals("string", JavaUtils.JAVA_TO_PROTO_TYPE.getOrDefault("UnknownType", "string"));
    }

    @Test
    @DisplayName("Unknown type is absent from map")
    void testUnknownTypeAbsent() {
        assertNull(JavaUtils.JAVA_TO_PROTO_TYPE.get("UnknownType"));
    }

    // ---- snakeToCamel ----

    @Test
    @DisplayName("snakeToCamel: converts snake_case to camelCase")
    void testSnakeToCamel() {
        assertEquals("snakeCase", JavaUtils.snakeToCamel("snake_case"));
    }

    @Test
    @DisplayName("snakeToCamel: multi-word snake_case")
    void testSnakeToCamelMultiWord() {
        assertEquals("snakeCaseString", JavaUtils.snakeToCamel("snake_case_string"));
    }

    @Test
    @DisplayName("snakeToCamel: no underscores is unchanged")
    void testSnakeToCamelNoUnderscore() {
        assertEquals("simple", JavaUtils.snakeToCamel("simple"));
    }

    @Test
    @DisplayName("snakeToCamel: null returns null")
    void testSnakeToCamelNull() {
        assertNull(JavaUtils.snakeToCamel(null));
    }

    @Test
    @DisplayName("snakeToCamel: empty string returns empty string")
    void testSnakeToCamelEmpty() {
        assertEquals("", JavaUtils.snakeToCamel(""));
    }

    @Test
    @DisplayName("snakeToCamel: domain field names")
    void testSnakeToCamelDomainFields() {
        assertEquals("roleId", JavaUtils.snakeToCamel("role_id"));
        assertEquals("invoiceNumber", JavaUtils.snakeToCamel("invoice_number"));
        assertEquals("yearsOfService", JavaUtils.snakeToCamel("years_of_service"));
        assertEquals("name", JavaUtils.snakeToCamel("name"));
    }

    // ---- snakeToPascal ----

    @Test
    @DisplayName("snakeToPascal: converts snake_case to PascalCase")
    void testSnakeToPascal() {
        assertEquals("SnakeCase", JavaUtils.snakeToPascal("snake_case"));
    }

    @Test
    @DisplayName("snakeToPascal: table names to PascalCase")
    void testSnakeToPascalTableNames() {
        assertEquals("Employees", JavaUtils.snakeToPascal("employees"));
        assertEquals("Salaries", JavaUtils.snakeToPascal("salaries"));
        assertEquals("Departments", JavaUtils.snakeToPascal("departments"));
        assertEquals("Invoices", JavaUtils.snakeToPascal("invoices"));
    }

    @Test
    @DisplayName("snakeToPascal: view names to PascalCase")
    void testSnakeToPascalViewNames() {
        assertEquals("EmployeeSalaryView", JavaUtils.snakeToPascal("employee_salary_view"));
        assertEquals("DepartmentBudgetView", JavaUtils.snakeToPascal("department_budget_view"));
    }

    @Test
    @DisplayName("snakeToPascal: no underscores capitalizes first letter")
    void testSnakeToPascalNoUnderscore() {
        assertEquals("Simple", JavaUtils.snakeToPascal("simple"));
    }

    @Test
    @DisplayName("snakeToPascal: null returns null")
    void testSnakeToPascalNull() {
        assertNull(JavaUtils.snakeToPascal(null));
    }

    @Test
    @DisplayName("snakeToPascal: empty string returns empty string")
    void testSnakeToPascalEmpty() {
        assertEquals("", JavaUtils.snakeToPascal(""));
    }
}
