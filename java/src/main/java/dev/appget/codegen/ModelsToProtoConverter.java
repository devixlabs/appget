package dev.appget.codegen;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Converts models.yaml to Protocol Buffer (.proto) files.
 * Replaces SchemaToProtoConverter by reading models.yaml (the single intermediate)
 * instead of re-parsing schema.sql and views.sql directly.
 *
 * Pipeline: models.yaml + specs.yaml -> .proto files -> protoc -> Java classes
 *
 * models.yaml stores language-agnostic snake_case field names and Java types.
 * This converter translates Java types to proto types via JavaUtils.JAVA_TO_PROTO_TYPE.
 *
 * Generated .proto files are intermediate artifacts (git-ignored).
 * models.yaml is the single source of truth for model definitions.
 */
public class ModelsToProtoConverter {

    private static final Logger logger = LogManager.getLogger(ModelsToProtoConverter.class);

    private record ProtoField(String name, String type) {}
    private record ProtoMessage(String name, List<ProtoField> fields) {}

    public static void main(String[] args) throws Exception {
        logger.debug("Entering main with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid arguments. Usage: ModelsToProtoConverter <models.yaml> <outputDir> [specs.yaml]");
            System.err.println("Usage: ModelsToProtoConverter <models.yaml> <outputDir> [specs.yaml]");
            System.exit(1);
        }

