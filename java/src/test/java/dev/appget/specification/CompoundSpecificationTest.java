package dev.appget.specification;

import dev.appget.auth.model.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Compound Specification Tests")
class CompoundSpecificationTest {

    private Users user;

    @BeforeEach
    void setUp() {
        user = Users.newBuilder()
                .setUsername("alice")
                .setEmail("alice@example.com")
                .setIsVerified(true)
                .setIsActive(true)
                .setFollowerCount(200)
                .build();
    }

    @Test
    @DisplayName("AND compound: all conditions true should return true")
    void testAndAllTrue() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("follower_count", ">", 100),
                        new Specification("username", "==", "alice")
                )
        );
        assertTrue(spec.isSatisfiedBy(user));
    }

    @Test
    @DisplayName("AND compound: one condition false should return false")
    void testAndOneFalse() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("follower_count", ">", 500),
                        new Specification("username", "==", "alice")
                )
        );
        assertFalse(spec.isSatisfiedBy(user));
    }

    @Test
    @DisplayName("OR compound: one condition true should return true")
    void testOrOneTrue() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR,
                List.of(
                        new Specification("follower_count", ">", 500),
                        new Specification("username", "==", "alice")
                )
        );
        assertTrue(spec.isSatisfiedBy(user));
    }

    @Test
    @DisplayName("OR compound: all conditions false should return false")
    void testOrAllFalse() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR,
                List.of(
                        new Specification("follower_count", ">", 500),
                        new Specification("username", "==", "bob")
                )
        );
        assertFalse(spec.isSatisfiedBy(user));
    }

    @Test
    @DisplayName("AND compound: single condition behaves like simple spec")
    void testAndSingleCondition() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(new Specification("follower_count", ">", 100))
        );
        assertTrue(spec.isSatisfiedBy(user));
    }

    @Test
    @DisplayName("Compound specification toString should contain logic type")
    void testToString() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(new Specification("follower_count", ">", 100))
        );
        String str = spec.toString();
        assertTrue(str.contains("AND"));
    }

    // ---- OR single condition (was missing) ----

    @Test
    @DisplayName("OR compound: single condition behaves like simple spec")
    void testOrSingleCondition() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR,
                List.of(new Specification("follower_count", ">", 100))
        );
        assertTrue(spec.isSatisfiedBy(user));
    }

    // ---- Empty list edge cases ----

    @Test
    @DisplayName("AND with empty list returns true (vacuous truth)")
    void testAndEmptyList() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND, List.of()
        );
        assertTrue(spec.isSatisfiedBy(user), "AND with no conditions is vacuously true");
    }

    @Test
    @DisplayName("OR with empty list returns false (no condition satisfied)")
    void testOrEmptyList() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR, List.of()
        );
        assertFalse(spec.isSatisfiedBy(user), "OR with no conditions has nothing to satisfy");
    }

    // ---- 3+ conditions ----

    @Test
    @DisplayName("AND with three conditions, all true")
    void testAndThreeConditionsAllTrue() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("follower_count", ">", 100),
                        new Specification("username", "==", "alice"),
                        new Specification("is_verified", "==", true)
                )
        );
        assertTrue(spec.isSatisfiedBy(user));
    }

    @Test
    @DisplayName("AND with three conditions, last false")
    void testAndThreeConditionsLastFalse() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("follower_count", ">", 100),
                        new Specification("username", "==", "alice"),
                        new Specification("is_verified", "==", false)
                )
        );
        assertFalse(spec.isSatisfiedBy(user));
    }

    @Test
    @DisplayName("OR with three conditions, only middle true")
    void testOrThreeConditionsMiddleTrue() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR,
                List.of(
                        new Specification("follower_count", ">", 500),
                        new Specification("username", "==", "alice"),
                        new Specification("is_verified", "==", false)
                )
        );
        assertTrue(spec.isSatisfiedBy(user));
    }

    // ---- Mixed field types in compound ----

    @Test
    @DisplayName("AND compound with integer + boolean + string conditions")
    void testAndMixedFieldTypes() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("follower_count", ">=", 200),
                        new Specification("is_active", "==", true),
                        new Specification("email", "!=", "")
                )
        );
        assertTrue(spec.isSatisfiedBy(user));
    }

    // ---- Accessor tests ----

    @Test
    @DisplayName("getLogic() returns correct logic type")
    void testGetLogic() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR, List.of(new Specification("follower_count", ">", 0))
        );
        assertEquals(CompoundSpecification.Logic.OR, spec.getLogic());
    }

    @Test
    @DisplayName("getSpecifications() returns the specification list")
    void testGetSpecifications() {
        List<Specification> specs = List.of(
                new Specification("follower_count", ">", 0),
                new Specification("username", "==", "alice")
        );
        CompoundSpecification compound = new CompoundSpecification(CompoundSpecification.Logic.AND, specs);
        assertEquals(2, compound.getSpecifications().size());
    }

    // ---- AND: all false ----

    @Test
    @DisplayName("AND compound: all conditions false should return false")
    void testAndAllFalse() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("follower_count", ">", 500),
                        new Specification("username", "==", "bob")
                )
        );
        assertFalse(spec.isSatisfiedBy(user));
    }

    // ---- OR: all true ----

    @Test
    @DisplayName("OR compound: all conditions true should return true")
    void testOrAllTrue() {
        CompoundSpecification spec = new CompoundSpecification(
                CompoundSpecification.Logic.OR,
                List.of(
                        new Specification("follower_count", ">", 100),
                        new Specification("username", "==", "alice")
                )
        );
        assertTrue(spec.isSatisfiedBy(user));
    }
}
