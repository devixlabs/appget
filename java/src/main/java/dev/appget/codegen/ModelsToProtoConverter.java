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
 * Reads models.yaml (the single intermediate) to produce proto schema definitions.
 * Business rules travel in specs.yaml and are NOT embedded in proto files.
 *
 * Pipeline: models.yaml → .proto files → protoc → model classes (in any target language)
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
            logger.error("Invalid arguments. Usage: ModelsToProtoConverter <models.yaml> <outputDir>");
            System.err.println("Usage: ModelsToProtoConverter <models.yaml> <outputDir>");
            System.exit(1);
        }
        logger.info("Converting models.yaml to proto: {} -> {}", args[0], args[1]);
        new ModelsToProtoConverter().convert(args[0], args[1]);
        logger.debug("Exiting main");
    }

    @SuppressWarnings("unchecked")
    public void convert(String modelsFile, String outputDir) throws Exception {
        logger.debug("Entering convert: modelsFile={}, outputDir={}", modelsFile, outputDir);

        Map<String, List<ProtoMessage>> domainModels = new TreeMap<>();
        Map<String, List<ProtoMessage>> domainViews = new TreeMap<>();
        parseModelsYaml(modelsFile, domainModels, domainViews);
        logger.info("Parsed {} domain(s) from models.yaml", domainModels.size());

        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        for (Map.Entry<String, List<ProtoMessage>> entry : domainModels.entrySet()) {
            String domain = entry.getKey();
            String content = generateModelProto(domain, entry.getValue());
            Path protoFile = outputPath.resolve(domain + "_models.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        for (Map.Entry<String, List<ProtoMessage>> entry : domainViews.entrySet()) {
            String domain = entry.getKey();
            String content = generateViewProto(domain, entry.getValue());
            Path protoFile = outputPath.resolve(domain + "_views.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        for (Map.Entry<String, List<ProtoMessage>> entry : domainModels.entrySet()) {
            String domain = entry.getKey();
            String content = generateServiceProto(domain, entry.getValue());
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

    // ---- Proto Output Generation ----

    private String generateModelProto(String domain, List<ProtoMessage> models) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("option java_package = \"").append(javaPackage(domain, "model")).append("\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package ").append(domain).append(";\n");

        for (ProtoMessage msg : models) {
            sb.append("\nmessage ").append(msg.name()).append(" {\n");
            int fieldNum = 1;
            for (ProtoField field : msg.fields()) {
                sb.append("  ").append(field.type()).append(" ").append(field.name())
                  .append(" = ").append(fieldNum++).append(";\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String generateViewProto(String domain, List<ProtoMessage> views) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("option java_package = \"").append(javaPackage(domain, "view")).append("\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package ").append(domain).append("_views;\n");

        for (ProtoMessage msg : views) {
            sb.append("\nmessage ").append(msg.name()).append(" {\n");
            int fieldNum = 1;
            for (ProtoField field : msg.fields()) {
                sb.append("  ").append(field.type()).append(" ").append(field.name())
                  .append(" = ").append(fieldNum++).append(";\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String generateServiceProto(String domain, List<ProtoMessage> models) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("import \"google/protobuf/empty.proto\";\n");
        sb.append("import \"").append(domain).append("_models.proto\";\n");
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
              .append(") returns (").append(domain).append(".").append(name).append(") {}\n");
            sb.append("  rpc Get").append(name).append("(").append(name).append("Id")
              .append(") returns (").append(domain).append(".").append(name).append(") {}\n");
            sb.append("  rpc Update").append(name).append("(").append(domain).append(".").append(name)
              .append(") returns (").append(domain).append(".").append(name).append(") {}\n");
            sb.append("  rpc Delete").append(name).append("(").append(name).append("Id")
              .append(") returns (google.protobuf.Empty) {}\n");
            sb.append("  rpc List").append(name).append("s(google.protobuf.Empty")
              .append(") returns (").append(name).append("List) {}\n");
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String javaPackage(String domain, String subpackage) {
        if (domain.equals("appget")) {
            return "dev.appget." + subpackage;
        }
        return "dev.appget." + domain + "." + subpackage;
    }
}
