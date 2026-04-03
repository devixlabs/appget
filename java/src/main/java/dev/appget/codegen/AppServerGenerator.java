package dev.appget.codegen;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.appget.codegen.JavaUtils;
import dev.appget.naming.JavaNaming;

/**
 * Framework-agnostic orchestrator for REST API server code generation.
 *
 * Loads domain models from {@code models.yaml} and business rules from
 * {@code specs.yaml}, then delegates all code emission to a pluggable
 * {@link ServerEmitter}. The default emitter is {@link SpringBootEmitter}.
 *
 * Responsibilities (framework-agnostic, stays here):
 * - YAML parsing and model/rule loading
 * - File iteration, directory creation, and file writing
 * - Composite key resolution and entity context construction
 *
 * Code generation (framework-specific, delegated to emitter):
 * - Annotations, imports, class structures, and build configuration
 *
 * Usage: java -cp <classpath> dev.appget.codegen.AppServerGenerator <models.yaml> <specs.yaml> <output-dir>
 */
public class AppServerGenerator {

    private static final Logger logger = LogManager.getLogger(AppServerGenerator.class);
    private static final String BASE_PACKAGE = "dev.appget.server";
    private Map<String, ModelInfo> modelIndex = new HashMap<>();
    private Map<String, ModelInfo> viewIndex = new LinkedHashMap<>();
    private List<RuleInfo> rules = new ArrayList<>();
    private Set<String> metadataCategories = new LinkedHashSet<>();
    private Map<String, List<Map<String, Object>>> metadataFieldDefinitions = new LinkedHashMap<>();
    private final ServerEmitter emitter;

    public AppServerGenerator() {
        this(new SpringBootEmitter());
    }

    public AppServerGenerator(ServerEmitter emitter) {
        this.emitter = emitter;
    }

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

        // Generate per-model components (deduplicate since modelIndex has both snake_case and PascalCase keys)
        Set<ModelInfo> uniqueModels = new LinkedHashSet<>(modelIndex.values());
        for (ModelInfo model : uniqueModels) {
            generateRepositoryInterface(model, outputDir);
            generateInMemoryRepository(model, outputDir);
            generateService(model, outputDir);
            generateController(model, outputDir);
        }

        // Generate per-view components (read-only GET endpoints, no POST/PUT/DELETE)
        Set<ModelInfo> uniqueViews = new LinkedHashSet<>(viewIndex.values());
        for (ModelInfo view : uniqueViews) {
            generateViewRepository(view, outputDir);
            generateViewService(view, outputDir);
            generateViewController(view, outputDir);
        }

