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
    @DisplayName("EmployeesServiceGrpc stub class exists and has service descriptor")
    void testEmployeeServiceGrpcExists() {
        assertNotNull(EmployeesServiceGrpc.getServiceDescriptor(),
                "EmployeesServiceGrpc should have a service descriptor");
        assertEquals("appget_services.EmployeesService",
                EmployeesServiceGrpc.getServiceDescriptor().getName());
    }

    @Test
    @DisplayName("EmployeesServiceGrpc has all CRUD method descriptors")
    void testEmployeeServiceMethods() {
        var desc = EmployeesServiceGrpc.getServiceDescriptor();
        Set<String> methodNames = desc.getMethods().stream()
                .map(m -> ((MethodDescriptor<?, ?>) m).getBareMethodName())
                .collect(Collectors.toSet());

        assertTrue(methodNames.contains("CreateEmployees"), "Should have CreateEmployees");
        assertTrue(methodNames.contains("GetEmployees"), "Should have GetEmployees");
        assertTrue(methodNames.contains("UpdateEmployees"), "Should have UpdateEmployees");
        assertTrue(methodNames.contains("DeleteEmployees"), "Should have DeleteEmployees");
        assertTrue(methodNames.contains("ListEmployees"), "Should have ListEmployees");
    }

    @Test
    @DisplayName("RolesServiceGrpc stub class exists")
    void testRoleServiceGrpcExists() {
        assertNotNull(RolesServiceGrpc.getServiceDescriptor(),
                "RolesServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("HR SalariesServiceGrpc stub exists")
    void testSalaryServiceGrpcExists() {
        assertNotNull(dev.appget.hr.service.SalariesServiceGrpc.getServiceDescriptor(),
                "SalariesServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("HR DepartmentsServiceGrpc stub exists")
    void testDepartmentServiceGrpcExists() {
        assertNotNull(dev.appget.hr.service.DepartmentsServiceGrpc.getServiceDescriptor(),
                "DepartmentsServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("Finance InvoicesServiceGrpc stub exists")
    void testInvoiceServiceGrpcExists() {
        assertNotNull(dev.appget.finance.service.InvoicesServiceGrpc.getServiceDescriptor(),
                "InvoicesServiceGrpc should have a service descriptor");
    }

    @Test
    @DisplayName("EmployeesServiceGrpc has 5 CRUD methods")
    void testEmployeeServiceMethodCount() {
        var desc = EmployeesServiceGrpc.getServiceDescriptor();
        assertEquals(5, desc.getMethods().size(), "EmployeesService should have 5 CRUD methods");
    }
}
