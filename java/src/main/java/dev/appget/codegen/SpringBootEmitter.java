package dev.appget.codegen;

import dev.appget.naming.JavaNaming;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot implementation of {@link ServerEmitter}.
 *
 * <p>Group A methods (Pure Infrastructure) generate files with no dependency on domain
 * models or specs — the Application entry-point class, the Jackson Decimal module, the
 * Spring Boot YAML configuration, the Log4j2 properties file, and the Gradle build
 * file.</p>
 *
 * <p>Group B methods (Middleware, DTOs, Exceptions) generate files that depend on
 * specs.yaml — MetadataExtractor, RuleService, SpecificationRegistry, GlobalExceptionHandler,
 * DTO classes, and exception classes.</p>
 *
 * <p>Group C methods (Per-Entity CRUD) generate files once per model or view.</p>
 */
public class SpringBootEmitter implements ServerEmitter {

    // -------------------------------------------------------------------------
    // Group A: Pure Infrastructure
    // -------------------------------------------------------------------------

    @Override
    public String emitApplicationClass(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(";\n\n");

        code.append("import org.springframework.boot.SpringApplication;\n");
        code.append("import org.springframework.boot.autoconfigure.SpringBootApplication;\n");
        code.append("import org.springframework.context.annotation.Bean;\n");
        code.append("import com.fasterxml.jackson.databind.ObjectMapper;\n");
        code.append("import com.fasterxml.jackson.databind.SerializationFeature;\n");
        code.append("import com.fasterxml.jackson.databind.DeserializationFeature;\n");
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
        code.append("        ObjectMapper mapper = new ObjectMapper();\n");
        code.append("        mapper.registerModule(new ProtobufModule());\n");
        code.append("        mapper.registerModule(new JavaTimeModule());\n");
        code.append("        mapper.registerModule(new DecimalJacksonModule());\n");
        code.append("        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);\n");
        code.append("        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);\n");
        code.append("        return mapper;\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitApplicationYaml() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("server:\n");
        yaml.append("  port: 8080\n\n");
        yaml.append("spring:\n");
        yaml.append("  application:\n");
        yaml.append("    name: appget-server\n");
        yaml.append("  mvc:\n");
        yaml.append("    hiddenmethod:\n");
        yaml.append("      filter:\n");
        yaml.append("        enabled: true\n\n");
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
        return yaml.toString();
    }

    @Override
    public String emitLog4j2Properties() {
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

        return props.toString();
    }

    @Override
    public String emitBuildGradle() {
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
        gradle.append("            srcDirs += ['src/main/resources']\n");
        gradle.append("            include 'application.yaml'\n");
        gradle.append("            include 'templates/**/*.html'\n");
        gradle.append("        }\n");
        gradle.append("    }\n");
        gradle.append("}\n\n");

        // Spring Boot configuration
        gradle.append("springBoot {\n");
        gradle.append("    mainClass = 'dev.appget.server.Application'\n");
        gradle.append("}\n\n");

        gradle.append("// Run with: gradle bootRun\n");

        return gradle.toString();
    }

    @Override
    public String emitDecimalModule(String basePackage) {
        String pkg = basePackage + ".config";
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
        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Group B: Middleware
    // -------------------------------------------------------------------------

    @Override
    public String emitMetadataExtractor(String basePackage, MetadataEmitContext ctx) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".config;\n\n");

        code.append("import dev.appget.specification.MetadataContext;\n");
        // Import each context POJO
        for (String category : ctx.categories) {
            code.append("import dev.appget.specification.context.").append(CodeGenUtils.capitalize(category)).append("Context;\n");
        }
        code.append("import ").append(basePackage).append(".exception.MetadataParsingException;\n");
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
        for (String category : ctx.categories) {
            List<Map<String, Object>> fields = ctx.fieldDefinitions.get(category);
            if (fields == null || fields.isEmpty()) continue;

            String contextClass = CodeGenUtils.capitalize(category) + "Context";
            String headerPrefix = "X-" + CodeGenUtils.capitalize(category) + "-";

            // Read headers
            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String camelFieldName = dev.appget.naming.JavaNaming.toFieldAccessor(fieldName);
                String varName = category + CodeGenUtils.capitalize(camelFieldName);
                String headerName = headerPrefix + JavaUtils.snakeToHeaderCase(fieldName);
                code.append("        String ").append(varName).append(" = request.getHeader(\"")
                    .append(headerName).append("\");\n");
            }

            // Build null-check condition (any header present)
            StringBuilder condition = new StringBuilder();
            for (int i = 0; i < fields.size(); i++) {
                String fieldName = (String) fields.get(i).get("name");
                String camelFieldName = dev.appget.naming.JavaNaming.toFieldAccessor(fieldName);
                String varName = category + CodeGenUtils.capitalize(camelFieldName);
                if (i > 0) condition.append(" || ");
                condition.append(varName).append(" != null");
            }

            code.append("        if (").append(condition).append(") {\n");
            code.append("            ").append(contextClass).append(" ").append(category).append("Context = ")
                .append(contextClass).append(".builder()\n");

            for (Map<String, Object> field : fields) {
                String fieldName = (String) field.get("name");
                String fieldType = (String) field.get("type");
                String camelFieldName = dev.appget.naming.JavaNaming.toFieldAccessor(fieldName);
                String varName = category + CodeGenUtils.capitalize(camelFieldName);
                String headerName = headerPrefix + JavaUtils.snakeToHeaderCase(fieldName);
                code.append("                .").append(camelFieldName).append("(")
                    .append(parseHeaderValue(varName, fieldType, headerName)).append(")\n");
            }

            code.append("                .build();\n");
            code.append("            context.with(\"").append(category).append("\", ").append(category).append("Context);\n");
            code.append("        }\n\n");
        }

        code.append("        return context;\n");
        code.append("    }\n\n");

        // Generate safe parsing helper methods
        code.append("    private int safeParseInt(String value, String headerName) {\n");
        code.append("        if (value == null) return 0;\n");
        code.append("        try {\n");
        code.append("            return Integer.parseInt(value);\n");
        code.append("        } catch (NumberFormatException e) {\n");
        code.append("            throw new MetadataParsingException(\n");
        code.append("                \"Invalid integer value for header \" + headerName + \": \" + value);\n");
        code.append("        }\n");
        code.append("    }\n\n");

        code.append("    private long safeParseLong(String value, String headerName) {\n");
        code.append("        if (value == null) return 0L;\n");
        code.append("        try {\n");
        code.append("            return Long.parseLong(value);\n");
        code.append("        } catch (NumberFormatException e) {\n");
        code.append("            throw new MetadataParsingException(\n");
        code.append("                \"Invalid long value for header \" + headerName + \": \" + value);\n");
        code.append("        }\n");
        code.append("    }\n\n");

        code.append("    private float safeParseFloat(String value, String headerName) {\n");
        code.append("        if (value == null) return 0.0f;\n");
        code.append("        try {\n");
        code.append("            return Float.parseFloat(value);\n");
        code.append("        } catch (NumberFormatException e) {\n");
        code.append("            throw new MetadataParsingException(\n");
        code.append("                \"Invalid float value for header \" + headerName + \": \" + value);\n");
        code.append("        }\n");
        code.append("    }\n\n");

