package dev.appget.codegen;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Generates OpenAPI 3.0 specification from .proto files.
 * Reads protobuf model definitions, service definitions, and custom options
 * to produce a comprehensive REST API specification.
 *
 * Pipeline: .proto files -> OpenAPI 3.0 YAML
 *
 * Replaces the models.yaml-based OpenAPIGenerator with a proto-first approach.
 *
 * Usage: java -cp <classpath> dev.appget.codegen.ProtoOpenAPIGenerator <proto-dir> <output-file>
 */
public class ProtoOpenAPIGenerator {

    private static final Logger logger = LogManager.getLogger(ProtoOpenAPIGenerator.class);

    private static final Map<String, String[]> PROTO_TO_OPENAPI_TYPE = new LinkedHashMap<>();

    static {
        PROTO_TO_OPENAPI_TYPE.put("string", new String[]{"string", null});
        PROTO_TO_OPENAPI_TYPE.put("int32", new String[]{"integer", "int32"});
        PROTO_TO_OPENAPI_TYPE.put("int64", new String[]{"integer", "int64"});
        PROTO_TO_OPENAPI_TYPE.put("double", new String[]{"number", "double"});
        PROTO_TO_OPENAPI_TYPE.put("float", new String[]{"number", "float"});
        PROTO_TO_OPENAPI_TYPE.put("bool", new String[]{"boolean", null});
        PROTO_TO_OPENAPI_TYPE.put("bytes", new String[]{"string", "byte"});
    }

    record ProtoField(String name, String type, int number) {}
    record ProtoMessage(String name, List<ProtoField> fields, String domain, boolean hasRules) {}
    record RpcMethod(String name, String inputType, String outputType,
                     String requiredRole, boolean checkOwnership) {}
    record ProtoService(String name, List<RpcMethod> methods) {}

    public static void main(String[] args) throws Exception {
        logger.debug("Entering main with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid arguments. Usage: ProtoOpenAPIGenerator <proto-dir> <output-file>");
            System.err.println("Usage: ProtoOpenAPIGenerator <proto-dir> <output-file>");
            System.exit(1);
        }

