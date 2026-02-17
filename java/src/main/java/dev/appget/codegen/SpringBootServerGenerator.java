package dev.appget.codegen;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Generates a production-ready Spring Boot REST API server from models and specifications.
 *
 * Features:
 * - REST endpoints (CRUD) for all generated models
 * - In-memory repositories with ConcurrentHashMap
 * - Rule Engine integration for business logic validation
 * - Metadata-aware authorization (from specs.yaml)
 * - Global exception handling
 * - Server configuration (application.yaml)
 *
 * Usage: java -cp <classpath> dev.appget.codegen.SpringBootServerGenerator <models.yaml> <specs.yaml> <output-dir>
 */
public class SpringBootServerGenerator {

    private static final Logger logger = LogManager.getLogger(SpringBootServerGenerator.class);
    private static final String BASE_PACKAGE = "dev.appget.server";
    private Map<String, ModelInfo> modelIndex = new HashMap<>();
    private List<RuleInfo> rules = new ArrayList<>();
    private Set<String> metadataCategories = new LinkedHashSet<>();
    private Map<String, List<Map<String, Object>>> metadataFieldDefinitions = new LinkedHashMap<>();

    public static void main(String[] args) {
        logger.debug("Entering main method with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid argument count. Usage: SpringBootServerGenerator <models.yaml> <specs.yaml> [output-dir]");
            System.err.println("Usage: SpringBootServerGenerator <models.yaml> <specs.yaml> [output-dir]");
            System.exit(1);
        }

        String modelsPath = args[0];
        String specsPath = args[1];
        String outputDir = args.length > 2 ? args[2] : "generated-server";

        logger.info("Starting SpringBootServerGenerator with modelsPath={}, specsPath={}, outputDir={}", modelsPath, specsPath, outputDir);