        code.append("    private double safeParseDouble(String value, String headerName) {\n");
        code.append("        if (value == null) return 0.0;\n");
        code.append("        try {\n");
        code.append("            return Double.parseDouble(value);\n");
        code.append("        } catch (NumberFormatException e) {\n");
        code.append("            throw new MetadataParsingException(\n");
        code.append("                \"Invalid double value for header \" + headerName + \": \" + value);\n");
        code.append("        }\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitRuleService(String basePackage, RuleEmitContext ctx) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".service;\n\n");

        code.append("import dev.appget.specification.EvaluableRule;\n");
        code.append("import dev.appget.specification.MetadataContext;\n");
        code.append("import ").append(basePackage).append(".dto.RuleOutcome;\n");
        code.append("import ").append(basePackage).append(".dto.RuleEvaluationResult;\n");
        code.append("import org.springframework.stereotype.Service;\n");
        code.append("import java.util.ArrayList;\n");
        code.append("import java.util.HashMap;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Map;\n\n");

        code.append("/**\n");
        code.append(" * Evaluates business rules using pre-compiled specification classes.\n");
        code.append(" * Calls specs through the {@link EvaluableRule} interface — no reflection.\n");
        code.append(" * Per EJ Item 64: refer to objects by their interfaces.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("@Service\n");
        code.append("public class RuleService {\n\n");

        code.append("    private final SpecificationRegistry registry;\n");
        code.append("    private static final Map<String, Boolean> BLOCKING_RULES = new HashMap<>();\n\n");

        code.append("    static {\n");
        for (Map.Entry<String, Boolean> entry : ctx.blockingMap.entrySet()) {
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
        code.append("        List<EvaluableRule> applicableSpecs = registry.getByTarget(modelName);\n\n");

        code.append("        for (EvaluableRule spec : applicableSpecs) {\n");
        code.append("            String ruleName = spec.getClass().getSimpleName();\n");
        code.append("            boolean satisfied = spec.evaluate(target, metadata);\n");
        code.append("            String status = spec.getResult(target, metadata);\n");
        code.append("            RuleOutcome outcome = RuleOutcome.builder()\n");
        code.append("                .ruleName(ruleName)\n");
        code.append("                .status(status)\n");
        code.append("                .satisfied(satisfied)\n");
        code.append("                .build();\n");
        code.append("            outcomes.add(outcome);\n\n");

        code.append("            boolean isBlocking = BLOCKING_RULES.getOrDefault(ruleName, false);\n");
        code.append("            if (isBlocking && !outcome.isSatisfied()) {\n");
        code.append("                hasFailures = true;\n");
        code.append("            }\n");
        code.append("        }\n\n");

        code.append("        return new RuleEvaluationResult(outcomes, hasFailures);\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitSpecificationRegistry(String basePackage, RuleEmitContext ctx) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".service;\n\n");

        // Import each generated spec class
        for (RuleEmitContext.RuleEntry rule : ctx.modelRules) {
            code.append("import dev.appget.specification.generated.").append(rule.name).append(";\n");
        }

        code.append("import dev.appget.specification.EvaluableRule;\n");
        code.append("import org.springframework.stereotype.Component;\n");
        code.append("import java.util.Collection;\n");
        code.append("import java.util.LinkedHashMap;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Map;\n");
        code.append("import java.util.stream.Collectors;\n\n");

        code.append("/**\n");
        code.append(" * Registry of all compiled specification classes.\n");
        code.append(" * Per EJ Item 64: refer to objects by their interfaces.\n");
        code.append(" * DO NOT EDIT MANUALLY - Regenerated from specs.yaml when rules change.\n");
        code.append(" */\n");
        code.append("@Component\n");
        code.append("public class SpecificationRegistry {\n");
        code.append("    private final Map<String, EvaluableRule> specs = new LinkedHashMap<>();\n\n");

        code.append("    public SpecificationRegistry() {\n");
        for (RuleEmitContext.RuleEntry rule : ctx.modelRules) {
            code.append("        register(\"").append(rule.name).append("\", new ").append(rule.name).append("());\n");
        }
        code.append("    }\n\n");

        code.append("    private void register(String name, EvaluableRule spec) {\n");
        code.append("        specs.put(name, spec);\n");
        code.append("    }\n\n");

        code.append("    /** Retrieve a single spec by rule name. Returns null if not found. */\n");
        code.append("    public EvaluableRule get(String name) {\n");
        code.append("        return specs.get(name);\n");
        code.append("    }\n\n");

        code.append("    /** All registered specs. */\n");
        code.append("    public Collection<EvaluableRule> getAll() {\n");
        code.append("        return specs.values();\n");
        code.append("    }\n\n");

        // Build static target map: ruleName -> PascalCase model name
        code.append("    private static final Map<String, String> SPEC_TARGETS = new java.util.HashMap<>();\n");
        code.append("    static {\n");
        for (RuleEmitContext.RuleEntry rule : ctx.modelRules) {
            String target = ctx.ruleTargetMap.getOrDefault(rule.name, JavaUtils.snakeToPascal(rule.targetName));
            code.append("        SPEC_TARGETS.put(\"").append(rule.name).append("\", \"").append(target).append("\");\n");
        }
        code.append("    }\n\n");

        code.append("    /**\n");
        code.append("     * All specs whose target model matches the given class simple name.\n");
        code.append("     */\n");
        code.append("    public List<EvaluableRule> getByTarget(String modelName) {\n");
        code.append("        return specs.values().stream()\n");
        code.append("            .filter(s -> modelName.equals(SPEC_TARGETS.get(s.getClass().getSimpleName())))\n");
        code.append("            .collect(Collectors.toList());\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitGlobalExceptionHandler(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".exception;\n\n");

        code.append("import ").append(basePackage).append(".dto.ErrorResponse;\n");
        code.append("import org.springframework.http.HttpStatus;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.http.converter.HttpMessageNotReadableException;\n");
        code.append("import org.springframework.web.bind.annotation.ControllerAdvice;\n");
        code.append("import org.springframework.web.bind.annotation.ExceptionHandler;\n");
        code.append("import java.time.OffsetDateTime;\n\n");

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
        code.append("            .timestamp(OffsetDateTime.now())\n");
        code.append("            .build();\n");
        code.append("        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);\n");
        code.append("    }\n\n");

        code.append("    @ExceptionHandler(ResourceNotFoundException.class)\n");
        code.append("    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {\n");
        code.append("        ErrorResponse response = ErrorResponse.builder()\n");
        code.append("            .errorCode(\"NOT_FOUND\")\n");
        code.append("            .message(ex.getMessage())\n");
        code.append("            .timestamp(OffsetDateTime.now())\n");
        code.append("            .build();\n");
        code.append("        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);\n");
        code.append("    }\n\n");

        code.append("    @ExceptionHandler(MetadataParsingException.class)\n");
        code.append("    public ResponseEntity<ErrorResponse> handleMetadataParsing(MetadataParsingException ex) {\n");
        code.append("        ErrorResponse response = ErrorResponse.builder()\n");
        code.append("            .errorCode(\"INVALID_METADATA\")\n");
        code.append("            .message(ex.getMessage())\n");
        code.append("            .timestamp(OffsetDateTime.now())\n");
        code.append("            .build();\n");
        code.append("        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);\n");
        code.append("    }\n\n");

        code.append("    @ExceptionHandler(HttpMessageNotReadableException.class)\n");
        code.append("    public ResponseEntity<ErrorResponse> handleBadRequest(HttpMessageNotReadableException ex) {\n");
        code.append("        ErrorResponse response = ErrorResponse.builder()\n");
        code.append("            .errorCode(\"BAD_REQUEST\")\n");
        code.append("            .message(\"Invalid request body: \" + ex.getMostSpecificCause().getMessage())\n");
        code.append("            .timestamp(OffsetDateTime.now())\n");
        code.append("            .build();\n");
        code.append("        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);\n");
        code.append("    }\n\n");

        code.append("    @ExceptionHandler(Exception.class)\n");
        code.append("    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {\n");
        code.append("        ErrorResponse response = ErrorResponse.builder()\n");
        code.append("            .errorCode(\"INTERNAL_ERROR\")\n");
        code.append("            .message(ex.getMessage())\n");
        code.append("            .timestamp(OffsetDateTime.now())\n");
        code.append("            .build();\n");
        code.append("        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Group B: DTOs
    // -------------------------------------------------------------------------

    @Override
    public String emitRuleAwareResponse(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".dto;\n\n");

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

        return code.toString();
    }

    @Override
    public String emitRuleEvaluationResult(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".dto;\n\n");

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

        return code.toString();
    }

    @Override
    public String emitRuleOutcome(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".dto;\n\n");

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

        return code.toString();
    }

    @Override
    public String emitErrorResponse(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".dto;\n\n");

        code.append("import lombok.AllArgsConstructor;\n");
        code.append("import lombok.Builder;\n");
        code.append("import lombok.Data;\n");
        code.append("import lombok.NoArgsConstructor;\n");
        code.append("import java.time.OffsetDateTime;\n\n");

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
        code.append("    private OffsetDateTime timestamp;\n");
        code.append("}\n");

        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Group B: Exceptions
    // -------------------------------------------------------------------------

    @Override
    public String emitRuleViolationException(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".exception;\n\n");

        code.append("import ").append(basePackage).append(".dto.RuleEvaluationResult;\n\n");

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

        return code.toString();
    }

    @Override
    public String emitResourceNotFoundException(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".exception;\n\n");

        code.append("/**\n");
        code.append(" * Thrown when a requested resource is not found\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("public class ResourceNotFoundException extends RuntimeException {\n");
        code.append("    public ResourceNotFoundException(String message) {\n");
        code.append("        super(message);\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitMetadataParsingException(String basePackage) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(basePackage).append(".exception;\n\n");

        code.append("/**\n");
        code.append(" * Thrown when a metadata header value cannot be parsed to the expected type\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("public class MetadataParsingException extends RuntimeException {\n");
        code.append("    public MetadataParsingException(String message) {\n");
        code.append("        super(message);\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Group B: Security
    // -------------------------------------------------------------------------

    @Override
    public String emitHtmlEscapeUtils(String basePackage) {
        String pkg = basePackage + ".util";
        StringBuilder code = new StringBuilder();
        code.append("package ").append(pkg).append(";\n\n");
        code.append("/**\n");
        code.append(" * Centralized HTML entity escaping utility for PageRenderer output.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from AppServerGenerator\n");
        code.append(" */\n");
        code.append("public final class HtmlEscapeUtils {\n\n");
        code.append("    private HtmlEscapeUtils() {}\n\n");
        code.append("    public static String escape(String value) {\n");
        code.append("        if (value == null) {\n");
        code.append("            return \"\";\n");
        code.append("        }\n");
        code.append("        StringBuilder sb = new StringBuilder(value.length());\n");
        code.append("        for (int i = 0; i < value.length(); i++) {\n");
        code.append("            char c = value.charAt(i);\n");
        code.append("            if (c == '&') {\n");
        code.append("                sb.append(\"&amp;\");\n");
        code.append("            } else if (c == '<') {\n");
        code.append("                sb.append(\"&lt;\");\n");
        code.append("            } else if (c == '>') {\n");
        code.append("                sb.append(\"&gt;\");\n");
        code.append("            } else if (c == '\"') {\n");
        code.append("                sb.append(\"&quot;\");\n");
        code.append("            } else if (c == '\\'') {\n");
        code.append("                sb.append(\"&#x27;\");\n");
        code.append("            } else {\n");
        code.append("                sb.append(c);\n");
        code.append("            }\n");
        code.append("        }\n");
        code.append("        return sb.toString();\n");
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates the Java expression to parse a string header value into the target type.
     * Produces type-aware runtime parsing code (Boolean.parseBoolean, Integer.parseInt, etc.).
     * Supports both neutral types (bool, int32, int64, float64) and legacy Java types.
     */
    private String parseHeaderValue(String varName, String type, String headerName) {
        // Boolean.parseBoolean never throws, so no safe wrapper needed
        if ("boolean".equals(type) || "bool".equals(type)) {
            return varName + " != null ? Boolean.parseBoolean(" + varName + ") : false";
        } else if ("int".equals(type) || "int32".equals(type)) {
            return "safeParseInt(" + varName + ", \"" + headerName + "\")";
        } else if ("long".equals(type) || "int64".equals(type)) {
            return "safeParseLong(" + varName + ", \"" + headerName + "\")";
        } else if ("float".equals(type)) {
            return "safeParseFloat(" + varName + ", \"" + headerName + "\")";
        } else if ("double".equals(type) || "float64".equals(type)) {
            return "safeParseDouble(" + varName + ", \"" + headerName + "\")";
        } else {
            return varName + " != null ? " + varName + " : \"\"";
        }
    }

    // -------------------------------------------------------------------------
    // Group B (continued): Root index controller
    // -------------------------------------------------------------------------

    @Override
    public String emitRootController(String basePackage) {
        String packageName = basePackage + ".controller";
        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import org.springframework.http.MediaType;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.web.bind.annotation.GetMapping;\n");
        code.append("import org.springframework.web.bind.annotation.RestController;\n");
        code.append("import org.apache.logging.log4j.LogManager;\n");
        code.append("import org.apache.logging.log4j.Logger;\n");
        code.append("import java.io.IOException;\n");
        code.append("import java.io.InputStream;\n");
        code.append("import java.nio.charset.StandardCharsets;\n\n");

        code.append("/**\n");
        code.append(" * Generated controller that serves the root index page at {@code GET /}.\n");
        code.append(" * Loads {@code templates/index.html} from the classpath and returns it as text/html.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from AppServerGenerator\n");
        code.append(" */\n");
        code.append("@RestController\n");
        code.append("public class RootController {\n\n");

        code.append("    private static final Logger log = LogManager.getLogger(RootController.class);\n\n");

        code.append("    private final String indexHtml;\n\n");

        code.append("    public RootController() {\n");
        code.append("        this.indexHtml = loadTemplate(\"templates/index.html\");\n");
        code.append("    }\n\n");

        code.append("    @GetMapping(value = \"/\", produces = MediaType.TEXT_HTML_VALUE)\n");
        code.append("    public ResponseEntity<String> index() {\n");
        code.append("        log.info(\"GET / - Serving root index page\");\n");
        code.append("        return ResponseEntity.ok(indexHtml);\n");
        code.append("    }\n\n");

        code.append("    private static String loadTemplate(String path) {\n");
        code.append("        try (InputStream in = RootController.class.getClassLoader().getResourceAsStream(path)) {\n");
        code.append("            if (in == null) {\n");
        code.append("                throw new IllegalStateException(\"Template not found on classpath: \" + path);\n");
        code.append("            }\n");
        code.append("            byte[] bytes = in.readAllBytes();\n");
        code.append("            return new String(bytes, StandardCharsets.UTF_8);\n");
        code.append("        } catch (IOException e) {\n");
        code.append("            throw new IllegalStateException(\"Failed to load template: \" + path, e);\n");
        code.append("        }\n");
        code.append("    }\n");
        code.append("}\n");
        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Group C: Per-Entity CRUD (models)
    // -------------------------------------------------------------------------

    @Override
    public String emitRepositoryInterface(String basePackage, EntityContext ctx) {
        String interfaceName = ctx.pascalName + "Repository";
        String packageName = basePackage + ".repository";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(ctx.namespace).append(".model.").append(ctx.pascalName).append(";\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Optional;\n\n");

        code.append("/**\n");
        code.append(" * Repository interface for ").append(ctx.name).append(" entities\n");
        code.append(" * Implement this interface to provide custom data access (database, cache, etc.)\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("public interface ").append(interfaceName).append(" {\n\n");

        code.append("    ").append(ctx.pascalName).append(" save(").append(ctx.pascalName).append(" entity);\n\n");
        code.append("    Optional<").append(ctx.pascalName).append("> findById(").append(ctx.idParams).append(");\n\n");
        code.append("    List<").append(ctx.pascalName).append("> findAll();\n\n");
        code.append("    void deleteById(").append(ctx.idParams).append(");\n\n");
        code.append("    boolean existsById(").append(ctx.idParams).append(");\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitInMemoryRepository(String basePackage, EntityContext ctx) {
        String className = "InMemory" + ctx.pascalName + "Repository";
        String interfaceName = ctx.pascalName + "Repository";
        String packageName = basePackage + ".repository";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(ctx.namespace).append(".model.").append(ctx.pascalName).append(";\n");
        code.append("import org.springframework.stereotype.Component;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import java.util.Map;\n");
        code.append("import java.util.Optional;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.concurrent.ConcurrentHashMap;\n");
        code.append("import java.util.concurrent.atomic.AtomicLong;\n");
        code.append("import java.util.stream.Collectors;\n\n");

        code.append("/**\n");
        code.append(" * Default in-memory repository for ").append(ctx.name).append(" entities\n");
        code.append(" * Replace by providing a @Primary bean of type ").append(interfaceName).append("\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@Component\n");
        code.append("public class ").append(className).append(" implements ").append(interfaceName).append(" {\n\n");

        code.append("    private final Map<String, ").append(ctx.pascalName).append("> store = new ConcurrentHashMap<>();\n");
        if (!ctx.hasIdField && !ctx.compositeKey) {
            code.append("    private final AtomicLong idGenerator = new AtomicLong();\n");
        }
        code.append("\n");

        // save method
        code.append("    @Override\n");
        code.append("    public ").append(ctx.pascalName).append(" save(").append(ctx.pascalName).append(" entity) {\n");
        if (ctx.compositeKey) {
            code.append("        String key = ").append(ctx.entityCompositeKeyExpr).append(";\n");
            code.append("        log.debug(\"Saving ").append(ctx.name).append(" with key: {}\", key);\n");
            code.append("        store.put(key, entity);\n");
            code.append("        log.info(\"Successfully saved ").append(ctx.name).append(" with key: {}\", key);\n");
        } else if (ctx.hasIdField) {
            code.append("        String id = entity.getId();\n");
            code.append("        log.debug(\"Saving ").append(ctx.name).append(" with id: {}\", id);\n");
            code.append("        store.put(id, entity);\n");
            code.append("        log.info(\"Successfully saved ").append(ctx.name).append(" with id: {}\", id);\n");
        } else {
            code.append("        String id = String.valueOf(idGenerator.incrementAndGet());\n");
            code.append("        log.debug(\"Saving ").append(ctx.name).append(" with id: {}\", id);\n");
            code.append("        store.put(id, entity);\n");
            code.append("        log.info(\"Successfully saved ").append(ctx.name).append(" with id: {}\", id);\n");
        }
        code.append("        return entity;\n");
        code.append("    }\n\n");

        // findById method
        code.append("    @Override\n");
        code.append("    public Optional<").append(ctx.pascalName).append("> findById(").append(ctx.idParams).append(") {\n");
        if (ctx.compositeKey) {
            code.append("        String key = ").append(ctx.compositeKeyExpr).append(";\n");
            code.append("        log.debug(\"Looking up ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
            code.append("        Optional<").append(ctx.pascalName).append("> result = Optional.ofNullable(store.get(key));\n");
        } else {
            code.append("        log.debug(\"Looking up ").append(ctx.name).append(" with id: {}\", id);\n");
            code.append("        Optional<").append(ctx.pascalName).append("> result = Optional.ofNullable(store.get(id));\n");
        }
        code.append("        if (result.isPresent()) {\n");
        code.append("            log.debug(\"Found ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        } else {\n");
        code.append("            log.debug(\"").append(ctx.name).append(" not found with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        }\n");
        code.append("        return result;\n");
        code.append("    }\n\n");

        // findAll method
        code.append("    @Override\n");
        code.append("    public List<").append(ctx.pascalName).append("> findAll() {\n");
        code.append("        log.debug(\"Retrieving all ").append(ctx.name).append(" entities\");\n");
        code.append("        List<").append(ctx.pascalName).append("> results = new java.util.ArrayList<>(store.values());\n");
        code.append("        log.debug(\"Found {} ").append(ctx.name).append(" entities\", results.size());\n");
        code.append("        return results;\n");
        code.append("    }\n\n");

        // deleteById method
        code.append("    @Override\n");
        code.append("    public void deleteById(").append(ctx.idParams).append(") {\n");
        if (ctx.compositeKey) {
            code.append("        String key = ").append(ctx.compositeKeyExpr).append(";\n");
            code.append("        log.debug(\"Deleting ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
            code.append("        store.remove(key);\n");
            code.append("        log.info(\"Successfully deleted ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        } else {
            code.append("        log.debug(\"Deleting ").append(ctx.name).append(" with id: {}\", id);\n");
            code.append("        store.remove(id);\n");
            code.append("        log.info(\"Successfully deleted ").append(ctx.name).append(" with id: {}\", id);\n");
        }
        code.append("    }\n\n");

        // existsById method
        code.append("    @Override\n");
        code.append("    public boolean existsById(").append(ctx.idParams).append(") {\n");
        if (ctx.compositeKey) {
            code.append("        String key = ").append(ctx.compositeKeyExpr).append(";\n");
            code.append("        boolean exists = store.containsKey(key);\n");
            code.append("        log.debug(\"Checking existence of ").append(ctx.name).append(" with ").append(ctx.logPattern).append(", exists: {}\", ").append(ctx.logArgs).append(", exists);\n");
        } else {
            code.append("        boolean exists = store.containsKey(id);\n");
            code.append("        log.debug(\"Checking existence of ").append(ctx.name).append(" with id: {}, exists: {}\", id, exists);\n");
        }
        code.append("        return exists;\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitService(String basePackage, EntityContext ctx) {
        String className = ctx.pascalName + "Service";
        String repositoryClass = ctx.pascalName + "Repository";
        String packageName = basePackage + ".service";

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(ctx.namespace).append(".model.").append(ctx.pascalName).append(";\n");
        code.append("import ").append(basePackage).append(".repository.").append(repositoryClass).append(";\n");
        code.append("import ").append(basePackage).append(".dto.RuleAwareResponse;\n");
        code.append("import ").append(basePackage).append(".dto.RuleEvaluationResult;\n");
        code.append("import ").append(basePackage).append(".exception.ResourceNotFoundException;\n");
        code.append("import ").append(basePackage).append(".exception.RuleViolationException;\n");
        code.append("import dev.appget.specification.MetadataContext;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import org.springframework.stereotype.Service;\n");
        code.append("import java.util.List;\n\n");

        code.append("/**\n");
        code.append(" * Business logic service for ").append(ctx.name).append(" with rule evaluation\n");
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

        // create method
        code.append("    public RuleAwareResponse<").append(ctx.pascalName).append("> create(").append(ctx.pascalName).append(" entity, MetadataContext metadata) {\n");
        code.append("        log.info(\"Creating new ").append(ctx.name).append(" entity\");\n");
        code.append("        log.debug(\"Entity data: {}\", entity);\n");
        code.append("        RuleEvaluationResult ruleResult = ruleService.evaluateAll(entity, metadata);\n");
        code.append("        log.debug(\"Rule evaluation completed with hasFailures: {}\", ruleResult.isHasFailures());\n");
        code.append("        if (ruleResult.isHasFailures()) {\n");
        code.append("            log.warn(\"").append(ctx.name).append(" creation failed validation\");\n");
        code.append("            throw new RuleViolationException(\"Validation failed\", ruleResult);\n");
        code.append("        }\n");
        code.append("        ").append(ctx.pascalName).append(" saved = repository.save(entity);\n");
        code.append("        log.info(\"Successfully created ").append(ctx.name).append(" entity\");\n");
        code.append("        return RuleAwareResponse.<").append(ctx.pascalName).append(">builder()\n");
        code.append("            .data(saved)\n");
        code.append("            .ruleResults(ruleResult)\n");
        code.append("            .build();\n");
        code.append("    }\n\n");

        // findById method
        code.append("    public ").append(ctx.pascalName).append(" findById(").append(ctx.idParams).append(") {\n");
        code.append("        log.debug(\"Fetching ").append(ctx.name).append(" by ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        return repository.findById(").append(ctx.idArgs).append(")\n");
        code.append("            .orElseThrow(() -> {\n");
        code.append("                log.warn(\"").append(ctx.name).append(" not found with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("                return new ResourceNotFoundException(").append(ctx.notFoundMsg).append(");\n");
        code.append("            });\n");
        code.append("    }\n\n");

        // findAll method
        code.append("    public List<").append(ctx.pascalName).append("> findAll() {\n");
        code.append("        log.debug(\"Fetching all ").append(ctx.name).append(" entities\");\n");
        code.append("        return repository.findAll();\n");
        code.append("    }\n\n");

        // update method
        code.append("    public RuleAwareResponse<").append(ctx.pascalName).append("> update(").append(ctx.idParams).append(", ").append(ctx.pascalName).append(" entity, MetadataContext metadata) {\n");
        code.append("        log.info(\"Updating ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        if (!repository.existsById(").append(ctx.idArgs).append(")) {\n");
        code.append("            log.warn(\"").append(ctx.name).append(" not found for update with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("            throw new ResourceNotFoundException(").append(ctx.notFoundMsg).append(");\n");
        code.append("        }\n");
        code.append("        log.debug(\"Entity data: {}\", entity);\n");
        code.append("        RuleEvaluationResult ruleResult = ruleService.evaluateAll(entity, metadata);\n");
        code.append("        log.debug(\"Rule evaluation completed with hasFailures: {}\", ruleResult.isHasFailures());\n");
        code.append("        if (ruleResult.isHasFailures()) {\n");
        code.append("            log.warn(\"").append(ctx.name).append(" update failed validation for ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("            throw new RuleViolationException(\"Validation failed\", ruleResult);\n");
        code.append("        }\n");
        code.append("        ").append(ctx.pascalName).append(" updated = repository.save(entity);\n");
        code.append("        log.info(\"Successfully updated ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        return RuleAwareResponse.<").append(ctx.pascalName).append(">builder()\n");
        code.append("            .data(updated)\n");
        code.append("            .ruleResults(ruleResult)\n");
        code.append("            .build();\n");
        code.append("    }\n\n");

        // deleteById method
        code.append("    public void deleteById(").append(ctx.idParams).append(") {\n");
        code.append("        log.info(\"Deleting ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        if (!repository.existsById(").append(ctx.idArgs).append(")) {\n");
        code.append("            log.warn(\"").append(ctx.name).append(" not found for deletion with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("            throw new ResourceNotFoundException(").append(ctx.notFoundMsg).append(");\n");
        code.append("        }\n");
        code.append("        repository.deleteById(").append(ctx.idArgs).append(");\n");
        code.append("        log.info(\"Successfully deleted ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitController(String basePackage, EntityContext ctx) {
        String className = ctx.pascalName + "Controller";
        String serviceClass = ctx.pascalName + "Service";
        String rendererClass = ctx.pascalName + "PageRenderer";
        String rendererField = Character.toLowerCase(ctx.pascalName.charAt(0)) + ctx.pascalName.substring(1) + "PageRenderer";
        String packageName = basePackage + ".controller";
        String resourcePath = "/" + ctx.resourcePath;

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(ctx.namespace).append(".model.").append(ctx.pascalName).append(";\n");
        code.append("import ").append(basePackage).append(".service.").append(serviceClass).append(";\n");
        code.append("import ").append(basePackage).append(".config.MetadataExtractor;\n");
        code.append("import ").append(basePackage).append(".dto.RuleAwareResponse;\n");
        code.append("import ").append(basePackage).append(".exception.RuleViolationException;\n");
        code.append("import dev.appget.specification.MetadataContext;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import org.springframework.http.HttpStatus;\n");
        code.append("import org.springframework.http.MediaType;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.web.bind.annotation.*;\n");
        code.append("import jakarta.servlet.http.HttpServletRequest;\n");
        code.append("import jakarta.validation.Valid;\n");
        code.append("import java.math.BigDecimal;\n");
        code.append("import java.net.URI;\n");
        code.append("import java.util.ArrayList;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.Map;\n");
        code.append("import com.google.protobuf.ByteString;\n\n");

        // Conditionally import Decimal only when any decimal field exists
        boolean hasDecimalField = false;
        for (Map<String, Object> field : ctx.fields) {
            if ("decimal".equals(field.get("type"))) {
                hasDecimalField = true;
                break;
            }
        }
        if (hasDecimalField) {
            code.append("import dev.appget.common.Decimal;\n\n");
        }

        code.append("/**\n");
        code.append(" * REST API endpoints for ").append(ctx.name).append(" entities\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml and specs.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@RestController\n");
        code.append("@RequestMapping(\"").append(resourcePath).append("\")\n");
        code.append("public class ").append(className).append(" {\n\n");

        code.append("    private final ").append(serviceClass).append(" service;\n");
        code.append("    private final MetadataExtractor metadataExtractor;\n");
        code.append("    private final ").append(rendererClass).append(" renderer;\n\n");

        code.append("    public ").append(className).append("(").append(serviceClass).append(" service, MetadataExtractor metadataExtractor, ").append(rendererClass).append(" ").append(rendererField).append(") {\n");
        code.append("        this.service = service;\n");
        code.append("        this.metadataExtractor = metadataExtractor;\n");
        code.append("        this.renderer = ").append(rendererField).append(";\n");
        code.append("    }\n\n");

        // POST /entities (JSON)
        code.append("    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)\n");
        code.append("    public ResponseEntity<RuleAwareResponse<").append(ctx.pascalName).append(">> create(\n");
        code.append("            @Valid @RequestBody ").append(ctx.pascalName).append(" entity,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"POST ").append(resourcePath).append(" - Creating new ").append(ctx.name).append("\");\n");
        code.append("        log.debug(\"Request headers: {} {}\", request.getMethod(), request.getRequestURI());\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        RuleAwareResponse<").append(ctx.pascalName).append("> response = service.create(entity, metadata);\n");
        code.append("        log.info(\"Successfully created ").append(ctx.name).append(" with status 201\");\n");
        code.append("        return ResponseEntity.status(HttpStatus.CREATED).body(response);\n");
        code.append("    }\n\n");

        // POST /entities (HTML form — application/x-www-form-urlencoded) → PRG
        code.append("    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)\n");
        code.append("    public ResponseEntity<String> createForm(\n");
        code.append("            @RequestParam Map<String,String> form,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"POST ").append(resourcePath).append(" (form) - Creating new ").append(ctx.name).append("\");\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        ").append(ctx.pascalName).append(" entity = coerce").append(ctx.pascalName).append("(form);\n");
        code.append("        try {\n");
        code.append("            service.create(entity, metadata);\n");
        code.append("            log.info(\"Form create ").append(ctx.name).append(" succeeded, redirecting\");\n");
        code.append("            return ResponseEntity.status(HttpStatus.SEE_OTHER)\n");
        code.append("                .location(URI.create(\"").append(resourcePath).append("\"))\n");
        code.append("                .build();\n");
        code.append("        } catch (RuleViolationException e) {\n");
        code.append("            log.warn(\"Form create ").append(ctx.name).append(" failed validation: {}\", e.getMessage());\n");
        code.append("            List<String> errors = new ArrayList<>();\n");
        code.append("            if (e.getResults() != null && e.getResults().getOutcomes() != null) {\n");
        code.append("                for (var outcome : e.getResults().getOutcomes()) {\n");
        code.append("                    if (!outcome.isSatisfied()) {\n");
        code.append("                        errors.add(outcome.getRuleName() + \": \" + outcome.getStatus());\n");
        code.append("                    }\n");
        code.append("                }\n");
        code.append("            }\n");
        code.append("            if (errors.isEmpty()) {\n");
        code.append("                errors.add(e.getMessage());\n");
        code.append("            }\n");
        code.append("            String html = renderer.renderCreateFormWithErrors(form, errors);\n");
        code.append("            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)\n");
        code.append("                .contentType(MediaType.TEXT_HTML)\n");
        code.append("                .body(html);\n");
        code.append("        }\n");
        code.append("    }\n\n");

        // GET /entities (JSON)
        code.append("    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)\n");
        code.append("    public ResponseEntity<List<").append(ctx.pascalName).append(">> list() {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(" - Retrieving all ").append(ctx.name).append(" entities\");\n");
        code.append("        List<").append(ctx.pascalName).append("> results = service.findAll();\n");
        code.append("        log.info(\"Retrieved {} ").append(ctx.name).append(" entities\", results.size());\n");
        code.append("        return ResponseEntity.ok(results);\n");
        code.append("    }\n\n");

        // GET /entities (HTML)
        code.append("    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)\n");
        code.append("    public ResponseEntity<String> listHtml(@RequestParam(required = false) String action) {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(" (html) - action={}\", action);\n");
        code.append("        String html;\n");
        code.append("        if (\"create\".equals(action)) {\n");
        code.append("            html = renderer.renderCreateForm();\n");
        code.append("        } else {\n");
        code.append("            html = renderer.renderList(service.findAll());\n");
        code.append("        }\n");
        code.append("        return ResponseEntity.ok(html);\n");
        code.append("    }\n\n");

        // GET /entities/{id} (JSON)
        code.append("    @GetMapping(value = \"").append(ctx.pathVarSegment).append("\", produces = MediaType.APPLICATION_JSON_VALUE)\n");
        code.append("    public ResponseEntity<").append(ctx.pascalName).append("> get(").append(ctx.pathVarParams).append(") {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(ctx.pathVarSegment).append(" - Retrieving ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        ").append(ctx.pascalName).append(" result = service.findById(").append(ctx.idArgs).append(");\n");
        code.append("        log.info(\"Found ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        return ResponseEntity.ok(result);\n");
        code.append("    }\n\n");

        // GET /entities/{id} (HTML)
        code.append("    @GetMapping(value = \"").append(ctx.pathVarSegment).append("\", produces = MediaType.TEXT_HTML_VALUE)\n");
        code.append("    public ResponseEntity<String> getHtml(").append(ctx.pathVarParams).append(", @RequestParam(defaultValue = \"view\") String action) {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(ctx.pathVarSegment).append(" (html) - action={}\", action);\n");
        code.append("        String html;\n");
        code.append("        if (\"edit\".equals(action)) {\n");
        code.append("            html = renderer.renderEditForm(service.findById(").append(ctx.idArgs).append("));\n");
        code.append("        } else {\n");
        code.append("            html = renderer.renderDetail(service.findById(").append(ctx.idArgs).append("));\n");
        code.append("        }\n");
        code.append("        return ResponseEntity.ok(html);\n");
        code.append("    }\n\n");

        // PUT /entities/{id} (JSON)
        code.append("    @PutMapping(value = \"").append(ctx.pathVarSegment).append("\", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)\n");
        code.append("    public ResponseEntity<RuleAwareResponse<").append(ctx.pascalName).append(">> update(\n");
        code.append("            ").append(ctx.pathVarParams).append(",\n");
        code.append("            @Valid @RequestBody ").append(ctx.pascalName).append(" entity,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"PUT ").append(resourcePath).append(ctx.pathVarSegment).append(" - Updating ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        log.debug(\"Request headers: {} {}\", request.getMethod(), request.getRequestURI());\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        RuleAwareResponse<").append(ctx.pascalName).append("> response = service.update(").append(ctx.idArgs).append(", entity, metadata);\n");
        code.append("        log.info(\"Successfully updated ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        return ResponseEntity.ok(response);\n");
        code.append("    }\n\n");

        // PUT /entities/{id} (HTML form — via _method=PUT override) → PRG
        code.append("    @PutMapping(value = \"").append(ctx.pathVarSegment).append("\", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)\n");
        code.append("    public ResponseEntity<String> updateForm(\n");
        code.append("            ").append(ctx.pathVarParams).append(",\n");
        code.append("            @RequestParam Map<String,String> form,\n");
        code.append("            HttpServletRequest request) {\n");
        code.append("        log.info(\"PUT ").append(resourcePath).append(ctx.pathVarSegment).append(" (form) - Updating ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        MetadataContext metadata = metadataExtractor.extractFromHeaders(request);\n");
        code.append("        ").append(ctx.pascalName).append(" entity = coerce").append(ctx.pascalName).append("(form);\n");
        code.append("        try {\n");
        code.append("            service.update(").append(ctx.idArgs).append(", entity, metadata);\n");
        code.append("            log.info(\"Form update ").append(ctx.name).append(" succeeded, redirecting\");\n");
        // Build the detail redirect path. For single-key entities with id, use resourcePath/id.
        // For composite keys build from idArgs (comma-separated).
        if (!ctx.compositeKey) {
            // idArgs is a single variable name like "id"
            code.append("            return ResponseEntity.status(HttpStatus.SEE_OTHER)\n");
            code.append("                .location(URI.create(\"").append(resourcePath).append("/\" + ").append(ctx.idArgs).append("))\n");
            code.append("                .build();\n");
        } else {
            // Composite key: redirect to list since detail URL is ambiguous to construct simply
            code.append("            return ResponseEntity.status(HttpStatus.SEE_OTHER)\n");
            code.append("                .location(URI.create(\"").append(resourcePath).append("\"))\n");
            code.append("                .build();\n");
        }
        code.append("        } catch (RuleViolationException e) {\n");
        code.append("            log.warn(\"Form update ").append(ctx.name).append(" failed validation: {}\", e.getMessage());\n");
        code.append("            List<String> errors = new ArrayList<>();\n");
        code.append("            if (e.getResults() != null && e.getResults().getOutcomes() != null) {\n");
        code.append("                for (var outcome : e.getResults().getOutcomes()) {\n");
        code.append("                    if (!outcome.isSatisfied()) {\n");
        code.append("                        errors.add(outcome.getRuleName() + \": \" + outcome.getStatus());\n");
        code.append("                    }\n");
        code.append("                }\n");
        code.append("            }\n");
        code.append("            if (errors.isEmpty()) {\n");
        code.append("                errors.add(e.getMessage());\n");
        code.append("            }\n");
        code.append("            String html = renderer.renderEditFormWithErrors(form, errors);\n");
        code.append("            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)\n");
        code.append("                .contentType(MediaType.TEXT_HTML)\n");
        code.append("                .body(html);\n");
        code.append("        }\n");
        code.append("    }\n\n");

        // DELETE /entities/{id} (JSON or form)
        code.append("    @DeleteMapping(\"").append(ctx.pathVarSegment).append("\")\n");
        code.append("    public ResponseEntity<Void> delete(").append(ctx.pathVarParams).append(") {\n");
        code.append("        log.info(\"DELETE ").append(resourcePath).append(ctx.pathVarSegment).append(" - Deleting ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        service.deleteById(").append(ctx.idArgs).append(");\n");
        code.append("        log.info(\"Successfully deleted ").append(ctx.name).append(" with ").append(ctx.logPattern).append("\", ").append(ctx.logArgs).append(");\n");
        code.append("        return ResponseEntity.noContent().build();\n");
        code.append("    }\n\n");

        // Private coerce method: form Map<String,String> → entity
        code.append("    private ").append(ctx.pascalName).append(" coerce").append(ctx.pascalName).append("(Map<String,String> form) {\n");
        code.append("        ").append(ctx.pascalName).append(".Builder builder = ").append(ctx.pascalName).append(".newBuilder();\n");
        for (Map<String, Object> field : ctx.fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            String camelName = JavaNaming.toFieldAccessor(fieldName);
            String setterName = "set" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);

            if ("string".equals(fieldType)) {
                code.append("        builder.").append(setterName).append("(form.getOrDefault(\"").append(fieldName).append("\", \"\"));\n");
            } else if ("int32".equals(fieldType)) {
                code.append("        {\n");
                code.append("            String _v = form.get(\"").append(fieldName).append("\");\n");
                code.append("            if (_v != null && !_v.isBlank()) {\n");
                code.append("                builder.").append(setterName).append("(Integer.parseInt(_v));\n");
                code.append("            }\n");
                code.append("        }\n");
            } else if ("int64".equals(fieldType)) {
                code.append("        {\n");
                code.append("            String _v = form.get(\"").append(fieldName).append("\");\n");
                code.append("            if (_v != null && !_v.isBlank()) {\n");
                code.append("                builder.").append(setterName).append("(Long.parseLong(_v));\n");
                code.append("            }\n");
                code.append("        }\n");
            } else if ("float64".equals(fieldType)) {
                code.append("        {\n");
                code.append("            String _v = form.get(\"").append(fieldName).append("\");\n");
                code.append("            if (_v != null && !_v.isBlank()) {\n");
                code.append("                builder.").append(setterName).append("(Double.parseDouble(_v));\n");
                code.append("            }\n");
                code.append("        }\n");
            } else if ("bool".equals(fieldType)) {
                // Checkbox: present in form map = true, absent = false
                code.append("        builder.").append(setterName).append("(form.containsKey(\"").append(fieldName).append("\"));\n");
            } else if ("decimal".equals(fieldType)) {
                code.append("        {\n");
                code.append("            String _v = form.get(\"").append(fieldName).append("\");\n");
                code.append("            if (_v != null && !_v.isBlank()) {\n");
                code.append("                BigDecimal _bd = new BigDecimal(_v);\n");
                code.append("                builder.").append(setterName).append("(Decimal.newBuilder()\n");
                code.append("                    .setUnscaled(ByteString.copyFrom(_bd.unscaledValue().toByteArray()))\n");
                code.append("                    .setScale(_bd.scale())\n");
                code.append("                    .build());\n");
                code.append("            }\n");
                code.append("        }\n");
            } else if ("date".equals(fieldType) || "datetime".equals(fieldType)) {
                // Proto stores date/datetime as string
                code.append("        {\n");
                code.append("            String _v = form.get(\"").append(fieldName).append("\");\n");
                code.append("            if (_v != null && !_v.isBlank()) {\n");
                code.append("                builder.").append(setterName).append("(_v);\n");
                code.append("            }\n");
                code.append("        }\n");
            } else {
                // Fallback: treat as string
                code.append("        builder.").append(setterName).append("(form.getOrDefault(\"").append(fieldName).append("\", \"\"));\n");
            }
        }
        code.append("        return builder.build();\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Group C: Per-View (read-only)
    // -------------------------------------------------------------------------

    /**
     * Emits the read-only repository interface for a view entity.
     * Declares save (for seeding), findById, and findAll operations.
     */
    @Override
    public String emitViewRepositoryInterface(String basePackage, EntityContext ctx) {
        String interfaceName = ctx.pascalName + "Repository";
        String packageName = basePackage + ".repository";
        String viewImport = ctx.namespace + ".view." + ctx.pascalName;

        StringBuilder iface = new StringBuilder();
        iface.append("package ").append(packageName).append(";\n\n");
        iface.append("import ").append(viewImport).append(";\n");
        iface.append("import java.util.List;\n");
        iface.append("import java.util.Optional;\n\n");
        iface.append("/**\n");
        iface.append(" * Read-only repository interface for ").append(ctx.name).append(" view\n");
        iface.append(" * Views expose GET only — no create/update/delete operations\n");
        iface.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        iface.append(" */\n");
        iface.append("public interface ").append(interfaceName).append(" {\n\n");
        iface.append("    ").append(ctx.pascalName).append(" save(").append(ctx.pascalName).append(" entity);\n\n");
        iface.append("    Optional<").append(ctx.pascalName).append("> findById(String id);\n\n");
        iface.append("    List<").append(ctx.pascalName).append("> findAll();\n");
        iface.append("}\n");

        return iface.toString();
    }

    /**
     * Emits the in-memory ConcurrentHashMap-backed repository implementation
     * for a view entity. Read-only view storage with save (for seeding),
     * findById, and findAll.
     */
    @Override
    public String emitInMemoryViewRepository(String basePackage, EntityContext ctx) {
        String interfaceName = ctx.pascalName + "Repository";
        String className = "InMemory" + ctx.pascalName + "Repository";
        String packageName = basePackage + ".repository";
        String viewImport = ctx.namespace + ".view." + ctx.pascalName;

        StringBuilder impl = new StringBuilder();
        impl.append("package ").append(packageName).append(";\n\n");
        impl.append("import ").append(viewImport).append(";\n");
        impl.append("import org.springframework.stereotype.Component;\n");
        impl.append("import lombok.extern.log4j.Log4j2;\n");
        impl.append("import java.util.List;\n");
        impl.append("import java.util.Map;\n");
        impl.append("import java.util.Optional;\n");
        impl.append("import java.util.concurrent.ConcurrentHashMap;\n");
        impl.append("import java.util.concurrent.atomic.AtomicLong;\n\n");
        impl.append("/**\n");
        impl.append(" * Default in-memory repository for ").append(ctx.name).append(" view\n");
        impl.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        impl.append(" */\n");
        impl.append("@Log4j2\n");
        impl.append("@Component\n");
        impl.append("public class ").append(className).append(" implements ").append(interfaceName).append(" {\n\n");
        impl.append("    private final Map<String, ").append(ctx.pascalName).append("> store = new ConcurrentHashMap<>();\n");
        impl.append("    private final AtomicLong idGenerator = new AtomicLong();\n\n");
        impl.append("    @Override\n");
        impl.append("    public ").append(ctx.pascalName).append(" save(").append(ctx.pascalName).append(" entity) {\n");
        impl.append("        String id = String.valueOf(idGenerator.incrementAndGet());\n");
        impl.append("        log.debug(\"Saving ").append(ctx.name).append(" view entry with id: {}\", id);\n");
        impl.append("        store.put(id, entity);\n");
        impl.append("        return entity;\n");
        impl.append("    }\n\n");
        impl.append("    @Override\n");
        impl.append("    public Optional<").append(ctx.pascalName).append("> findById(String id) {\n");
        impl.append("        log.debug(\"Looking up ").append(ctx.name).append(" with id: {}\", id);\n");
        impl.append("        return Optional.ofNullable(store.get(id));\n");
        impl.append("    }\n\n");
        impl.append("    @Override\n");
        impl.append("    public List<").append(ctx.pascalName).append("> findAll() {\n");
        impl.append("        log.debug(\"Retrieving all ").append(ctx.name).append(" entries\");\n");
        impl.append("        return new java.util.ArrayList<>(store.values());\n");
        impl.append("    }\n");
        impl.append("}\n");

        return impl.toString();
    }

    @Override
    public String emitViewService(String basePackage, EntityContext ctx) {
        String className = ctx.pascalName + "Service";
        String repositoryClass = ctx.pascalName + "Repository";
        String packageName = basePackage + ".service";
        String viewImport = ctx.namespace + ".view." + ctx.pascalName;

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");
        code.append("import ").append(viewImport).append(";\n");
        code.append("import ").append(basePackage).append(".repository.").append(repositoryClass).append(";\n");
        code.append("import ").append(basePackage).append(".exception.ResourceNotFoundException;\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import org.springframework.stereotype.Service;\n");
        code.append("import java.util.List;\n\n");
        code.append("/**\n");
        code.append(" * Read-only service for ").append(ctx.name).append(" view\n");
        code.append(" * Views expose GET only — no rule evaluation, no write operations\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@Service\n");
        code.append("public class ").append(className).append(" {\n\n");
        code.append("    private final ").append(repositoryClass).append(" repository;\n\n");
        code.append("    public ").append(className).append("(").append(repositoryClass).append(" repository) {\n");
        code.append("        this.repository = repository;\n");
        code.append("    }\n\n");
        code.append("    public ").append(ctx.pascalName).append(" findById(String id) {\n");
        code.append("        log.debug(\"Fetching ").append(ctx.name).append(" by id: {}\", id);\n");
        code.append("        return repository.findById(id)\n");
        code.append("            .orElseThrow(() -> {\n");
        code.append("                log.warn(\"").append(ctx.name).append(" not found with id: {}\", id);\n");
        code.append("                return new ResourceNotFoundException(\"").append(ctx.name).append(" not found: \" + id);\n");
        code.append("            });\n");
        code.append("    }\n\n");
        code.append("    public List<").append(ctx.pascalName).append("> findAll() {\n");
        code.append("        log.debug(\"Fetching all ").append(ctx.name).append(" entries\");\n");
        code.append("        return repository.findAll();\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    @Override
    public String emitViewController(String basePackage, EntityContext ctx) {
        String className = ctx.pascalName + "Controller";
        String serviceClass = ctx.pascalName + "Service";
        String rendererClass = ctx.pascalName + "PageRenderer";
        String rendererField = Character.toLowerCase(ctx.pascalName.charAt(0)) + ctx.pascalName.substring(1) + "PageRenderer";
        String packageName = basePackage + ".controller";
        String viewImport = ctx.namespace + ".view." + ctx.pascalName;
        String resourcePath = "/views/" + ctx.resourcePath;

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");
        code.append("import ").append(viewImport).append(";\n");
        code.append("import ").append(basePackage).append(".service.").append(serviceClass).append(";\n");
        code.append("import lombok.extern.log4j.Log4j2;\n");
        code.append("import org.springframework.http.MediaType;\n");
        code.append("import org.springframework.http.ResponseEntity;\n");
        code.append("import org.springframework.web.bind.annotation.*;\n");
        code.append("import java.util.List;\n\n");
        code.append("/**\n");
        code.append(" * Read-only REST endpoints for ").append(ctx.name).append(" view\n");
        code.append(" * Views expose GET only (list + by ID) — no create/update/delete\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("@Log4j2\n");
        code.append("@RestController\n");
        code.append("@RequestMapping(\"").append(resourcePath).append("\")\n");
        code.append("public class ").append(className).append(" {\n\n");
        code.append("    private final ").append(serviceClass).append(" service;\n");
        code.append("    private final ").append(rendererClass).append(" renderer;\n\n");
        code.append("    public ").append(className).append("(").append(serviceClass).append(" service, ").append(rendererClass).append(" ").append(rendererField).append(") {\n");
        code.append("        this.service = service;\n");
        code.append("        this.renderer = ").append(rendererField).append(";\n");
        code.append("    }\n\n");
        // GET (JSON)
        code.append("    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)\n");
        code.append("    public ResponseEntity<List<").append(ctx.pascalName).append(">> list() {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(" - Retrieving all ").append(ctx.name).append(" entries\");\n");
        code.append("        return ResponseEntity.ok(service.findAll());\n");
        code.append("    }\n\n");
        // GET (HTML)
        code.append("    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)\n");
        code.append("    public ResponseEntity<String> listHtml() {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append(" (html) - Retrieving all ").append(ctx.name).append(" entries\");\n");
        code.append("        String html = renderer.renderList(service.findAll());\n");
        code.append("        return ResponseEntity.ok(html);\n");
        code.append("    }\n\n");
        // GET /{id} (JSON)
        code.append("    @GetMapping(value = \"/{id}\", produces = MediaType.APPLICATION_JSON_VALUE)\n");
        code.append("    public ResponseEntity<").append(ctx.pascalName).append("> get(@PathVariable String id) {\n");
        code.append("        log.info(\"GET ").append(resourcePath).append("/{id} - Retrieving ").append(ctx.name).append(" with id: {}\", id);\n");
        code.append("        return ResponseEntity.ok(service.findById(id));\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    // -------------------------------------------------------------------------
    // Group C: Per-Entity Page Renderers (HTML content negotiation)
    // -------------------------------------------------------------------------

    @Override
    public String emitPageRenderer(String basePackage, EntityContext ctx) {
        String className = ctx.pascalName + "PageRenderer";
        String packageName = basePackage + ".controller";
        String modelImport = ctx.namespace + ".model." + ctx.pascalName;
        // Template directory: domain/resourcePath (model)
        String templateDir = "templates/" + CodeGenUtils.templateDir(ctx.domain, ctx.resourcePath, false);

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(modelImport).append(";\n");
        code.append("import ").append(basePackage).append(".util.HtmlEscapeUtils;\n");
        code.append("import org.springframework.stereotype.Component;\n");
        code.append("import java.io.IOException;\n");
        code.append("import java.io.InputStream;\n");
        code.append("import java.nio.charset.StandardCharsets;\n");
        code.append("import java.util.List;\n\n");

        code.append("/**\n");
        code.append(" * HTML page renderer for ").append(ctx.name).append(" entities.\n");
        code.append(" * Loads HTML templates from the classpath and fills {{CONTENT}} with escaped data.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from AppServerGenerator\n");
        code.append(" */\n");
        code.append("@Component\n");
        code.append("public class ").append(className).append(" {\n\n");

        // Final template fields
        code.append("    private final String listTemplate;\n");
        code.append("    private final String detailTemplate;\n");
        code.append("    private final String editTemplate;\n");
        code.append("    private final String createTemplate;\n\n");

        // Constructor loads all four templates
        code.append("    public ").append(className).append("() {\n");
        code.append("        this.listTemplate = loadTemplate(\"").append(templateDir).append("/list.html\");\n");
        code.append("        this.detailTemplate = loadTemplate(\"").append(templateDir).append("/detail.html\");\n");
        code.append("        this.editTemplate = loadTemplate(\"").append(templateDir).append("/edit.html\");\n");
        code.append("        this.createTemplate = loadTemplate(\"").append(templateDir).append("/create.html\");\n");
        code.append("    }\n\n");

        // loadTemplate static helper — uses ClassName.class.getClassLoader() (never getClass() in static)
        code.append("    private static String loadTemplate(String path) {\n");
        code.append("        try (InputStream in = ").append(className).append(".class.getClassLoader().getResourceAsStream(path)) {\n");
        code.append("            if (in == null) {\n");
        code.append("                throw new IllegalStateException(\"Template not found on classpath: \" + path);\n");
        code.append("            }\n");
        code.append("            byte[] bytes = in.readAllBytes();\n");
        code.append("            return new String(bytes, StandardCharsets.UTF_8);\n");
        code.append("        } catch (IOException e) {\n");
        code.append("            throw new IllegalStateException(\"Failed to load template: \" + path, e);\n");
        code.append("        }\n");
        code.append("    }\n\n");

        // renderList — builds <tr> rows
        code.append("    public String renderList(List<").append(ctx.pascalName).append("> items) {\n");
        code.append("        StringBuilder rows = new StringBuilder();\n");
        code.append("        for (").append(ctx.pascalName).append(" item : items) {\n");
        code.append("            rows.append(\"<tr>\");\n");
        // Per-field <td> cells in template order (same order as ctx.fields)
        for (Map<String, Object> field : ctx.fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            String camelName = JavaNaming.toFieldAccessor(fieldName);
            String getterName = "get" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
            String escapedExpr;
            if ("string".equals(fieldType)) {
                escapedExpr = "HtmlEscapeUtils.escape(item." + getterName + "())";
            } else {
                escapedExpr = "HtmlEscapeUtils.escape(String.valueOf(item." + getterName + "()))";
            }
            code.append("            rows.append(\"<td>\").append(").append(escapedExpr).append(").append(\"</td>\");\n");
        }
        // Actions column: View link by record id
        // id field getter
        String idCamel = JavaNaming.toFieldAccessor("id");
        String idGetter = "get" + Character.toUpperCase(idCamel.charAt(0)) + idCamel.substring(1);
        String idEscaped = "HtmlEscapeUtils.escape(item." + idGetter + "())";
        code.append("            rows.append(\"<td><a href=\\\"/").append(ctx.resourcePath).append("/\").append(").append(idEscaped).append(").append(\"\\\">View</a></td>\");\n");
        code.append("            rows.append(\"</tr>\");\n");
        code.append("        }\n");
        code.append("        return listTemplate.replace(\"{{CONTENT}}\", rows.toString());\n");
        code.append("    }\n\n");

        // renderDetail — builds <dt>/<dd> pairs
        code.append("    public String renderDetail(").append(ctx.pascalName).append(" item) {\n");
        code.append("        StringBuilder dl = new StringBuilder();\n");
        for (Map<String, Object> field : ctx.fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            String camelName = JavaNaming.toFieldAccessor(fieldName);
            String getterName = "get" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
            String escapedExpr;
            if ("string".equals(fieldType)) {
                escapedExpr = "HtmlEscapeUtils.escape(item." + getterName + "())";
            } else {
                escapedExpr = "HtmlEscapeUtils.escape(String.valueOf(item." + getterName + "()))";
            }
            code.append("        dl.append(\"  <dt>\").append(\"").append(fieldName).append("\").append(\"</dt>\\n\");\n");
            code.append("        dl.append(\"  <dd>\").append(").append(escapedExpr).append(").append(\"</dd>\\n\");\n");
        }
        code.append("        return detailTemplate.replace(\"{{CONTENT}}\", dl.toString());\n");
        code.append("    }\n\n");

        // renderEditForm — builds per-field input blocks (mirrors generateEditHtml, skips PK)
        code.append("    public String renderEditForm(").append(ctx.pascalName).append(" item) {\n");
        code.append("        StringBuilder inputs = new StringBuilder();\n");
        for (Map<String, Object> field : ctx.fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            String sqlType = field.get("original_sql_type") != null
                    ? ((String) field.get("original_sql_type")).toUpperCase()
                    : "";
            boolean isPrimaryKey = Boolean.TRUE.equals(field.get("primary_key"));
            boolean nullable = Boolean.TRUE.equals(field.get("nullable"));
            if (isPrimaryKey) {
                continue; // PK rendered as hidden input outside {{CONTENT}}
            }
            String camelName = JavaNaming.toFieldAccessor(fieldName);
            String getterName = "get" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);

            String humanLabel = buildHumanLabel(fieldName);
            String req = nullable ? "" : " required";

            code.append("        inputs.append(\"  <div>\\n\");\n");
            code.append("        inputs.append(\"    <label for=\\\"").append(fieldName).append("\\\">").append(humanLabel).append("</label>\\n\");\n");
            // Determine input type and build it with value injected
            if (sqlType.equals("TEXT")) {
                // textarea — value is inner text
                String escapedExpr = "HtmlEscapeUtils.escape(item." + getterName + "())";
                code.append("        inputs.append(\"    <textarea name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\"").append(req).append(">\");\n");
                code.append("        inputs.append(").append(escapedExpr).append(");\n");
                code.append("        inputs.append(\"</textarea>\\n\");\n");
            } else if ("bool".equals(fieldType)) {
                // checkbox — checked attribute; no required
                String escapedExpr = "HtmlEscapeUtils.escape(String.valueOf(item." + getterName + "()))";
                code.append("        boolean ").append(camelName).append("Val = item.").append(getterName).append("();\n");
                code.append("        inputs.append(\"    <input type=\\\"checkbox\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\"\");\n");
                code.append("        if (").append(camelName).append("Val) {\n");
                code.append("            inputs.append(\" checked\");\n");
                code.append("        }\n");
                code.append("        inputs.append(\">\\n\");\n");
            } else if ("int32".equals(fieldType) || "int64".equals(fieldType)) {
                String escapedExpr = "HtmlEscapeUtils.escape(String.valueOf(item." + getterName + "()))";
                code.append("        inputs.append(\"    <input type=\\\"number\\\" step=\\\"1\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(").append(escapedExpr).append(");\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("decimal".equals(fieldType)) {
                String escapedExpr = "HtmlEscapeUtils.escape(String.valueOf(item." + getterName + "()))";
                code.append("        inputs.append(\"    <input type=\\\"number\\\" step=\\\"0.01\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(").append(escapedExpr).append(");\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("float64".equals(fieldType)) {
                String escapedExpr = "HtmlEscapeUtils.escape(String.valueOf(item." + getterName + "()))";
                code.append("        inputs.append(\"    <input type=\\\"number\\\" step=\\\"any\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(").append(escapedExpr).append(");\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("date".equals(fieldType)) {
                String escapedExpr = "HtmlEscapeUtils.escape(item." + getterName + "())";
                code.append("        inputs.append(\"    <input type=\\\"date\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(").append(escapedExpr).append(");\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("datetime".equals(fieldType)) {
                String escapedExpr = "HtmlEscapeUtils.escape(item." + getterName + "())";
                code.append("        inputs.append(\"    <input type=\\\"datetime-local\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(").append(escapedExpr).append(");\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else {
                // Default: text input (string types)
                String escapedExpr = "HtmlEscapeUtils.escape(item." + getterName + "())";
                code.append("        inputs.append(\"    <input type=\\\"text\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(").append(escapedExpr).append(");\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            }
            code.append("        inputs.append(\"  </div>\\n\");\n");
        }
        // Inject id value into hidden input outside {{CONTENT}} slot
        String idEscapedEdit = "HtmlEscapeUtils.escape(item." + "get" + Character.toUpperCase(idCamel.charAt(0)) + idCamel.substring(1) + "())";
        code.append("        String filled = editTemplate.replace(\"{{CONTENT}}\", inputs.toString());\n");
        code.append("        filled = filled.replace(\n");
        code.append("            \"<input type=\\\"hidden\\\" name=\\\"id\\\" id=\\\"id\\\">\",\n");
        code.append("            \"<input type=\\\"hidden\\\" name=\\\"id\\\" id=\\\"id\\\" value=\\\"\" + ").append(idEscapedEdit).append(" + \"\\\">\");\n");
        code.append("        return filled;\n");
        code.append("    }\n\n");

        // renderCreateForm — no data, return create template unchanged
        code.append("    public String renderCreateForm() {\n");
        code.append("        return createTemplate;\n");
        code.append("    }\n\n");

        // renderCreateFormWithErrors — re-render create form with errors visible.
        // The create template is fully static (no {{CONTENT}} slot), so the form parameter
        // is accepted for API compatibility but input pre-filling is not applied here
        // (see Deviation D-CREATE-PREFILL in report). Errors are injected before <form.
        code.append("    public String renderCreateFormWithErrors(java.util.Map<String,String> form, java.util.List<String> errors) {\n");
        code.append("        StringBuilder errHtml = new StringBuilder();\n");
        code.append("        errHtml.append(\"<ul class=\\\"errors\\\">\\n\");\n");
        code.append("        for (String err : errors) {\n");
        code.append("            errHtml.append(\"<li>\").append(HtmlEscapeUtils.escape(err)).append(\"</li>\\n\");\n");
        code.append("        }\n");
        code.append("        errHtml.append(\"</ul>\\n\");\n");
        code.append("        return createTemplate.replace(\"<form\", errHtml + \"<form\");\n");
        code.append("    }\n\n");

        // renderEditFormWithErrors — re-render edit form from submitted data + error list
        code.append("    public String renderEditFormWithErrors(java.util.Map<String,String> form, java.util.List<String> errors) {\n");
        code.append("        StringBuilder errHtml = new StringBuilder();\n");
        code.append("        errHtml.append(\"<ul class=\\\"errors\\\">\\n\");\n");
        code.append("        for (String err : errors) {\n");
        code.append("            errHtml.append(\"<li>\").append(HtmlEscapeUtils.escape(err)).append(\"</li>\\n\");\n");
        code.append("        }\n");
        code.append("        errHtml.append(\"</ul>\\n\");\n");
        code.append("        StringBuilder inputs = new StringBuilder();\n");
        code.append("        inputs.append(errHtml);\n");
        for (Map<String, Object> field : ctx.fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            String sqlType = field.get("original_sql_type") != null
                    ? ((String) field.get("original_sql_type")).toUpperCase()
                    : "";
            boolean isPrimaryKey = Boolean.TRUE.equals(field.get("primary_key"));
            boolean nullable = Boolean.TRUE.equals(field.get("nullable"));
            if (isPrimaryKey) {
                continue;
            }
            String humanLabel = buildHumanLabel(fieldName);
            String req = nullable ? "" : " required";
            code.append("        inputs.append(\"  <div>\\n\");\n");
            code.append("        inputs.append(\"    <label for=\\\"").append(fieldName).append("\\\">").append(humanLabel).append("</label>\\n\");\n");
            if (sqlType.equals("TEXT")) {
                code.append("        inputs.append(\"    <textarea name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\"").append(req).append(">\");\n");
                code.append("        inputs.append(HtmlEscapeUtils.escape(form.getOrDefault(\"").append(fieldName).append("\", \"\")));\n");
                code.append("        inputs.append(\"</textarea>\\n\");\n");
            } else if ("bool".equals(fieldType)) {
                code.append("        inputs.append(\"    <input type=\\\"checkbox\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\"\");\n");
                code.append("        if (form.containsKey(\"").append(fieldName).append("\")) {\n");
                code.append("            inputs.append(\" checked\");\n");
                code.append("        }\n");
                code.append("        inputs.append(\">\\n\");\n");
            } else if ("int32".equals(fieldType) || "int64".equals(fieldType)) {
                code.append("        inputs.append(\"    <input type=\\\"number\\\" step=\\\"1\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(HtmlEscapeUtils.escape(form.getOrDefault(\"").append(fieldName).append("\", \"\")));\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("decimal".equals(fieldType)) {
                code.append("        inputs.append(\"    <input type=\\\"number\\\" step=\\\"0.01\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(HtmlEscapeUtils.escape(form.getOrDefault(\"").append(fieldName).append("\", \"\")));\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("float64".equals(fieldType)) {
                code.append("        inputs.append(\"    <input type=\\\"number\\\" step=\\\"any\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(HtmlEscapeUtils.escape(form.getOrDefault(\"").append(fieldName).append("\", \"\")));\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("date".equals(fieldType)) {
                code.append("        inputs.append(\"    <input type=\\\"date\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(HtmlEscapeUtils.escape(form.getOrDefault(\"").append(fieldName).append("\", \"\")));\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else if ("datetime".equals(fieldType)) {
                code.append("        inputs.append(\"    <input type=\\\"datetime-local\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(HtmlEscapeUtils.escape(form.getOrDefault(\"").append(fieldName).append("\", \"\")));\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            } else {
                code.append("        inputs.append(\"    <input type=\\\"text\\\" name=\\\"").append(fieldName).append("\\\" id=\\\"").append(fieldName).append("\\\" value=\\\"\");\n");
                code.append("        inputs.append(HtmlEscapeUtils.escape(form.getOrDefault(\"").append(fieldName).append("\", \"\")));\n");
                code.append("        inputs.append(\"\\\"").append(req).append(">\\n\");\n");
            }
            code.append("        inputs.append(\"  </div>\\n\");\n");
        }
        String idEscapedErr = "HtmlEscapeUtils.escape(form.getOrDefault(\"id\", \"\"))";
        code.append("        String filled = editTemplate.replace(\"{{CONTENT}}\", inputs.toString());\n");
        code.append("        filled = filled.replace(\n");
        code.append("            \"<input type=\\\"hidden\\\" name=\\\"id\\\" id=\\\"id\\\">\",\n");
        code.append("            \"<input type=\\\"hidden\\\" name=\\\"id\\\" id=\\\"id\\\" value=\\\"\" + ").append(idEscapedErr).append(" + \"\\\">\");\n");
        code.append("        return filled;\n");
        code.append("    }\n");

        code.append("}\n");
        return code.toString();
    }

    @Override
    public String emitViewPageRenderer(String basePackage, EntityContext ctx) {
        String className = ctx.pascalName + "PageRenderer";
        String packageName = basePackage + ".controller";
        String viewImport = ctx.namespace + ".view." + ctx.pascalName;
        // Template directory: views/resourcePath (view, resourcePath already has _view stripped)
        String templateDir = "templates/" + CodeGenUtils.templateDir(null, ctx.resourcePath, true);

        StringBuilder code = new StringBuilder();
        code.append("package ").append(packageName).append(";\n\n");

        code.append("import ").append(viewImport).append(";\n");
        code.append("import ").append(basePackage).append(".util.HtmlEscapeUtils;\n");
        code.append("import org.springframework.stereotype.Component;\n");
        code.append("import java.io.IOException;\n");
        code.append("import java.io.InputStream;\n");
        code.append("import java.nio.charset.StandardCharsets;\n");
        code.append("import java.util.List;\n\n");

        code.append("/**\n");
        code.append(" * HTML page renderer for ").append(ctx.name).append(" view (read-only).\n");
        code.append(" * Loads the list HTML template from the classpath and fills {{CONTENT}} with escaped data.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from AppServerGenerator\n");
        code.append(" */\n");
        code.append("@Component\n");
        code.append("public class ").append(className).append(" {\n\n");

        code.append("    private final String listTemplate;\n\n");

        code.append("    public ").append(className).append("() {\n");
        code.append("        this.listTemplate = loadTemplate(\"").append(templateDir).append("/list.html\");\n");
        code.append("    }\n\n");

        code.append("    private static String loadTemplate(String path) {\n");
        code.append("        try (InputStream in = ").append(className).append(".class.getClassLoader().getResourceAsStream(path)) {\n");
        code.append("            if (in == null) {\n");
        code.append("                throw new IllegalStateException(\"Template not found on classpath: \" + path);\n");
        code.append("            }\n");
        code.append("            byte[] bytes = in.readAllBytes();\n");
        code.append("            return new String(bytes, StandardCharsets.UTF_8);\n");
        code.append("        } catch (IOException e) {\n");
        code.append("            throw new IllegalStateException(\"Failed to load template: \" + path, e);\n");
        code.append("        }\n");
        code.append("    }\n\n");

        // renderList — builds <tr> rows with escaped values, NO Actions column
        code.append("    public String renderList(List<").append(ctx.pascalName).append("> items) {\n");
        code.append("        StringBuilder rows = new StringBuilder();\n");
        code.append("        for (").append(ctx.pascalName).append(" item : items) {\n");
        code.append("            rows.append(\"<tr>\");\n");
        for (Map<String, Object> field : ctx.fields) {
            String fieldName = (String) field.get("name");
            String fieldType = (String) field.get("type");
            String camelName = JavaNaming.toFieldAccessor(fieldName);
            String getterName = "get" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
            String escapedExpr;
            if ("string".equals(fieldType)) {
                escapedExpr = "HtmlEscapeUtils.escape(item." + getterName + "())";
            } else {
                escapedExpr = "HtmlEscapeUtils.escape(String.valueOf(item." + getterName + "()))";
            }
            code.append("            rows.append(\"<td>\").append(").append(escapedExpr).append(").append(\"</td>\");\n");
        }
        // NO Actions column for views
        code.append("            rows.append(\"</tr>\");\n");
        code.append("        }\n");
        code.append("        return listTemplate.replace(\"{{CONTENT}}\", rows.toString());\n");
        code.append("    }\n");

        code.append("}\n");
        return code.toString();
    }

    /**
     * Converts a snake_case field name to a human-readable label (Title Case with spaces).
     * Example: "display_name" -> "Display Name", "is_verified" -> "Is Verified".
     * Mirrors HtmlCrudGenerator.humanize().
     */
    private static String buildHumanLabel(String snakeName) {
        String[] parts = snakeName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }
}
