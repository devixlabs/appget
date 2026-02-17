package dev.appget.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Parses schema.sql and views.sql using regex-based parsing to generate models.yaml.
 * Supports multiple database dialects (MySQL, SQLite, Oracle, MSSQL, PostgreSQL).
 */
public class SQLSchemaParser {

    private static final Logger logger = LogManager.getLogger(SQLSchemaParser.class);
    private static final String ORGANIZATION = "appget";
    private static final Map<String, String> SQL_TO_JAVA_TYPE = createSqlToJavaTypeMapping();
    private static final Map<String, String> DOMAIN_MAPPING = createDomainMapping();
    private static final Map<String, String> VIEW_DOMAIN_MAPPING = createViewDomainMapping();

    private static Map<String, String> createSqlToJavaTypeMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("VARCHAR", "String");
        map.put("CHAR", "String");
        map.put("TEXT", "String");
        map.put("INT", "int");
        map.put("INTEGER", "int");
        map.put("BIGINT", "long");
        map.put("LONG", "long");
        map.put("SMALLINT", "int");
        map.put("DECIMAL", "BigDecimal");
        map.put("NUMERIC", "BigDecimal");
        map.put("FLOAT", "double");
        map.put("DOUBLE", "double");
        map.put("REAL", "double");
        map.put("DATE", "LocalDate");
        map.put("TIMESTAMP", "LocalDateTime");
        map.put("DATETIME", "LocalDateTime");
        map.put("BOOLEAN", "boolean");
        map.put("BOOL", "boolean");
        return map;
    }

    private static Map<String, String> createDomainMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("roles", "appget");
        map.put("employees", "appget");
        map.put("departments", "hr");
        map.put("salaries", "hr");
        map.put("invoices", "finance");
        return map;
    }

    private static Map<String, String> createViewDomainMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("employee_salary_view", "appget");
        map.put("department_budget_view", "hr");
        return map;
    }

    // Stores parsed table columns for view resolution
    private final Map<String, List<ColumnInfo>> tableColumns = new HashMap<>();

    public static void main(String[] args) throws IOException {
        logger.debug("Entering main method with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid argument count. Usage: SQLSchemaParser <schema.sql> [views.sql] <output-models.yaml>");
            System.err.println("Usage: SQLSchemaParser <schema.sql> [views.sql] <output-models.yaml>");
            System.exit(1);
        }

        logger.info("Starting SQLSchemaParser");
        SQLSchemaParser parser = new SQLSchemaParser();

        if (args.length == 2) {
            // schema.sql output.yaml
            logger.info("Parsing schema file: {}", args[0]);
            parser.parseAndGenerate(args[0], null, args[1]);
        } else {
            // schema.sql views.sql output.yaml
            logger.info("Parsing schema file: {} and views file: {}", args[0], args[1]);
            parser.parseAndGenerate(args[0], args[1], args[2]);
        }
        logger.debug("Exiting main method");
    }

    public void parseAndGenerate(String schemaFile, String viewsFile, String outputFile) throws IOException {
        logger.debug("Entering parseAndGenerate with schemaFile={}, viewsFile={}, outputFile={}", schemaFile, viewsFile, outputFile);
        String sql = Files.readString(Paths.get(schemaFile));
        logger.info("Successfully read schema file: {} ({} bytes)", schemaFile, sql.length());

        Map<String, Map<String, Object>> domains = new TreeMap<>();

        // Parse CREATE TABLE statements
        logger.debug("Starting to parse CREATE TABLE statements");
        parseTables(sql, domains);
        logger.info("Parsed {} domain(s) from tables", domains.size());

        // Parse CREATE VIEW statements if views.sql provided
        if (viewsFile != null && Files.exists(Paths.get(viewsFile))) {
            logger.debug("Views file provided, parsing views from: {}", viewsFile);
            String viewsSql = Files.readString(Paths.get(viewsFile));
            logger.info("Successfully read views file: {} ({} bytes)", viewsFile, viewsSql.length());
            parseViews(viewsSql, domains);
        } else {
            logger.debug("No views file provided or file does not exist");
        }

        // Generate YAML
        logger.debug("Generating YAML output");
        String yaml = generateYaml(domains);
        Files.writeString(Paths.get(outputFile), yaml);
        logger.info("Generated {} from {} {} ({} bytes written)", outputFile, schemaFile,
                viewsFile != null ? " + " + viewsFile : "", yaml.length());
        System.out.println("Generated " + outputFile + " from " + schemaFile
                + (viewsFile != null ? " + " + viewsFile : ""));
        logger.debug("Exiting parseAndGenerate");
    }

    // Backwards-compatible method for tests
    public void parseAndGenerate(String schemaFile, String outputFile) throws IOException {
        parseAndGenerate(schemaFile, null, outputFile);
    }

    private void parseTables(String sql, Map<String, Map<String, Object>> domains) {
        logger.debug("Entering parseTables");
        int pos = 0;
        int tableCount = 0;
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

            // Store columns for view resolution
            logger.debug("Parsing table: {}", tableName);
            List<ColumnInfo> columns = parseColumnsForLookup(columnDefs);
            tableColumns.put(tableName.toLowerCase(), columns);

            // Determine domain
            String domain = DOMAIN_MAPPING.getOrDefault(tableName.toLowerCase(), "appget");
            domains.putIfAbsent(domain, new LinkedHashMap<>());

            Map<String, Object> model = new LinkedHashMap<>();
            model.put("name", toModelName(tableName));
            model.put("fields", parseColumns(columnDefs));

            Map<String, Object> domainData = domains.get(domain);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> models = (List<Map<String, Object>>) domainData.computeIfAbsent("models", k -> new ArrayList<>());
            models.add(model);
            logger.debug("Added model {} to domain {}", toModelName(tableName), domain);
            tableCount++;

            pos = closeParen + 1;
        }
        logger.info("Parsed {} table(s)", tableCount);
        logger.debug("Exiting parseTables");
    }

    @SuppressWarnings("unchecked")
    private void parseViews(String sql, Map<String, Map<String, Object>> domains) {
        logger.debug("Entering parseViews");
        // Pattern: CREATE VIEW <name> AS SELECT ... FROM <table> [alias] [JOIN ...]
        Pattern viewPattern = Pattern.compile(
            "CREATE\\s+VIEW\\s+(\\w+)\\s+AS\\s+SELECT\\s+(.*?)\\s+FROM\\s+(.*?)(?:;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );

        Matcher viewMatcher = viewPattern.matcher(sql);
        int viewCount = 0;
        while (viewMatcher.find()) {
            String viewName = viewMatcher.group(1);
            String selectPart = viewMatcher.group(2).trim();
            String fromPart = viewMatcher.group(3).trim();

            logger.debug("Parsing view: {}", viewName);

            // Build alias -> table name mapping from FROM/JOIN clause
            Map<String, String> aliases = parseAliases(fromPart);

            // Parse SELECT columns and resolve types from table columns
            List<Map<String, Object>> viewFields = parseViewColumns(selectPart, aliases);

            if (viewFields.isEmpty()) {
                logger.debug("No fields parsed for view: {}, skipping", viewName);
                continue;
            }

            // Determine domain for view
            String domain = VIEW_DOMAIN_MAPPING.getOrDefault(viewName.toLowerCase(), "appget");
            domains.putIfAbsent(domain, new LinkedHashMap<>());

            Map<String, Object> viewModel = new LinkedHashMap<>();
            viewModel.put("name", toViewModelName(viewName));
            viewModel.put("source", viewName);
            viewModel.put("fields", viewFields);

            Map<String, Object> domainData = domains.get(domain);
            List<Map<String, Object>> views = (List<Map<String, Object>>) domainData.computeIfAbsent("views", k -> new ArrayList<>());
            views.add(viewModel);

            logger.debug("Added view {} to domain {}", toViewModelName(viewName), domain);
            System.out.println("  Parsed view: " + viewName + " -> " + toViewModelName(viewName) + " (domain: " + domain + ")");
            viewCount++;
        }
        logger.info("Parsed {} view(s)", viewCount);
        logger.debug("Exiting parseViews");
    }

    private Map<String, String> parseAliases(String fromPart) {
        Map<String, String> aliases = new HashMap<>();

        // Split on JOIN keywords
        String[] parts = fromPart.split("(?i)\\s+(?:INNER\\s+|LEFT\\s+|RIGHT\\s+|FULL\\s+|CROSS\\s+)?JOIN\\s+");

        for (String part : parts) {
            // Remove ON clause
            String tablePart = part.replaceAll("(?i)\\s+ON\\s+.*", "").trim();
            // Parse: tableName [alias]
            String[] tokens = tablePart.split("\\s+");
            if (tokens.length >= 2) {
                String table = tokens[0].toLowerCase();
                String alias = tokens[1].toLowerCase();
                aliases.put(alias, table);
            } else if (tokens.length == 1) {
                String table = tokens[0].toLowerCase();
                aliases.put(table, table);
            }
        }

        return aliases;
    }

    private List<Map<String, Object>> parseViewColumns(String selectPart, Map<String, String> aliases) {
        List<Map<String, Object>> fields = new ArrayList<>();

        // Split columns by comma (respecting parentheses for functions)
        List<String> columns = smartSplit(selectPart, ',');

        for (String col : columns) {
            col = col.trim();
            if (col.isEmpty()) continue;

            // Check for AS alias
            String outputName;
            String expression;
            Pattern asPattern = Pattern.compile("(?i)(.+?)\\s+AS\\s+(\\w+)");
            Matcher asMatcher = asPattern.matcher(col);
            if (asMatcher.matches()) {
                expression = asMatcher.group(1).trim();
                outputName = asMatcher.group(2).trim();
            } else {
                expression = col;
                // Use the column name (after dot if present)
                if (expression.contains(".")) {
                    outputName = expression.substring(expression.indexOf('.') + 1).trim();
                } else {
                    outputName = expression.trim();
                }
            }

            // Resolve type from expression
            String javaType = resolveExpressionType(expression, aliases);
            boolean nullable = resolveExpressionNullable(expression, aliases);

            // Handle nullable wrappers for primitives
            String finalType = javaType;
            if (nullable && isPrimitive(javaType)) {
                finalType = wrapPrimitive(javaType);
            }

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", toFieldName(outputName));
            field.put("type", finalType);
            field.put("nullable", nullable);
            fields.add(field);
        }

        return fields;
    }

    private String resolveExpressionType(String expression, Map<String, String> aliases) {
        // Check for aggregate functions
        String upper = expression.toUpperCase().trim();
        if (upper.startsWith("COUNT(")) return "long";
        if (upper.startsWith("SUM(")) return "BigDecimal";
        if (upper.startsWith("AVG(")) return "double";
        if (upper.startsWith("MIN(") || upper.startsWith("MAX(")) {
            // Resolve inner type
            String inner = expression.substring(expression.indexOf('(') + 1, expression.lastIndexOf(')')).trim();
            return resolveExpressionType(inner, aliases);
        }

        // Check for alias.column pattern
        if (expression.contains(".")) {
            String[] parts = expression.split("\\.");
            String alias = parts[0].trim().toLowerCase();
            String colName = parts[1].trim().toLowerCase();

            String tableName = aliases.get(alias);
            if (tableName != null) {
                List<ColumnInfo> cols = tableColumns.get(tableName);
                if (cols != null) {
                    for (ColumnInfo ci : cols) {
                        if (ci.name.equalsIgnoreCase(colName)) {
                            return ci.javaType;
                        }
                    }
                }
            }
        }

        return "String"; // fallback
    }

    private boolean resolveExpressionNullable(String expression, Map<String, String> aliases) {
        String upper = expression.toUpperCase().trim();
        if (upper.startsWith("COUNT(")) return false;
        if (upper.startsWith("SUM(") || upper.startsWith("AVG(") || upper.startsWith("MIN(") || upper.startsWith("MAX(")) {
            return true; // aggregates can be null
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
                        if (ci.name.equalsIgnoreCase(colName)) {
                            return ci.nullable;
                        }
                    }
                }
            }
        }

        return true; // default nullable
    }

    private List<Map<String, Object>> parseColumns(String columnDefs) {
        List<Map<String, Object>> fields = new ArrayList<>();

        List<String> columnLines = smartSplit(columnDefs, ',');

        for (String columnLine : columnLines) {
            columnLine = columnLine.trim();
            if (columnLine.isEmpty() || isConstraintLine(columnLine)) {
                continue;
            }

            Map<String, Object> field = parseColumn(columnLine);
            if (field != null) {
                fields.add(field);
            }
        }

        return fields;
    }

    private List<ColumnInfo> parseColumnsForLookup(String columnDefs) {
        List<ColumnInfo> columns = new ArrayList<>();

        List<String> columnLines = smartSplit(columnDefs, ',');

        for (String columnLine : columnLines) {
            columnLine = columnLine.trim();
            if (columnLine.isEmpty() || isConstraintLine(columnLine)) {
                continue;
            }

            String[] tokens = columnLine.split("\\s+");
            if (tokens.length < 2) continue;

            String columnName = tokens[0];
            StringBuilder typeBuilder = new StringBuilder();
            int typeTokenEnd = 1;

            for (int i = 1; i < tokens.length; i++) {
                String token = tokens[i].toUpperCase();
                if (token.equals("NOT") || token.equals("NULL") || token.equals("DEFAULT")
                    || token.equals("PRIMARY") || token.equals("UNIQUE") || token.equals("CHECK")) {
                    typeTokenEnd = i;
                    break;
                }
                if (i > 1) typeBuilder.append(" ");
                typeBuilder.append(tokens[i]);
                typeTokenEnd = i + 1;
            }

            String typePart = typeBuilder.toString().toUpperCase();
            if (typePart.isEmpty()) continue;

            String baseType = extractBaseType(typePart);
            String javaType = mapSqlTypeToJava(baseType);

            boolean isNullable = true;
            for (int i = typeTokenEnd; i < tokens.length - 1; i++) {
                if (tokens[i].toUpperCase().equals("NOT") && tokens[i + 1].toUpperCase().equals("NULL")) {
                    isNullable = false;
                    break;
                }
            }

            columns.add(new ColumnInfo(columnName.toLowerCase(), javaType, isNullable));
        }

        return columns;
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

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private boolean isConstraintLine(String line) {
        String upper = line.toUpperCase();
        return upper.startsWith("PRIMARY KEY") || upper.startsWith("FOREIGN KEY")
            || upper.startsWith("CONSTRAINT") || upper.startsWith("UNIQUE")
            || upper.startsWith("CHECK");
    }

    private Map<String, Object> parseColumn(String columnLine) {
        String[] tokens = columnLine.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        String columnName = tokens[0];
        StringBuilder typeBuilder = new StringBuilder();
        int typeTokenEnd = 1;

        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].toUpperCase();
            if (token.equals("NOT") || token.equals("NULL") || token.equals("DEFAULT")
                || token.equals("PRIMARY") || token.equals("UNIQUE") || token.equals("CHECK")) {
                typeTokenEnd = i;
                break;
            }
            if (i > 1) typeBuilder.append(" ");
            typeBuilder.append(tokens[i]);
            typeTokenEnd = i + 1;
        }

        String typePart = typeBuilder.toString().toUpperCase();
        if (typePart.isEmpty()) {
            return null;
        }

        String baseType = extractBaseType(typePart);
        String javaType = mapSqlTypeToJava(baseType);

        boolean isNullable = true;
        for (int i = typeTokenEnd; i < tokens.length - 1; i++) {
            if (tokens[i].toUpperCase().equals("NOT") && tokens[i + 1].toUpperCase().equals("NULL")) {
                isNullable = false;
                break;
            }
        }

        String finalType = javaType;
        if (isNullable && isPrimitive(javaType)) {
            finalType = wrapPrimitive(javaType);
        }

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", toFieldName(columnName));
        field.put("type", finalType);
        field.put("nullable", isNullable);

        return field;
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

    private String extractBaseType(String typePart) {
        String[] tokens = typePart.split("[\\s(]");
        return tokens[0];
    }

    private String mapSqlTypeToJava(String sqlType) {
        return SQL_TO_JAVA_TYPE.getOrDefault(sqlType, "String");
    }

    private boolean isPrimitive(String javaType) {
        return javaType.matches("^(int|long|double|float|boolean|byte|char|short)$");
    }

    private String wrapPrimitive(String primitiveType) {
        Map<String, String> wrappers = new HashMap<>();
        wrappers.put("int", "Integer");
        wrappers.put("long", "Long");
        wrappers.put("double", "Double");
        wrappers.put("float", "Float");
        wrappers.put("boolean", "Boolean");
        wrappers.put("byte", "Byte");
        wrappers.put("char", "Character");
        wrappers.put("short", "Short");
        return wrappers.getOrDefault(primitiveType, primitiveType);
    }

    private String toModelName(String tableName) {
        String singular = singularize(tableName);
        return singular.substring(0, 1).toUpperCase() + singular.substring(1);
    }

    private String toViewModelName(String viewName) {
        // employee_salary_view -> EmployeeSalaryView
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

    private String toFieldName(String columnName) {
        StringBuilder camelCase = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : columnName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                camelCase.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                camelCase.append(c);
            }
        }
        return camelCase.toString();
    }

    private String singularize(String tableName) {
        String lower = tableName.toLowerCase();

        if (lower.endsWith("ies")) {
            return lower.substring(0, lower.length() - 3) + "y";
        } else if (lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes") || lower.endsWith("ches") || lower.endsWith("shes")) {
            return lower.substring(0, lower.length() - 2);
        } else if (lower.endsWith("oes")) {
            return lower.substring(0, lower.length() - 2);
        } else if (lower.endsWith("s")) {
            return lower.substring(0, lower.length() - 1);
        }
        return lower;
    }

    @SuppressWarnings("unchecked")
    private String generateYaml(Map<String, Map<String, Object>> domains) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("organization: ").append(ORGANIZATION).append("\n\n");
        yaml.append("domains:\n");

        for (Map.Entry<String, Map<String, Object>> domainEntry : domains.entrySet()) {
            String domain = domainEntry.getKey();
            Map<String, Object> domainData = domainEntry.getValue();

            yaml.append("  ").append(domain).append(":\n");
            yaml.append("    namespace: dev.appget");
            if (!domain.equals("appget")) {
                yaml.append(".").append(domain);
            }
            yaml.append("\n");

            // Write models
            List<Map<String, Object>> models = (List<Map<String, Object>>) domainData.get("models");
            if (models != null && !models.isEmpty()) {
                yaml.append("    models:\n");
                for (Map<String, Object> model : models) {
                    yaml.append("      - name: ").append(model.get("name")).append("\n");
                    yaml.append("        fields:\n");

                    List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");
                    for (Map<String, Object> field : fields) {
                        yaml.append("          - name: ").append(field.get("name")).append("\n");
                        yaml.append("            type: ").append(field.get("type")).append("\n");
                        yaml.append("            nullable: ").append(field.get("nullable")).append("\n");
                    }
                    yaml.append("\n");
                }
            }

            // Write views
            List<Map<String, Object>> views = (List<Map<String, Object>>) domainData.get("views");
            if (views != null && !views.isEmpty()) {
                yaml.append("    views:\n");
                for (Map<String, Object> view : views) {
                    yaml.append("      - name: ").append(view.get("name")).append("\n");
                    yaml.append("        source: ").append(view.get("source")).append("\n");
                    yaml.append("        fields:\n");

                    List<Map<String, Object>> fields = (List<Map<String, Object>>) view.get("fields");
                    for (Map<String, Object> field : fields) {
                        yaml.append("          - name: ").append(field.get("name")).append("\n");
                        yaml.append("            type: ").append(field.get("type")).append("\n");
                        yaml.append("            nullable: ").append(field.get("nullable")).append("\n");
                    }
                    yaml.append("\n");
                }
            }
        }

        return yaml.toString();
    }

    // Internal helper to store column metadata for view resolution
    private static class ColumnInfo {
        final String name;
        final String javaType;
        final boolean nullable;

        ColumnInfo(String name, String javaType, boolean nullable) {
            this.name = name;
            this.javaType = javaType;
            this.nullable = nullable;
        }
    }
}
