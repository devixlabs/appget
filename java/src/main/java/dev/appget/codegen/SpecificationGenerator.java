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

/**
 * Generates Specification implementation classes from YAML rule definitions.
 * Supports simple conditions, compound AND/OR conditions, metadata requirements,
 * and target type resolution (models and views).
 *
 * Usage: java -cp <classpath> dev.appget.codegen.SpecificationGenerator <specs.yaml> <models.yaml> <output-dir>
 */
public class SpecificationGenerator {

    private static final Logger logger = LogManager.getLogger(SpecificationGenerator.class);

    // Maps domain+type+name -> fully qualified class name (resolved from models.yaml)
    private final Map<String, String> targetImportMap = new HashMap<>();
    private final TemplateEngine templateEngine;

    public SpecificationGenerator() {
        this.templateEngine = new TemplateEngine();
    }

    public static void main(String[] args) {
        logger.debug("Entering main method with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid argument count. Usage: SpecificationGenerator <specs.yaml> [models.yaml] <output-dir>");
            System.err.println("Usage: SpecificationGenerator <specs.yaml> [models.yaml] <output-dir>");
            System.exit(1);
        }

        String specsPath;
        String modelsPath;
        String outputDir;

        if (args.length == 2) {
            specsPath = args[0];
            modelsPath = null;
            outputDir = args[1];
            logger.info("Starting SpecificationGenerator with specsPath={}, outputDir={}", specsPath, outputDir);
        } else {
            specsPath = args[0];
            modelsPath = args[1];
            outputDir = args[2];
            logger.info("Starting SpecificationGenerator with specsPath={}, modelsPath={}, outputDir={}", specsPath, modelsPath, outputDir);
        }

