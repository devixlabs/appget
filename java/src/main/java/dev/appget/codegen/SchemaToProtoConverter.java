package dev.appget.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * Converts schema.sql and views.sql to Protocol Buffer (.proto) files.
 * Replaces ModelGenerator by leveraging protoc for Java code generation.
 *
 * Pipeline: schema.sql + views.sql -> .proto files -> protoc -> Java classes
 *
 * Generated .proto files are intermediate artifacts (git-ignored).
 * SQL remains the source of truth.
 */
public class SchemaToProtoConverter {

    private static final Logger logger = LogManager.getLogger(SchemaToProtoConverter.class);

    private static final Map<String, String> SQL_TO_PROTO_TYPE = new LinkedHashMap<>();
    private static final Map<String, String> DOMAIN_MAPPING = new LinkedHashMap<>();
    private static final Map<String, String> VIEW_DOMAIN_MAPPING = new LinkedHashMap<>();

    static {
        SQL_TO_PROTO_TYPE.put("VARCHAR", "string");
        SQL_TO_PROTO_TYPE.put("CHAR", "string");
        SQL_TO_PROTO_TYPE.put("TEXT", "string");
        SQL_TO_PROTO_TYPE.put("INT", "int32");
        SQL_TO_PROTO_TYPE.put("INTEGER", "int32");
        SQL_TO_PROTO_TYPE.put("BIGINT", "int64");
        SQL_TO_PROTO_TYPE.put("LONG", "int64");
        SQL_TO_PROTO_TYPE.put("SMALLINT", "int32");
        SQL_TO_PROTO_TYPE.put("DECIMAL", "double");
        SQL_TO_PROTO_TYPE.put("NUMERIC", "double");
        SQL_TO_PROTO_TYPE.put("FLOAT", "double");
        SQL_TO_PROTO_TYPE.put("DOUBLE", "double");
        SQL_TO_PROTO_TYPE.put("REAL", "double");
        SQL_TO_PROTO_TYPE.put("DATE", "string");
        SQL_TO_PROTO_TYPE.put("TIMESTAMP", "string");
        SQL_TO_PROTO_TYPE.put("DATETIME", "string");
        SQL_TO_PROTO_TYPE.put("BOOLEAN", "bool");
        SQL_TO_PROTO_TYPE.put("BOOL", "bool");

        DOMAIN_MAPPING.put("roles", "appget");
        DOMAIN_MAPPING.put("employees", "appget");
        DOMAIN_MAPPING.put("departments", "hr");
        DOMAIN_MAPPING.put("salaries", "hr");
        DOMAIN_MAPPING.put("invoices", "finance");

        VIEW_DOMAIN_MAPPING.put("employee_salary_view", "appget");
        VIEW_DOMAIN_MAPPING.put("department_budget_view", "hr");
    }

    private record ProtoField(String name, String type) {}
    private record ProtoMessage(String name, List<ProtoField> fields) {}
    private record ColumnInfo(String name, String protoType) {}

    private final Map<String, List<ColumnInfo>> tableColumns = new HashMap<>();

    public static void main(String[] args) throws Exception {
        logger.debug("Entering main with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid arguments. Usage: SchemaToProtoConverter <schema.sql> [views.sql] <outputDir> [specs.yaml]");
            System.err.println("Usage: SchemaToProtoConverter <schema.sql> [views.sql] <outputDir> [specs.yaml]");
            System.exit(1);
        }

