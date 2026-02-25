package dev.appget.model;

import dev.appget.auth.model.Users;
import dev.appget.admin.model.Roles;
import dev.appget.social.view.PostDetailView;
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

    private Users user;
    private Specification usernameSpecification;
    private Rule rule;

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
    @DisplayName("Rule should evaluate to success status when specification is satisfied")
    void testRuleEvaluatesToSuccessStatus() {
        usernameSpecification = new Specification("follower_count", ">", 100);
        rule = Rule.builder()
                .name("FollowerCheck")
                .spec(usernameSpecification)
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        String result = rule.evaluate(user);
        assertEquals("APPROVED", result, "Users follower_count 200 > 100 should result in APPROVED");
    }

    @Test
    @DisplayName("Rule should evaluate to failure status when specification is not satisfied")
    void testRuleEvaluatesToFailureStatus() {
        usernameSpecification = new Specification("follower_count", ">", 500);
        rule = Rule.builder()
                .name("FollowerCheck")
                .spec(usernameSpecification)
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        String result = rule.evaluate(user);
        assertEquals("REJECTED", result, "Users follower_count 200 > 500 should result in REJECTED");
    }

    @Test
    @DisplayName("Rule with username specification")
    void testRuleWithRoleSpecification() {
        Specification usernameSpec = new Specification("username", "==", "alice");
        rule = Rule.builder()
                .name("UsernameCheck")
                .spec(usernameSpec)
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        String result = rule.evaluate(user);
        assertEquals("APPROVED", result, "Users username 'alice' == 'alice' should result in APPROVED");
    }

    @Test
    @DisplayName("Rule should return correct name")
    void testRuleGetName() {
        usernameSpecification = new Specification("follower_count", ">", 100);
        rule = Rule.builder()
                .name("FollowerCheck")
                .spec(usernameSpecification)
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        assertEquals("FollowerCheck", rule.getName(), "Rule name should match constructor argument");
    }

    @Test
    @DisplayName("Rule with custom success status")
    void testRuleWithCustomSuccessStatus() {
        usernameSpecification = new Specification("follower_count", ">", 100);
        rule = Rule.builder()
                .name("CustomRule")
                .spec(usernameSpecification)
                .successStatus("PASSED")
                .failureStatus("FAILED")
                .build();

        String result = rule.evaluate(user);
        assertEquals("PASSED", result, "Rule should use custom success status");
    }

    @Test
    @DisplayName("Rule with custom failure status")
    void testRuleWithCustomFailureStatus() {
        usernameSpecification = new Specification("follower_count", ">", 500);
        rule = Rule.builder()
                .name("CustomRule")
                .spec(usernameSpecification)
                .successStatus("PASSED")
                .failureStatus("FAILED")
                .build();

        String result = rule.evaluate(user);
        assertEquals("FAILED", result, "Rule should use custom failure status");
    }

    @Test
    @DisplayName("Multiple rules against same user")
    void testMultipleRulesAgainstSameEmployee() {
        Rule followerRule = Rule.builder()
                .name("FollowerCheck")
                .spec(new Specification("follower_count", ">", 100))
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        Rule usernameRule = Rule.builder()
                .name("UsernameCheck")
                .spec(new Specification("username", "==", "alice"))
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        String followerResult = followerRule.evaluate(user);
        String usernameResult = usernameRule.evaluate(user);

        assertEquals("APPROVED", followerResult, "Follower check should pass");
        assertEquals("APPROVED", usernameResult, "Username check should pass");
    }

    @Test
    @DisplayName("Rule evaluation consistency")
    void testRuleEvaluationConsistency() {
        usernameSpecification = new Specification("follower_count", ">", 100);
        rule = Rule.builder()
                .name("ConsistencyCheck")
                .spec(usernameSpecification)
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        String result1 = rule.evaluate(user);
        String result2 = rule.evaluate(user);

        assertEquals(result1, result2, "Multiple evaluations of same rule should give same result");
    }

    @Test
    @DisplayName("Rule with different users")
    void testRuleWithDifferentEmployees() {
        usernameSpecification = new Specification("follower_count", ">", 100);
        rule = Rule.builder()
                .name("FollowerCheck")
                .spec(usernameSpecification)
                .successStatus("APPROVED")
                .failureStatus("REJECTED")
                .build();

        Users newUser = Users.newBuilder()
                .setUsername("bob")
                .setEmail("bob@example.com")
                .setIsVerified(false)
                .setIsActive(true)
                .setFollowerCount(10)
                .build();

        Users popularUser = Users.newBuilder()
                .setUsername("charlie")
                .setEmail("charlie@example.com")
                .setIsVerified(true)
                .setIsActive(true)
                .setFollowerCount(5000)
                .build();

        assertEquals("REJECTED", rule.evaluate(newUser), "New user should be rejected");
        assertEquals("APPROVED", rule.evaluate(popularUser), "Popular user should be approved");
    }

    // Generic + compound + metadata tests

    @Test
    @DisplayName("Rule evaluates Roles model generically")
    void testRuleWithRolesModel() {
        Roles role = Roles.newBuilder()
                .setRoleName("Admin")
                .setPermissionLevel(8)
                .build();

        Specification permissionSpec = new Specification("permission_level", ">", 5);
        Rule roleRule = Rule.builder()
                .name("PermissionCheck")
                .spec(permissionSpec)
                .successStatus("HIGH_PERMISSION")
                .failureStatus("LOW_PERMISSION")
                .build();

        assertEquals("HIGH_PERMISSION", roleRule.evaluate(role));
    }

    @Test
    @DisplayName("Rule with compound specification")
    void testRuleWithCompoundSpecification() {
        Users verifiedUser = Users.newBuilder()
                .setUsername("bob")
                .setEmail("bob@example.com")
                .setIsVerified(true)
                .setIsActive(true)
                .setFollowerCount(500)
                .build();

        CompoundSpecification compound = new CompoundSpecification(
                CompoundSpecification.Logic.AND,
                List.of(
                        new Specification("follower_count", ">=", 100),
                        new Specification("is_verified", "==", true)
                )
        );

        Rule compoundRule = Rule.builder()
                .name("VerifiedInfluencer")
                .spec(compound)
                .successStatus("INFLUENCER")
                .failureStatus("REGULAR")
                .build();
        assertEquals("INFLUENCER", compoundRule.evaluate(verifiedUser));
    }

    @Test
    @DisplayName("Rule with metadata requirements - passes with valid metadata")
    void testRuleWithMetadataPassesWhenValid() {
        Map<String, List<Specification>> metaReqs = Map.of(
                "sso", List.of(new Specification("authenticated", "==", true))
        );

        Rule authRule = Rule.builder()
                .name("AuthRule")
                .spec(new Specification("follower_count", ">=", 0))
                .successStatus("ALLOWED")
                .failureStatus("DENIED")
                .targetType("User")
                .metadataRequirements(metaReqs)
                .build();

        SsoContext sso = SsoContext.builder().authenticated(true).sessionId("s1").build();
        MetadataContext metadata = new MetadataContext().with("sso", sso);

        assertEquals("ALLOWED", authRule.evaluate(user, metadata));
    }

    @Test
    @DisplayName("Rule with metadata requirements - fails without metadata")
    void testRuleWithMetadataFailsWithoutMetadata() {
        Map<String, List<Specification>> metaReqs = Map.of(
                "sso", List.of(new Specification("authenticated", "==", true))
        );

        Rule authRule = Rule.builder()
                .name("AuthRule")
                .spec(new Specification("follower_count", ">=", 0))
                .successStatus("ALLOWED")
                .failureStatus("DENIED")
                .targetType("User")
                .metadataRequirements(metaReqs)
                .build();

        assertEquals("DENIED", authRule.evaluate(user));
    }

    @Test
    @DisplayName("Rule targeting view field must not pass when evaluated against wrong model type")
    void testRuleWithViewFieldFailsOnWrongModelType() {
        // like_count exists on PostDetailView but NOT on Users
        Specification viewFieldSpec = new Specification("post_content", "!=", "");
        Rule viewRule = Rule.builder()
                .name("PostContentCheck")
                .spec(viewFieldSpec)
                .successStatus("HAS_CONTENT")
                .failureStatus("NO_CONTENT")
                .build();

        // Evaluating against Users (which lacks post_content) must return failure status
        String result = viewRule.evaluate(user);
        assertEquals("NO_CONTENT", result,
                "Rule with field not present on target model should return failure status, not throw or pass");
    }

    @Test
    @DisplayName("Rule targeting view field succeeds when evaluated against correct view type")
    void testRuleWithViewFieldSucceedsOnCorrectViewType() {
        PostDetailView view = PostDetailView.newBuilder()
                .setPostContent("Hello world")
                .setAuthorUsername("alice")
                .setAuthorVerified(true)
                .setIsPublic(true)
                .setLikeCount(2000)
                .build();

        Specification viewFieldSpec = new Specification("like_count", ">", 1000);
        Rule viewRule = Rule.builder()
                .name("HighEngagementCheck")
                .spec(viewFieldSpec)
                .successStatus("HIGH_ENGAGEMENT")
                .failureStatus("LOW_ENGAGEMENT")
                .build();

        String result = viewRule.evaluate(view);
        assertEquals("HIGH_ENGAGEMENT", result,
                "Rule should pass when evaluated against the correct view type with matching field");
    }
}
