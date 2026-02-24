package dev.appget.codegen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Specification Generator Tests")
class SpecificationGeneratorTest {

    private SpecificationGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SpecificationGenerator();
    }

    @Test
    @DisplayName("Specification generator should exist and be instantiable")
    void testSpecificationGeneratorInstantiation() {
        assertNotNull(generator, "SpecificationGenerator should be instantiable");
    }

    @Test
    @DisplayName("SpecificationGenerator generates files from valid specs.yaml")
    void testGenerateSpecificationsFromValidYaml(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            // Load models.yaml for target resolution
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path agePath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "EmployeeAgeCheck.java");
            assertTrue(Files.exists(agePath), "EmployeeAgeCheck.java should be generated");

            String content = Files.readString(agePath);
            assertTrue(content.contains("public class EmployeeAgeCheck"), "Generated file should contain class");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator generates classes with evaluate method")
    void testGeneratedSpecificationHasEvaluateMethod(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path agePath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "EmployeeAgeCheck.java");
            String content = Files.readString(agePath);

            assertTrue(content.contains("public boolean evaluate(Employees target)"), "Should have evaluate method");
            assertTrue(content.contains("public String getResult(Employees target)"), "Should have getResult method");
            assertTrue(content.contains("getSpec()"), "Should have getSpec method");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator uses correct package for generated classes")
    void testGeneratedClassPackage(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path agePath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "EmployeeAgeCheck.java");
            String content = Files.readString(agePath);

            assertTrue(content.contains("package dev.appget.specification.generated"), "Should use correct package");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator includes proper imports")
    void testGeneratedClassImports(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path agePath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "EmployeeAgeCheck.java");
            String content = Files.readString(agePath);

            assertTrue(content.contains("import dev.appget.model.Employees"), "Should import Employees model");
            assertTrue(content.contains("import dev.appget.specification.Specification"), "Should import Specification");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator handles multiple rules")
    void testMultipleRulesGeneration(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path basePath = Paths.get(outputDir, "dev", "appget", "specification", "generated");
            long javaFileCount = Files.walk(basePath)
                    .filter(p -> p.toString().endsWith(".java"))
                    .count();

            assertTrue(javaFileCount >= 6, "Should generate at least 6 specification classes");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator generates comment indicating source")
    void testGeneratedCommentIncludesSource(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path agePath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "EmployeeAgeCheck.java");
            String content = Files.readString(agePath);

            assertTrue(content.contains("Generated from specs.yaml"), "Generated file should indicate source YAML");
            assertTrue(content.contains("DO NOT EDIT MANUALLY"), "Generated file should have warning");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator creates correct directory structure")
    void testDirectoryStructure(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path packagePath = Paths.get(outputDir, "dev", "appget", "specification", "generated");
            assertTrue(Files.exists(packagePath) && Files.isDirectory(packagePath),
                    "Should create proper package directory structure");

            // Metadata context directory should also exist
            Path contextPath = Paths.get(outputDir, "dev", "appget", "specification", "context");
            assertTrue(Files.exists(contextPath) && Files.isDirectory(contextPath),
                    "Should create metadata context directory");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator handles string values in rules")
    void testStringValueHandling(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path rolePath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "EmployeeRoleCheck.java");
            String content = Files.readString(rolePath);

            assertTrue(content.contains("\"Manager\"") || content.contains("Manager"),
                    "Should handle string values correctly");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator generates compound specification classes")
    void testCompoundSpecGeneration(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path seniorPath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "SeniorManagerCheck.java");
            assertTrue(Files.exists(seniorPath), "SeniorManagerCheck.java should be generated");

            String content = Files.readString(seniorPath);
            assertTrue(content.contains("CompoundSpecification"), "Should use CompoundSpecification for AND rule");
            assertTrue(content.contains("CompoundSpecification.Logic.AND"), "Should use AND logic");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator generates metadata-required specification classes")
    void testMetadataSpecGeneration(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path authPath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "AuthenticatedApproval.java");
            assertTrue(Files.exists(authPath), "AuthenticatedApproval.java should be generated");

            String content = Files.readString(authPath);
            assertTrue(content.contains("MetadataContext"), "Should import MetadataContext");
            assertTrue(content.contains("metadata.get(\"sso\")"), "Should check sso metadata");
            assertTrue(content.contains("metadata.get(\"roles\")"), "Should check roles metadata");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator generates metadata context POJOs")
    void testMetadataPojoGeneration(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path ssoPath = Paths.get(outputDir, "dev", "appget", "specification", "context", "SsoContext.java");
            assertTrue(Files.exists(ssoPath), "SsoContext.java should be generated");

            String content = Files.readString(ssoPath);
            assertTrue(content.contains("boolean authenticated"), "SsoContext should have authenticated field");
            assertTrue(content.contains("String sessionId"), "SsoContext should have sessionId field");
            assertTrue(content.contains("@Data"), "Should have Lombok annotations");
        }
    }

    @Test
    @DisplayName("SpecificationGenerator generates view-targeting specification")
    void testViewTargetSpecGeneration(@TempDir Path tempDir) throws Exception {
        String outputDir = tempDir.toString();
        String yamlFile = "specs.yaml";

        File testYaml = new File(yamlFile);
        if (testYaml.exists()) {
            if (new File("models.yaml").exists()) {
                generator.loadModelsYaml("models.yaml");
            }
            generator.generateSpecifications(yamlFile, outputDir);

            Path earnerPath = Paths.get(outputDir, "dev", "appget", "specification", "generated", "HighEarnerCheck.java");
            assertTrue(Files.exists(earnerPath), "HighEarnerCheck.java should be generated");

            String content = Files.readString(earnerPath);
            assertTrue(content.contains("import dev.appget.view.EmployeeSalaryView"), "Should import view class");
            assertTrue(content.contains("EmployeeSalaryView target"), "Should use view type as parameter");
        }
    }
}
