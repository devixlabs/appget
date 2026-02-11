package dev.appget.model;

import dev.appget.hr.model.Salary;
import dev.appget.view.EmployeeSalaryView;
import dev.appget.specification.CompoundSpecification;
import dev.appget.specification.MetadataContext;
import dev.appget.specification.Specification;
import dev.appget.specification.context.SsoContext;
import dev.appget.specification.context.RolesContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rule Engine Tests")
class RuleTest {

    private Employee employee;
    private Specification ageSpecification;
    private Rule rule;

    @BeforeEach
    void setUp() {
        employee = Employee.newBuilder()
                .setName("Alice")
                .setAge(28)
                .setRoleId("Engineer")
                .build();
    }

    @Test
    @DisplayName("Rule should evaluate to success status when specification is satisfied")
    void testRuleEvaluatesToSuccessStatus() {
        ageSpecification = new Specification("age", ">", 25);
        rule = new Rule("AgeCheck", ageSpecification, "APPROVED", "REJECTED");

        String result = rule.evaluate(employee);
        assertEquals("APPROVED", result, "Employee age 28 > 25 should result in APPROVED");
    }

    @Test
    @DisplayName("Rule should evaluate to failure status when specification is not satisfied")
    void testRuleEvaluatesToFailureStatus() {
        ageSpecification = new Specification("age", ">", 30);
        rule = new Rule("AgeCheck", ageSpecification, "APPROVED", "REJECTED");

        String result = rule.evaluate(employee);
        assertEquals("REJECTED", result, "Employee age 28 > 30 should result in REJECTED");
    }

    @Test
    @DisplayName("Rule with role specification")
    void testRuleWithRoleSpecification() {
        Specification roleSpecification = new Specification("role_id", "==", "Engineer");
        rule = new Rule("RoleCheck", roleSpecification, "APPROVED", "REJECTED");

        String result = rule.evaluate(employee);
        assertEquals("APPROVED", result, "Employee role 'Engineer' == 'Engineer' should result in APPROVED");
    }

    @Test
    @DisplayName("Rule should return correct name")
    void testRuleGetName() {
        ageSpecification = new Specification("age", ">", 25);
        rule = new Rule("AgeCheck", ageSpecification, "APPROVED", "REJECTED");

        assertEquals("AgeCheck", rule.getName(), "Rule name should match constructor argument");
    }

    @Test
    @DisplayName("Rule with custom success status")
    void testRuleWithCustomSuccessStatus() {
        ageSpecification = new Specification("age", ">", 25);
        rule = new Rule("CustomRule", ageSpecification, "PASSED", "FAILED");

        String result = rule.evaluate(employee);
        assertEquals("PASSED", result, "Rule should use custom success status");
    }

    @Test
    @DisplayName("Rule with custom failure status")
    void testRuleWithCustomFailureStatus() {
        ageSpecification = new Specification("age", ">", 30);
        rule = new Rule("CustomRule", ageSpecification, "PASSED", "FAILED");

        String result = rule.evaluate(employee);
        assertEquals("FAILED", result, "Rule should use custom failure status");
    }

    @Test
    @DisplayName("Multiple rules against same employee")
    void testMultipleRulesAgainstSameEmployee() {
        Rule ageRule = new Rule("AgeCheck",
                new Specification("age", ">", 25),
                "APPROVED", "REJECTED");

        Rule roleRule = new Rule("RoleCheck",
                new Specification("role_id", "==", "Engineer"),
                "APPROVED", "REJECTED");

        String ageResult = ageRule.evaluate(employee);
        String roleResult = roleRule.evaluate(employee);

        assertEquals("APPROVED", ageResult, "Age check should pass");
        assertEquals("APPROVED", roleResult, "Role check should pass");
    }

    @Test
    @DisplayName("Rule evaluation consistency")
    void testRuleEvaluationConsistency() {
        ageSpecification = new Specification("age", ">", 25);
        rule = new Rule("ConsistencyCheck", ageSpecification, "APPROVED", "REJECTED");

        String result1 = rule.evaluate(employee);
        String result2 = rule.evaluate(employee);

        assertEquals(result1, result2, "Multiple evaluations of same rule should give same result");
    }

