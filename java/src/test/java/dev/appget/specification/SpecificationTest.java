package dev.appget.specification;

import dev.appget.auth.model.Users;
import dev.appget.admin.model.Roles;
import dev.appget.social.view.PostDetailView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Specification Pattern Tests")
class SpecificationTest {

    private Users user;

    @BeforeEach
    void setUp() {
        user = Users.newBuilder()
                .setUsername("alice")
                .setEmail("alice@example.com")
                .setIsVerified(true)
                .setIsActive(true)
                .setFollowerCount(100)
                .build();
    }

    @Test
    @DisplayName("follower_count greater than operator should evaluate correctly")
    void testAgeGreaterThan() {
        Specification spec = new Specification("follower_count", ">", 50);
        assertTrue(spec.isSatisfiedBy(user), "User follower_count 100 > 50 should be true");
    }

    @Test
    @DisplayName("follower_count greater than operator should fail when count is less")
    void testAgeGreaterThanFails() {
        Specification spec = new Specification("follower_count", ">", 200);
        assertFalse(spec.isSatisfiedBy(user), "User follower_count 100 > 200 should be false");
    }

    @Test
    @DisplayName("follower_count greater than or equal operator")
    void testAgeGreaterThanOrEqual() {
        Specification spec = new Specification("follower_count", ">=", 100);
        assertTrue(spec.isSatisfiedBy(user), "User follower_count 100 >= 100 should be true");
    }

    @Test
    @DisplayName("follower_count less than or equal operator")
    void testAgeLessThanOrEqual() {
        Specification spec = new Specification("follower_count", "<=", 200);
        assertTrue(spec.isSatisfiedBy(user), "User follower_count 100 <= 200 should be true");
    }

    @Test
    @DisplayName("follower_count equals operator")
    void testAgeEquals() {
        Specification spec = new Specification("follower_count", "==", 100);
        assertTrue(spec.isSatisfiedBy(user), "User follower_count 100 == 100 should be true");
    }

    @Test
    @DisplayName("follower_count equals operator should fail for different count")
    void testAgeEqualsFails() {
        Specification spec = new Specification("follower_count", "==", 50);
        assertFalse(spec.isSatisfiedBy(user), "User follower_count 100 == 50 should be false");
    }

    @Test
    @DisplayName("username equals operator for string field")
    void testRoleEquals() {
        Specification spec = new Specification("username", "==", "alice");
        assertTrue(spec.isSatisfiedBy(user), "Username 'alice' == 'alice' should be true");
    }

    @Test
    @DisplayName("username equals operator should fail for different username")
    void testRoleEqualsFails() {
        Specification spec = new Specification("username", "==", "bob");
        assertFalse(spec.isSatisfiedBy(user), "Username 'alice' == 'bob' should be false");
    }

    @Test
    @DisplayName("username not equals operator")
    void testRoleNotEquals() {
        Specification spec = new Specification("username", "!=", "bob");
        assertTrue(spec.isSatisfiedBy(user), "Username 'alice' != 'bob' should be true");
    }

    @Test
    @DisplayName("username not equals operator should fail when usernames match")
    void testRoleNotEqualsFails() {
        Specification spec = new Specification("username", "!=", "alice");
        assertFalse(spec.isSatisfiedBy(user), "Username 'alice' != 'alice' should be false");
    }

    @Test
    @DisplayName("Invalid field should return false")
    void testInvalidField() {
        Specification spec = new Specification("invalidField", ">", 25);
        assertFalse(spec.isSatisfiedBy(user), "Invalid field should return false");
    }

    @Test
    @DisplayName("Invalid operator should return false")
    void testInvalidOperator() {
        Specification spec = new Specification("follower_count", ">>", 25);
        assertFalse(spec.isSatisfiedBy(user), "Invalid operator should return false");
    }

    @Test
    @DisplayName("Specification toString should be informative")
    void testToString() {
        Specification spec = new Specification("follower_count", ">", 50);
        String result = spec.toString();
        assertTrue(result.contains("follower_count"), "toString should contain field name");
        assertTrue(result.contains(">"), "toString should contain operator");
        assertTrue(result.contains("50"), "toString should contain value");
    }

    @Test
    @DisplayName("Multiple specifications with same user")
    void testMultipleSpecifications() {
        Specification countSpec = new Specification("follower_count", ">", 0);
        Specification usernameSpec = new Specification("username", "==", "alice");

        assertTrue(countSpec.isSatisfiedBy(user), "follower_count check should pass");
        assertTrue(usernameSpec.isSatisfiedBy(user), "Username check should pass");
    }

    @Test
    @DisplayName("Edge case: minimum follower_count boundary")
    void testMinimumAgeBoundary() {
        Users newUser = Users.newBuilder()
                .setUsername("bob")
                .setEmail("bob@example.com")
                .setIsVerified(false)
                .setIsActive(true)
                .setFollowerCount(0)
                .build();

        Specification spec = new Specification("follower_count", ">=", 0);
        assertTrue(spec.isSatisfiedBy(newUser), "follower_count 0 >= 0 should be true");
    }

    @Test
    @DisplayName("Edge case: large follower_count value")
    void testLargeAgeValue() {
        Users popularUser = Users.newBuilder()
                .setUsername("charlie")
                .setEmail("charlie@example.com")
                .setIsVerified(true)
                .setIsActive(true)
                .setFollowerCount(1000000)
                .build();

        Specification spec = new Specification("follower_count", ">=", 500000);
        assertTrue(spec.isSatisfiedBy(popularUser), "follower_count 1000000 >= 500000 should be true");
    }

    // Tests for non-Users models

    @Test
    @DisplayName("Roles permission_level integer comparison")
    void testPermissionLevelComparison() {
        Roles role = Roles.newBuilder()
                .setRoleName("Admin")
                .setPermissionLevel(8)
                .build();

        Specification spec = new Specification("permission_level", ">", 5);
        assertTrue(spec.isSatisfiedBy(role), "permission_level 8 > 5 should be true");
    }

    @Test
    @DisplayName("Roles permission_level boundary comparison")
    void testPermissionLevelBoundary() {
        Roles role = Roles.newBuilder()
                .setRoleName("SuperAdmin")
                .setPermissionLevel(10)
                .build();

        Specification spec = new Specification("permission_level", ">=", 10);
        assertTrue(spec.isSatisfiedBy(role), "permission_level 10 >= 10 should be true");
    }

    @Test
    @DisplayName("PostDetailView author_username string field comparison")
    void testInvoiceStringFieldComparison() {
        PostDetailView view = PostDetailView.newBuilder()
                .setPostContent("Hello world")
                .setAuthorUsername("alice")
                .setAuthorVerified(true)
                .setIsPublic(true)
                .build();

        Specification spec = new Specification("author_username", "==", "alice");
        assertTrue(spec.isSatisfiedBy(view), "author_username should match");
    }

    @Test
    @DisplayName("Roles integer field comparison (permission_level)")
    void testRolesIntegerField() {
        Roles role = Roles.newBuilder()
                .setRoleName("Moderator")
                .setPermissionLevel(10)
                .build();

        Specification spec = new Specification("permission_level", ">", 7);
        assertTrue(spec.isSatisfiedBy(role), "permission_level 10 > 7 should be true");
    }

    @Test
    @DisplayName("username field comparison on Users (string via descriptor)")
    void testNameFieldComparison() {
        Specification spec = new Specification("username", "==", "alice");
        assertTrue(spec.isSatisfiedBy(user), "Username 'alice' == 'alice' should be true");
    }
}
