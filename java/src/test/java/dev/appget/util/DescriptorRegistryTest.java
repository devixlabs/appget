package dev.appget.util;

import com.google.protobuf.Descriptors;
import dev.appget.codegen.JavaUtils;
import org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Descriptor Registry Tests")
class DescriptorRegistryTest {

    private DescriptorRegistry registry;
    private Set<String> expectedModelViewNames;
    private int expectedTotalCount;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws IOException {
        registry = new DescriptorRegistry();

        // Read models.yaml to build expected model/view names
        expectedModelViewNames = new HashSet<>();
        expectedTotalCount = 0;

        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File("models.yaml"))) {
            data = yaml.load(in);
        }

        Map<String, Object> domains = (Map<String, Object>) data.get("domains");
        if (domains != null) {
            for (Map.Entry<String, Object> domainEntry : domains.entrySet()) {
                Map<String, Object> domainConfig = (Map<String, Object>) domainEntry.getValue();

                // Collect models
                List<Map<String, Object>> models = (List<Map<String, Object>>) domainConfig.get("models");
                if (models != null) {
                    for (Map<String, Object> model : models) {
                        String name = (String) model.get("name");
                        expectedModelViewNames.add(name);
                        expectedTotalCount++;
                    }
                }

                // Collect views
                List<Map<String, Object>> views = (List<Map<String, Object>>) domainConfig.get("views");
                if (views != null) {
                    for (Map<String, Object> view : views) {
                        String name = (String) view.get("name");
                        expectedModelViewNames.add(name);
                        expectedTotalCount++;
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Registry should contain all models and views from models.yaml")
    void testRegistrySize() {
        assertEquals(expectedTotalCount, registry.getAllModelNames().size(),
                "Registry should have " + expectedTotalCount + " entries from models.yaml");
    }

    @Test
    @DisplayName("Registry should find Users descriptor")
    void testFindEmployee() {
        Descriptors.Descriptor desc = registry.getDescriptorByName("users");
        assertNotNull(desc, "Users descriptor should be registered");
        assertEquals("Users", desc.getName());
    }

    @Test
    @DisplayName("Registry should find ModerationFlags descriptor")
    void testFindSalary() {
        Descriptors.Descriptor desc = registry.getDescriptorByName("moderation_flags");
        assertNotNull(desc, "ModerationFlags descriptor should be registered");
        assertEquals("ModerationFlags", desc.getName());
    }

    @Test
    @DisplayName("Registry should find all expected models and views")
    void testFindAllExpectedModelsAndViews() {
        for (String name : expectedModelViewNames) {
            assertNotNull(registry.getDescriptorByName(name),
                    "Registry should have descriptor for: " + name);
        }
    }

    @Test
    @DisplayName("All descriptors should have fields")
    void testAllDescriptorsHaveFields() {
        for (String name : expectedModelViewNames) {
            Descriptors.Descriptor desc = registry.getDescriptorByName(name);
            assertNotNull(desc, "Descriptor should exist for: " + name);
            assertTrue(desc.getFields().size() > 0,
                    "Descriptor for " + name + " should have at least one field");
        }
    }

    @Test
    @DisplayName("Descriptor names should match PascalCase of registry keys")
    void testDescriptorNamesMatchKeys() {
        for (String name : expectedModelViewNames) {
            Descriptors.Descriptor desc = registry.getDescriptorByName(name);
            assertNotNull(desc, "Descriptor should exist for key: " + name);
            assertEquals(JavaUtils.snakeToPascal(name), desc.getName(),
                    "Descriptor name should be PascalCase of registry key: " + name);
        }
    }

    @Test
    @DisplayName("Registry should return null for unknown model")
    void testUnknownModel() {
        assertNull(registry.getDescriptorByName("NonExistent"));
    }

    @Test
    @DisplayName("Users descriptor should have expected fields")
    void testEmployeeFields() {
        Descriptors.Descriptor desc = registry.getDescriptorByName("users");
        assertNotNull(desc, "Users descriptor should exist");
        assertNotNull(desc.findFieldByName("username"), "Users should have username field");
        assertNotNull(desc.findFieldByName("email"), "Users should have email field");
        assertNotNull(desc.findFieldByName("is_verified"), "Users should have is_verified field");
    }

    @Test
    @DisplayName("PostDetailView descriptor should have expected fields")
    void testViewFields() {
        Descriptors.Descriptor desc = registry.getDescriptorByName("post_detail_view");
        assertNotNull(desc, "PostDetailView descriptor should exist");
        assertNotNull(desc.findFieldByName("post_content"));
        assertNotNull(desc.findFieldByName("author_username"));
    }
}
