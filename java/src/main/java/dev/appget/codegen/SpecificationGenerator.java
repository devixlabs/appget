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
        Set<String> imports = new LinkedHashSet<>();
        imports.add("lombok.AllArgsConstructor");
        imports.add("lombok.Builder");
        imports.add("lombok.Data");
        imports.add("lombok.NoArgsConstructor");

        Map<String, String> typeImports = Map.of(
            "LocalDate", "java.time.LocalDate",
            "LocalDateTime", "java.time.LocalDateTime",
            "BigDecimal", "java.math.BigDecimal"
        );

        for (Map<String, Object> field : fields) {
            String type = (String) field.get("type");
            if (type != null && typeImports.containsKey(type)) {
                imports.add(typeImports.get(type));
            }
        }

        StringBuilder code = new StringBuilder();
        code.append("package dev.appget.specification.context;\n\n");

        for (String imp : imports) {
            code.append("import ").append(imp).append(";\n");
        }
        code.append("\n");

        code.append("/**\n");
        code.append(" * Generated metadata context class: ").append(className).append("\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("@Data\n");
        code.append("@Builder\n");
        code.append("@AllArgsConstructor\n");
        code.append("@NoArgsConstructor\n");
        code.append("public class ").append(className).append(" {\n");

        for (Map<String, Object> field : fields) {
            String name = (String) field.get("name");
            String type = (String) field.get("type");
            code.append("    private ").append(type).append(" ").append(name).append(";\n");
        }

        code.append("}\n");
        return code.toString();
    }

    @SuppressWarnings("unchecked")
    private String generateRuleClass(Map<String, Object> rule) {
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

        // Build imports
        Set<String> imports = new LinkedHashSet<>();
        imports.add(targetImport);
        imports.add("dev.appget.specification.Specification");
        if (isCompound) {
            imports.add("dev.appget.specification.CompoundSpecification");
            imports.add("java.util.List");
        }
        if (hasMetadata) {
            imports.add("dev.appget.specification.MetadataContext");
        }

        // Generate code
        StringBuilder code = new StringBuilder();
        code.append("package dev.appget.specification.generated;\n\n");
        for (String imp : imports) {
            code.append("import ").append(imp).append(";\n");
        }
        code.append("\n");

        code.append("/**\n");
        code.append(" * Generated specification class for rule: ").append(className).append("\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from specs.yaml\n");
        code.append(" */\n");
        code.append("public class ").append(className).append(" {\n");

        // Fields
        if (isCompound) {
            code.append("    private final CompoundSpecification spec;\n");
        } else {
            code.append("    private final Specification spec;\n");
        }

        // Metadata requirement fields
        List<MetadataReqInfo> metadataReqs = new ArrayList<>();
        if (hasMetadata) {
            for (Map.Entry<String, Object> reqEntry : requires.entrySet()) {
                String category = reqEntry.getKey();
                List<Map<String, Object>> reqs = (List<Map<String, Object>>) reqEntry.getValue();
                for (int i = 0; i < reqs.size(); i++) {
                    Map<String, Object> req = reqs.get(i);
                    String fieldName = category + "Req" + i;
                    metadataReqs.add(new MetadataReqInfo(category, fieldName, req));
                    code.append("    private final Specification ").append(fieldName).append(";\n");
                }
            }
        }

        code.append("\n");

        // Constructor
        code.append("    public ").append(className).append("() {\n");

        if (isCompound) {
            code.append("        this.spec = new CompoundSpecification(\n");
            code.append("            CompoundSpecification.Logic.").append(compoundOperator).append(",\n");
            code.append("            List.of(\n");
            for (int i = 0; i < conditionClauses.size(); i++) {
                Map<String, Object> clause = conditionClauses.get(i);
                code.append("                new Specification(\"").append(clause.get("field")).append("\", \"");
                code.append(clause.get("operator")).append("\", ");
                code.append(formatValue(clause.get("value"))).append(")");
                if (i < conditionClauses.size() - 1) code.append(",");
                code.append("\n");
            }
            code.append("            )\n");
            code.append("        );\n");
        } else if (!conditionClauses.isEmpty()) {
            Map<String, Object> cond = conditionClauses.get(0);
            code.append("        this.spec = new Specification(\"").append(cond.get("field")).append("\", \"");
            code.append(cond.get("operator")).append("\", ");
            code.append(formatValue(cond.get("value"))).append(");\n");
        }

        for (MetadataReqInfo mri : metadataReqs) {
            code.append("        this.").append(mri.fieldName).append(" = new Specification(\"");
            code.append(mri.condition.get("field")).append("\", \"");
            code.append(mri.condition.get("operator")).append("\", ");
            code.append(formatValue(mri.condition.get("value"))).append(");\n");
        }

        code.append("    }\n\n");

        // evaluate with metadata
        if (hasMetadata) {
            code.append("    public boolean evaluate(").append(targetTypeName).append(" target, MetadataContext metadata) {\n");
            code.append("        if (metadata == null) return false;\n");

            // Group metadata reqs by category
            Map<String, List<MetadataReqInfo>> groupedReqs = new LinkedHashMap<>();
            for (MetadataReqInfo mri : metadataReqs) {
                groupedReqs.computeIfAbsent(mri.category, k -> new ArrayList<>()).add(mri);
            }

            for (Map.Entry<String, List<MetadataReqInfo>> ge : groupedReqs.entrySet()) {
                String cat = ge.getKey();
                code.append("        Object ").append(cat).append("Ctx = metadata.get(\"").append(cat).append("\");\n");
                code.append("        if (").append(cat).append("Ctx == null) return false;\n");
                for (MetadataReqInfo mri : ge.getValue()) {
                    code.append("        if (!").append(mri.fieldName).append(".isSatisfiedBy(").append(cat).append("Ctx)) return false;\n");
                }
            }

            code.append("        return spec.isSatisfiedBy(target);\n");
            code.append("    }\n\n");

            // evaluate without metadata - always false for metadata-required rules
            code.append("    public boolean evaluate(").append(targetTypeName).append(" target) {\n");
            code.append("        return false;\n");
            code.append("    }\n\n");

            // getResult with metadata
            code.append("    public String getResult(").append(targetTypeName).append(" target, MetadataContext metadata) {\n");
            code.append("        return evaluate(target, metadata) ? \"").append(successStatus).append("\" : \"").append(failureStatus).append("\";\n");
            code.append("    }\n\n");

            // getResult without metadata
            code.append("    public String getResult(").append(targetTypeName).append(" target) {\n");
            code.append("        return \"").append(failureStatus).append("\";\n");
            code.append("    }\n");
        } else {
            // Simple evaluate
            code.append("    public boolean evaluate(").append(targetTypeName).append(" target) {\n");
            code.append("        return spec.isSatisfiedBy(target);\n");
            code.append("    }\n\n");

            // getResult
            code.append("    public String getResult(").append(targetTypeName).append(" target) {\n");
            code.append("        if (spec.isSatisfiedBy(target)) {\n");
            code.append("            return \"").append(successStatus).append("\";\n");
            code.append("        } else {\n");
            code.append("            return \"").append(failureStatus).append("\";\n");
            code.append("        }\n");
            code.append("    }\n");
        }

        code.append("\n");
        code.append("    public ").append(isCompound ? "CompoundSpecification" : "Specification").append(" getSpec() {\n");
        code.append("        return spec;\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
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
            return "\"" + escapeString((String) value) + "\"";
        } else if (value instanceof Double) {
            return value.toString();
        } else if (value instanceof Boolean) {
            return value.toString();
        } else {
            return "\"" + value.toString() + "\"";
        }
    }

    private String escapeString(String str) {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
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