        SchemaToProtoConverter converter = new SchemaToProtoConverter();
        if (args.length == 2) {
            logger.info("Converting schema to proto: {} -> {}", args[0], args[1]);
            converter.convert(args[0], null, args[1]);
        } else if (args.length == 3) {
            logger.info("Converting schema + views to proto: {} + {} -> {}", args[0], args[1], args[2]);
            converter.convert(args[0], args[1], args[2]);
        } else {
            logger.info("Converting schema + views + specs to proto: {} + {} + {} -> {}", args[0], args[1], args[3], args[2]);
            converter.convert(args[0], args[1], args[2], args[3]);
        }
        logger.debug("Exiting main");
    }

    public void convert(String schemaFile, String viewsFile, String outputDir) throws Exception {
        convert(schemaFile, viewsFile, outputDir, null);
    }

    @SuppressWarnings("unchecked")
    public void convert(String schemaFile, String viewsFile, String outputDir, String specsFile) throws Exception {
        logger.debug("Entering convert: schemaFile={}, viewsFile={}, outputDir={}, specsFile={}", schemaFile, viewsFile, outputDir, specsFile);
        String sql = Files.readString(Paths.get(schemaFile));
        logger.info("Read schema file: {} ({} bytes)", schemaFile, sql.length());

        Map<String, List<ProtoMessage>> domainModels = new TreeMap<>();
        parseTables(sql, domainModels);

        Map<String, List<ProtoMessage>> domainViews = new TreeMap<>();
        if (viewsFile != null && Files.exists(Paths.get(viewsFile))) {
            String viewsSql = Files.readString(Paths.get(viewsFile));
            logger.info("Read views file: {} ({} bytes)", viewsFile, viewsSql.length());
            parseViews(viewsSql, domainViews);
        }

        // Parse rules from specs.yaml, grouped by target model/view name
        Map<String, List<Map<String, Object>>> rulesByTarget = new LinkedHashMap<>();
        if (specsFile != null && new File(specsFile).exists()) {
            logger.info("Parsing rules from {}", specsFile);
            rulesByTarget = parseSpecsYaml(specsFile);
            logger.info("Loaded rules for {} target(s)", rulesByTarget.size());
        }

        Path outputPath = Paths.get(outputDir);
        Files.createDirectories(outputPath);

        boolean hasRules = !rulesByTarget.isEmpty();

        for (var entry : domainModels.entrySet()) {
            String domain = entry.getKey();
            String content = generateModelProto(domain, entry.getValue(), rulesByTarget, hasRules);
            Path protoFile = outputPath.resolve(domain + "_models.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        for (var entry : domainViews.entrySet()) {
            String domain = entry.getKey();
            String content = generateViewProto(domain, entry.getValue(), rulesByTarget, hasRules);
            Path protoFile = outputPath.resolve(domain + "_views.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        // Generate service .proto files (gRPC CRUD services per domain)
        for (var entry : domainModels.entrySet()) {
            String domain = entry.getKey();
            String content = generateServiceProto(domain, entry.getValue(), hasRules);
            Path protoFile = outputPath.resolve(domain + "_services.proto");
            Files.writeString(protoFile, content);
            logger.info("Generated {}", protoFile);
            System.out.println("Generated " + protoFile);
        }

        logger.debug("Exiting convert");
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

    // ---- SQL Parsing ----

    private void parseTables(String sql, Map<String, List<ProtoMessage>> domainModels) {
        logger.debug("Parsing CREATE TABLE statements");
        int pos = 0;
        int count = 0;

        while (true) {
            int tableStart = sql.indexOf("CREATE TABLE", pos);
            if (tableStart < 0) break;

            int openParen = sql.indexOf('(', tableStart);
            if (openParen < 0) break;

            String tableHeader = sql.substring(tableStart, openParen).trim();
            String[] headerParts = tableHeader.split("\\s+");
            String tableName = headerParts[headerParts.length - 1];

            int closeParen = findMatchingParen(sql, openParen);
            if (closeParen < 0) break;

            String columnDefs = sql.substring(openParen + 1, closeParen);

            logger.debug("Parsing table: {}", tableName);
            List<ColumnInfo> columns = parseColumnsForLookup(columnDefs);
            tableColumns.put(tableName.toLowerCase(), columns);

            List<ProtoField> fields = parseColumns(columnDefs);
            String messageName = toModelName(tableName);
            ProtoMessage message = new ProtoMessage(messageName, fields);

            String domain = DOMAIN_MAPPING.getOrDefault(tableName.toLowerCase(), "appget");
            domainModels.computeIfAbsent(domain, k -> new ArrayList<>()).add(message);

            logger.debug("Added message {} to domain {}", messageName, domain);
            count++;
            pos = closeParen + 1;
        }
        logger.info("Parsed {} table(s)", count);
    }

    private void parseViews(String sql, Map<String, List<ProtoMessage>> domainViews) {
        logger.debug("Parsing CREATE VIEW statements");
        Pattern viewPattern = Pattern.compile(
            "CREATE\\s+VIEW\\s+(\\w+)\\s+AS\\s+SELECT\\s+(.*?)\\s+FROM\\s+(.*?)(?:;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher matcher = viewPattern.matcher(sql);
        int count = 0;

        while (matcher.find()) {
            String viewName = matcher.group(1);
            String selectPart = matcher.group(2).trim();
            String fromPart = matcher.group(3).trim();

            logger.debug("Parsing view: {}", viewName);
            Map<String, String> aliases = parseAliases(fromPart);
            List<ProtoField> fields = parseViewColumns(selectPart, aliases);

            if (fields.isEmpty()) {
                logger.debug("No fields for view {}, skipping", viewName);
                continue;
            }

            String messageName = toViewModelName(viewName);
            ProtoMessage message = new ProtoMessage(messageName, fields);

            String domain = VIEW_DOMAIN_MAPPING.getOrDefault(viewName.toLowerCase(), "appget");
            domainViews.computeIfAbsent(domain, k -> new ArrayList<>()).add(message);

            logger.debug("Added view message {} to domain {}", messageName, domain);
            System.out.println("  Parsed view: " + viewName + " -> " + messageName + " (domain: " + domain + ")");
            count++;
        }
        logger.info("Parsed {} view(s)", count);
    }

    private List<ProtoField> parseColumns(String columnDefs) {
        List<ProtoField> fields = new ArrayList<>();
        List<String> columnLines = smartSplit(columnDefs, ',');

        for (String columnLine : columnLines) {
            columnLine = columnLine.trim();
            if (columnLine.isEmpty() || isConstraintLine(columnLine)) continue;

            ProtoField field = parseColumn(columnLine);
            if (field != null) fields.add(field);
        }
        return fields;
    }

    private ProtoField parseColumn(String columnLine) {
        String[] tokens = columnLine.split("\\s+");
        if (tokens.length < 2) return null;

        String columnName = tokens[0].toLowerCase();
        String typePart = extractTypePart(tokens);
        if (typePart.isEmpty()) return null;

        String baseType = extractBaseType(typePart);
        String protoType = SQL_TO_PROTO_TYPE.getOrDefault(baseType, "string");

        return new ProtoField(columnName, protoType);
    }

    private List<ColumnInfo> parseColumnsForLookup(String columnDefs) {
        List<ColumnInfo> columns = new ArrayList<>();
        List<String> columnLines = smartSplit(columnDefs, ',');

        for (String columnLine : columnLines) {
            columnLine = columnLine.trim();
            if (columnLine.isEmpty() || isConstraintLine(columnLine)) continue;

            String[] tokens = columnLine.split("\\s+");
            if (tokens.length < 2) continue;

            String columnName = tokens[0].toLowerCase();
            String typePart = extractTypePart(tokens);
            if (typePart.isEmpty()) continue;

            String baseType = extractBaseType(typePart);
            String protoType = SQL_TO_PROTO_TYPE.getOrDefault(baseType, "string");

            columns.add(new ColumnInfo(columnName, protoType));
        }
        return columns;
    }

    private List<ProtoField> parseViewColumns(String selectPart, Map<String, String> aliases) {
        List<ProtoField> fields = new ArrayList<>();
        List<String> columns = smartSplit(selectPart, ',');

        for (String col : columns) {
            col = col.trim();
            if (col.isEmpty()) continue;

            String outputName;
            String expression;
            Pattern asPattern = Pattern.compile("(?i)(.+?)\\s+AS\\s+(\\w+)");
            Matcher asMatcher = asPattern.matcher(col);
            if (asMatcher.matches()) {
                expression = asMatcher.group(1).trim();
                outputName = asMatcher.group(2).trim().toLowerCase();
            } else {
                expression = col;
                outputName = expression.contains(".")
                    ? expression.substring(expression.indexOf('.') + 1).trim().toLowerCase()
                    : expression.trim().toLowerCase();
            }

            String protoType = resolveExpressionType(expression, aliases);
            fields.add(new ProtoField(outputName, protoType));
        }
        return fields;
    }

    private Map<String, String> parseAliases(String fromPart) {
        Map<String, String> aliases = new HashMap<>();
        String[] parts = fromPart.split("(?i)\\s+(?:INNER\\s+|LEFT\\s+|RIGHT\\s+|FULL\\s+|CROSS\\s+)?JOIN\\s+");

        for (String part : parts) {
            String tablePart = part.replaceAll("(?i)\\s+ON\\s+.*", "").trim();
            String[] tokens = tablePart.split("\\s+");
            if (tokens.length >= 2) {
                aliases.put(tokens[1].toLowerCase(), tokens[0].toLowerCase());
            } else if (tokens.length == 1) {
                aliases.put(tokens[0].toLowerCase(), tokens[0].toLowerCase());
            }
        }
        return aliases;
    }

    private String resolveExpressionType(String expression, Map<String, String> aliases) {
        String upper = expression.toUpperCase().trim();
        if (upper.startsWith("COUNT(")) return "int64";
        if (upper.startsWith("SUM(")) return "double";
        if (upper.startsWith("AVG(")) return "double";
        if (upper.startsWith("MIN(") || upper.startsWith("MAX(")) {
            String inner = expression.substring(expression.indexOf('(') + 1, expression.lastIndexOf(')')).trim();
            return resolveExpressionType(inner, aliases);
        }

        if (expression.contains(".")) {
            String[] parts = expression.split("\\.");
            String alias = parts[0].trim().toLowerCase();
            String colName = parts[1].trim().toLowerCase();

            String tableName = aliases.get(alias);
            if (tableName != null) {
                List<ColumnInfo> cols = tableColumns.get(tableName);
                if (cols != null) {
                    for (ColumnInfo ci : cols) {
                        if (ci.name().equalsIgnoreCase(colName)) {
                            return ci.protoType();
                        }
                    }
                }
            }
        }
        return "string";
    }

    // ---- Proto Output Generation ----

    private String generateModelProto(String domain, List<ProtoMessage> models) {
        return generateModelProto(domain, models, Map.of(), false);
    }

    private String generateModelProto(String domain, List<ProtoMessage> models,
            Map<String, List<Map<String, Object>>> rulesByTarget, boolean hasRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from schema.sql - DO NOT EDIT MANUALLY\n");
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

    private String generateViewProto(String domain, List<ProtoMessage> views) {
        return generateViewProto(domain, views, Map.of(), false);
    }

    private String generateViewProto(String domain, List<ProtoMessage> views,
            Map<String, List<Map<String, Object>>> rulesByTarget, boolean hasRules) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from views.sql - DO NOT EDIT MANUALLY\n");
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
        sb.append("// Generated from schema.sql - DO NOT EDIT MANUALLY\n");
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

            // ID request message
            sb.append("\nmessage ").append(name).append("Id {\n");
            sb.append("  string id = 1;\n");
            sb.append("}\n");

            // List response message
            sb.append("\nmessage ").append(name).append("List {\n");
            sb.append("  repeated ").append(domain).append(".").append(name).append(" items = 1;\n");
            sb.append("}\n");

            // Service definition
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
                // Compound conditions
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
                // Simple conditions
                List<Map<String, Object>> condList = (List<Map<String, Object>>) conditions;
                for (Map<String, Object> cond : condList) {
                    sb.append("      simple_conditions: {\n");
                    sb.append("        field: \"").append(cond.get("field")).append("\"\n");
                    sb.append("        operator: \"").append(cond.get("operator")).append("\"\n");
                    sb.append("        value: \"").append(cond.get("value")).append("\"\n");
                    sb.append("      }\n");
                }
            }

            // Metadata requirements
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

    // ---- String Utilities ----

    private String extractTypePart(String[] tokens) {
        StringBuilder typeBuilder = new StringBuilder();
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].toUpperCase();
            if (token.equals("NOT") || token.equals("NULL") || token.equals("DEFAULT")
                || token.equals("PRIMARY") || token.equals("UNIQUE") || token.equals("CHECK")) {
                break;
            }
            if (i > 1) typeBuilder.append(" ");
            typeBuilder.append(tokens[i]);
        }
        return typeBuilder.toString().toUpperCase();
    }

    private String extractBaseType(String typePart) {
        return typePart.split("[\\s(]")[0];
    }

    private int findMatchingParen(String sql, int openParen) {
        int depth = 0;
        for (int i = openParen; i < sql.length(); i++) {
            if (sql.charAt(i) == '(') depth++;
            else if (sql.charAt(i) == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private List<String> smartSplit(String text, char delimiter) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;

        for (char c : text.toCharArray()) {
            if (c == '(' || c == '[') {
                parenDepth++;
                current.append(c);
            } else if (c == ')' || c == ']') {
                parenDepth--;
                current.append(c);
            } else if (c == delimiter && parenDepth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private boolean isConstraintLine(String line) {
        String upper = line.toUpperCase();
        return upper.startsWith("PRIMARY KEY") || upper.startsWith("FOREIGN KEY")
            || upper.startsWith("CONSTRAINT") || upper.startsWith("UNIQUE")
            || upper.startsWith("CHECK");
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
        if (lower.endsWith("ies")) return lower.substring(0, lower.length() - 3) + "y";
        if (lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes")
            || lower.endsWith("ches") || lower.endsWith("shes")) {
            return lower.substring(0, lower.length() - 2);
        }
        if (lower.endsWith("oes")) return lower.substring(0, lower.length() - 2);
        if (lower.endsWith("s")) return lower.substring(0, lower.length() - 1);
        return lower;
    }
}
