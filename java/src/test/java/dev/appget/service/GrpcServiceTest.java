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
    @DisplayName("EmployeeServiceGrpc stub class exists and has service descriptor")
    void testEmployeeServiceGrpcExists() {
        assertNotNull(EmployeeServiceGrpc.getServiceDescriptor(),
                "EmployeeServiceGrpc should have a service descriptor");
        assertEquals("appget_services.EmployeeService",
                EmployeeServiceGrpc.getServiceDescriptor().getName());
    }

    @Test
    @DisplayName("EmployeeServiceGrpc has all CRUD method descriptors")
    void testEmployeeServiceMethods() {
        var desc = EmployeeServiceGrpc.getServiceDescriptor();
        Set<String> methodNames = desc.getMethods().stream()
                .map(m -> ((MethodDescriptor<?, ?>) m).getBareMethodName())
                .collect(Collectors.toSet());

        assertTrue(methodNames.contains("CreateEmployee"), "Should have CreateEmployee");
        assertTrue(methodNames.contains("GetEmployee"), "Should have GetEmployee");
        assertTrue(methodNames.contains("UpdateEmployee"), "Should have UpdateEmployee");
        assertTrue(methodNames.contains("DeleteEmployee"), "Should have DeleteEmployee");
        assertTrue(methodNames.contains("ListEmployees"), "Should have ListEmployees");
    }

    @Test
    @DisplayName("RoleServiceGrpc stub class exists")
    void testRoleServiceGrpcExists() {
        assertNotNull(RoleServiceGrpc.getServiceDescriptor(),
                "RoleServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("HR SalaryServiceGrpc stub exists")
    void testSalaryServiceGrpcExists() {
        assertNotNull(dev.appget.hr.service.SalaryServiceGrpc.getServiceDescriptor(),
                "SalaryServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("HR DepartmentServiceGrpc stub exists")
    void testDepartmentServiceGrpcExists() {
        assertNotNull(dev.appget.hr.service.DepartmentServiceGrpc.getServiceDescriptor(),
                "DepartmentServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("Finance InvoiceServiceGrpc stub exists")
    void testInvoiceServiceGrpcExists() {
        assertNotNull(dev.appget.finance.service.InvoiceServiceGrpc.getServiceDescriptor(),
                "InvoiceServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("EmployeeServiceGrpc has 5 CRUD methods")
    void testEmployeeServiceMethodCount() {
        var desc = EmployeeServiceGrpc.getServiceDescriptor();
        assertEquals(5, desc.getMethods().size(), "EmployeeService should have 5 CRUD methods");
    }
}
