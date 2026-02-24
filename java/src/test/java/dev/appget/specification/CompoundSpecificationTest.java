package dev.appget.specification;

import dev.appget.model.Employees;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Compound Specification Tests")
class CompoundSpecificationTest {

    private Employees employee;

    @BeforeEach
    void setUp() {
        employee = Employees.newBuilder()
                .setName("Alice")
                .setAge(35)
                .setRoleId("Manager")
                .build();
    }

    @Test
    @DisplayName("AND compound: all conditions true should return true")
    void testAndAllTrue() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("age", ">", 30),
                        new Specification("role_id", "==", "Manager")
                )
        );
        assertTrue(spec.isSatisfiedBy(employee));
    }

    @Test
    @DisplayName("AND compound: one condition false should return false")
    void testAndOneFalse() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("age", ">", 40),
                        new Specification("role_id", "==", "Manager")
                )
        );
        assertFalse(spec.isSatisfiedBy(employee));
    }

    @Test
    @DisplayName("OR compound: one condition true should return true")
    void testOrOneTrue() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR,
                List.of(
                        new Specification("age", ">", 40),
                        new Specification("role_id", "==", "Manager")
                )
        );
        assertTrue(spec.isSatisfiedBy(employee));
    }

    @Test
    @DisplayName("OR compound: all conditions false should return false")
    void testOrAllFalse() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR,
                List.of(
                        new Specification("age", ">", 40),
                        new Specification("role_id", "==", "Engineer")
                )
        );
        assertFalse(spec.isSatisfiedBy(employee));
    }

    @Test
    @DisplayName("AND compound: single condition behaves like simple spec")
    void testAndSingleCondition() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(new Specification("age", ">", 30))
        );
        assertTrue(spec.isSatisfiedBy(employee));
    }

    @Test
    @DisplayName("Compound specification toString should contain logic type")
    void testToString() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(new Specification("age", ">", 30))
        );
        String str = spec.toString();
        assertTrue(str.contains("AND"));
    }
}
