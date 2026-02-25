package dev.appget.util;

import com.google.protobuf.Descriptors;
import com.google.protobuf.MessageOrBuilder;
import dev.appget.auth.model.Users;
import dev.appget.admin.model.ModerationActions;
import dev.appget.admin.model.Roles;
import dev.appget.social.view.PostDetailView;
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
    @DisplayName("Should build Users with default values")
    void testBuildEmployee() {
        MessageOrBuilder user = builder.buildSampleMessage(Users.getDescriptor());
        assertNotNull(user);

        Descriptors.Descriptor desc = Users.getDescriptor();
        assertEquals("Sample_username", user.getField(desc.findFieldByName("username")));
        assertEquals(42, user.getField(desc.findFieldByName("follower_count")));
        assertEquals("Sample_email", user.getField(desc.findFieldByName("email")));
    }

    @Test
    @DisplayName("Should build ModerationActions with correct types")
    void testBuildModerationActions() {
        MessageOrBuilder action = builder.buildSampleMessage(ModerationActions.getDescriptor());
        assertNotNull(action);

        Descriptors.Descriptor desc = ModerationActions.getDescriptor();
        assertEquals("Sample_action_type", action.getField(desc.findFieldByName("action_type")));
        // is_active is bool
        assertEquals(true, action.getField(desc.findFieldByName("is_active")));
    }

    @Test
    @DisplayName("Should build PostDetailView with all fields")
    void testBuildView() {
        MessageOrBuilder view = builder.buildSampleMessage(PostDetailView.getDescriptor());
        assertNotNull(view);

        Descriptors.Descriptor desc = PostDetailView.getDescriptor();
        assertEquals("Sample_post_content", view.getField(desc.findFieldByName("post_content")));
        assertEquals("Sample_author_username", view.getField(desc.findFieldByName("author_username")));
        // like_count is int32
        assertEquals(42, view.getField(desc.findFieldByName("like_count")));
    }

    @Test
    @DisplayName("String fields should have Sample_ prefix")
    void testStringFieldFormat() {
        MessageOrBuilder user = builder.buildSampleMessage(Users.getDescriptor());
        Object username = user.getField(Users.getDescriptor().findFieldByName("username"));
        assertTrue(username.toString().startsWith("Sample_"), "String fields should start with 'Sample_'");
    }

    @Test
    @DisplayName("Integer fields should default to 42")
    void testIntFieldDefault() {
        MessageOrBuilder user = builder.buildSampleMessage(Users.getDescriptor());
        Object count = user.getField(Users.getDescriptor().findFieldByName("follower_count"));
        assertEquals(42, count);
    }

    @Test
    @DisplayName("Roles permission_level field should default to 42")
    void testIntFieldDefaultOnRoles() {
        MessageOrBuilder role = builder.buildSampleMessage(Roles.getDescriptor());
        Object permLevel = role.getField(Roles.getDescriptor().findFieldByName("permission_level"));
        assertNotNull(permLevel, "permission_level field should not be null");
        assertEquals(42, permLevel, "permission_level int field should default to 42");
    }
}