        ModelsToProtoConverter converter = new ModelsToProtoConverter();
        if (args.length == 2) {
            logger.info("Converting models.yaml to proto: {} -> {}", args[0], args[1]);
            converter.convert(args[0], args[1]);
        } else {
            logger.info("Converting models.yaml + specs to proto: {} + {} -> {}", args[0], args[2], args[1]);
            converter.convert(args[0], args[1], args[2]);
        }
        logger.debug("Exiting main");
    }

    public void convert(String modelsFile, String outputDir) throws Exception {
        convert(modelsFile, outputDir, null);
    }

    @SuppressWarnings("unchecked")
    public void convert(String modelsFile, String outputDir, String specsFile) throws Exception {
        logger.debug("Entering convert: modelsFile={}, outputDir={}, specsFile={}", modelsFile, outputDir, specsFile);

        Map<String, List<ProtoMessage>> domainModels = new TreeMap<>();
        Map<String, List<ProtoMessage>> domainViews = new TreeMap<>();
        parseModelsYaml(modelsFile, domainModels, domainViews);
        logger.info("Parsed {} domain(s) from models.yaml", domainModels.size());

        Map<String, List<Map<String, Object>>> rulesByTarget = new LinkedHashMap<>();
        if (specsFile != null && new File(specsFile).exists()) {
            logger.info("Parsing rules from {}", specsFile);
            rulesByTarget = parseSpecsYaml(specsFile);
            logger.info("Loaded rules for {} target(s)", rulesByTarget.size());
        }

        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        boolean hasRules = !rulesByTarget.isEmpty();

        for (Map.Entry<String, List<ProtoMessage>> entry : domainModels.entrySet()) {
            String domain = entry.getKey();
            String content = generateModelProto(domain, entry.getValue(), rulesByTarget, hasRules);
            Path protoFile = outputPath.resolve(domain + "_models.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        for (Map.Entry<String, List<ProtoMessage>> entry : domainViews.entrySet()) {
            String domain = entry.getKey();
            String content = generateViewProto(domain, entry.getValue(), rulesByTarget, hasRules);
            Path protoFile = outputPath.resolve(domain + "_views.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        for (Map.Entry<String, List<ProtoMessage>> entry : domainModels.entrySet()) {
            String domain = entry.getKey();
            String content = generateServiceProto(domain, entry.getValue(), hasRules);
            Path protoFile = outputPath.resolve(domain + "_services.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        logger.debug("Exiting convert");
    }

    // ---- YAML Parsing ----

    @SuppressWarnings("unchecked")
    private void parseModelsYaml(String modelsFile,
                                  Map<String, List<ProtoMessage>> domainModels,
                                  Map<String, List<ProtoMessage>> domainViews) throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File(modelsFile))) {
            data = yaml.load(in);
        }
        logger.info("Read models file: {}", modelsFile);

        Map<String, Object> domains = (Map<String, Object>) data.get("domains");
        if (domains == null) {
            logger.warn("No domains found in {}", modelsFile);
            return;
        }

        for (Map.Entry<String, Object> domainEntry : domains.entrySet()) {
            String domain = domainEntry.getKey();
            Map<String, Object> domainConfig = (Map<String, Object>) domainEntry.getValue();

            List<Map<String, Object>> models = (List<Map<String, Object>>) domainConfig.get("models");
            if (models != null) {
                for (Map<String, Object> model : models) {
                    String name = (String) model.get("name");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");
                    List<ProtoField> protoFields = convertFields(fields);
                    domainModels.computeIfAbsent(domain, k -> new ArrayList<>()).add(new ProtoMessage(name, protoFields));
                    logger.debug("Parsed model: {} in domain: {}", name, domain);
                }
            }

            List<Map<String, Object>> views = (List<Map<String, Object>>) domainConfig.get("views");
            if (views != null) {
                for (Map<String, Object> view : views) {
                    String name = (String) view.get("name");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) view.get("fields");
                    List<ProtoField> protoFields = convertFields(fields);
                    domainViews.computeIfAbsent(domain, k -> new ArrayList<>()).add(new ProtoMessage(name, protoFields));
                    logger.debug("Parsed view: {} in domain: {}", name, domain);
                }
            }
        }
    }

    private List<ProtoField> convertFields(List<Map<String, Object>> fields) {
        List<ProtoField> protoFields = new ArrayList<>();
        if (fields == null) {
            return protoFields;
        }
        for (Map<String, Object> field : fields) {
            String name = (String) field.get("name");
            String javaType = (String) field.get("type");
            String protoType = JavaUtils.JAVA_TO_PROTO_TYPE.getOrDefault(javaType, "string");
            protoFields.add(new ProtoField(name, protoType));
        }
        return protoFields;
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<Map<String, Object>>> parseSpecsYaml(String specsFile) throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File(specsFile))) {
            data = yaml.load(in);
        }

        Map<String, List<Map<String, Object>>> rulesByTarget = new LinkedHashMap<>();
        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) data.get("rules");

        if (rawRules != null) {
            for (Map<String, Object> raw : rawRules) {
                Map<String, Object> target = (Map<String, Object>) raw.get("target");
                String targetName = target != null ? (String) target.get("name") : "Employee";
                rulesByTarget.computeIfAbsent(targetName, k -> new ArrayList<>()).add(raw);
            }
        }
        return rulesByTarget;
    }

    // ---- Proto Output Generation ----

    private String generateModelProto(String domain, List<ProtoMessage> models,
            Map<String, List<Map<String, Object>>> rulesByTarget, boolean hasRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        if (hasRules) {
            sb.append("import \"rules.proto\";\n\n");
        }
        sb.append("option java_package = \"").append(javaPackage(domain, "model")).append("\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package ").append(domain).append(";\n");

        for (ProtoMessage msg : models) {
            sb.append("\nmessage ").append(msg.name()).append(" {\n");
            List<Map<String, Object>> rules = rulesByTarget.getOrDefault(msg.name(), List.of());
            if (!rules.isEmpty()) {
                sb.append("  option (rules.domain) = \"").append(domain).append("\";\n");
                sb.append(generateRuleSetOption(rules));
            }
            int fieldNum = 1;
            for (ProtoField field : msg.fields()) {
                sb.append("  ").append(field.type()).append(" ").append(field.name())
                  .append(" = ").append(fieldNum++).append(";\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String generateViewProto(String domain, List<ProtoMessage> views,
            Map<String, List<Map<String, Object>>> rulesByTarget, boolean hasRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        if (hasRules) {
            sb.append("import \"rules.proto\";\n\n");
        }
        sb.append("option java_package = \"").append(javaPackage(domain, "view")).append("\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package ").append(domain).append("_views;\n");

        for (ProtoMessage msg : views) {
            sb.append("\nmessage ").append(msg.name()).append(" {\n");
            List<Map<String, Object>> rules = rulesByTarget.getOrDefault(msg.name(), List.of());
            if (!rules.isEmpty()) {
                sb.append("  option (rules.domain) = \"").append(domain).append("\";\n");
                sb.append(generateRuleSetOption(rules));
            }
            int fieldNum = 1;
            for (ProtoField field : msg.fields()) {
                sb.append("  ").append(field.type()).append(" ").append(field.name())
                  .append(" = ").append(fieldNum++).append(";\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String generateServiceProto(String domain, List<ProtoMessage> models, boolean hasRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("import \"google/protobuf/empty.proto\";\n");
        sb.append("import \"").append(domain).append("_models.proto\";\n");
        if (hasRules) {
            sb.append("import \"rules.proto\";\n");
        }
        sb.append("\n");

        String servicePkg = domain.equals("appget") ? "dev.appget.service" : "dev.appget." + domain + ".service";
        sb.append("option java_package = \"").append(servicePkg).append("\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package ").append(domain).append("_services;\n");

        for (ProtoMessage msg : models) {
            String name = msg.name();

            sb.append("\nmessage ").append(name).append("Id {\n");
            sb.append("  string id = 1;\n");
            sb.append("}\n");

            sb.append("\nmessage ").append(name).append("List {\n");
            sb.append("  repeated ").append(domain).append(".").append(name).append(" items = 1;\n");
            sb.append("}\n");

            sb.append("\nservice ").append(name).append("Service {\n");
            sb.append("  rpc Create").append(name).append("(").append(domain).append(".").append(name)
              .append(") returns (").append(domain).append(".").append(name).append(") {");
            if (hasRules) {
                sb.append("\n    option (rules.required_role) = \"ROLE_USER\";\n  ");
            }
            sb.append("}\n");
            sb.append("  rpc Get").append(name).append("(").append(name).append("Id")
              .append(") returns (").append(domain).append(".").append(name).append(") {}\n");
            sb.append("  rpc Update").append(name).append("(").append(domain).append(".").append(name)
              .append(") returns (").append(domain).append(".").append(name).append(") {");
            if (hasRules) {
                sb.append("\n    option (rules.required_role) = \"ROLE_USER\";\n  ");
            }
            sb.append("}\n");
            sb.append("  rpc Delete").append(name).append("(").append(name).append("Id")
              .append(") returns (google.protobuf.Empty) {");
            if (hasRules) {
                sb.append("\n    option (rules.required_role) = \"ROLE_USER\";\n    option (rules.check_ownership) = true;\n  ");
            }
            sb.append("}\n");
            sb.append("  rpc List").append(name).append("s(google.protobuf.Empty")
              .append(") returns (").append(name).append("List) {}\n");
            sb.append("}\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String generateRuleSetOption(List<Map<String, Object>> rules) {
        StringBuilder sb = new StringBuilder();
        sb.append("  option (rules.rule_set) = {\n");
        for (Map<String, Object> rule : rules) {
            sb.append("    rules: {\n");
            sb.append("      name: \"").append(rule.get("name")).append("\"\n");

            Boolean blocking = (Boolean) rule.get("blocking");
            if (blocking != null && blocking) {
                sb.append("      blocking: true\n");
            }

            Map<String, String> thenBlock = (Map<String, String>) rule.get("then");
            Map<String, String> elseBlock = (Map<String, String>) rule.get("else");
            sb.append("      success_status: \"").append(thenBlock.get("status")).append("\"\n");
            sb.append("      failure_status: \"").append(elseBlock.get("status")).append("\"\n");

            Object conditions = rule.get("conditions");
            if (conditions instanceof Map) {
                Map<String, Object> compound = (Map<String, Object>) conditions;
                sb.append("      compound_conditions: {\n");
                sb.append("        logic: \"").append(compound.get("operator")).append("\"\n");
                List<Map<String, Object>> clauses = (List<Map<String, Object>>) compound.get("clauses");
                if (clauses != null) {
                    for (Map<String, Object> clause : clauses) {
                        sb.append("        clauses: {\n");
                        sb.append("          field: \"").append(clause.get("field")).append("\"\n");
                        sb.append("          operator: \"").append(clause.get("operator")).append("\"\n");
                        sb.append("          value: \"").append(clause.get("value")).append("\"\n");
                        sb.append("        }\n");
                    }
                }
                sb.append("      }\n");
            } else if (conditions instanceof List) {
                List<Map<String, Object>> condList = (List<Map<String, Object>>) conditions;
                for (Map<String, Object> cond : condList) {
                    sb.append("      simple_conditions: {\n");
                    sb.append("        field: \"").append(cond.get("field")).append("\"\n");
                    sb.append("        operator: \"").append(cond.get("operator")).append("\"\n");
                    sb.append("        value: \"").append(cond.get("value")).append("\"\n");
                    sb.append("      }\n");
                }
            }

            Map<String, Object> requires = (Map<String, Object>) rule.get("requires");
            if (requires != null) {
                for (Map.Entry<String, Object> entry : requires.entrySet()) {
                    sb.append("      metadata_requirements: {\n");
                    sb.append("        category: \"").append(entry.getKey()).append("\"\n");
                    List<Map<String, Object>> reqs = (List<Map<String, Object>>) entry.getValue();
                    if (reqs != null) {
                        for (Map<String, Object> req : reqs) {
                            sb.append("        conditions: {\n");
                            sb.append("          field: \"").append(req.get("field")).append("\"\n");
                            sb.append("          operator: \"").append(req.get("operator")).append("\"\n");
                            sb.append("          value: \"").append(req.get("value")).append("\"\n");
                            sb.append("        }\n");
                        }
                    }
                    sb.append("      }\n");
                }
            }

            sb.append("    }\n");
        }
        sb.append("  };\n");
        return sb.toString();
    }

    private String javaPackage(String domain, String subpackage) {
        if (domain.equals("appget")) {
            return "dev.appget." + subpackage;
        }
        return "dev.appget." + domain + "." + subpackage;
    }

    private String toModelName(String tableName) {
        String singular = singularize(tableName);
        return singular.substring(0, 1).toUpperCase() + singular.substring(1);
    }

    private String toViewModelName(String viewName) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : viewName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String singularize(String tableName) {
        String lower = tableName.toLowerCase();
        if (lower.endsWith("ies")) {
            return lower.substring(0, lower.length() - 3) + "y";
        }
        if (lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes")
            || lower.endsWith("ches") || lower.endsWith("shes")) {
            return lower.substring(0, lower.length() - 2);
        }
        if (lower.endsWith("oes")) {
            return lower.substring(0, lower.length() - 2);
        }
        if (lower.endsWith("s")) {
            return lower.substring(0, lower.length() - 1);
        }
        return lower;
    }
}
