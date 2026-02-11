package dev.appget.specification;

import dev.appget.model.Employee;
import dev.appget.hr.model.Salary;
import dev.appget.hr.model.Department;
import dev.appget.finance.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Specification Pattern Tests")
class SpecificationTest {

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = Employee.newBuilder()
                .setName("Alice")
                .setAge(28)
                .setRoleId("Manager")
                .build();
    }

    @Test
    @DisplayName("Age greater than operator should evaluate correctly")
    void testAgeGreaterThan() {
        Specification spec = new Specification("age", ">", 25);
        assertTrue(spec.isSatisfiedBy(employee), "Employee age 28 > 25 should be true");
    }

    @Test
    @DisplayName("Age greater than operator should fail when age is less")
    void testAgeGreaterThanFails() {
        Specification spec = new Specification("age", ">", 30);
        assertFalse(spec.isSatisfiedBy(employee), "Employee age 28 > 30 should be false");
    }

    @Test
    @DisplayName("Age greater than or equal operator")
    void testAgeGreaterThanOrEqual() {
        Specification spec = new Specification("age", ">=", 28);
        assertTrue(spec.isSatisfiedBy(employee), "Employee age 28 >= 28 should be true");
    }

    @Test
    @DisplayName("Age less than or equal operator")
    void testAgeLessThanOrEqual() {
        Specification spec = new Specification("age", "<=", 30);
        assertTrue(spec.isSatisfiedBy(employee), "Employee age 28 <= 30 should be true");
    }

    @Test
    @DisplayName("Age equals operator")
    void testAgeEquals() {
        Specification spec = new Specification("age", "==", 28);
        assertTrue(spec.isSatisfiedBy(employee), "Employee age 28 == 28 should be true");
    }

    @Test
    @DisplayName("Age equals operator should fail for different age")
    void testAgeEqualsFails() {
        Specification spec = new Specification("age", "==", 25);
        assertFalse(spec.isSatisfiedBy(employee), "Employee age 28 == 25 should be false");
    }

    @Test
    @DisplayName("Role equals operator for string field")
    void testRoleEquals() {
        Specification spec = new Specification("role_id", "==", "Manager");
        assertTrue(spec.isSatisfiedBy(employee), "Role 'Manager' == 'Manager' should be true");
    }

    @Test
    @DisplayName("Role equals operator should fail for different role")
    void testRoleEqualsFails() {
        Specification spec = new Specification("role_id", "==", "Engineer");
        assertFalse(spec.isSatisfiedBy(employee), "Role 'Manager' == 'Engineer' should be false");
    }

    @Test
    @DisplayName("Role not equals operator")
    void testRoleNotEquals() {
        Specification spec = new Specification("role_id", "!=", "Engineer");
        assertTrue(spec.isSatisfiedBy(employee), "Role 'Manager' != 'Engineer' should be true");
    }

    @Test
    @DisplayName("Role not equals operator should fail when roles match")
    void testRoleNotEqualsFails() {
        Specification spec = new Specification("role_id", "!=", "Manager");
        assertFalse(spec.isSatisfiedBy(employee), "Role 'Manager' != 'Manager' should be false");
    }

    @Test
    @DisplayName("Invalid field should return false")
    void testInvalidField() {
        Specification spec = new Specification("invalidField", ">", 25);
        assertFalse(spec.isSatisfiedBy(employee), "Invalid field should return false");
    }

    @Test
    @DisplayName("Invalid operator should return false")
    void testInvalidOperator() {
        Specification spec = new Specification("age", ">>", 25);
        assertFalse(spec.isSatisfiedBy(employee), "Invalid operator should return false");
    }

    @Test
    @DisplayName("Specification toString should be informative")
    void testToString() {
        Specification spec = new Specification("age", ">", 25);
        String result = spec.toString();
        assertTrue(result.contains("age"), "toString should contain field name");
        assertTrue(result.contains(">"), "toString should contain operator");
        assertTrue(result.contains("25"), "toString should contain value");
    }

    @Test
    @DisplayName("Multiple specifications with same employee")
    void testMultipleSpecifications() {
        Specification ageSpec = new Specification("age", ">", 20);
        Specification roleSpec = new Specification("role_id", "==", "Manager");

        assertTrue(ageSpec.isSatisfiedBy(employee), "Age check should pass");
        assertTrue(roleSpec.isSatisfiedBy(employee), "Role check should pass");
    }

    @Test
    @DisplayName("Edge case: minimum age boundary")
    void testMinimumAgeBoundary() {
        Employee youngEmployee = Employee.newBuilder()
                .setName("Bob")
                .setAge(0)
                .setRoleId("Intern")
                .build();

        Specification spec = new Specification("age", ">=", 0);
        assertTrue(spec.isSatisfiedBy(youngEmployee), "Age 0 >= 0 should be true");
    }

    @Test
    @DisplayName("Edge case: large age value")
    void testLargeAgeValue() {
        Employee seniorEmployee = Employee.newBuilder()
                .setName("Charlie")
                .setAge(65)
                .setRoleId("Director")
                .build();

        Specification spec = new Specification("age", ">=", 60);
        assertTrue(spec.isSatisfiedBy(seniorEmployee), "Age 65 >= 60 should be true");
    }

    // Tests for non-Employee models

    @Test
    @DisplayName("Salary double amount comparison")
    void testSalaryAmountComparison() {
        Salary salary = Salary.newBuilder()
                .setEmployeeId("Alice")
                .setAmount(75000.50)
                .setYearsOfService(5)
                .build();

        Specification spec = new Specification("amount", ">", 50000);
        assertTrue(spec.isSatisfiedBy(salary), "Salary 75000.50 > 50000 should be true");
    }

    @Test
    @DisplayName("Department budget double comparison")
    void testDepartmentBudgetComparison() {
        Department dept = Department.newBuilder()
                .setId("D1")
                .setName("Engineering")
                .setBudget(500000.0)
                .build();

        Specification spec = new Specification("budget", ">=", 500000);
        assertTrue(spec.isSatisfiedBy(dept), "Budget 500000 >= 500000 should be true");
    }

    @Test
    @DisplayName("Invoice string field comparison")
    void testInvoiceStringFieldComparison() {
        Invoice invoice = Invoice.newBuilder()
                .setInvoiceNumber("INV-001")
                .setAmount(1500.0)
                .setIssueDate("2025-06-15")
                .build();

        Specification spec = new Specification("invoice_number", "==", "INV-001");
        assertTrue(spec.isSatisfiedBy(invoice), "Invoice number should match");
    }

    @Test
    @DisplayName("Salary integer field comparison")
    void testSalaryIntegerField() {
        Salary salary = Salary.newBuilder()
                .setEmployeeId("Bob")
                .setAmount(60000.0)
                .setYearsOfService(10)
                .build();

        Specification spec = new Specification("years_of_service", ">", 5);
        assertTrue(spec.isSatisfiedBy(salary), "Years of service 10 > 5 should be true");
    }

    @Test
    @DisplayName("Name field comparison on Employee (string via descriptor)")
    void testNameFieldComparison() {
        Specification spec = new Specification("name", "==", "Alice");
        assertTrue(spec.isSatisfiedBy(employee), "Name 'Alice' == 'Alice' should be true");
    }
}
