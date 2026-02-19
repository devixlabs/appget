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
 * models.yaml stores language-neutral types (string, int32, int64, float64, bool, date, datetime, decimal).
 * This converter uses JavaTypeRegistry.INSTANCE.neutralToProto() for all type lookups.
 *
 * Generated .proto files are intermediate artifacts (git-ignored).
 * models.yaml is the single source of truth for model definitions.
 */
public class ModelsToProtoConverter {

    private static final Logger logger = LogManager.getLogger(ModelsToProtoConverter.class);

    private record ProtoField(String name, String protoType, boolean optional, int fieldNumber) {}
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

        // Detect if any domain has decimal or timestamp fields for shared imports
        boolean anyDecimal = hasDecimalFields(domainModels, domainViews);

        // Generate appget_common.proto if any decimal fields exist
        if (anyDecimal) {
            String commonContent = generateCommonProto();
            Path commonProto = outputPath.resolve("appget_common.proto");
            Files.writeString(commonProto, commonContent);
            logger.info("Generated {}", commonProto);
            System.out.println("Generated " + commonProto);
        }

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
        int fallbackFieldNum = 1;
        for (Map<String, Object> field : fields) {
            String name = (String) field.get("name");
            String neutralType = (String) field.get("type");

            // Handle both neutral types (new) and legacy Java types (backward compat)
            String protoType;
            if (isNeutralType(neutralType)) {
                protoType = JavaTypeRegistry.INSTANCE.neutralToProto(neutralType);
            } else {
                // Legacy Java type from old models.yaml - convert via javaToNeutral
                String neutral = JavaTypeRegistry.javaToNeutral(neutralType);
                protoType = JavaTypeRegistry.INSTANCE.neutralToProto(neutral);
            }

            Object nullableObj = field.get("nullable");
            boolean nullable = (nullableObj instanceof Boolean) ? (Boolean) nullableObj : false;

            // Use field_number from models.yaml if present, otherwise sequential
            Object fnObj = field.get("field_number");
            int fieldNumber;
            if (fnObj instanceof Integer) {
                fieldNumber = (Integer) fnObj;
            } else {
                fieldNumber = fallbackFieldNum;
            }
            fallbackFieldNum = fieldNumber + 1;

            protoFields.add(new ProtoField(name, protoType, nullable, fieldNumber));
        }
        return protoFields;
    }

    /**
     * Check if a type string is a neutral type (models.yaml new format).
     * Neutral types: string, int32, int64, float64, bool, date, datetime, decimal
     */
    private boolean isNeutralType(String type) {
        if (type == null) return false;
        if ("string".equals(type)) return true;
        if ("int32".equals(type)) return true;
        if ("int64".equals(type)) return true;
        if ("float64".equals(type)) return true;
        if ("bool".equals(type)) return true;
        if ("date".equals(type)) return true;
        if ("datetime".equals(type)) return true;
        if ("decimal".equals(type)) return true;
        return false;
    }

    private boolean hasDecimalFields(Map<String, List<ProtoMessage>> domainModels,
                                      Map<String, List<ProtoMessage>> domainViews) {
        for (List<ProtoMessage> msgs : domainModels.values()) {
            for (ProtoMessage msg : msgs) {
                for (ProtoField f : msg.fields()) {
                    if ("appget.common.Decimal".equals(f.protoType())) return true;
                }
            }
        }
        for (List<ProtoMessage> msgs : domainViews.values()) {
            for (ProtoMessage msg : msgs) {
                for (ProtoField f : msg.fields()) {
                    if ("appget.common.Decimal".equals(f.protoType())) return true;
                }
            }
        }
        return false;
    }

    // ---- Proto Output Generation ----

    private String generateCommonProto() {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        sb.append("option java_package = \"dev.appget.common\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package appget.common;\n\n");
        sb.append("message Decimal {\n");
        sb.append("  bytes unscaled = 1;\n");
        sb.append("  int32 scale    = 2;\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateModelProto(String domain, List<ProtoMessage> models) {
        boolean needsTimestamp = needsTimestampImport(models);
        boolean needsDecimal = needsDecimalImport(models);

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        if (needsTimestamp) {
            sb.append("import \"google/protobuf/timestamp.proto\";\n");
        }
        if (needsDecimal) {
            sb.append("import \"appget_common.proto\";\n");
        }
        if (needsTimestamp || needsDecimal) {
            sb.append("\n");
        }
        sb.append("option java_package = \"").append(javaPackage(domain, "model")).append("\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package ").append(domain).append(";\n");

        for (ProtoMessage msg : models) {
            sb.append("\nmessage ").append(msg.name()).append(" {\n");
            for (ProtoField field : msg.fields()) {
                sb.append("  ");
                if (field.optional()) {
                    sb.append("optional ");
                }
                sb.append(field.protoType()).append(" ").append(field.name())
                  .append(" = ").append(field.fieldNumber()).append(";\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private String generateViewProto(String domain, List<ProtoMessage> views) {
        boolean needsTimestamp = needsTimestampImport(views);
        boolean needsDecimal = needsDecimalImport(views);

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from models.yaml - DO NOT EDIT MANUALLY\n");
        sb.append("syntax = \"proto3\";\n\n");
        if (needsTimestamp) {
            sb.append("import \"google/protobuf/timestamp.proto\";\n");
        }
        if (needsDecimal) {
            sb.append("import \"appget_common.proto\";\n");
        }
        if (needsTimestamp || needsDecimal) {
            sb.append("\n");
        }
        sb.append("option java_package = \"").append(javaPackage(domain, "view")).append("\";\n");
        sb.append("option java_multiple_files = true;\n\n");
        sb.append("package ").append(domain).append("_views;\n");

        for (ProtoMessage msg : views) {
            sb.append("\nmessage ").append(msg.name()).append(" {\n");
            for (ProtoField field : msg.fields()) {
                sb.append("  ");
                if (field.optional()) {
                    sb.append("optional ");
                }
                sb.append(field.protoType()).append(" ").append(field.name())
                  .append(" = ").append(field.fieldNumber()).append(";\n");
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

    private boolean needsTimestampImport(List<ProtoMessage> messages) {
        for (ProtoMessage msg : messages) {
            for (ProtoField f : msg.fields()) {
                if ("google.protobuf.Timestamp".equals(f.protoType())) return true;
            }
        }
        return false;
    }

    private boolean needsDecimalImport(List<ProtoMessage> messages) {
        for (ProtoMessage msg : messages) {
            for (ProtoField f : msg.fields()) {
                if ("appget.common.Decimal".equals(f.protoType())) return true;
            }
        }
        return false;
    }

    private String javaPackage(String domain, String subpackage) {
        if (domain.equals("appget")) {
            return "dev.appget." + subpackage;
        }
        return "dev.appget." + domain + "." + subpackage;
    }
}
