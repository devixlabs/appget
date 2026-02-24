package dev.appget.codegen;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.appget.codegen.CodeGenUtils;
import dev.appget.codegen.JavaUtils;

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
 * Usage: java -cp <classpath> dev.appget.codegen.AppServerGenerator <models.yaml> <specs.yaml> <output-dir>
 */
public class AppServerGenerator {

    private static final Logger logger = LogManager.getLogger(AppServerGenerator.class);
    private static final String BASE_PACKAGE = "dev.appget.server";
    private Map<String, ModelInfo> modelIndex = new HashMap<>();
    private List<RuleInfo> rules = new ArrayList<>();
    private Set<String> metadataCategories = new LinkedHashSet<>();
    private Map<String, List<Map<String, Object>>> metadataFieldDefinitions = new LinkedHashMap<>();

    public static void main(String[] args) {
        logger.debug("Entering main method with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid argument count. Usage: AppServerGenerator <models.yaml> <specs.yaml> [output-dir]");
            System.err.println("Usage: AppServerGenerator <models.yaml> <specs.yaml> [output-dir]");
            System.exit(1);
        }

        String modelsPath = args[0];
        String specsPath = args[1];
        String outputDir = args.length > 2 ? args[2] : "generated-server";

        logger.info("Starting AppServerGenerator with modelsPath={}, specsPath={}, outputDir={}", modelsPath, specsPath, outputDir);

