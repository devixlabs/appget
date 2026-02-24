package dev.appget.service;

import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("gRPC Service Stub Tests")
class GrpcServiceTest {

    @Test
    @DisplayName("UsersServiceGrpc stub class exists and has service descriptor")
    void testEmployeeServiceGrpcExists() {
        assertNotNull(dev.appget.auth.service.UsersServiceGrpc.getServiceDescriptor(),
                "UsersServiceGrpc should have a service descriptor");
        assertEquals("auth_services.UsersService",
                dev.appget.auth.service.UsersServiceGrpc.getServiceDescriptor().getName());
    }

    @Test
    @DisplayName("UsersServiceGrpc has all CRUD method descriptors")
    void testEmployeeServiceMethods() {
        var desc = dev.appget.auth.service.UsersServiceGrpc.getServiceDescriptor();
        Set<String> methodNames = desc.getMethods().stream()
                .map(m -> ((MethodDescriptor<?, ?>) m).getBareMethodName())
                .collect(Collectors.toSet());

        assertTrue(methodNames.contains("CreateUsers"), "Should have CreateUsers");
        assertTrue(methodNames.contains("GetUsers"), "Should have GetUsers");
        assertTrue(methodNames.contains("UpdateUsers"), "Should have UpdateUsers");
        assertTrue(methodNames.contains("DeleteUsers"), "Should have DeleteUsers");
        assertTrue(methodNames.contains("ListUsers"), "Should have ListUsers");
    }

    @Test
    @DisplayName("SessionsServiceGrpc stub class exists")
    void testRoleServiceGrpcExists() {
        assertNotNull(dev.appget.auth.service.SessionsServiceGrpc.getServiceDescriptor(),
                "SessionsServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("social PostsServiceGrpc stub exists")
    void testSalaryServiceGrpcExists() {
        assertNotNull(dev.appget.social.service.PostsServiceGrpc.getServiceDescriptor(),
                "PostsServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("social CommentsServiceGrpc stub exists")
    void testDepartmentServiceGrpcExists() {
        assertNotNull(dev.appget.social.service.CommentsServiceGrpc.getServiceDescriptor(),
                "CommentsServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("admin ModerationActionsServiceGrpc stub exists")
    void testModerationActionsServiceGrpcExists() {
        assertNotNull(dev.appget.admin.service.ModerationActionsServiceGrpc.getServiceDescriptor(),
                "ModerationActionsServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("UsersServiceGrpc has 5 CRUD methods")
    void testEmployeeServiceMethodCount() {
        var desc = dev.appget.auth.service.UsersServiceGrpc.getServiceDescriptor();
        assertEquals(5, desc.getMethods().size(), "UsersService should have 5 CRUD methods");
    }
}
