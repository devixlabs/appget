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

    // ---- Less-than operator (was missing) ----

    @Test
    @DisplayName("follower_count less than operator passes when count is lower")
    void testLessThanPasses() {
        Specification spec = new Specification("follower_count", "<", 200);
        assertTrue(spec.isSatisfiedBy(user), "follower_count 100 < 200 should be true");
    }

    @Test
    @DisplayName("follower_count less than operator fails when count is higher")
    void testLessThanFails() {
        Specification spec = new Specification("follower_count", "<", 50);
        assertFalse(spec.isSatisfiedBy(user), "follower_count 100 < 50 should be false");
    }

    @Test
    @DisplayName("follower_count less than fails at boundary (equal)")
    void testLessThanBoundary() {
        Specification spec = new Specification("follower_count", "<", 100);
        assertFalse(spec.isSatisfiedBy(user), "follower_count 100 < 100 should be false");
    }

    // ---- Not-equals on numbers (was missing) ----

    @Test
    @DisplayName("follower_count != passes for different value")
    void testNumberNotEqualsPasses() {
        Specification spec = new Specification("follower_count", "!=", 50);
        assertTrue(spec.isSatisfiedBy(user), "follower_count 100 != 50 should be true");
    }

    @Test
    @DisplayName("follower_count != fails for equal value")
    void testNumberNotEqualsFails() {
        Specification spec = new Specification("follower_count", "!=", 100);
        assertFalse(spec.isSatisfiedBy(user), "follower_count 100 != 100 should be false");
    }

    // ---- Boolean comparison (was missing entirely) ----

    @Test
    @DisplayName("Boolean == true passes when field is true")
    void testBooleanEqualsTruePasses() {
        Specification spec = new Specification("is_verified", "==", true);
        assertTrue(spec.isSatisfiedBy(user), "is_verified true == true should be true");
    }

    @Test
    @DisplayName("Boolean == true fails when field is false")
    void testBooleanEqualsTrueFails() {
        Users unverified = Users.newBuilder()
                .setUsername("bob")
                .setEmail("bob@test.com")
                .setIsVerified(false)
                .setIsActive(true)
                .build();
        Specification spec = new Specification("is_verified", "==", true);
        assertFalse(spec.isSatisfiedBy(unverified), "is_verified false == true should be false");
    }

    @Test
    @DisplayName("Boolean != passes when values differ")
    void testBooleanNotEqualsPasses() {
        Specification spec = new Specification("is_verified", "!=", false);
        assertTrue(spec.isSatisfiedBy(user), "is_verified true != false should be true");
    }

    @Test
    @DisplayName("Boolean != fails when values match")
    void testBooleanNotEqualsFails() {
        Specification spec = new Specification("is_verified", "!=", true);
        assertFalse(spec.isSatisfiedBy(user), "is_verified true != true should be false");
    }

    @Test
    @DisplayName("Boolean == false passes when field is false")
    void testBooleanEqualsFalsePasses() {
        Users suspended = Users.newBuilder()
                .setUsername("eve")
                .setEmail("eve@test.com")
                .setIsVerified(false)
                .setIsActive(false)
                .build();
        Specification spec = new Specification("is_active", "==", false);
        assertTrue(spec.isSatisfiedBy(suspended), "is_active false == false should be true");
    }

    // ---- Null handling (was missing entirely) ----

    @Test
    @DisplayName("Null actual == null expected returns true")
    void testNullEqualsNull() {
        Specification spec = new Specification("bio", "==", null);
        // Protobuf default for unset string is "" not null, so this tests compareNulls path
        // when expected is null but actual is "" (non-null) — should return false
        assertFalse(spec.isSatisfiedBy(user), "Non-null actual == null expected should be false");
    }

    @Test
    @DisplayName("Null expected with != on non-null actual returns true")
    void testNonNullNotEqualsNull() {
        Specification spec = new Specification("username", "!=", null);
        // actual is "alice" (non-null), expected is null -> compareNulls -> != -> true
        assertTrue(spec.isSatisfiedBy(user), "Non-null actual != null expected should be true");
    }

    // ---- String ordering operators (was missing) ----

    @Test
    @DisplayName("String > comparison (lexicographic)")
    void testStringGreaterThan() {
        Specification spec = new Specification("username", ">", "aaa");
        assertTrue(spec.isSatisfiedBy(user), "'alice' > 'aaa' should be true");
    }

    @Test
    @DisplayName("String < comparison (lexicographic)")
    void testStringLessThan() {
        Specification spec = new Specification("username", "<", "zzz");
        assertTrue(spec.isSatisfiedBy(user), "'alice' < 'zzz' should be true");
    }

    @Test
    @DisplayName("String >= comparison at boundary")
    void testStringGreaterThanOrEqual() {
        Specification spec = new Specification("username", ">=", "alice");
        assertTrue(spec.isSatisfiedBy(user), "'alice' >= 'alice' should be true");
    }

    @Test
    @DisplayName("String <= comparison at boundary")
    void testStringLessThanOrEqual() {
        Specification spec = new Specification("username", "<=", "alice");
        assertTrue(spec.isSatisfiedBy(user), "'alice' <= 'alice' should be true");
    }

    // ---- BigDecimal comparison (was missing entirely) ----

    @Test
    @DisplayName("BigDecimal comparison via Specification with numeric expected value")
    void testBigDecimalComparison() {
        // BigDecimal path is exercised when actual or expected is BigDecimal
        // Using direct compare method via Specification against a protobuf with decimal field
        // would require a Decimal protobuf message. Test the path indirectly:
        // Specification with BigDecimal value against a Number field should promote to BigDecimal
        Specification spec = new Specification("follower_count", ">", new java.math.BigDecimal("50"));
        assertTrue(spec.isSatisfiedBy(user), "follower_count 100 > BigDecimal(50) should be true");
    }

    @Test
    @DisplayName("BigDecimal equality comparison preserves precision")
    void testBigDecimalEquality() {
        Specification spec = new Specification("follower_count", "==", new java.math.BigDecimal("100"));
        assertTrue(spec.isSatisfiedBy(user), "follower_count 100 == BigDecimal(100) should be true");
    }

    // ---- "equals" alias for "==" (was missing) ----

    @Test
    @DisplayName("'equals' alias works the same as '=='")
    void testEqualsAlias() {
        Specification spec = new Specification("follower_count", "equals", 100);
        assertTrue(spec.isSatisfiedBy(user), "'equals' should work like '=='");
    }

    @Test
    @DisplayName("'equals' alias works for boolean comparison")
    void testEqualsAliasBooleans() {
        Specification spec = new Specification("is_verified", "equals", true);
        assertTrue(spec.isSatisfiedBy(user), "'equals' should work for booleans");
    }

    @Test
    @DisplayName("'equals' alias works for string comparison")
    void testEqualsAliasStrings() {
        Specification spec = new Specification("username", "equals", "alice");
        assertTrue(spec.isSatisfiedBy(user), "'equals' should work for strings");
    }
}
