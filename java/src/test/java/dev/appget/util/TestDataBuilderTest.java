package dev.appget.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.MessageOrBuilder;
import dev.appget.model.Employee;
import dev.appget.hr.model.Salary;
import dev.appget.view.EmployeeSalaryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Test Data Builder Tests")
class TestDataBuilderTest {

    private TestDataBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new TestDataBuilder();
    }

    @Test
    @DisplayName("Should build Employee with default values")
    void testBuildEmployee() {
        MessageOrBuilder employee = builder.buildSampleMessage(Employee.getDescriptor());
        assertNotNull(employee);

        Descriptors.Descriptor desc = Employee.getDescriptor();
        assertEquals("Sample_name", employee.getField(desc.findFieldByName("name")));
        assertEquals(42, employee.getField(desc.findFieldByName("age")));
        assertEquals("Sample_role_id", employee.getField(desc.findFieldByName("role_id")));
    }

    @Test
    @DisplayName("Should build Salary with correct types")
    void testBuildSalary() {
        MessageOrBuilder salary = builder.buildSampleMessage(Salary.getDescriptor());
        assertNotNull(salary);

        Descriptors.Descriptor desc = Salary.getDescriptor();
        assertEquals("Sample_employee_id", salary.getField(desc.findFieldByName("employee_id")));
        assertEquals(42.0, salary.getField(desc.findFieldByName("amount")));
        assertEquals(42, salary.getField(desc.findFieldByName("years_of_service")));
    }

    @Test
    @DisplayName("Should build EmployeeSalaryView with all fields")
    void testBuildView() {
        MessageOrBuilder view = builder.buildSampleMessage(EmployeeSalaryView.getDescriptor());
        assertNotNull(view);

        Descriptors.Descriptor desc = EmployeeSalaryView.getDescriptor();
        assertEquals("Sample_employee_name", view.getField(desc.findFieldByName("employee_name")));
        assertEquals(42, view.getField(desc.findFieldByName("employee_age")));
        assertEquals(42.0, view.getField(desc.findFieldByName("salary_amount")));
    }

    @Test
    @DisplayName("String fields should have Sample_ prefix")
    void testStringFieldFormat() {
        MessageOrBuilder employee = builder.buildSampleMessage(Employee.getDescriptor());
        Object name = employee.getField(Employee.getDescriptor().findFieldByName("name"));
        assertTrue(name.toString().startsWith("Sample_"), "String fields should start with 'Sample_'");
    }

    @Test
    @DisplayName("Integer fields should default to 42")
    void testIntFieldDefault() {
        MessageOrBuilder employee = builder.buildSampleMessage(Employee.getDescriptor());
        Object age = employee.getField(Employee.getDescriptor().findFieldByName("age"));
        assertEquals(42, age);
    }

    @Test
    @DisplayName("Double fields should default to 42.0")
    void testDoubleFieldDefault() {
        MessageOrBuilder salary = builder.buildSampleMessage(Salary.getDescriptor());
        Object amount = salary.getField(Salary.getDescriptor().findFieldByName("amount"));
        assertEquals(42.0, amount);
    }
}