        try {
            new SpringBootServerGenerator().generateServer(modelsPath, specsPath, outputDir);
            logger.info("Successfully generated Spring Boot server to: {}", outputDir);
            System.out.println("✓ Successfully generated Spring Boot server to: " + outputDir);
        } catch (Exception e) {
            logger.error("Failed to generate server", e);
            System.err.println("✗ Failed to generate server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        logger.debug("Exiting main method");
    }

    @SuppressWarnings("unchecked")
    public void generateServer(String modelsPath, String specsPath, String outputDir) throws IOException {
        // Load models
        loadModels(modelsPath);

        // Load rules and metadata
        loadRules(specsPath);

        // Generate infrastructure
        generateBuildGradle(outputDir);
        generateApplicationClass(outputDir);
        generateApplicationYaml(outputDir);
        generateLog4j2Properties(outputDir);
        generateMetadataExtractor(outputDir);
        generateRuleService(outputDir);
        generateDTOs(outputDir);
        generateExceptionClasses(outputDir);
        generateGlobalExceptionHandler(outputDir);

        // Generate per-model components
        for (ModelInfo model : modelIndex.values()) {
            generateRepository(model, outputDir);
            generateService(model, outputDir);
            generateController(model, outputDir);
        }

        System.out.println("  Generated: build.gradle");
        System.out.println("  Generated: Application.java");
        System.out.println("  Generated: application.yaml");
        System.out.println("  Generated: log4j2.properties (src/main/resources/)");
        System.out.println("  Generated: " + modelIndex.size() + " model endpoints (Controller/Service/Repository)");
    }

    @SuppressWarnings("unchecked")
    private void loadModels(String yamlPath) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File(yamlPath))) {
            data = yaml.load(in);
        }

        Map<String, Object> domains = (Map<String, Object>) data.get("domains");
        if (domains == null) {
            System.out.println("No domains found in " + yamlPath);
            return;
        }

        for (Map.Entry<String, Object> domainEntry : domains.entrySet()) {
            String domainName = domainEntry.getKey();
            Map<String, Object> domainConfig = (Map<String, Object>) domainEntry.getValue();

            String namespace = (String) domainConfig.get("namespace");
            List<Map<String, Object>> models = (List<Map<String, Object>>) domainConfig.get("models");

            if (models != null) {
                for (Map<String, Object> model : models) {
                    String modelName = (String) model.get("name");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");

                    ModelInfo info = new ModelInfo();
                    info.name = modelName;
                    info.domain = domainName;
                    info.namespace = namespace;
                    info.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();

                    modelIndex.put(modelName, info);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadRules(String yamlPath) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File(yamlPath))) {
            data = yaml.load(in);
        }

        // Parse metadata categories and field definitions
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        if (metadata != null) {
            metadataCategories.addAll(metadata.keySet());
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                Map<String, Object> categoryConfig = (Map<String, Object>) entry.getValue();
                List<Map<String, Object>> fields = (List<Map<String, Object>>) categoryConfig.get("fields");
                if (fields != null) {
                    metadataFieldDefinitions.put(entry.getKey(), fields);
                }
            }
        }

        // Parse rules
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) data.get("rules");
        if (rawRules != null) {
            for (Map<String, Object> raw : rawRules) {
                String ruleName = (String) raw.get("name");
                Map<String, Object> target = (Map<String, Object>) raw.get("target");

                RuleInfo info = new RuleInfo();
                info.name = ruleName;

                if (target != null) {
                    info.targetType = (String) target.get("type"); // "model" or "view"
                    info.targetName = (String) target.get("name");
                    info.targetDomain = (String) target.get("domain");
                }

                // Check if rule requires metadata
                Map<String, Object> requires = (Map<String, Object>) raw.get("requires");
                info.requiresMetadata = requires != null && !requires.isEmpty();

                // Parse blocking flag (defaults to false)
                Object blockingVal = raw.get("blocking");
                info.blocking = blockingVal != null && Boolean.TRUE.equals(blockingVal);

                rules.add(info);
            }
        }
    }

    private void generateApplicationClass(String outputDir) throws IOException {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(BASE_PACKAGE).append(";\n\n");

        code.append("import org.springframework.boot.SpringApplication;\n");
        code.append("import org.springframework.boot.autoconfigure.SpringBootApplication;\n");
        code.append("import org.springframework.context.annotation.Bean;\n");
        code.append("import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;\n");
        code.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");
        code.append("import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;\n");
        code.append("import com.hubspot.jackson.datatype.protobuf.ProtobufModule;\n\n");

        code.append("/**\n");
        code.append(" * Generated Spring Boot server for APPGET REST API\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml and specs.yaml\n");
        code.append(" */\n");
        code.append("@SpringBootApplication\n");
        code.append("public class Application {\n\n");
        code.append("    public static void main(String[] args) {\n");
        code.append("        SpringApplication.run(Application.class, args);\n");
        code.append("    }\n\n");
        code.append("    @Bean\n");
        code.append("    public ObjectMapper objectMapper() {\n");
        code.append("        return Jackson2ObjectMapperBuilder.json()\n");
        code.append("            .modules(new ProtobufModule(), new JavaTimeModule())\n");
        code.append("            .build();\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, BASE_PACKAGE, "Application", code.toString());
    }

    private void generateApplicationYaml(String outputDir) throws IOException {
        StringBuilder yaml = new StringBuilder();
        yaml.append("server:\n");
        yaml.append("  port: 8080\n\n");
        yaml.append("spring:\n");
        yaml.append("  application:\n");
        yaml.append("    name: appget-server\n\n");
        yaml.append("logging:\n");
        yaml.append("  level:\n");
        yaml.append("    root: INFO\n");
        yaml.append("    dev.appget.server: DEBUG\n");
        yaml.append("    org.springframework.web: DEBUG\n");
        yaml.append("    org.springframework.web.servlet.mvc: DEBUG\n\n");
        yaml.append("  # File logging configuration\n");
        yaml.append("  file:\n");
        yaml.append("    name: logs/appget-server.log\n");
        yaml.append("  logback:\n");
        yaml.append("    rollingpolicy:\n");
        yaml.append("      max-file-size: 10MB\n");
        yaml.append("      max-history: 10\n");

        // Create application.yaml in generated server directory itself
        Path serverDir = Paths.get(outputDir);
        Files.createDirectories(serverDir);
        Path yamlFile = serverDir.resolve("application.yaml");
        Files.writeString(yamlFile, yaml.toString());
    }

    private void generateLog4j2Properties(String outputDir) throws IOException {
        StringBuilder props = new StringBuilder();
        props.append("# Log4j2 Configuration for Spring Boot Server\n");
        props.append("# DO NOT EDIT MANUALLY - Generated from SpringBootServerGenerator\n\n");

        props.append("status = warn\n");
        props.append("name = AppgetServerLogging\n\n");

        props.append("# Define appenders\n");
        props.append("appender.console.type = Console\n");
        props.append("appender.console.name = STDOUT\n");
        props.append("appender.console.layout.type = PatternLayout\n");
        props.append("appender.console.layout.pattern = [%d{ISO8601}] [%-5p] [%t] [%c] - %m%n\n\n");

        props.append("appender.file.type = RollingFile\n");
        props.append("appender.file.name = FILE\n");
        props.append("appender.file.fileName = logs/appget-server.log\n");
        props.append("appender.file.filePattern = logs/appget-server-%d{yyyy-MM-dd}-%i.log.gz\n");
        props.append("appender.file.layout.type = PatternLayout\n");
        props.append("appender.file.layout.pattern = [%d{ISO8601}] [%-5p] [%t] [%c] - %m%n\n");
        props.append("appender.file.policies.type = Policies\n");
        props.append("appender.file.policies.time.type = TimeBasedTriggeringPolicy\n");
        props.append("appender.file.policies.time.interval = 1\n");
        props.append("appender.file.policies.time.modulate = true\n");
        props.append("appender.file.policies.size.type = SizeBasedTriggeringPolicy\n");
        props.append("appender.file.policies.size.size = 10MB\n");
        props.append("appender.file.strategy.type = DefaultRolloverStrategy\n");
        props.append("appender.file.strategy.max = 10\n\n");

        props.append("# Root logger\n");
        props.append("rootLogger.level = INFO\n");
        props.append("rootLogger.appenderRef.stdout.ref = STDOUT\n");
        props.append("rootLogger.appenderRef.file.ref = FILE\n\n");

        props.append("# Package-specific loggers\n");
        props.append("logger.dev_appget_server.name = dev.appget.server\n");
        props.append("logger.dev_appget_server.level = DEBUG\n\n");

        props.append("logger.springframework_web.name = org.springframework.web\n");
        props.append("logger.springframework_web.level = DEBUG\n\n");

        props.append("logger.springframework_mvc.name = org.springframework.web.servlet.mvc\n");
        props.append("logger.springframework_mvc.level = DEBUG\n\n");

        props.append("# Suppress noisy loggers\n");
        props.append("logger.snakeyaml.name = org.yaml.snakeyaml\n");
        props.append("logger.snakeyaml.level = WARN\n\n");

        props.append("logger.protobuf.name = com.google.protobuf\n");
        props.append("logger.protobuf.level = WARN\n");

        // Create src/main/resources directory
        Path resourcesDir = Paths.get(outputDir, "src", "main", "resources");
        Files.createDirectories(resourcesDir);

        // Write log4j2.properties
        Path propsFile = resourcesDir.resolve("log4j2.properties");
        Files.writeString(propsFile, props.toString());
    }

    private void generateBuildGradle(String outputDir) throws IOException {
        StringBuilder gradle = new StringBuilder();

        // Header
        gradle.append("// Auto-generated build.gradle for Spring Boot server\n");
        gradle.append("// DO NOT EDIT MANUALLY - Regenerate via: make generate-server\n\n");

        // Plugins
        gradle.append("plugins {\n");
        gradle.append("    id 'java'\n");
        gradle.append("    id 'org.springframework.boot' version '3.3.5'\n");
        gradle.append("    id 'io.spring.dependency-management' version '1.1.6'\n");
        gradle.append("}\n\n");

        // Java version (Spring Boot 3.3.5 requires Java 21 or lower)
        gradle.append("java {\n");
        gradle.append("    sourceCompatibility = JavaVersion.VERSION_21\n");
        gradle.append("    targetCompatibility = JavaVersion.VERSION_21\n");
        gradle.append("}\n\n");

        // Group and version
        gradle.append("group = 'dev.appget'\n");
        gradle.append("version = '1.0.0'\n\n");

        // Repositories
        gradle.append("repositories {\n");
        gradle.append("    mavenCentral()\n");
        gradle.append("}\n\n");

        // Dependencies
        gradle.append("dependencies {\n");
        gradle.append("    // Spring Boot (exclude Logback, use Log4j2 instead)\n");
        gradle.append("    implementation('org.springframework.boot:spring-boot-starter-web') {\n");
        gradle.append("        exclude group: 'org.springframework.boot', module: 'spring-boot-starter-logging'\n");
        gradle.append("    }\n");
        gradle.append("    implementation 'org.springframework.boot:spring-boot-starter-validation'\n\n");

        gradle.append("    // Lombok for DTOs and context POJOs\n");
        gradle.append("    compileOnly 'org.projectlombok:lombok:1.18.42'\n");
        gradle.append("    annotationProcessor 'org.projectlombok:lombok:1.18.42'\n\n");

        gradle.append("    // Protocol Buffers runtime (for generated models)\n");
        gradle.append("    implementation 'com.google.protobuf:protobuf-java:3.25.3'\n");
        gradle.append("    implementation 'com.google.protobuf:protobuf-java-util:3.25.3'\n\n");

        gradle.append("    // Jackson support for protobuf (JSON serialization/deserialization)\n");
        gradle.append("    implementation 'com.hubspot.jackson:jackson-datatype-protobuf:0.9.15'\n\n");

        gradle.append("    // Log4j2 for logging (direct API, no SLF4J)\n");
        gradle.append("    implementation 'org.apache.logging.log4j:log4j-api:2.25.3'\n");
        gradle.append("    implementation 'org.apache.logging.log4j:log4j-core:2.25.3'\n");
        gradle.append("}\n\n");

        // Source sets - include main project's classes
        gradle.append("sourceSets {\n");
        gradle.append("    main {\n");
        gradle.append("        java {\n");
        gradle.append("            // Generated server source (dev/appget/server/)\n");
        gradle.append("            srcDirs = ['dev']\n");
        gradle.append("\n");
        gradle.append("            // Main project's manual source (base Specification, CompoundSpecification, etc.)\n");
        gradle.append("            srcDirs += ['../src/main/java']\n");
        gradle.append("\n");
        gradle.append("            // Main project's generated classes (specification instances and models)\n");
        gradle.append("            srcDirs += ['../src/main/java-generated']\n");
        gradle.append("            srcDirs += ['../build/generated/source/proto/main/java']\n");
        gradle.append("\n");
        gradle.append("            // Exclude build-time generators (not needed at runtime)\n");
        gradle.append("            excludes = ['**/codegen/**']\n");
        gradle.append("        }\n");
        gradle.append("        resources {\n");
        gradle.append("            srcDirs = ['.']\n");
        gradle.append("            include 'application.yaml'\n");
        gradle.append("        }\n");
        gradle.append("    }\n");
        gradle.append("}\n\n");

        // Spring Boot configuration
        gradle.append("springBoot {\n");
        gradle.append("    mainClass = 'dev.appget.server.Application'\n");
        gradle.append("}\n\n");

        gradle.append("// Run with: gradle bootRun\n");

        // Write build.gradle to generated-server directory
        Path serverDir = Paths.get(outputDir);
        Files.createDirectories(serverDir);
        Path gradleFile = serverDir.resolve("build.gradle");
        Files.writeString(gradleFile, gradle.toString());

        logger.info("Generated build.gradle for Spring Boot server");
    }

    private void generateMetadataExtractor(String outputDir) throws IOException {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(BASE_PACKAGE).append(".config;\n\n");

        code.append("import dev.appget.specification.MetadataContext;\n");
        // Import each context POJO
        for (String category : metadataCategories) {
            code.append("import dev.appget.specification.context.").append(capitalize(category)).append("Context;\n");
        }
        code.append("import org.springframework.stereotype.Component;\n");
        code.append("import jakarta.servlet.http.HttpServletRequest;\n\n");

        code.append("/**\n");
        code.append(" * Extracts metadata (auth, roles, user context) from HTTP request headers\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("@Component\n");
        code.append("public class MetadataExtractor {\n\n");

        code.append("    public MetadataContext extractFromHeaders(HttpServletRequest request) {\n");
        code.append("        MetadataContext context = new MetadataContext();\n\n");

        // Generate extraction for each metadata category
        for (String category : metadataCategories) {
            List<Map<String, Object>> fields = metadataFieldDefinitions.get(category);
            if (fields == null || fields.isEmpty()) continue;

            String contextClass = capitalize(category) + "Context";
            String headerPrefix = "X-" + capitalize(category) + "-";

            // Read headers
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String varName = category + capitalize(fieldName);
                String headerName = headerPrefix + camelToHeaderCase(fieldName);
                code.append("        String ").append(varName).append(" = request.getHeader(\"")
                    .append(headerName).append("\");\n");
            }

            // Build null-check condition (any header present)
            StringBuilder condition = new StringBuilder();
            for (int i = 0; i < fields.size(); i++) {
                String fieldName = (String) fields.get(i).get("name");
                String varName = category + capitalize(fieldName);
                if (i > 0) condition.append(" || ");
                condition.append(varName).append(" != null");
            }

            code.append("        if (").append(condition).append(") {\n");
            code.append("            ").append(contextClass).append(" ").append(category).append("Context = ")
                .append(contextClass).append(".builder()\n");

            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String fieldType = (String) field.get("type");
                String varName = category + capitalize(fieldName);
                code.append("                .").append(fieldName).append("(")
                    .append(parseHeaderValue(varName, fieldType)).append(")\n");
            }

            code.append("                .build();\n");
            code.append("            context.with(\"").append(category).append("\", ").append(category).append("Context);\n");
            code.append("        }\n\n");
        }

        code.append("        return context;\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, BASE_PACKAGE + ".config", "MetadataExtractor", code.toString());
    }

    private void generateRuleService(String outputDir) throws IOException {
        // Collect model-targeting rules (skip views)
        List<RuleInfo> modelRules = new ArrayList<>();
        for (RuleInfo rule : rules) {
            if (!"view".equals(rule.targetType)) {
                modelRules.add(rule);
            }
        }

        // Group rules by target name
        Map<String, List<RuleInfo>> rulesByTarget = new LinkedHashMap<>();
        for (RuleInfo rule : modelRules) {
            rulesByTarget.computeIfAbsent(rule.targetName, k -> new ArrayList<>()).add(rule);
        }

        StringBuilder code = new StringBuilder();
        code.append("package ").append(BASE_PACKAGE).append(".service;\n\n");

        // Import each generated spec class
        for (RuleInfo rule : modelRules) {
            code.append("import dev.appget.specification.generated.").append(rule.name).append(";\n");
        }

        // Import target model classes (deduplicate)
        Set<String> modelImports = new LinkedHashSet<>();
        for (RuleInfo rule : modelRules) {
            modelImports.add(resolveModelImport(rule));
        }
        for (String imp : modelImports) {
            code.append("import ").append(imp).append(";\n");
        }

        code.append("import dev.appget.specification.MetadataContext;\n");
        code.append("import ").append(BASE_PACKAGE).append(".dto.RuleOutcome;\n");
        code.append("import ").append(BASE_PACKAGE).append(".dto.RuleEvaluationResult;\n");
        code.append("import org.springframework.stereotype.Service;\n");
        code.append("import java.util.ArrayList;\n");
        code.append("import java.util.List;\n\n");

        code.append("/**\n");
        code.append(" * Evaluates business rules using pre-compiled specification classes\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("@Service\n");
        code.append("public class RuleService {\n\n");

        // Direct instantiation fields
        for (RuleInfo rule : modelRules) {
            String fieldName = lowerFirst(rule.name);
            code.append("    private final ").append(rule.name).append(" ").append(fieldName)
                .append(" = new ").append(rule.name).append("();\n");
        }
        code.append("\n");

        // evaluateAll method
        code.append("    public <T> RuleEvaluationResult evaluateAll(T target, MetadataContext metadata) {\n");
        code.append("        List<RuleOutcome> outcomes = new ArrayList<>();\n");
        code.append("        boolean hasFailures = false;\n\n");

        for (Map.Entry<String, List<RuleInfo>> entry : rulesByTarget.entrySet()) {
            String targetName = entry.getKey();
            List<RuleInfo> targetRules = entry.getValue();

            code.append("        if (target instanceof ").append(targetName).append(") {\n");
            code.append("            ").append(targetName).append(" typedTarget = (").append(targetName).append(") target;\n\n");

            for (RuleInfo rule : targetRules) {
                String fieldName = lowerFirst(rule.name);
                String satisfiedVar = fieldName + "Satisfied";
                String statusVar = fieldName + "Status";

                // Evaluate
                if (rule.requiresMetadata) {
                    code.append("            boolean ").append(satisfiedVar).append(" = ").append(fieldName)
                        .append(".evaluate(typedTarget, metadata);\n");
                    code.append("            String ").append(statusVar).append(" = ").append(fieldName)
                        .append(".getResult(typedTarget, metadata);\n");
                } else {
                    code.append("            boolean ").append(satisfiedVar).append(" = ").append(fieldName)
                        .append(".evaluate(typedTarget);\n");
                    code.append("            String ").append(statusVar).append(" = ").append(fieldName)
                        .append(".getResult(typedTarget);\n");
                }

                // Add outcome
                code.append("            outcomes.add(RuleOutcome.builder()\n");
                code.append("                .ruleName(\"").append(rule.name).append("\")\n");
                code.append("                .status(").append(statusVar).append(")\n");
                code.append("                .satisfied(").append(satisfiedVar).append(")\n");
                code.append("                .build());\n");

                // Only blocking rules set hasFailures
                if (rule.blocking) {
                    code.append("            if (!").append(satisfiedVar).append(") {\n");
                    code.append("                hasFailures = true;\n");
                    code.append("            }\n");
                }

                code.append("\n");
            }

            code.append("        }\n\n");
        }

        code.append("        return new RuleEvaluationResult(outcomes, hasFailures);\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, BASE_PACKAGE + ".service", "RuleService", code.toString());
    }

    private void generateDTOs(String outputDir) throws IOException {
        // RuleAwareResponse
        {
            StringBuilder code = new StringBuilder();
            code.append("package ").append(BASE_PACKAGE).append(".dto;\n\n");

            code.append("import lombok.AllArgsConstructor;\n");
            code.append("import lombok.Builder;\n");
            code.append("import lombok.Data;\n");
            code.append("import lombok.NoArgsConstructor;\n\n");

            code.append("/**\n");
            code.append(" * HTTP response wrapper that includes rule evaluation results\n");
            code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
            code.append(" */\n");
            code.append("@Data\n");
            code.append("@Builder\n");
            code.append("@AllArgsConstructor\n");
            code.append("@NoArgsConstructor\n");
            code.append("public class RuleAwareResponse<T> {\n");
            code.append("    private T data;\n");
            code.append("    private RuleEvaluationResult ruleResults;\n");
            code.append("}\n");

            writefile(outputDir, BASE_PACKAGE + ".dto", "RuleAwareResponse", code.toString());
        }

        // RuleEvaluationResult
        {
            StringBuilder code = new StringBuilder();
            code.append("package ").append(BASE_PACKAGE).append(".dto;\n\n");

            code.append("import java.util.List;\n");
            code.append("import lombok.AllArgsConstructor;\n");
            code.append("import lombok.Builder;\n");
            code.append("import lombok.Data;\n");
            code.append("import lombok.NoArgsConstructor;\n\n");

            code.append("/**\n");
            code.append(" * Result of evaluating all applicable rules for a target\n");
            code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
            code.append(" */\n");
            code.append("@Data\n");
            code.append("@Builder\n");
            code.append("@AllArgsConstructor\n");
            code.append("@NoArgsConstructor\n");
            code.append("public class RuleEvaluationResult {\n");
            code.append("    private List<RuleOutcome> outcomes;\n");
            code.append("    private boolean hasFailures;\n");
            code.append("}\n");

            writefile(outputDir, BASE_PACKAGE + ".dto", "RuleEvaluationResult", code.toString());
        }

        // RuleOutcome
        {
            StringBuilder code = new StringBuilder();
            code.append("package ").append(BASE_PACKAGE).append(".dto;\n\n");

            code.append("import lombok.AllArgsConstructor;\n");
            code.append("import lombok.Builder;\n");
            code.append("import lombok.Data;\n");
            code.append("import lombok.NoArgsConstructor;\n\n");

            code.append("/**\n");
            code.append(" * Outcome of evaluating a single business rule\n");
            code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
            code.append(" */\n");
            code.append("@Data\n");
            code.append("@Builder\n");
            code.append("@AllArgsConstructor\n");
            code.append("@NoArgsConstructor\n");
            code.append("public class RuleOutcome {\n");
            code.append("    private String ruleName;\n");
            code.append("    private String status;\n");
            code.append("    private boolean satisfied;\n");
            code.append("}\n");

            writefile(outputDir, BASE_PACKAGE + ".dto", "RuleOutcome", code.toString());
        }

        // ErrorResponse
        {
            StringBuilder code = new StringBuilder();
            code.append("package ").append(BASE_PACKAGE).append(".dto;\n\n");

            code.append("import lombok.AllArgsConstructor;\n");
            code.append("import lombok.Builder;\n");
            code.append("import lombok.Data;\n");
            code.append("import lombok.NoArgsConstructor;\n");
            code.append("import java.time.LocalDateTime;\n\n");

            code.append("/**\n");
            code.append(" * Standard error response format\n");
            code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
            code.append(" */\n");
            code.append("@Data\n");
            code.append("@Builder\n");
            code.append("@AllArgsConstructor\n");
            code.append("@NoArgsConstructor\n");
            code.append("public class ErrorResponse {\n");
            code.append("    private String errorCode;\n");
            code.append("    private String message;\n");
            code.append("    private RuleEvaluationResult ruleResults;\n");
            code.append("    private LocalDateTime timestamp;\n");
            code.append("}\n");

            writefile(outputDir, BASE_PACKAGE + ".dto", "ErrorResponse", code.toString());
        }
    }

    private void generateExceptionClasses(String outputDir) throws IOException {
        // RuleViolationException
        {
            StringBuilder code = new StringBuilder();
            code.append("package ").append(BASE_PACKAGE).append(".exception;\n\n");

            code.append("import ").append(BASE_PACKAGE).append(".dto.RuleEvaluationResult;\n\n");

            code.append("/**\n");
            code.append(" * Thrown when business rule evaluation fails\n");
            code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
            code.append(" */\n");
            code.append("public class RuleViolationException extends RuntimeException {\n");
            code.append("    private final RuleEvaluationResult results;\n\n");

            code.append("    public RuleViolationException(String message, RuleEvaluationResult results) {\n");
            code.append("        super(message);\n");
            code.append("        this.results = results;\n");
            code.append("    }\n\n");

            code.append("    public RuleEvaluationResult getResults() {\n");
            code.append("        return results;\n");
            code.append("    }\n");
            code.append("}\n");

            writefile(outputDir, BASE_PACKAGE + ".exception", "RuleViolationException", code.toString());
        }

        // ResourceNotFoundException
        {
            StringBuilder code = new StringBuilder();
            code.append("package ").append(BASE_PACKAGE).append(".exception;\n\n");

            code.append("/**\n");
            code.append(" * Thrown when a requested resource is not found\n");
            code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
            code.append(" */\n");
            code.append("public class ResourceNotFoundException extends RuntimeException {\n");
            code.append("    public ResourceNotFoundException(String message) {\n");
            code.append("        super(message);\n");
            code.append("    }\n");
            code.append("}\n");

            writefile(outputDir, BASE_PACKAGE + ".exception", "ResourceNotFoundException", code.toString());
        }
    }

    private void generateGlobalExceptionHandler(String outputDir) throws IOException {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(BASE_PACKAGE).append(".exception;\n\n");

        code.append("import ").append(BASE_PACKAGE).append(".dto.ErrorResponse;\n");
        code.append("import org.springframework.http.HttpStatus;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.web.bind.annotation.ControllerAdvice;\n");
        code.append("import org.springframework.web.bind.annotation.ExceptionHandler;\n");
        code.append("import java.time.LocalDateTime;\n\n");

        code.append("/**\n");
        code.append(" * Global exception handler for REST API\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("@ControllerAdvice\n");
        code.append("public class GlobalExceptionHandler {\n\n");

        code.append("    @ExceptionHandler(RuleViolationException.class)\n");
        code.append("    public ResponseEntity<ErrorResponse> handleRuleViolation(RuleViolationException ex) {\n");
        code.append("        ErrorResponse response = ErrorResponse.builder()\n");
        code.append("            .errorCode(\"RULE_VIOLATION\")\n");
        code.append("            .message(ex.getMessage())\n");
        code.append("            .ruleResults(ex.getResults())\n");
        code.append("            .timestamp(LocalDateTime.now())\n");
        code.append("            .build();\n");
        code.append("        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);\n");
        code.append("    }\n\n");

        code.append("    @ExceptionHandler(ResourceNotFoundException.class)\n");
        code.append("    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {\n");
        code.append("        ErrorResponse response = ErrorResponse.builder()\n");
        code.append("            .errorCode(\"NOT_FOUND\")\n");
        code.append("            .message(ex.getMessage())\n");
        code.append("            .timestamp(LocalDateTime.now())\n");
        code.append("            .build();\n");
        code.append("        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, BASE_PACKAGE + ".exception", "GlobalExceptionHandler", code.toString());
    }

    private void generateRepository(ModelInfo model, String outputDir) throws IOException {
        String className = model.name + "Repository";
        String packageName = BASE_PACKAGE + ".repository";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(model.namespace).append(".model.").append(model.name).append(";\n");
        code.append("import org.springframework.stereotype.Repository;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import java.util.Map;\n");
        code.append("import java.util.Optional;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.concurrent.ConcurrentHashMap;\n");
        code.append("import java.util.concurrent.atomic.AtomicLong;\n");
        code.append("import java.util.stream.Collectors;\n\n");

        code.append("/**\n");
        code.append(" * In-memory repository for ").append(model.name).append(" entities\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@Repository\n");
        code.append("public class ").append(className).append(" {\n\n");

        // Check if model has an 'id' field
        boolean hasIdField = model.fields.stream()
            .anyMatch(f -> "id".equals(f.get("name")));

        code.append("    private final Map<String, ").append(model.name).append("> store = new ConcurrentHashMap<>();\n");
        if (!hasIdField) {
            code.append("    private final AtomicLong idGenerator = new AtomicLong();\n");
        }
        code.append("\n");

        code.append("    public ").append(model.name).append(" save(").append(model.name).append(" entity) {\n");
        if (hasIdField) {
            code.append("        String id = entity.getId();\n");
        } else {
            code.append("        String id = String.valueOf(idGenerator.incrementAndGet());\n");
        }
        code.append("        log.debug(\"Saving ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        store.put(id, entity);\n");
        code.append("        log.info(\"Successfully saved ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        return entity;\n");
        code.append("    }\n\n");

        code.append("    public Optional<").append(model.name).append("> findById(String id) {\n");
        code.append("        log.debug(\"Looking up ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        Optional<").append(model.name).append("> result = Optional.ofNullable(store.get(id));\n");
        code.append("        if (result.isPresent()) {\n");
        code.append("            log.debug(\"Found ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        } else {\n");
        code.append("            log.debug(\"").append(model.name).append(" not found with id: {}\", id);\n");
        code.append("        }\n");
        code.append("        return result;\n");
        code.append("    }\n\n");

        code.append("    public List<").append(model.name).append("> findAll() {\n");
        code.append("        log.debug(\"Retrieving all ").append(model.name).append(" entities\");\n");
        code.append("        List<").append(model.name).append("> results = new java.util.ArrayList<>(store.values());\n");
        code.append("        log.debug(\"Found {} ").append(model.name).append(" entities\", results.size());\n");
        code.append("        return results;\n");
        code.append("    }\n\n");

        code.append("    public void deleteById(String id) {\n");
        code.append("        log.debug(\"Deleting ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        store.remove(id);\n");
        code.append("        log.info(\"Successfully deleted ").append(model.name).append(" with id: {}\", id);\n");
        code.append("    }\n\n");

        code.append("    public boolean existsById(String id) {\n");
        code.append("        boolean exists = store.containsKey(id);\n");
        code.append("        log.debug(\"Checking existence of ").append(model.name).append(" with id: {}, exists: {}\", id, exists);\n");
        code.append("        return exists;\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, packageName, className, code.toString());
    }

    private void generateService(ModelInfo model, String outputDir) throws IOException {
        String className = model.name + "Service";
        String packageName = BASE_PACKAGE + ".service";
        String repositoryClass = model.name + "Repository";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(model.namespace).append(".model.").append(model.name).append(";\n");
        code.append("import ").append(BASE_PACKAGE).append(".repository.").append(repositoryClass).append(";\n");
        code.append("import ").append(BASE_PACKAGE).append(".dto.RuleAwareResponse;\n");
        code.append("import ").append(BASE_PACKAGE).append(".dto.RuleEvaluationResult;\n");
        code.append("import ").append(BASE_PACKAGE).append(".exception.ResourceNotFoundException;\n");
        code.append("import ").append(BASE_PACKAGE).append(".exception.RuleViolationException;\n");
        code.append("import dev.appget.specification.MetadataContext;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import org.springframework.stereotype.Service;\n");
        code.append("import java.util.List;\n\n");

        code.append("/**\n");
        code.append(" * Business logic service for ").append(model.name).append(" with rule evaluation\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml and specs.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@Service\n");
        code.append("public class ").append(className).append(" {\n\n");

        code.append("    private final ").append(repositoryClass).append(" repository;\n");
        code.append("    private final RuleService ruleService;\n\n");

        code.append("    public ").append(className).append("(").append(repositoryClass).append(" repository, RuleService ruleService) {\n");
        code.append("        this.repository = repository;\n");
        code.append("        this.ruleService = ruleService;\n");
        code.append("    }\n\n");

        code.append("    public RuleAwareResponse<").append(model.name).append("> create(").append(model.name).append(" entity, MetadataContext metadata) {\n");
        code.append("        log.info(\"Creating new ").append(model.name).append(" entity\");\n");
        code.append("        log.debug(\"Entity data: {}\", entity);\n");
        code.append("        RuleEvaluationResult ruleResult = ruleService.evaluateAll(entity, metadata);\n");
        code.append("        log.debug(\"Rule evaluation completed with hasFailures: {}\", ruleResult.isHasFailures());\n");
        code.append("        if (ruleResult.isHasFailures()) {\n");
        code.append("            log.warn(\"").append(model.name).append(" creation failed validation\");\n");
        code.append("            throw new RuleViolationException(\"Validation failed\", ruleResult);\n");
        code.append("        }\n");
        code.append("        ").append(model.name).append(" saved = repository.save(entity);\n");
        code.append("        log.info(\"Successfully created ").append(model.name).append(" entity\");\n");
        code.append("        return RuleAwareResponse.<").append(model.name).append(">builder()\n");
        code.append("            .data(saved)\n");
        code.append("            .ruleResults(ruleResult)\n");
        code.append("            .build();\n");
        code.append("    }\n\n");

        code.append("    public ").append(model.name).append(" findById(String id) {\n");
        code.append("        log.debug(\"Fetching ").append(model.name).append(" by id: {}\", id);\n");
        code.append("        return repository.findById(id)\n");
        code.append("            .orElseThrow(() -> {\n");
        code.append("                log.warn(\"").append(model.name).append(" not found with id: {}\", id);\n");
        code.append("                return new ResourceNotFoundException(\"").append(model.name).append(" not found: \" + id);\n");
        code.append("            });\n");
        code.append("    }\n\n");

        code.append("    public List<").append(model.name).append("> findAll() {\n");
        code.append("        log.debug(\"Fetching all ").append(model.name).append(" entities\");\n");
        code.append("        return repository.findAll();\n");
        code.append("    }\n\n");

        code.append("    public RuleAwareResponse<").append(model.name).append("> update(String id, ").append(model.name).append(" entity, MetadataContext metadata) {\n");
        code.append("        log.info(\"Updating ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        if (!repository.existsById(id)) {\n");
        code.append("            log.warn(\"").append(model.name).append(" not found for update with id: {}\", id);\n");
        code.append("            throw new ResourceNotFoundException(\"").append(model.name).append(" not found: \" + id);\n");
        code.append("        }\n");
        code.append("        log.debug(\"Entity data: {}\", entity);\n");
        code.append("        RuleEvaluationResult ruleResult = ruleService.evaluateAll(entity, metadata);\n");
        code.append("        log.debug(\"Rule evaluation completed with hasFailures: {}\", ruleResult.isHasFailures());\n");
        code.append("        if (ruleResult.isHasFailures()) {\n");
        code.append("            log.warn(\"").append(model.name).append(" update failed validation for id: {}\", id);\n");
        code.append("            throw new RuleViolationException(\"Validation failed\", ruleResult);\n");
        code.append("        }\n");
        code.append("        ").append(model.name).append(" updated = repository.save(entity);\n");
        code.append("        log.info(\"Successfully updated ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        return RuleAwareResponse.<").append(model.name).append(">builder()\n");
        code.append("            .data(updated)\n");
        code.append("            .ruleResults(ruleResult)\n");
        code.append("            .build();\n");
        code.append("    }\n\n");

        code.append("    public void deleteById(String id) {\n");
        code.append("        log.info(\"Deleting ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        if (!repository.existsById(id)) {\n");
        code.append("            log.warn(\"").append(model.name).append(" not found for deletion with id: {}\", id);\n");
        code.append("            throw new ResourceNotFoundException(\"").append(model.name).append(" not found: \" + id);\n");
        code.append("        }\n");
        code.append("        repository.deleteById(id);\n");
        code.append("        log.info(\"Successfully deleted ").append(model.name).append(" with id: {}\", id);\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, packageName, className, code.toString());
    }

    private void generateController(ModelInfo model, String outputDir) throws IOException {
        String className = model.name + "Controller";
        String packageName = BASE_PACKAGE + ".controller";
        String serviceClass = model.name + "Service";
        String resourcePath = "/" + camelToKebab(model.name) + "s";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(model.namespace).append(".model.").append(model.name).append(";\n");
        code.append("import ").append(BASE_PACKAGE).append(".service.").append(serviceClass).append(";\n");
        code.append("import ").append(BASE_PACKAGE).append(".config.MetadataExtractor;\n");
        code.append("import ").append(BASE_PACKAGE).append(".dto.RuleAwareResponse;\n");
        code.append("import dev.appget.specification.MetadataContext;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import org.springframework.http.HttpStatus;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.web.bind.annotation.*;\n");
        code.append("import jakarta.servlet.http.HttpServletRequest;\n");
        code.append("import jakarta.validation.Valid;\n");
        code.append("import java.util.List;\n\n");

        code.append("/**\n");
        code.append(" * REST API endpoints for ").append(model.name).append(" entities\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml and specs.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@RestController\n");
        code.append("@RequestMapping(\"").append(resourcePath).append("\")\n");
        code.append("public class ").append(className).append(" {\n\n");

        code.append("    private final ").append(serviceClass).append(" service;\n");
        code.append("    private final MetadataExtractor metadataExtractor;\n\n");

        code.append("    public ").append(className).append("(").append(serviceClass).append(" service, MetadataExtractor metadataExtractor) {\n");
        code.append("        this.service = service;\n");
        code.append("        this.metadataExtractor = metadataExtractor;\n");
        code.append("    }\n\n");

        // POST /entities
        code.append("    @PostMapping\n");
        code.append("    public ResponseEntity<RuleAwareResponse<").append(model.name).append(">> create(\n");
        code.append("            @Valid @RequestBody ").append(model.name).append(" entity,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"POST ").append(resourcePath).append(" - Creating new ").append(model.name).append("\");\n");
        code.append("        log.debug(\"Request headers: {} {}\", request.getMethod(), request.getRequestURI());\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        RuleAwareResponse<").append(model.name).append("> response = service.create(entity, metadata);\n");
        code.append("        log.info(\"Successfully created ").append(model.name).append(" with status 201\");\n");
        code.append("        return ResponseEntity.status(HttpStatus.CREATED).body(response);\n");
        code.append("    }\n\n");

        // GET /entities
        code.append("    @GetMapping\n");
        code.append("    public ResponseEntity<List<").append(model.name).append(">> list() {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(" - Retrieving all ").append(model.name).append(" entities\");\n");
        code.append("        List<").append(model.name).append("> results = service.findAll();\n");
        code.append("        log.info(\"Retrieved {} ").append(model.name).append(" entities\", results.size());\n");
        code.append("        return ResponseEntity.ok(results);\n");
        code.append("    }\n\n");

        // GET /entities/{id}
        code.append("    @GetMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<").append(model.name).append("> get(@PathVariable String id) {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append("/{id} - Retrieving ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        ").append(model.name).append(" result = service.findById(id);\n");
        code.append("        log.info(\"Found ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        return ResponseEntity.ok(result);\n");
        code.append("    }\n\n");

        // PUT /entities/{id}
        code.append("    @PutMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<RuleAwareResponse<").append(model.name).append(">> update(\n");
        code.append("            @PathVariable String id,\n");
        code.append("            @Valid @RequestBody ").append(model.name).append(" entity,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"PUT ").append(resourcePath).append("/{id} - Updating ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        log.debug(\"Request headers: {} {}\", request.getMethod(), request.getRequestURI());\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        RuleAwareResponse<").append(model.name).append("> response = service.update(id, entity, metadata);\n");
        code.append("        log.info(\"Successfully updated ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        return ResponseEntity.ok(response);\n");
        code.append("    }\n\n");

        // DELETE /entities/{id}
        code.append("    @DeleteMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<Void> delete(@PathVariable String id) {\n");
        code.append("        log.info(\"DELETE ").append(resourcePath).append("/{id} - Deleting ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        service.deleteById(id);\n");
        code.append("        log.info(\"Successfully deleted ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        return ResponseEntity.noContent().build();\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, packageName, className, code.toString());
    }

    private String lowerFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String camelToHeaderCase(String camelCase) {
        // roleLevel -> Role-Level, sessionId -> Session-Id
        StringBuilder result = new StringBuilder();
        result.append(Character.toUpperCase(camelCase.charAt(0)));
        for (int i = 1; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('-').append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String resolveModelImport(RuleInfo rule) {
        ModelInfo model = modelIndex.get(rule.targetName);
        if (model != null) {
            return model.namespace + ".model." + model.name;
        }
        return "dev.appget.model." + rule.targetName;
    }

    private String parseHeaderValue(String varName, String type) {
        if ("boolean".equals(type)) {
            return varName + " != null ? Boolean.parseBoolean(" + varName + ") : false";
        } else if ("int".equals(type)) {
            return varName + " != null ? Integer.parseInt(" + varName + ") : 0";
        } else if ("long".equals(type)) {
            return varName + " != null ? Long.parseLong(" + varName + ") : 0L";
        } else if ("float".equals(type)) {
            return varName + " != null ? Float.parseFloat(" + varName + ") : 0.0f";
        } else if ("double".equals(type)) {
            return varName + " != null ? Double.parseDouble(" + varName + ") : 0.0";
        } else {
            return varName + " != null ? " + varName + " : \"\"";
        }
    }

    private void writefile(String outputDir, String packageName, String className, String javaCode) throws IOException {
        Path packagePath = Paths.get(outputDir, packageName.split("\\."));
        Files.createDirectories(packagePath);
        Path outputFile = packagePath.resolve(className + ".java");
        Files.writeString(outputFile, javaCode);
    }

    private String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    // Helper classes
    static class ModelInfo {
        String name;
        String domain;
        String namespace;
        List<Map<String, Object>> fields;
    }

    static class RuleInfo {
        String name;
        String targetType;
        String targetName;
        String targetDomain;
        boolean requiresMetadata;
        boolean blocking;
    }
}
