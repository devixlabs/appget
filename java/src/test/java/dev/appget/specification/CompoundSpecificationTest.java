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
                .setIsSuspended(false)
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
}