        System.out.println("  Generated: build.gradle");
        System.out.println("  Generated: Application.java");
        System.out.println("  Generated: application.yaml");
        System.out.println("  Generated: log4j2.properties (src/main/resources/)");
        System.out.println("  Generated: SpecificationRegistry");
        System.out.println("  Generated: RuleService (with SpecificationRegistry injection)");
        System.out.println("  Generated: " + uniqueModels.size() + " model endpoints (Controller/Service/Interface/InMemoryRepository)");
        System.out.println("  Generated: " + uniqueViews.size() + " view endpoints (Controller/Service/Repository, GET only)");
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
                    modelIndex.put(JavaUtils.snakeToPascal(modelName), info);
                }
            }

            List<Map<String, Object>> viewList = (List<Map<String, Object>>) domainConfig.get("views");
            if (viewList != null) {
                for (Map<String, Object> view : viewList) {
                    String viewName = (String) view.get("name");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) view.get("fields");

                    ModelInfo info = new ModelInfo();
                    info.name = viewName;
                    info.isView = true;
                    info.domain = domainName;
                    info.namespace = namespace;
                    info.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();

                    viewIndex.put(viewName, info);
                    viewIndex.put(JavaUtils.snakeToPascal(viewName), info);
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
        writefile(outputDir, BASE_PACKAGE, "Application", emitter.emitApplicationClass(BASE_PACKAGE));
    }

    private void generateDecimalModule(String outputDir) throws IOException {
        writefile(outputDir, BASE_PACKAGE + ".config", "DecimalJacksonModule", emitter.emitDecimalModule(BASE_PACKAGE));
    }

    private void generateApplicationYaml(String outputDir) throws IOException {
        Path serverDir = Paths.get(outputDir);
        Files.createDirectories(serverDir);
        Path yamlFile = serverDir.resolve("application.yaml");
        Files.writeString(yamlFile, emitter.emitApplicationYaml());
    }

    private void generateLog4j2Properties(String outputDir) throws IOException {
        Path resourcesDir = Paths.get(outputDir, "src", "main", "resources");
        Files.createDirectories(resourcesDir);
        Path propsFile = resourcesDir.resolve("log4j2.properties");
        Files.writeString(propsFile, emitter.emitLog4j2Properties());
    }

    private void generateBuildGradle(String outputDir) throws IOException {
        Path serverDir = Paths.get(outputDir);
        Files.createDirectories(serverDir);
        Path gradleFile = serverDir.resolve("build.gradle");
        Files.writeString(gradleFile, emitter.emitBuildGradle());
        logger.info("Generated build.gradle for Spring Boot server");
    }

    private void generateMetadataExtractor(String outputDir) throws IOException {
        MetadataEmitContext ctx = buildMetadataEmitContext();
        writefile(outputDir, BASE_PACKAGE + ".config", "MetadataExtractor", emitter.emitMetadataExtractor(BASE_PACKAGE, ctx));
    }

    private MetadataEmitContext buildMetadataEmitContext() {
        return new MetadataEmitContext(metadataCategories, metadataFieldDefinitions);
    }

    private void generateSpecificationRegistry(String outputDir) throws IOException {
        RuleEmitContext ctx = buildRuleEmitContext();
        writefile(outputDir, BASE_PACKAGE + ".service", "SpecificationRegistry", emitter.emitSpecificationRegistry(BASE_PACKAGE, ctx));
    }

    private void generateRuleService(String outputDir) throws IOException {
        RuleEmitContext ctx = buildRuleEmitContext();
        writefile(outputDir, BASE_PACKAGE + ".service", "RuleService", emitter.emitRuleService(BASE_PACKAGE, ctx));
    }

    private RuleEmitContext buildRuleEmitContext() {
        List<RuleEmitContext.RuleEntry> modelRules = new ArrayList<>();
        Map<String, Boolean> blockingMap = new LinkedHashMap<>();
        Map<String, String> ruleTargetMap = new LinkedHashMap<>();

        for (RuleInfo rule : rules) {
            if ("view".equals(rule.targetType)) continue;
            modelRules.add(new RuleEmitContext.RuleEntry(
                rule.name, rule.targetName, rule.targetDomain,
                rule.requiresMetadata, rule.blocking));
            blockingMap.put(rule.name, rule.blocking);
            ModelInfo model = modelIndex.get(rule.targetName);
            if (model != null) {
                ruleTargetMap.put(rule.name, pascalName(model));
            }
        }
        return new RuleEmitContext(modelRules, blockingMap, ruleTargetMap);
    }

    private void generateDTOs(String outputDir) throws IOException {
        String dtoPkg = BASE_PACKAGE + ".dto";
        writefile(outputDir, dtoPkg, "RuleAwareResponse", emitter.emitRuleAwareResponse(BASE_PACKAGE));
        writefile(outputDir, dtoPkg, "RuleEvaluationResult", emitter.emitRuleEvaluationResult(BASE_PACKAGE));
        writefile(outputDir, dtoPkg, "RuleOutcome", emitter.emitRuleOutcome(BASE_PACKAGE));
        writefile(outputDir, dtoPkg, "ErrorResponse", emitter.emitErrorResponse(BASE_PACKAGE));
    }

    private void generateExceptionClasses(String outputDir) throws IOException {
        String exPkg = BASE_PACKAGE + ".exception";
        writefile(outputDir, exPkg, "RuleViolationException", emitter.emitRuleViolationException(BASE_PACKAGE));
        writefile(outputDir, exPkg, "ResourceNotFoundException", emitter.emitResourceNotFoundException(BASE_PACKAGE));
        writefile(outputDir, exPkg, "MetadataParsingException", emitter.emitMetadataParsingException(BASE_PACKAGE));
    }

    private void generateGlobalExceptionHandler(String outputDir) throws IOException {
        writefile(outputDir, BASE_PACKAGE + ".exception", "GlobalExceptionHandler", emitter.emitGlobalExceptionHandler(BASE_PACKAGE));
    }

    private EntityContext buildEntityContext(ModelInfo model) {
        return new EntityContext(
            model.name,
            pascalName(model),
            model.domain,
            model.namespace,
            model.fields,
            model.isView,
            isCompositeKey(model),
            model.fields.stream().anyMatch(f -> "id".equals(f.get("name"))),
            getPrimaryKeyFields(model),
            model.isView ? toViewResourceName(model.name) : toResourceName(model.name),
            buildIdParams(model),
            buildIdArgs(model),
            buildCompositeKeyExpr(model),
            buildEntityCompositeKeyExpr(model),
            buildLogPattern(model),
            buildLogArgs(model),
            buildNotFoundMsg(model, model.name),
            buildPathVariableSegment(model),
            buildPathVariableParams(model)
        );
    }

    private void generateRepositoryInterface(ModelInfo model, String outputDir) throws IOException {
        EntityContext ctx = buildEntityContext(model);
        String interfaceName = ctx.pascalName + "Repository";
        writefile(outputDir, BASE_PACKAGE + ".repository", interfaceName, emitter.emitRepositoryInterface(BASE_PACKAGE, ctx));
    }

    private void generateInMemoryRepository(ModelInfo model, String outputDir) throws IOException {
        EntityContext ctx = buildEntityContext(model);
        String className = "InMemory" + ctx.pascalName + "Repository";
        writefile(outputDir, BASE_PACKAGE + ".repository", className, emitter.emitInMemoryRepository(BASE_PACKAGE, ctx));
    }

    private void generateService(ModelInfo model, String outputDir) throws IOException {
        EntityContext ctx = buildEntityContext(model);
        String className = ctx.pascalName + "Service";
        writefile(outputDir, BASE_PACKAGE + ".service", className, emitter.emitService(BASE_PACKAGE, ctx));
    }

    private void generateController(ModelInfo model, String outputDir) throws IOException {
        EntityContext ctx = buildEntityContext(model);
        String className = ctx.pascalName + "Controller";
        writefile(outputDir, BASE_PACKAGE + ".controller", className, emitter.emitController(BASE_PACKAGE, ctx));
    }

    private static final String VIEW_REPO_SPLIT = "\n// ---FILE_SPLIT---\n";

    private void generateViewRepository(ModelInfo view, String outputDir) throws IOException {
        EntityContext ctx = buildEntityContext(view);
        String interfaceName = ctx.pascalName + "Repository";
        String className = "InMemory" + ctx.pascalName + "Repository";
        String combined = emitter.emitViewRepository(BASE_PACKAGE, ctx);
        String[] parts = combined.split(VIEW_REPO_SPLIT, 2);
        writefile(outputDir, BASE_PACKAGE + ".repository", interfaceName, parts[0]);
        writefile(outputDir, BASE_PACKAGE + ".repository", className, parts[1]);
    }

    private void generateViewService(ModelInfo view, String outputDir) throws IOException {
        EntityContext ctx = buildEntityContext(view);
        String className = ctx.pascalName + "Service";
        writefile(outputDir, BASE_PACKAGE + ".service", className, emitter.emitViewService(BASE_PACKAGE, ctx));
    }

    private void generateViewController(ModelInfo view, String outputDir) throws IOException {
        EntityContext ctx = buildEntityContext(view);
        String className = ctx.pascalName + "Controller";
        writefile(outputDir, BASE_PACKAGE + ".controller", className, emitter.emitViewController(BASE_PACKAGE, ctx));
    }

    private String pascalName(ModelInfo model) {
        return JavaUtils.snakeToPascal(model.name);
    }

    private String toResourceName(String snakeName) {
        return snakeName.replace('_', '-');
    }

    private String toViewResourceName(String snakeName) {
        // post_detail_view → strip _view → post_detail → replace _ with - → post-detail
        String base = snakeName.endsWith("_view") ? snakeName.substring(0, snakeName.length() - 5) : snakeName;
        return base.replace('_', '-');
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

    // ---- Composite primary key helpers ----

    /**
     * Extracts primary key fields from a model, sorted by primary_key_position.
     * Falls back to the single "id" field if no fields have primary_key: true.
     * Returns a list of field maps, each with at least "name" and "type".
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPrimaryKeyFields(ModelInfo model) {
        List<Map<String, Object>> pkFields = new ArrayList<>();
        for (Map<String, Object> field : model.fields) {
            Object pk = field.get("primary_key");
            if (pk != null && Boolean.TRUE.equals(pk)) {
                pkFields.add(field);
            }
        }
        if (pkFields.isEmpty()) {
            // Fallback: use the "id" field if present
            for (Map<String, Object> field : model.fields) {
                if ("id".equals(field.get("name"))) {
                    pkFields.add(field);
                    break;
                }
            }
            return pkFields;
        }
        // Sort by primary_key_position
        pkFields.sort((a, b) -> {
            int posA = a.get("primary_key_position") != null ? ((Number) a.get("primary_key_position")).intValue() : 0;
            int posB = b.get("primary_key_position") != null ? ((Number) b.get("primary_key_position")).intValue() : 0;
            return Integer.compare(posA, posB);
        });
        return pkFields;
    }

    /** Returns true if the model has a composite primary key (more than one PK field). */
    private boolean isCompositeKey(ModelInfo model) {
        return getPrimaryKeyFields(model).size() > 1;
    }

    /**
     * Builds the path variable segment for URL mappings.
     * Single PK: "/{id}"
     * Composite PK: "/{userId}/{roleId}"
     */
    private String buildPathVariableSegment(ModelInfo model) {
        List<Map<String, Object>> pkFields = getPrimaryKeyFields(model);
        if (pkFields.size() <= 1) {
            return "/{id}";
        }
        StringBuilder path = new StringBuilder();
        for (Map<String, Object> field : pkFields) {
            String camelName = JavaNaming.toFieldAccessor((String) field.get("name"));
            path.append("/{").append(camelName).append("}");
        }
        return path.toString();
    }

    /**
     * Builds the @PathVariable parameter list for controller method signatures.
     * Single PK: "@PathVariable String id"
     * Composite PK: "@PathVariable String userId, @PathVariable String roleId"
     */
    private String buildPathVariableParams(ModelInfo model) {
        List<Map<String, Object>> pkFields = getPrimaryKeyFields(model);
        if (pkFields.size() <= 1) {
            return "@PathVariable String id";
        }
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < pkFields.size(); i++) {
            if (i > 0) {
                params.append(", ");
            }
            String camelName = JavaNaming.toFieldAccessor((String) pkFields.get(i).get("name"));
            params.append("@PathVariable String ").append(camelName);
        }
        return params.toString();
    }

    /**
     * Builds the method parameter list for service/repository method signatures (no annotations).
     * Single PK: "String id"
     * Composite PK: "String userId, String roleId"
     */
    private String buildIdParams(ModelInfo model) {
        List<Map<String, Object>> pkFields = getPrimaryKeyFields(model);
        if (pkFields.size() <= 1) {
            return "String id";
        }
        StringBuilder params = new StringBuilder();
        for (int i = 0; i < pkFields.size(); i++) {
            if (i > 0) {
                params.append(", ");
            }
            String camelName = JavaNaming.toFieldAccessor((String) pkFields.get(i).get("name"));
            params.append("String ").append(camelName);
        }
        return params.toString();
    }

    /**
     * Builds the argument list for passing ID values to another method.
     * Single PK: "id"
     * Composite PK: "userId, roleId"
     */
    private String buildIdArgs(ModelInfo model) {
        List<Map<String, Object>> pkFields = getPrimaryKeyFields(model);
        if (pkFields.size() <= 1) {
            return "id";
        }
        StringBuilder args = new StringBuilder();
        for (int i = 0; i < pkFields.size(); i++) {
            if (i > 0) {
                args.append(", ");
            }
            args.append(JavaNaming.toFieldAccessor((String) pkFields.get(i).get("name")));
        }
        return args.toString();
    }

    /**
     * Builds the composite key expression for in-memory storage.
     * Single PK: "id"
     * Composite PK: "userId + \":\" + roleId"
     */
    private String buildCompositeKeyExpr(ModelInfo model) {
        List<Map<String, Object>> pkFields = getPrimaryKeyFields(model);
        if (pkFields.size() <= 1) {
            return "id";
        }
        StringBuilder expr = new StringBuilder();
        for (int i = 0; i < pkFields.size(); i++) {
            if (i > 0) {
                expr.append(" + \":\" + ");
            }
            expr.append(JavaNaming.toFieldAccessor((String) pkFields.get(i).get("name")));
        }
        return expr.toString();
    }

    /**
     * Builds the composite key expression extracted from an entity (for the save method).
     * Single PK with id field: "entity.getId()"
     * Composite PK: "entity.getUserId() + \":\" + entity.getRoleId()"
     */
    private String buildEntityCompositeKeyExpr(ModelInfo model) {
        List<Map<String, Object>> pkFields = getPrimaryKeyFields(model);
        if (pkFields.size() <= 1) {
            return "entity.getId()";
        }
        StringBuilder expr = new StringBuilder();
        for (int i = 0; i < pkFields.size(); i++) {
            if (i > 0) {
                expr.append(" + \":\" + ");
            }
            String camelName = JavaNaming.toFieldAccessor((String) pkFields.get(i).get("name"));
            String getterName = "get" + Character.toUpperCase(camelName.charAt(0)) + camelName.substring(1);
            expr.append("entity.").append(getterName).append("()");
        }
        return expr.toString();
    }

    /**
     * Builds a log-friendly string for composite key values.
     * Single PK: "id: {}", id
     * Composite PK: "userId: {}, roleId: {}", userId, roleId
     */
    private String buildLogPattern(ModelInfo model) {
        List<Map<String, Object>> pkFields = getPrimaryKeyFields(model);
        if (pkFields.size() <= 1) {
            return "id: {}";
        }
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < pkFields.size(); i++) {
            if (i > 0) {
                pattern.append(", ");
            }
            pattern.append(JavaNaming.toFieldAccessor((String) pkFields.get(i).get("name")));
            pattern.append(": {}");
        }
        return pattern.toString();
    }

    /**
     * Builds the log argument list for composite key log messages.
     * Single PK: "id"
     * Composite PK: "userId, roleId"
     */
    private String buildLogArgs(ModelInfo model) {
        return buildIdArgs(model);
    }

    /**
     * Builds the not-found message expression for composite keys.
     * Single PK: "\"model not found: \" + id"
     * Composite PK: "\"model not found: \" + userId + \":\" + roleId"
     */
    private String buildNotFoundMsg(ModelInfo model, String label) {
        if (!isCompositeKey(model)) {
            return "\"" + label + " not found: \" + id";
        }
        return "\"" + label + " not found: \" + " + buildCompositeKeyExpr(model);
    }

    // Helper classes
    static class ModelInfo {
        String name;
        String domain;
        String namespace;
        List<Map<String, Object>> fields;
        boolean isView;
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