        try {
            SpecificationGenerator gen = new SpecificationGenerator();
            if (modelsPath != null) {
                logger.debug("Loading models from: {}", modelsPath);
                gen.loadModelsYaml(modelsPath);
            }
            gen.generateSpecifications(specsPath, outputDir);
            logger.info("Successfully generated specifications to: {}", outputDir);
            System.out.println("✓ Successfully generated specifications to: " + outputDir);
        } catch (Exception e) {
            logger.error("Failed to generate specifications", e);
            System.err.println("✗ Failed to generate specifications: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        logger.debug("Exiting main method");
    }

    @SuppressWarnings("unchecked")
    public void loadModelsYaml(String modelsPath) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File(modelsPath))) {
            data = yaml.load(in);
        }

        Map<String, Object> domains = (Map<String, Object>) data.get("domains");
        if (domains == null) return;

        for (Map.Entry<String, Object> domainEntry : domains.entrySet()) {
            String domainName = domainEntry.getKey();
            Map<String, Object> domainConfig = (Map<String, Object>) domainEntry.getValue();
            String namespace = (String) domainConfig.get("namespace");

            // Index models
            List<Map<String, Object>> models = (List<Map<String, Object>>) domainConfig.get("models");
            if (models != null) {
                for (Map<String, Object> model : models) {
                    String name = (String) model.get("name");
                    String key = domainName + ":model:" + name;
                    targetImportMap.put(key, namespace + ".model." + name);
                }
            }

            // Index views
            List<Map<String, Object>> views = (List<Map<String, Object>>) domainConfig.get("views");
            if (views != null) {
                for (Map<String, Object> view : views) {
                    String name = (String) view.get("name");
                    String key = domainName + ":view:" + name;
                    targetImportMap.put(key, namespace + ".view." + name);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void generateSpecifications(String yamlPath, String outputDir) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream inputStream = new FileInputStream(new File(yamlPath))) {
            data = yaml.load(inputStream);
        }

        // Generate metadata context POJOs
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        if (metadata != null) {
            generateMetadataPojos(metadata, outputDir);
        }

        List<Map<String, Object>> rules = (List<Map<String, Object>>) data.get("rules");
        if (rules == null || rules.isEmpty()) {
            System.out.println("No rules found in " + yamlPath);
            return;
        }

        // Create output directory
        Path packagePath = Paths.get(outputDir, "dev", "appget", "specification", "generated");
        Files.createDirectories(packagePath);

        for (Map<String, Object> rule : rules) {
            String className = (String) rule.get("name");
            String javaCode = generateRuleClass(rule);

            Path outputFile = packagePath.resolve(className + ".java");
            Files.writeString(outputFile, javaCode);
            System.out.println("  Generated: " + className + ".java");
        }
    }

    @SuppressWarnings("unchecked")
    private void generateMetadataPojos(Map<String, Object> metadata, String outputDir) throws IOException {
        Path contextPath = Paths.get(outputDir, "dev", "appget", "specification", "context");
        Files.createDirectories(contextPath);

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String categoryName = entry.getKey();
            Map<String, Object> categoryConfig = (Map<String, Object>) entry.getValue();
            List<Map<String, Object>> fields = (List<Map<String, Object>>) categoryConfig.get("fields");

            String className = Character.toUpperCase(categoryName.charAt(0)) + categoryName.substring(1) + "Context";
            String javaCode = generateMetadataPojo(className, fields);

            Path outputFile = contextPath.resolve(className + ".java");
            Files.writeString(outputFile, javaCode);
            System.out.println("  Generated metadata: " + className + ".java");
        }
    }

    @SuppressWarnings("unchecked")
    private String generateMetadataPojo(String className, List<Map<String, Object>> fields) {
        logger.debug("Generating metadata POJO: {}", className);

        Set<String> imports = new LinkedHashSet<>();

        Map<String, String> typeImports = new HashMap<>();
        typeImports.put("LocalDate", "java.time.LocalDate");
        typeImports.put("LocalDateTime", "java.time.LocalDateTime");
        typeImports.put("BigDecimal", "java.math.BigDecimal");

        // Convert field types to Java types (neutral types → Java types)
        // and build a new fields list with Java type names
        List<Map<String, Object>> javaFields = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            String rawType = (String) field.get("type");
            // Convert neutral type to Java type; fall back to raw if already Java
            String javaType = JavaTypeRegistry.INSTANCE.neutralToJava(rawType);
            if (typeImports.containsKey(javaType)) {
                imports.add(typeImports.get(javaType));
            }
            // Build new field map with Java type
            Map<String, Object> javaField = new LinkedHashMap<>(field);
            javaField.put("type", javaType);
            javaFields.add(javaField);
        }

        Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("imports", imports);
        context.put("fields", javaFields);

        logger.debug("Rendering MetadataPojo template for {}", className);
        return templateEngine.render("specification/MetadataPojo.java", context);
    }

    @SuppressWarnings("unchecked")
    private String generateRuleClass(Map<String, Object> rule) {
        logger.debug("Generating rule class for rule: {}", rule.get("name"));
        String className = (String) rule.get("name");

        // Parse target
        Map<String, Object> target = (Map<String, Object>) rule.get("target");
        String targetTypeName;
        String targetImport;
        if (target != null) {
            String targetKind = (String) target.get("type"); // "model" or "view"
            String targetName = (String) target.get("name");
            String targetDomain = (String) target.get("domain");
            String key = targetDomain + ":" + targetKind + ":" + targetName;
            targetImport = targetImportMap.getOrDefault(key, guessImport(targetDomain, targetKind, targetName));
            targetTypeName = targetName;
        } else {
            targetTypeName = "Employee";
            targetImport = "dev.appget.model.Employee";
        }

        // Parse conditions
        Object conditionsRaw = rule.get("conditions");
        boolean isCompound = false;
        String compoundOperator = null;
        List<Map<String, Object>> conditionClauses = new ArrayList<>();

        if (conditionsRaw instanceof Map) {
            // Compound: { operator: AND/OR, clauses: [...] }
            Map<String, Object> condMap = (Map<String, Object>) conditionsRaw;
            isCompound = true;
            compoundOperator = (String) condMap.get("operator");
            conditionClauses = (List<Map<String, Object>>) condMap.get("clauses");
        } else if (conditionsRaw instanceof List) {
            List<Map<String, Object>> condList = (List<Map<String, Object>>) conditionsRaw;
            if (condList.size() == 1) {
                conditionClauses = condList;
                isCompound = false;
            } else {
                conditionClauses = condList;
                isCompound = true;
                compoundOperator = "AND"; // implicit AND for multiple simple conditions
            }
        }

        // Parse requires (metadata)
        Map<String, Object> requires = (Map<String, Object>) rule.get("requires");
        boolean hasMetadata = requires != null && !requires.isEmpty();

        // Parse outcomes
        Map<String, String> thenBlock = (Map<String, String>) rule.get("then");
        Map<String, String> elseBlock = (Map<String, String>) rule.get("else");
        String successStatus = thenBlock.get("status");
        String failureStatus = elseBlock.get("status");

        // Build metadata requirements with field name info
        List<MetadataReqInfo> metadataReqs = new ArrayList<>();
        List<Map<String, Object>> metadataReqsMaps = new ArrayList<>();
        if (hasMetadata) {
            for (Map.Entry<String, Object> reqEntry : requires.entrySet()) {
                String category = reqEntry.getKey();
                List<Map<String, Object>> reqs = (List<Map<String, Object>>) reqEntry.getValue();
                for (int i = 0; i < reqs.size(); i++) {
                    Map<String, Object> req = reqs.get(i);
                    String fieldName = category + "Req" + i;
                    metadataReqs.add(new MetadataReqInfo(category, fieldName, req));

                    Map<String, Object> reqMap = new HashMap<>(req);
                    reqMap.put("fieldName", fieldName);
                    reqMap.put("category", category);
                    reqMap.put("formattedValue", formatValue(req.get("value")));
                    metadataReqsMaps.add(reqMap);
                }
            }
        }

        // Group metadata reqs by category for template
        Map<String, Object> metadataReqsByCategory = new LinkedHashMap<>();
        for (MetadataReqInfo mri : metadataReqs) {
            metadataReqsByCategory.computeIfAbsent(mri.category, k -> {
                Map<String, Object> catMap = new LinkedHashMap<>();
                catMap.put("category", mri.category);
                catMap.put("reqs", new ArrayList<>());
                return catMap;
            });
            ((List<Map<String, Object>>) ((Map<String, Object>) metadataReqsByCategory.get(mri.category)).get("reqs")).add(
                new HashMap<String, Object>() {{
                    put("fieldName", mri.fieldName);
                    put("category", mri.category);
                }}
            );
        }

        // Format condition clauses for template
        List<Map<String, Object>> formattedClauses = new ArrayList<>();
        for (Map<String, Object> clause : conditionClauses) {
            Map<String, Object> formatted = new HashMap<>(clause);
            formatted.put("formattedValue", formatValue(clause.get("value")));
            formattedClauses.add(formatted);
        }

        // Build context for template
        Map<String, Object> context = new HashMap<>();
        context.put("className", className);
        context.put("targetImport", targetImport);
        context.put("targetTypeName", targetTypeName);
        context.put("isCompound", isCompound);
        context.put("compoundOperator", compoundOperator);
        context.put("hasMetadata", hasMetadata);
        context.put("successStatus", successStatus);
        context.put("failureStatus", failureStatus);
        context.put("metadataReqs", metadataReqsMaps);
        context.put("metadataReqsByCategory", new ArrayList<>(metadataReqsByCategory.values()));

        // For simple specs, add first condition
        if (!formattedClauses.isEmpty()) {
            Map<String, Object> firstClause = formattedClauses.get(0);
            context.putAll(firstClause);
        }

        // For compound specs, add all clauses
        context.put("conditionClauses", formattedClauses);

        String templateName = isCompound ? "specification/CompoundSpecification.java" : "specification/SimpleSpecification.java";
        logger.debug("Rendering {} template for {}", templateName, className);
        return templateEngine.render(templateName, context);
    }

    private String guessImport(String domain, String kind, String name) {
        String namespace = "dev.appget";
        if (domain != null && !domain.equals("appget")) {
            namespace += "." + domain;
        }
        String subpackage = "view".equals(kind) ? "view" : "model";
        return namespace + "." + subpackage + "." + name;
    }

    private String formatValue(Object value) {
        if (value instanceof Integer) {
            return value.toString();
        } else if (value instanceof String) {
            return "\"" + CodeGenUtils.escapeString((String) value) + "\"";
        } else if (value instanceof Double) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else {
            return "\"" + value.toString() + "\"";
        }
    }

    private static class MetadataReqInfo {
        final String category;
        final String fieldName;
        final Map<String, Object> condition;

        MetadataReqInfo(String category, String fieldName, Map<String, Object> condition) {
            this.category = category;
            this.fieldName = fieldName;
            this.condition = condition;
        }
    }
}