        try {
            new AppServerGenerator().generateServer(modelsPath, specsPath, outputDir);
            logger.info("Successfully generated application server to: {}", outputDir);
            System.out.println("✓ Successfully generated application server to: " + outputDir);
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
        // Clean output directory to remove stale generated files
        Path outPath = Paths.get(outputDir);
        if (Files.exists(outPath)) {
            deleteDirectory(outPath);
            logger.debug("Cleaned output directory: {}", outputDir);
        }

        // Load models
        loadModels(modelsPath);

        // Load rules and metadata
        loadRules(specsPath);

        // Generate infrastructure
        generateBuildGradle(outputDir);
        generateApplicationClass(outputDir);
        generateDecimalModule(outputDir);
        generateApplicationYaml(outputDir);
        generateLog4j2Properties(outputDir);
        generateMetadataExtractor(outputDir);
        generateRuleService(outputDir);
        generateDTOs(outputDir);
        generateExceptionClasses(outputDir);
        generateGlobalExceptionHandler(outputDir);

        // Generate specification registry
        generateSpecificationRegistry(outputDir);

        // Generate per-model components
        for (ModelInfo model : modelIndex.values()) {
            generateRepositoryInterface(model, outputDir);
            generateInMemoryRepository(model, outputDir);
            generateService(model, outputDir);
            generateController(model, outputDir);
        }

        System.out.println("  Generated: build.gradle");
        System.out.println("  Generated: Application.java");
        System.out.println("  Generated: application.yaml");
        System.out.println("  Generated: log4j2.properties (src/main/resources/)");
        System.out.println("  Generated: SpecificationRegistry");
        System.out.println("  Generated: RuleService (with SpecificationRegistry injection)");
        System.out.println("  Generated: " + modelIndex.size() + " model endpoints (Controller/Service/Interface/InMemoryRepository)");
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
        code.append("import com.hubspot.jackson.datatype.protobuf.ProtobufModule;\n");
        code.append("import dev.appget.server.config.DecimalJacksonModule;\n\n");

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
        code.append("            .modules(new ProtobufModule(), new JavaTimeModule(), new DecimalJacksonModule())\n");
        code.append("            .build();\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, BASE_PACKAGE, "Application", code.toString());
    }

    private void generateDecimalModule(String outputDir) throws IOException {
        String pkg = BASE_PACKAGE + ".config";
        StringBuilder code = new StringBuilder();
        code.append("package ").append(pkg).append(";\n\n");
        code.append("import com.fasterxml.jackson.core.JsonGenerator;\n");
        code.append("import com.fasterxml.jackson.core.JsonParser;\n");
        code.append("import com.fasterxml.jackson.core.JsonToken;\n");
        code.append("import com.fasterxml.jackson.databind.DeserializationContext;\n");
        code.append("import com.fasterxml.jackson.databind.JsonDeserializer;\n");
        code.append("import com.fasterxml.jackson.databind.JsonSerializer;\n");
        code.append("import com.fasterxml.jackson.databind.SerializerProvider;\n");
        code.append("import com.fasterxml.jackson.databind.module.SimpleModule;\n");
        code.append("import com.google.protobuf.ByteString;\n");
        code.append("import dev.appget.common.Decimal;\n");
        code.append("import java.io.IOException;\n");
        code.append("import java.math.BigDecimal;\n");
        code.append("import java.math.BigInteger;\n\n");
        code.append("/**\n");
        code.append(" * Jackson module for appget.common.Decimal serialization.\n");
        code.append(" * Accepts JSON strings like \"99.99\" and maps to/from dev.appget.common.Decimal.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml and specs.yaml\n");
        code.append(" */\n");
        code.append("public class DecimalJacksonModule extends SimpleModule {\n\n");
        code.append("    public DecimalJacksonModule() {\n");
        code.append("        super(\"DecimalJacksonModule\");\n");
        code.append("        addDeserializer(Decimal.class, new DecimalDeserializer());\n");
        code.append("        addSerializer(Decimal.class, new DecimalSerializer());\n");
        code.append("    }\n\n");
        code.append("    private static class DecimalDeserializer extends JsonDeserializer<Decimal> {\n");
        code.append("        @Override\n");
        code.append("        public Decimal deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {\n");
        code.append("            JsonToken token = p.currentToken();\n");
        code.append("            if (token == JsonToken.VALUE_STRING\n");
        code.append("                    || token == JsonToken.VALUE_NUMBER_FLOAT\n");
        code.append("                    || token == JsonToken.VALUE_NUMBER_INT) {\n");
        code.append("                BigDecimal bd = new BigDecimal(p.getText());\n");
        code.append("                byte[] unscaledBytes = bd.unscaledValue().toByteArray();\n");
        code.append("                return Decimal.newBuilder()\n");
        code.append("                    .setUnscaled(ByteString.copyFrom(unscaledBytes))\n");
        code.append("                    .setScale(bd.scale())\n");
        code.append("                    .build();\n");
        code.append("            }\n");
        code.append("            return Decimal.newBuilder().build();\n");
        code.append("        }\n");
        code.append("    }\n\n");
        code.append("    private static class DecimalSerializer extends JsonSerializer<Decimal> {\n");
        code.append("        @Override\n");
        code.append("        public void serialize(Decimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {\n");
        code.append("            if (value.getUnscaled().isEmpty()) {\n");
        code.append("                gen.writeString(\"0\");\n");
        code.append("            } else {\n");
        code.append("                BigInteger unscaled = new BigInteger(value.getUnscaled().toByteArray());\n");
        code.append("                BigDecimal bd = new BigDecimal(unscaled, value.getScale());\n");
        code.append("                gen.writeString(bd.toPlainString());\n");
        code.append("            }\n");
        code.append("        }\n");
        code.append("    }\n");
        code.append("}\n");
        writefile(outputDir, pkg, "DecimalJacksonModule", code.toString());
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
        props.append("# DO NOT EDIT MANUALLY - Generated from AppServerGenerator\n\n");

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
            code.append("import dev.appget.specification.context.").append(CodeGenUtils.capitalize(category)).append("Context;\n");
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

            String contextClass = CodeGenUtils.capitalize(category) + "Context";
            String headerPrefix = "X-" + CodeGenUtils.capitalize(category) + "-";

            // Read headers
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String varName = category + CodeGenUtils.capitalize(fieldName);
                String headerName = headerPrefix + camelToHeaderCase(fieldName);
                code.append("        String ").append(varName).append(" = request.getHeader(\"")
                    .append(headerName).append("\");\n");
            }

            // Build null-check condition (any header present)
            StringBuilder condition = new StringBuilder();
            for (int i = 0; i < fields.size(); i++) {
                String fieldName = (String) fields.get(i).get("name");
                String varName = category + CodeGenUtils.capitalize(fieldName);
                if (i > 0) condition.append(" || ");
                condition.append(varName).append(" != null");
            }

            code.append("        if (").append(condition).append(") {\n");
            code.append("            ").append(contextClass).append(" ").append(category).append("Context = ")
                .append(contextClass).append(".builder()\n");

            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String fieldType = (String) field.get("type");
                String varName = category + CodeGenUtils.capitalize(fieldName);
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

    private void generateSpecificationRegistry(String outputDir) throws IOException {
        // Collect model-targeting rules (skip views)
        List<RuleInfo> modelRules = new ArrayList<>();
        for (RuleInfo rule : rules) {
            if (!"view".equals(rule.targetType)) {
                modelRules.add(rule);
            }
        }

        StringBuilder code = new StringBuilder();
        code.append("package ").append(BASE_PACKAGE).append(".service;\n\n");

        // Import each generated spec class
        for (RuleInfo rule : modelRules) {
            code.append("import dev.appget.specification.generated.").append(rule.name).append(";\n");
        }

        code.append("import org.springframework.stereotype.Component;\n");
        code.append("import java.util.Collection;\n");
        code.append("import java.util.LinkedHashMap;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Map;\n");
        code.append("import java.util.stream.Collectors;\n\n");

        code.append("/**\n");
        code.append(" * Registry of all compiled specification classes.\n");
        code.append(" * DO NOT EDIT MANUALLY - Regenerated from specs.yaml when rules change.\n");
        code.append(" */\n");
        code.append("@Component\n");
        code.append("public class SpecificationRegistry {\n");
        code.append("    private final Map<String, Object> specs = new LinkedHashMap<>();\n\n");

        code.append("    public SpecificationRegistry() {\n");
        for (RuleInfo rule : modelRules) {
            code.append("        register(\"").append(rule.name).append("\", new ").append(rule.name).append("());\n");
        }
        code.append("    }\n\n");

        code.append("    private void register(String name, Object spec) {\n");
        code.append("        specs.put(name, spec);\n");
        code.append("    }\n\n");

        code.append("    /** Retrieve a single spec by rule name. Returns null if not found. */\n");
        code.append("    public Object get(String name) {\n");
        code.append("        return specs.get(name);\n");
        code.append("    }\n\n");

        code.append("    /** All registered specs. */\n");
        code.append("    public Collection<Object> getAll() {\n");
        code.append("        return specs.values();\n");
        code.append("    }\n\n");

        // Build static target map: ruleName -> PascalCase model name
        code.append("    private static final Map<String, String> SPEC_TARGETS = new java.util.HashMap<>();\n");
        code.append("    static {\n");
        for (RuleInfo rule : modelRules) {
            ModelInfo model = modelIndex.get(rule.targetName);
            String target = model != null ? pascalName(model) : JavaUtils.snakeToPascal(rule.targetName);
            code.append("        SPEC_TARGETS.put(\"").append(rule.name).append("\", \"").append(target).append("\");\n");
        }
        code.append("    }\n\n");

        code.append("    /**\n");
        code.append("     * All specs whose target model matches the given class simple name.\n");
        code.append("     */\n");
        code.append("    public List<Object> getByTarget(String modelName) {\n");
        code.append("        return specs.values().stream()\n");
        code.append("            .filter(s -> modelName.equals(SPEC_TARGETS.get(s.getClass().getSimpleName())))\n");
        code.append("            .collect(Collectors.toList());\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, BASE_PACKAGE + ".service", "SpecificationRegistry", code.toString());
    }

    private void generateRuleService(String outputDir) throws IOException {
        // Collect model-targeting rules (skip views)
        List<RuleInfo> modelRules = new ArrayList<>();
        Map<String, Boolean> blockingMap = new HashMap<>();
        for (RuleInfo rule : rules) {
            if (!"view".equals(rule.targetType)) {
                modelRules.add(rule);
                blockingMap.put(rule.name, rule.blocking);
            }
        }

        StringBuilder code = new StringBuilder();
        code.append("package ").append(BASE_PACKAGE).append(".service;\n\n");

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
        code.append("import java.util.HashMap;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Map;\n\n");

        code.append("/**\n");
        code.append(" * Evaluates business rules using pre-compiled specification classes.\n");
        code.append(" * Stable service that injects SpecificationRegistry for dynamic rule lookup.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("@Service\n");
        code.append("public class RuleService {\n\n");

        code.append("    private final SpecificationRegistry registry;\n");
        code.append("    private static final Map<String, Boolean> BLOCKING_RULES = new HashMap<>();\n\n");

        code.append("    static {\n");
        for (Map.Entry<String, Boolean> entry : blockingMap.entrySet()) {
            code.append("        BLOCKING_RULES.put(\"").append(entry.getKey()).append("\", ").append(entry.getValue()).append(");\n");
        }
        code.append("    }\n\n");

        code.append("    public RuleService(SpecificationRegistry registry) {\n");
        code.append("        this.registry = registry;\n");
        code.append("    }\n\n");

        // evaluateAll method
        code.append("    public <T> RuleEvaluationResult evaluateAll(T target, MetadataContext metadata) {\n");
        code.append("        List<RuleOutcome> outcomes = new ArrayList<>();\n");
        code.append("        boolean hasFailures = false;\n\n");

        code.append("        String modelName = target.getClass().getSimpleName();\n");
        code.append("        List<Object> applicableSpecs = registry.getByTarget(modelName);\n\n");

        code.append("        for (Object spec : applicableSpecs) {\n");
        code.append("            String ruleName = getRuleName(spec);\n");
        code.append("            RuleOutcome outcome = evaluate(spec, target, metadata, ruleName);\n");
        code.append("            outcomes.add(outcome);\n\n");

        code.append("            boolean isBlocking = BLOCKING_RULES.getOrDefault(ruleName, false);\n");
        code.append("            if (isBlocking && !outcome.isSatisfied()) {\n");
        code.append("                hasFailures = true;\n");
        code.append("            }\n");
        code.append("        }\n\n");

        code.append("        return new RuleEvaluationResult(outcomes, hasFailures);\n");
        code.append("    }\n\n");

        code.append("    private String getRuleName(Object spec) {\n");
        code.append("        return spec.getClass().getSimpleName();\n");
        code.append("    }\n\n");

        code.append("    private RuleOutcome evaluate(Object spec, Object target, MetadataContext metadata, String ruleName) {\n");
        code.append("        boolean satisfied = evaluateSpec(spec, target, metadata);\n");
        code.append("        String status = getSpecStatus(spec, target, metadata);\n");
        code.append("        return RuleOutcome.builder()\n");
        code.append("            .ruleName(ruleName)\n");
        code.append("            .status(status)\n");
        code.append("            .satisfied(satisfied)\n");
        code.append("            .build();\n");
        code.append("    }\n\n");

        code.append("    private boolean evaluateSpec(Object spec, Object target, MetadataContext metadata) {\n");
        code.append("        try {\n");
        code.append("            // Find evaluate(T, MetadataContext) by parameter count — spec classes use typed params\n");
        code.append("            for (java.lang.reflect.Method m : spec.getClass().getMethods()) {\n");
        code.append("                if (\"evaluate\".equals(m.getName()) && m.getParameterCount() == 2) {\n");
        code.append("                    return (boolean) m.invoke(spec, target, metadata);\n");
        code.append("                }\n");
        code.append("            }\n");
        code.append("            // Fall back to evaluate(T)\n");
        code.append("            for (java.lang.reflect.Method m : spec.getClass().getMethods()) {\n");
        code.append("                if (\"evaluate\".equals(m.getName()) && m.getParameterCount() == 1) {\n");
        code.append("                    return (boolean) m.invoke(spec, target);\n");
        code.append("                }\n");
        code.append("            }\n");
        code.append("            return false;\n");
        code.append("        } catch (Exception e) {\n");
        code.append("            return false;\n");
        code.append("        }\n");
        code.append("    }\n\n");

        code.append("    private String getSpecStatus(Object spec, Object target, MetadataContext metadata) {\n");
        code.append("        try {\n");
        code.append("            // Find getResult(T, MetadataContext) by parameter count\n");
        code.append("            for (java.lang.reflect.Method m : spec.getClass().getMethods()) {\n");
        code.append("                if (\"getResult\".equals(m.getName()) && m.getParameterCount() == 2) {\n");
        code.append("                    return (String) m.invoke(spec, target, metadata);\n");
        code.append("                }\n");
        code.append("            }\n");
        code.append("            // Fall back to getResult(T)\n");
        code.append("            for (java.lang.reflect.Method m : spec.getClass().getMethods()) {\n");
        code.append("                if (\"getResult\".equals(m.getName()) && m.getParameterCount() == 1) {\n");
        code.append("                    return (String) m.invoke(spec, target);\n");
        code.append("                }\n");
        code.append("            }\n");
        code.append("            return \"UNKNOWN\";\n");
        code.append("        } catch (Exception e) {\n");
        code.append("            return \"UNKNOWN\";\n");
        code.append("        }\n");
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

    private void generateRepositoryInterface(ModelInfo model, String outputDir) throws IOException {
        String interfaceName = pascalName(model) + "Repository";
        String packageName = BASE_PACKAGE + ".repository";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(model.namespace).append(".model.").append(pascalName(model)).append(";\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Optional;\n\n");

        code.append("/**\n");
        code.append(" * Repository interface for ").append(model.name).append(" entities\n");
        code.append(" * Implement this interface to provide custom data access (database, cache, etc.)\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("public interface ").append(interfaceName).append(" {\n\n");

        code.append("    ").append(pascalName(model)).append(" save(").append(pascalName(model)).append(" entity);\n\n");
        code.append("    Optional<").append(pascalName(model)).append("> findById(String id);\n\n");
        code.append("    List<").append(pascalName(model)).append("> findAll();\n\n");
        code.append("    void deleteById(String id);\n\n");
        code.append("    boolean existsById(String id);\n");
        code.append("}\n");

        writefile(outputDir, packageName, interfaceName, code.toString());
    }

    private void generateInMemoryRepository(ModelInfo model, String outputDir) throws IOException {
        String className = "InMemory" + pascalName(model) + "Repository";
        String interfaceName = pascalName(model) + "Repository";
        String packageName = BASE_PACKAGE + ".repository";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(model.namespace).append(".model.").append(pascalName(model)).append(";\n");
        code.append("import org.springframework.stereotype.Component;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import java.util.Map;\n");
        code.append("import java.util.Optional;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.concurrent.ConcurrentHashMap;\n");
        code.append("import java.util.concurrent.atomic.AtomicLong;\n");
        code.append("import java.util.stream.Collectors;\n\n");

        code.append("/**\n");
        code.append(" * Default in-memory repository for ").append(model.name).append(" entities\n");
        code.append(" * Replace by providing a @Primary bean of type ").append(interfaceName).append("\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@Component\n");
        code.append("public class ").append(className).append(" implements ").append(interfaceName).append(" {\n\n");

        // Check if model has an 'id' field
        boolean hasIdField = model.fields.stream()
            .anyMatch(f -> "id".equals(f.get("name")));

        code.append("    private final Map<String, ").append(pascalName(model)).append("> store = new ConcurrentHashMap<>();\n");
        if (!hasIdField) {
            code.append("    private final AtomicLong idGenerator = new AtomicLong();\n");
        }
        code.append("\n");

        code.append("    @Override\n");
        code.append("    public ").append(pascalName(model)).append(" save(").append(pascalName(model)).append(" entity) {\n");
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

        code.append("    @Override\n");
        code.append("    public Optional<").append(pascalName(model)).append("> findById(String id) {\n");
        code.append("        log.debug(\"Looking up ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        Optional<").append(pascalName(model)).append("> result = Optional.ofNullable(store.get(id));\n");
        code.append("        if (result.isPresent()) {\n");
        code.append("            log.debug(\"Found ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        } else {\n");
        code.append("            log.debug(\"").append(model.name).append(" not found with id: {}\", id);\n");
        code.append("        }\n");
        code.append("        return result;\n");
        code.append("    }\n\n");

        code.append("    @Override\n");
        code.append("    public List<").append(pascalName(model)).append("> findAll() {\n");
        code.append("        log.debug(\"Retrieving all ").append(model.name).append(" entities\");\n");
        code.append("        List<").append(pascalName(model)).append("> results = new java.util.ArrayList<>(store.values());\n");
        code.append("        log.debug(\"Found {} ").append(model.name).append(" entities\", results.size());\n");
        code.append("        return results;\n");
        code.append("    }\n\n");

        code.append("    @Override\n");
        code.append("    public void deleteById(String id) {\n");
        code.append("        log.debug(\"Deleting ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        store.remove(id);\n");
        code.append("        log.info(\"Successfully deleted ").append(model.name).append(" with id: {}\", id);\n");
        code.append("    }\n\n");

        code.append("    @Override\n");
        code.append("    public boolean existsById(String id) {\n");
        code.append("        boolean exists = store.containsKey(id);\n");
        code.append("        log.debug(\"Checking existence of ").append(model.name).append(" with id: {}, exists: {}\", id, exists);\n");
        code.append("        return exists;\n");
        code.append("    }\n");
        code.append("}\n");

        writefile(outputDir, packageName, className, code.toString());
    }

    private void generateService(ModelInfo model, String outputDir) throws IOException {
        String className = pascalName(model) + "Service";
        String packageName = BASE_PACKAGE + ".service";
        String repositoryClass = pascalName(model) + "Repository";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(model.namespace).append(".model.").append(pascalName(model)).append(";\n");
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

        code.append("    public RuleAwareResponse<").append(pascalName(model)).append("> create(").append(pascalName(model)).append(" entity, MetadataContext metadata) {\n");
        code.append("        log.info(\"Creating new ").append(model.name).append(" entity\");\n");
        code.append("        log.debug(\"Entity data: {}\", entity);\n");
        code.append("        RuleEvaluationResult ruleResult = ruleService.evaluateAll(entity, metadata);\n");
        code.append("        log.debug(\"Rule evaluation completed with hasFailures: {}\", ruleResult.isHasFailures());\n");
        code.append("        if (ruleResult.isHasFailures()) {\n");
        code.append("            log.warn(\"").append(model.name).append(" creation failed validation\");\n");
        code.append("            throw new RuleViolationException(\"Validation failed\", ruleResult);\n");
        code.append("        }\n");
        code.append("        ").append(pascalName(model)).append(" saved = repository.save(entity);\n");
        code.append("        log.info(\"Successfully created ").append(model.name).append(" entity\");\n");
        code.append("        return RuleAwareResponse.<").append(pascalName(model)).append(">builder()\n");
        code.append("            .data(saved)\n");
        code.append("            .ruleResults(ruleResult)\n");
        code.append("            .build();\n");
        code.append("    }\n\n");

        code.append("    public ").append(pascalName(model)).append(" findById(String id) {\n");
        code.append("        log.debug(\"Fetching ").append(model.name).append(" by id: {}\", id);\n");
        code.append("        return repository.findById(id)\n");
        code.append("            .orElseThrow(() -> {\n");
        code.append("                log.warn(\"").append(model.name).append(" not found with id: {}\", id);\n");
        code.append("                return new ResourceNotFoundException(\"").append(model.name).append(" not found: \" + id);\n");
        code.append("            });\n");
        code.append("    }\n\n");

        code.append("    public List<").append(pascalName(model)).append("> findAll() {\n");
        code.append("        log.debug(\"Fetching all ").append(model.name).append(" entities\");\n");
        code.append("        return repository.findAll();\n");
        code.append("    }\n\n");

        code.append("    public RuleAwareResponse<").append(pascalName(model)).append("> update(String id, ").append(pascalName(model)).append(" entity, MetadataContext metadata) {\n");
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
        code.append("        ").append(pascalName(model)).append(" updated = repository.save(entity);\n");
        code.append("        log.info(\"Successfully updated ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        return RuleAwareResponse.<").append(pascalName(model)).append(">builder()\n");
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
        String className = pascalName(model) + "Controller";
        String packageName = BASE_PACKAGE + ".controller";
        String serviceClass = pascalName(model) + "Service";
        String resourcePath = "/" + toResourceName(model.name);

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(model.namespace).append(".model.").append(pascalName(model)).append(";\n");
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
        code.append("    public ResponseEntity<RuleAwareResponse<").append(pascalName(model)).append(">> create(\n");
        code.append("            @Valid @RequestBody ").append(pascalName(model)).append(" entity,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"POST ").append(resourcePath).append(" - Creating new ").append(model.name).append("\");\n");
        code.append("        log.debug(\"Request headers: {} {}\", request.getMethod(), request.getRequestURI());\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        RuleAwareResponse<").append(pascalName(model)).append("> response = service.create(entity, metadata);\n");
        code.append("        log.info(\"Successfully created ").append(model.name).append(" with status 201\");\n");
        code.append("        return ResponseEntity.status(HttpStatus.CREATED).body(response);\n");
        code.append("    }\n\n");

        // GET /entities
        code.append("    @GetMapping\n");
        code.append("    public ResponseEntity<List<").append(pascalName(model)).append(">> list() {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(" - Retrieving all ").append(model.name).append(" entities\");\n");
        code.append("        List<").append(pascalName(model)).append("> results = service.findAll();\n");
        code.append("        log.info(\"Retrieved {} ").append(model.name).append(" entities\", results.size());\n");
        code.append("        return ResponseEntity.ok(results);\n");
        code.append("    }\n\n");

        // GET /entities/{id}
        code.append("    @GetMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<").append(pascalName(model)).append("> get(@PathVariable String id) {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append("/{id} - Retrieving ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        ").append(pascalName(model)).append(" result = service.findById(id);\n");
        code.append("        log.info(\"Found ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        return ResponseEntity.ok(result);\n");
        code.append("    }\n\n");

        // PUT /entities/{id}
        code.append("    @PutMapping(\"/{id}\")\n");
        code.append("    public ResponseEntity<RuleAwareResponse<").append(pascalName(model)).append(">> update(\n");
        code.append("            @PathVariable String id,\n");
        code.append("            @Valid @RequestBody ").append(pascalName(model)).append(" entity,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"PUT ").append(resourcePath).append("/{id} - Updating ").append(model.name).append(" with id: {}\", id);\n");
        code.append("        log.debug(\"Request headers: {} {}\", request.getMethod(), request.getRequestURI());\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        RuleAwareResponse<").append(pascalName(model)).append("> response = service.update(id, entity, metadata);\n");
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

    private String pascalName(ModelInfo model) {
        return JavaUtils.snakeToPascal(model.name);
    }

    private String toResourceName(String snakeName) {
        return snakeName.replace('_', '-');
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
            return model.namespace + ".model." + pascalName(model);
        }
        return "dev.appget.model." + rule.targetName;
    }

    private String parseHeaderValue(String varName, String type) {
        // Support both neutral types (bool, int32, int64, float64) and legacy Java types
        if ("boolean".equals(type) || "bool".equals(type)) {
            return varName + " != null ? Boolean.parseBoolean(" + varName + ") : false";
        } else if ("int".equals(type) || "int32".equals(type)) {
            return varName + " != null ? Integer.parseInt(" + varName + ") : 0";
        } else if ("long".equals(type) || "int64".equals(type)) {
            return varName + " != null ? Long.parseLong(" + varName + ") : 0L";
        } else if ("float".equals(type)) {
            return varName + " != null ? Float.parseFloat(" + varName + ") : 0.0f";
        } else if ("double".equals(type) || "float64".equals(type)) {
            return varName + " != null ? Double.parseDouble(" + varName + ") : 0.0";
        } else {
            return varName + " != null ? " + varName + " : \"\"";
        }
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(java.io.File::delete);
    }

    private void writefile(String outputDir, String packageName, String className, String javaCode) throws IOException {
        Path packagePath = Paths.get(outputDir, packageName.split("\\."));
        Files.createDirectories(packagePath);
        Path outputFile = packagePath.resolve(className + ".java");
        Files.writeString(outputFile, javaCode);
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