    @Test
    @DisplayName("Rule with different employees")
    void testRuleWithDifferentEmployees() {
        ageSpecification = new Specification("age", ">", 25);
        rule = new Rule("AgeCheck", ageSpecification, "APPROVED", "REJECTED");

        Employee young = Employee.newBuilder()
                .setName("Bob")
                .setAge(22)
                .setRoleId("Intern")
                .build();

        Employee senior = Employee.newBuilder()
                .setName("Charlie")
                .setAge(45)
                .setRoleId("Manager")
                .build();

        assertEquals("REJECTED", rule.evaluate(young), "Young employee should be rejected");
        assertEquals("APPROVED", rule.evaluate(senior), "Senior employee should be approved");
    }

    // Generic + compound + metadata tests

    @Test
    @DisplayName("Rule evaluates Salary model generically")
    void testRuleWithSalaryModel() {
        Salary salary = Salary.newBuilder()
                .setEmployeeId("Alice")
                .setAmount(75000.0)
                .setYearsOfService(5)
                .build();

        Specification amountSpec = new Specification("amount", ">", 50000);
        Rule salaryRule = new Rule("SalaryCheck", amountSpec, "PREMIUM", "STANDARD");

        assertEquals("PREMIUM", salaryRule.evaluate(salary));
    }

    @Test
    @DisplayName("Rule with compound specification")
    void testRuleWithCompoundSpecification() {
        Employee manager = Employee.newBuilder()
                .setName("Bob")
                .setAge(40)
                .setRoleId("Manager")
                .build();

        CompoundSpecification compound = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("age", ">=", 30),
                        new Specification("role_id", "==", "Manager")
                )
        );

        Rule compoundRule = new Rule("SeniorManager", compound, "SENIOR", "JUNIOR");
        assertEquals("SENIOR", compoundRule.evaluate(manager));
    }

    @Test
    @DisplayName("Rule with metadata requirements - passes with valid metadata")
    void testRuleWithMetadataPassesWhenValid() {
        Map<String, List<Specification>> metaReqs = Map.of(
                "sso", List.of(new Specification("authenticated", "==", true))
        );

        Rule authRule = new Rule("AuthRule",
                new Specification("age", ">=", 25),
                "ALLOWED", "DENIED",
                "Employee", metaReqs);

        SsoContext sso = SsoContext.builder().authenticated(true).sessionId("s1").build();
        MetadataContext metadata = new MetadataContext().with("sso", sso);

        assertEquals("ALLOWED", authRule.evaluate(employee, metadata));
    }

    @Test
    @DisplayName("Rule with metadata requirements - fails without metadata")
    void testRuleWithMetadataFailsWithoutMetadata() {
        Map<String, List<Specification>> metaReqs = Map.of(
                "sso", List.of(new Specification("authenticated", "==", true))
        );

        Rule authRule = new Rule("AuthRule",
                new Specification("age", ">=", 25),
                "ALLOWED", "DENIED",
                "Employee", metaReqs);

        assertEquals("DENIED", authRule.evaluate(employee));
    }

    @Test
    @DisplayName("Rule targeting view field must not pass when evaluated against wrong model type")
    void testRuleWithViewFieldFailsOnWrongModelType() {
        // salaryAmount exists on EmployeeSalaryView but NOT on Employee
        Specification viewFieldSpec = new Specification("salary_amount", ">", 100000);
        Rule viewRule = new Rule("HighEarnerCheck", viewFieldSpec, "HIGH_EARNER", "STANDARD_EARNER");

        // Evaluating against Employee (which lacks salaryAmount) must return failure status
        String result = viewRule.evaluate(employee);
        assertEquals("STANDARD_EARNER", result,
                "Rule with field not present on target model should return failure status, not throw or pass");
    }

    @Test
    @DisplayName("Rule targeting view field succeeds when evaluated against correct view type")
    void testRuleWithViewFieldSucceedsOnCorrectViewType() {
        EmployeeSalaryView view = EmployeeSalaryView.newBuilder()
                .setEmployeeName("Alice")
                .setEmployeeAge(35)
                .setSalaryAmount(150000.0)
                .build();

        Specification viewFieldSpec = new Specification("salary_amount", ">", 100000);
        Rule viewRule = new Rule("HighEarnerCheck", viewFieldSpec, "HIGH_EARNER", "STANDARD_EARNER");

        String result = viewRule.evaluate(view);
        assertEquals("HIGH_EARNER", result,
                "Rule should pass when evaluated against the correct view type with matching field");
    }
}
