package dev.appget.specification;

import com.google.protobuf.Descriptors;
import dev.appget.model.Employee;
import dev.appget.model.Rule;
import dev.appget.hr.model.Salary;
import dev.appget.view.EmployeeSalaryView;
import dev.appget.rules.RulesProto;
import dev.appget.specification.context.SsoContext;
import dev.appget.specification.context.RolesContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Rule Interceptor Tests")
class RuleInterceptorTest {

    private int expectedEmployeeRuleCount;
    private int expectedSalaryRuleCount;
    private int expectedViewRuleCount;

    @BeforeEach
    void loadTestSpecifications() {
        // Load specs.yaml from project root and count expected rules per model
        try (InputStream input = new FileInputStream(new File("specs.yaml"))) {
            Yaml yaml = new Yaml();
            Map<String, Object> specs = yaml.load(input);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) specs.get("rules");

            // Count rules per target model
            expectedEmployeeRuleCount = (int) rules.stream()
                    .filter(rule -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> target = (Map<String, Object>) rule.get("target");
                        return target != null &&
                               "appget".equals(target.get("domain")) &&
                               "Employee".equals(target.get("name"));
                    })
                    .count();

            expectedSalaryRuleCount = (int) rules.stream()
                    .filter(rule -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> target = (Map<String, Object>) rule.get("target");
                        return target != null &&
                               "hr".equals(target.get("domain")) &&
                               "Salary".equals(target.get("name"));
                    })
                    .count();

            expectedViewRuleCount = (int) rules.stream()
                    .filter(rule -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> target = (Map<String, Object>) rule.get("target");
                        return target != null &&
                               "view".equals(target.get("type")) &&
                               "EmployeeSalaryView".equals(target.get("name"));
                    })
                    .count();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load specs.yaml", e);
        }
    }

    @Test
    @DisplayName("Should load rules from Employee protobuf descriptor")
    void testLoadEmployeeRules() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(Employee.getDescriptor());
        assertEquals(expectedEmployeeRuleCount, rules.size(),
                "Employee should have " + expectedEmployeeRuleCount + " rules from specs.yaml");
    }

    @Test
    @DisplayName("Should load rules from Salary protobuf descriptor")
    void testLoadSalaryRules() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(Salary.getDescriptor());
        assertEquals(expectedSalaryRuleCount, rules.size(),
                "Salary should have " + expectedSalaryRuleCount + " rule from specs.yaml");
        assertEquals("SalaryAmountCheck", rules.get(0).getName());
    }

    @Test
    @DisplayName("Should load rules from EmployeeSalaryView protobuf descriptor")
    void testLoadViewRules() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(EmployeeSalaryView.getDescriptor());
        assertEquals(expectedViewRuleCount, rules.size(),
                "EmployeeSalaryView should have " + expectedViewRuleCount + " rule from specs.yaml");
        assertEquals("HighEarnerCheck", rules.get(0).getName());
    }

    @Test
    @DisplayName("Should return empty list for model with no rules")
    void testNoRulesForRole() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(
                dev.appget.model.Role.getDescriptor());
        assertTrue(rules.isEmpty(), "Role should have no rules");
    }

    @Test
    @DisplayName("EmployeeAgeCheck rule evaluates correctly via interceptor")
    void testEmployeeAgeCheckEvaluation() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(Employee.getDescriptor());
        Rule ageCheck = rules.stream()
                .filter(r -> r.getName().equals("EmployeeAgeCheck"))
                .findFirst().orElseThrow();

        Employee adult = Employee.newBuilder()
                .setName("Alice").setAge(25).setRoleId("Engineer").build();
        Employee minor = Employee.newBuilder()
                .setName("Bob").setAge(16).setRoleId("Intern").build();

        assertEquals("APPROVED", ageCheck.evaluate(adult));
        assertEquals("REJECTED", ageCheck.evaluate(minor));
    }

    @Test
    @DisplayName("SeniorManagerCheck compound rule evaluates correctly")
    void testCompoundRuleEvaluation() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(Employee.getDescriptor());
        Rule seniorManager = rules.stream()
                .filter(r -> r.getName().equals("SeniorManagerCheck"))
                .findFirst().orElseThrow();

        Employee manager = Employee.newBuilder()
                .setName("Charlie").setAge(40).setRoleId("Manager").build();
        Employee youngManager = Employee.newBuilder()
                .setName("Dave").setAge(25).setRoleId("Manager").build();

        assertEquals("SENIOR_MANAGER", seniorManager.evaluate(manager));
        assertEquals("NOT_SENIOR_MANAGER", seniorManager.evaluate(youngManager));
    }

    @Test
    @DisplayName("AuthenticatedApproval rule with metadata evaluates correctly")
    void testMetadataRuleEvaluation() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(Employee.getDescriptor());
        Rule authRule = rules.stream()
                .filter(r -> r.getName().equals("AuthenticatedApproval"))
                .findFirst().orElseThrow();

        Employee employee = Employee.newBuilder()
                .setName("Eve").setAge(30).setRoleId("Engineer").build();

        // Without metadata -> DENIED
        assertEquals("DENIED", authRule.evaluate(employee));

        // With valid metadata -> APPROVED_WITH_AUTH
        SsoContext sso = SsoContext.builder().authenticated(true).sessionId("s1").build();
        RolesContext roles = RolesContext.builder().roleName("admin").roleLevel(5).build();
        MetadataContext metadata = new MetadataContext().with("sso", sso).with("roles", roles);

        assertEquals("APPROVED_WITH_AUTH", authRule.evaluate(employee, metadata));
    }

    @Test
    @DisplayName("SalaryAmountCheck evaluates against Salary model")
    void testSalaryRuleEvaluation() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(Salary.getDescriptor());
        Rule salaryCheck = rules.get(0);

        Salary high = Salary.newBuilder()
                .setEmployeeId("Alice").setAmount(75000.0).setYearsOfService(5).build();
        Salary low = Salary.newBuilder()
                .setEmployeeId("Bob").setAmount(30000.0).setYearsOfService(1).build();

        assertEquals("PREMIUM", salaryCheck.evaluate(high));
        assertEquals("STANDARD", salaryCheck.evaluate(low));
    }

    @Test
    @DisplayName("HighEarnerCheck evaluates against EmployeeSalaryView")
    void testViewRuleEvaluation() {
        List<Rule> rules = RuleInterceptor.getRulesForMessage(EmployeeSalaryView.getDescriptor());
        Rule highEarner = rules.get(0);

        EmployeeSalaryView high = EmployeeSalaryView.newBuilder()
                .setEmployeeName("Alice").setEmployeeAge(35)
                .setSalaryAmount(150000.0).build();
        EmployeeSalaryView standard = EmployeeSalaryView.newBuilder()
                .setEmployeeName("Bob").setEmployeeAge(25)
                .setSalaryAmount(50000.0).build();

        assertEquals("HIGH_EARNER", highEarner.evaluate(high));
        assertEquals("STANDARD_EARNER", highEarner.evaluate(standard));
    }

    @Test
    @DisplayName("getDomain returns correct domain from message options")
    void testGetDomain() {
        assertEquals("appget", RuleInterceptor.getDomain(Employee.getDescriptor()));
        assertEquals("hr", RuleInterceptor.getDomain(Salary.getDescriptor()));
        assertEquals("appget", RuleInterceptor.getDomain(EmployeeSalaryView.getDescriptor()));
    }

    @Test
    @DisplayName("hasBlockingRules correctly identifies blocking rules")
    void testHasBlockingRules() {
        assertTrue(RuleInterceptor.hasBlockingRules(Employee.getDescriptor()),
                "Employee has blocking rules (EmployeeAgeCheck, AuthenticatedApproval)");
        assertFalse(RuleInterceptor.hasBlockingRules(Salary.getDescriptor()),
                "Salary has no blocking rules");
    }
}