        new ProtoOpenAPIGenerator().generate(args[0], args[1]);
        logger.info("Successfully generated OpenAPI spec to: {}", args[1]);
        System.out.println("✓ Generated OpenAPI spec to: " + args[1]);
    }

    public void generate(String protoDir, String outputFile) throws Exception {
        logger.debug("Generating OpenAPI from proto dir: {} -> {}", protoDir, outputFile);
        Path dir = Paths.get(protoDir);

        List<ProtoMessage> allModels = new ArrayList<>();
        List<ProtoMessage> allViews = new ArrayList<>();
        List<ProtoService> allServices = new ArrayList<>();

        for (Path file : listProtoFiles(dir, "_models.proto")) {
            String content = Files.readString(file);
            String domain = fileName(file).replace("_models.proto", "");
            allModels.addAll(parseMessages(content, domain));
        }

        for (Path file : listProtoFiles(dir, "_views.proto")) {
            String content = Files.readString(file);
            String domain = fileName(file).replace("_views.proto", "");
            allViews.addAll(parseMessages(content, domain));
        }

        for (Path file : listProtoFiles(dir, "_services.proto")) {
            String content = Files.readString(file);
            allServices.addAll(parseServices(content));
        }

        logger.info("Parsed {} model(s), {} view(s), {} service(s)",
                allModels.size(), allViews.size(), allServices.size());

        Map<String, Object> openapi = buildOpenAPISpec(allModels, allViews, allServices);

        Yaml yaml = new Yaml();
        Files.writeString(Paths.get(outputFile), yaml.dump(openapi));
        logger.debug("Wrote OpenAPI spec to {}", outputFile);
    }

    // ---- Proto File Parsing ----

    List<ProtoMessage> parseMessages(String protoContent, String domain) {
        List<ProtoMessage> messages = new ArrayList<>();
        List<String> blocks = extractTopLevelBlocks(protoContent, "message");

        for (String block : blocks) {
            int braceStart = block.indexOf('{');
            String header = block.substring(0, braceStart).trim();
            String name = header.replace("message", "").trim();

            // Skip helper messages (Id/List wrappers from service protos)
            if (name.endsWith("Id") || name.endsWith("List")) continue;

            String body = block.substring(braceStart + 1, block.lastIndexOf('}'));

            List<ProtoField> fields = parseFields(body);
            if (fields.isEmpty()) continue;

            boolean hasRules = body.contains("rules.rule_set");
            String msgDomain = domain;
            Pattern domainPat = Pattern.compile("option\\s+\\(rules\\.domain\\)\\s*=\\s*\"(\\w+)\"");
            Matcher dm = domainPat.matcher(body);
            if (dm.find()) {
                msgDomain = dm.group(1);
            }

            messages.add(new ProtoMessage(name, fields, msgDomain, hasRules));
            logger.debug("Parsed message: {} ({} fields, domain={})", name, fields.size(), msgDomain);
        }

        return messages;
    }

    List<ProtoService> parseServices(String protoContent) {
        List<ProtoService> services = new ArrayList<>();
        List<String> blocks = extractTopLevelBlocks(protoContent, "service");

        for (String block : blocks) {
            int braceStart = block.indexOf('{');
            String header = block.substring(0, braceStart).trim();
            String name = header.replace("service", "").trim();
            String body = block.substring(braceStart + 1, block.lastIndexOf('}'));

            List<RpcMethod> methods = parseRpcMethods(body);
            services.add(new ProtoService(name, methods));
            logger.debug("Parsed service: {} ({} methods)", name, methods.size());
        }

        return services;
    }

    private List<ProtoField> parseFields(String msgBody) {
        List<ProtoField> fields = new ArrayList<>();

        for (String line : msgBody.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("option") || line.startsWith("//")
                    || line.startsWith("repeated") || line.startsWith("}")) continue;

            Matcher fm = Pattern.compile("^(\\w+)\\s+(\\w+)\\s*=\\s*(\\d+)\\s*;")
                    .matcher(line);
            if (fm.find()) {
                fields.add(new ProtoField(fm.group(2), fm.group(1), Integer.parseInt(fm.group(3))));
            }
        }

        return fields;
    }

    private List<RpcMethod> parseRpcMethods(String svcBody) {
        List<RpcMethod> methods = new ArrayList<>();
        List<String> rpcBlocks = extractRpcBlocks(svcBody);

        for (String rpc : rpcBlocks) {
            Pattern rpcHeader = Pattern.compile(
                    "rpc\\s+(\\w+)\\s*\\(([^)]+)\\)\\s*returns\\s*\\(([^)]+)\\)");
            Matcher m = rpcHeader.matcher(rpc);
            if (!m.find()) continue;

            String methodName = m.group(1);
            String inputType = stripPackage(m.group(2).trim());
            String outputType = stripPackage(m.group(3).trim());

            String requiredRole = null;
            Matcher rm = Pattern.compile("option\\s+\\(rules\\.required_role\\)\\s*=\\s*\"([^\"]+)\"")
                    .matcher(rpc);
            if (rm.find()) requiredRole = rm.group(1);

            boolean checkOwnership = rpc.contains("check_ownership") && rpc.contains("true");

            methods.add(new RpcMethod(methodName, inputType, outputType, requiredRole, checkOwnership));
        }

        return methods;
    }

    // ---- Block Extraction (brace-counting) ----

    private List<String> extractTopLevelBlocks(String content, String keyword) {
        List<String> blocks = new ArrayList<>();
        int pos = 0;

        while (true) {
            int start = content.indexOf(keyword + " ", pos);
            if (start < 0) break;

            // Ensure it's at start of line (not inside another block)
            if (start > 0 && content.charAt(start - 1) != '\n' && content.charAt(start - 1) != ' '
                    && content.charAt(start - 1) != '\r') {
                pos = start + 1;
                continue;
            }

            int braceStart = content.indexOf('{', start);
            if (braceStart < 0) break;

            int braceEnd = findMatchingBrace(content, braceStart);
            if (braceEnd < 0) break;

            blocks.add(content.substring(start, braceEnd + 1));
            pos = braceEnd + 1;
        }

        return blocks;
    }

    private List<String> extractRpcBlocks(String svcBody) {
        List<String> rpcs = new ArrayList<>();
        int pos = 0;

        while (true) {
            int rpcStart = svcBody.indexOf("rpc ", pos);
            if (rpcStart < 0) break;

            // Find the end: either closing brace or next rpc
            int braceOpen = svcBody.indexOf('{', rpcStart);
            int nextRpc = svcBody.indexOf("rpc ", rpcStart + 4);

            if (braceOpen >= 0 && (nextRpc < 0 || braceOpen < nextRpc)) {
                int braceClose = findMatchingBrace(svcBody, braceOpen);
                if (braceClose < 0) break;
                rpcs.add(svcBody.substring(rpcStart, braceClose + 1));
                pos = braceClose + 1;
            } else {
                // Empty method body: rpc Foo(...) returns (...) {}
                int lineEnd = svcBody.indexOf('\n', rpcStart);
                if (lineEnd < 0) lineEnd = svcBody.length();
                rpcs.add(svcBody.substring(rpcStart, lineEnd));
                pos = lineEnd;
            }
        }

        return rpcs;
    }

    private int findMatchingBrace(String text, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            if (text.charAt(i) == '{') depth++;
            else if (text.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ---- OpenAPI Spec Building ----

    private Map<String, Object> buildOpenAPISpec(
            List<ProtoMessage> models, List<ProtoMessage> views, List<ProtoService> services) {

        Map<String, Object> openapi = new LinkedHashMap<>();
        openapi.put("openapi", "3.0.0");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", "AppGet API");
        info.put("version", "1.0.0");
        info.put("description", "Auto-generated REST API from protobuf service definitions");
        openapi.put("info", info);

        List<Map<String, Object>> servers = new ArrayList<>();
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("url", "http://localhost:8080");
        server.put("description", "Development server");
        servers.add(server);
        openapi.put("servers", servers);

        // Schemas from models + views
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (ProtoMessage msg : models) {
            schemas.put(msg.name(), buildSchema(msg));
        }
        for (ProtoMessage view : views) {
            schemas.put(view.name(), buildSchema(view));
        }

        // Paths from services
        Map<String, Object> paths = new LinkedHashMap<>();
        for (ProtoService svc : services) {
            String modelName = svc.name().replace("Service", "");
            String resourcePath = "/" + camelToKebab(modelName) + "s";

            Map<String, Object> collectionOps = new LinkedHashMap<>();
            Map<String, Object> itemOps = new LinkedHashMap<>();

            for (RpcMethod method : svc.methods()) {
                if (method.name().startsWith("Create")) {
                    collectionOps.put("post", buildCreateOp(modelName, method));
                } else if (method.name().startsWith("List")) {
                    collectionOps.put("get", buildListOp(modelName));
                } else if (method.name().startsWith("Get")) {
                    itemOps.put("get", buildGetOp(modelName));
                } else if (method.name().startsWith("Update")) {
                    itemOps.put("put", buildUpdateOp(modelName, method));
                } else if (method.name().startsWith("Delete")) {
                    itemOps.put("delete", buildDeleteOp(modelName, method));
                }
            }

            if (!collectionOps.isEmpty()) paths.put(resourcePath, collectionOps);
            if (!itemOps.isEmpty()) paths.put(resourcePath + "/{id}", itemOps);
        }

        // Components
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", schemas);

        boolean hasAuth = services.stream()
                .flatMap(s -> s.methods().stream())
                .anyMatch(m -> m.requiredRole() != null);
        if (hasAuth) {
            Map<String, Object> bearerAuth = new LinkedHashMap<>();
            bearerAuth.put("type", "http");
            bearerAuth.put("scheme", "bearer");
            bearerAuth.put("bearerFormat", "JWT");
            components.put("securitySchemes", Map.of("bearerAuth", bearerAuth));
        }

        openapi.put("components", components);
        openapi.put("paths", paths);

        return openapi;
    }

    Map<String, Object> buildSchema(ProtoMessage msg) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", msg.name() + " model");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ProtoField field : msg.fields()) {
            Map<String, Object> fieldSchema = new LinkedHashMap<>();
            String[] openAPIType = PROTO_TO_OPENAPI_TYPE.getOrDefault(field.type(), new String[]{"string", null});
            fieldSchema.put("type", openAPIType[0]);
            if (openAPIType[1] != null) {
                fieldSchema.put("format", openAPIType[1]);
            }

            String jsonName = snakeToCamel(field.name());
            properties.put(jsonName, fieldSchema);
            required.add(jsonName);
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    // ---- Operation Builders ----

    private Map<String, Object> buildCreateOp(String modelName, RpcMethod method) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Create " + modelName);
        op.put("operationId", "create" + modelName);
        op.put("tags", List.of(modelName));
        op.put("requestBody", buildRequestBody(modelName));

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("201", buildJsonResponse(modelName + " created successfully", modelName));
        responses.put("422", buildPlainResponse("Validation or rule check failed"));
        op.put("responses", responses);

        if (method.requiredRole() != null) {
            op.put("security", List.of(Map.of("bearerAuth", List.of())));
        }
        return op;
    }

    private Map<String, Object> buildListOp(String modelName) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "List " + modelName + " records");
        op.put("operationId", "list" + modelName + "s");
        op.put("tags", List.of(modelName));

        Map<String, Object> arraySchema = new LinkedHashMap<>();
        arraySchema.put("type", "array");
        arraySchema.put("items", Map.of("$ref", "#/components/schemas/" + modelName));

        Map<String, Object> response200 = new LinkedHashMap<>();
        response200.put("description", "List of " + modelName + " records");
        Map<String, Object> jsonContent = new LinkedHashMap<>();
        jsonContent.put("schema", arraySchema);
        response200.put("content", Map.of("application/json", jsonContent));

        op.put("responses", Map.of("200", response200));
        return op;
    }

    private Map<String, Object> buildGetOp(String modelName) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Get " + modelName + " by ID");
        op.put("operationId", "get" + modelName);
        op.put("tags", List.of(modelName));
        op.put("parameters", List.of(buildIdParam()));

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", buildJsonResponse(modelName + " found", modelName));
        responses.put("404", buildPlainResponse(modelName + " not found"));
        op.put("responses", responses);
        return op;
    }

    private Map<String, Object> buildUpdateOp(String modelName, RpcMethod method) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Update " + modelName);
        op.put("operationId", "update" + modelName);
        op.put("tags", List.of(modelName));
        op.put("parameters", List.of(buildIdParam()));
        op.put("requestBody", buildRequestBody(modelName));

        Map<String, Object> responses = new LinkedHashMap<>();
        responses.put("200", buildJsonResponse(modelName + " updated successfully", modelName));
        responses.put("404", buildPlainResponse(modelName + " not found"));
        responses.put("422", buildPlainResponse("Validation or rule check failed"));
        op.put("responses", responses);

        if (method.requiredRole() != null) {
            op.put("security", List.of(Map.of("bearerAuth", List.of())));
        }
        return op;
    }

    private Map<String, Object> buildDeleteOp(String modelName, RpcMethod method) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Delete " + modelName);
        op.put("operationId", "delete" + modelName);
        op.put("tags", List.of(modelName));
        op.put("parameters", List.of(buildIdParam()));

        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> r204 = new LinkedHashMap<>();
        r204.put("description", modelName + " deleted successfully");
        responses.put("204", r204);
        responses.put("404", buildPlainResponse(modelName + " not found"));
        op.put("responses", responses);

        if (method.requiredRole() != null) {
            op.put("security", List.of(Map.of("bearerAuth", List.of())));
        }
        return op;
    }

    // ---- Response/Parameter Helpers ----

    private Map<String, Object> buildIdParam() {
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("name", "id");
        param.put("in", "path");
        param.put("required", true);
        param.put("schema", Map.of("type", "string"));
        return param;
    }

    private Map<String, Object> buildRequestBody(String modelName) {
        Map<String, Object> rb = new LinkedHashMap<>();
        rb.put("required", true);
        Map<String, Object> jsonContent = new LinkedHashMap<>();
        jsonContent.put("schema", Map.of("$ref", "#/components/schemas/" + modelName));
        rb.put("content", Map.of("application/json", jsonContent));
        return rb;
    }

    private Map<String, Object> buildJsonResponse(String description, String schemaName) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("description", description);
        Map<String, Object> jsonContent = new LinkedHashMap<>();
        jsonContent.put("schema", Map.of("$ref", "#/components/schemas/" + schemaName));
        response.put("content", Map.of("application/json", jsonContent));
        return response;
    }

    private Map<String, Object> buildPlainResponse(String description) {
        return Map.of("description", description);
    }

    // ---- Utilities ----

    private List<Path> listProtoFiles(Path dir, String suffix) throws IOException {
        return Files.list(dir)
                .filter(f -> f.getFileName().toString().endsWith(suffix))
                .sorted()
                .toList();
    }

    private String fileName(Path file) {
        return file.getFileName().toString();
    }

    private String stripPackage(String fullType) {
        if (fullType.contains(".")) {
            return fullType.substring(fullType.lastIndexOf('.') + 1);
        }
        return fullType;
    }

    private String camelToKebab(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    String snakeToCamel(String snake) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : snake.toCharArray()) {
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
}
