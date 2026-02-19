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
 *
 * Emits language-neutral types (string, int32, int64, float64, bool, date, datetime, decimal)
 * with field_number, source_table/source_view, resource, primary_key, and precision/scale.
 */
public class SQLSchemaParser {

    private static final Logger logger = LogManager.getLogger(SQLSchemaParser.class);
    private static final String ORGANIZATION = "appget";
    private static final int SCHEMA_VERSION = 1;
    private static final Map<String, String> SQL_TO_NEUTRAL_TYPE = createSqlToNeutralTypeMapping();
    private static final Map<String, String> DOMAIN_MAPPING = createDomainMapping();
    private static final Map<String, String> VIEW_DOMAIN_MAPPING = createViewDomainMapping();

    // Pattern to extract precision,scale from DECIMAL(p,s)
    private static final Pattern DECIMAL_PRECISION_PATTERN = Pattern.compile(
            "(?:DECIMAL|NUMERIC)\\((\\d+),\\s*(\\d+)\\)", Pattern.CASE_INSENSITIVE);

    private static Map<String, String> createSqlToNeutralTypeMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("VARCHAR", "string");
        map.put("CHAR", "string");
        map.put("TEXT", "string");
        map.put("INT", "int32");
        map.put("INTEGER", "int32");
        map.put("BIGINT", "int64");
        map.put("LONG", "int64");
        map.put("SMALLINT", "int32");
        map.put("DECIMAL", "decimal");
        map.put("NUMERIC", "decimal");
        map.put("FLOAT", "float64");
        map.put("DOUBLE", "float64");
        map.put("REAL", "float64");
        map.put("DATE", "date");
        map.put("TIMESTAMP", "datetime");
        map.put("DATETIME", "datetime");
        map.put("BOOLEAN", "bool");
        map.put("BOOL", "bool");
        return map;
    }

    private static Map<String, String> createDomainMapping() {
        Map<String, String> map = new HashMap<>();
        map.put("roles", "appget");
        map.put("employees", "appget");
        map.put("vendors", "appget");
        map.put("locations", "appget");
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

    // Stores parsed table columns for view resolution (neutral types)
    private final Map<String, List<ColumnInfo>> tableColumns = new HashMap<>();

    // Field number registry: key="domain/ModelName/fieldName" -> fieldNumber
    // Loaded from existing models.yaml if present for stability
    private final Map<String, Integer> fieldNumberRegistry = new HashMap<>();

    // Track next available field number per model key: "domain/ModelName"
    private final Map<String, Integer> nextFieldNumber = new HashMap<>();

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

        // Load existing models.yaml for stable field numbers (if it exists)
        if (Files.exists(Paths.get(outputFile))) {
            loadExistingFieldNumbers(outputFile);
        }

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

    @SuppressWarnings("unchecked")
    private void loadExistingFieldNumbers(String modelsFile) {
        try {
            String content = Files.readString(Paths.get(modelsFile));
            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Map<String, Object> data = yaml.load(content);
            if (data == null) return;

            Map<String, Object> domains = (Map<String, Object>) data.get("domains");
            if (domains == null) return;

            for (Map.Entry<String, Object> domainEntry : domains.entrySet()) {
                String domain = domainEntry.getKey();
                Map<String, Object> domainConfig = (Map<String, Object>) domainEntry.getValue();

                List<Map<String, Object>> models = (List<Map<String, Object>>) domainConfig.get("models");
                if (models != null) {
                    for (Map<String, Object> model : models) {
                        String modelName = (String) model.get("name");
                        List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");
                        if (fields != null) {
                            for (Map<String, Object> field : fields) {
                                String fieldName = (String) field.get("name");
                                Object fn = field.get("field_number");
                                if (fn instanceof Integer) {
                                    String key = domain + "/" + modelName + "/" + fieldName;
                                    fieldNumberRegistry.put(key, (Integer) fn);
                                }
                            }
                        }
                    }
                }

                List<Map<String, Object>> views = (List<Map<String, Object>>) domainConfig.get("views");
                if (views != null) {
                    for (Map<String, Object> view : views) {
                        String viewName = (String) view.get("name");
                        List<Map<String, Object>> fields = (List<Map<String, Object>>) view.get("fields");
                        if (fields != null) {
                            for (Map<String, Object> field : fields) {
                                String fieldName = (String) field.get("name");
                                Object fn = field.get("field_number");
                                if (fn instanceof Integer) {
                                    String key = domain + "/" + viewName + "/" + fieldName;
                                    fieldNumberRegistry.put(key, (Integer) fn);
                                }
                            }
                        }
                    }
                }
            }
            logger.debug("Loaded {} existing field numbers from {}", fieldNumberRegistry.size(), modelsFile);
        } catch (Exception e) {
            logger.warn("Could not load existing field numbers from {}: {}", modelsFile, e.getMessage());
        }
    }

    private int getFieldNumber(String domain, String modelName, String fieldName) {
        String key = domain + "/" + modelName + "/" + fieldName;
        if (fieldNumberRegistry.containsKey(key)) {
            return fieldNumberRegistry.get(key);
        }
        // Assign next available field number for this model
        String modelKey = domain + "/" + modelName;
        int next = nextFieldNumber.getOrDefault(modelKey, 1);
        // Find the max existing field number for this model to avoid conflicts
        int maxExisting = 0;
        for (Map.Entry<String, Integer> entry : fieldNumberRegistry.entrySet()) {
            if (entry.getKey().startsWith(modelKey + "/")) {
                if (entry.getValue() > maxExisting) {
                    maxExisting = entry.getValue();
                }
            }
        }
        // Also check what we've assigned so far for this model
        if (next <= maxExisting) {
            next = maxExisting + 1;
        }
        fieldNumberRegistry.put(key, next);
        nextFieldNumber.put(modelKey, next + 1);
        return next;
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

            String modelName = toModelName(tableName);
            String sourceTable = tableName.toLowerCase();
            String resource = toResourceName(tableName);

            // Parse primary keys (table-level constraint)
            Set<String> primaryKeys = parsePrimaryKeys(columnDefs);

            Map<String, Object> model = new LinkedHashMap<>();
            model.put("name", modelName);
            model.put("source_table", sourceTable);
            model.put("resource", resource);
            model.put("fields", parseColumns(columnDefs, domain, modelName, primaryKeys));

            Map<String, Object> domainData = domains.get(domain);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> models = (List<Map<String, Object>>) domainData.computeIfAbsent("models", k -> new ArrayList<>());
            models.add(model);
            logger.debug("Added model {} to domain {}", modelName, domain);
            tableCount++;

            pos = closeParen + 1;
        }
        logger.info("Parsed {} table(s)", tableCount);
        logger.debug("Exiting parseTables");
    }

    /**
     * Parse PRIMARY KEY columns from column definitions.
     * Handles: PRIMARY KEY (col1, col2) table-level constraint
     * and inline: col_name TYPE PRIMARY KEY
     */
    private Set<String> parsePrimaryKeys(String columnDefs) {
        Set<String> pks = new LinkedHashSet<>();

        // Table-level: PRIMARY KEY (col1, col2)
        Pattern tableLevel = Pattern.compile(
                "PRIMARY\\s+KEY\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
        Matcher m = tableLevel.matcher(columnDefs);
        if (m.find()) {
            String pkList = m.group(1);
            for (String pk : pkList.split(",")) {
                pks.add(pk.trim().toLowerCase());
            }
        }

        // Inline: col_name TYPE ... PRIMARY KEY
        // We'll detect this in parseColumn and add to set
        return pks;
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

            // Determine domain for view
            String domain = VIEW_DOMAIN_MAPPING.getOrDefault(viewName.toLowerCase(), "appget");
            String viewModelName = toViewModelName(viewName);

            // Parse SELECT columns and resolve types from table columns
            List<Map<String, Object>> viewFields = parseViewColumns(selectPart, aliases, domain, viewModelName);

            if (viewFields.isEmpty()) {
                logger.debug("No fields parsed for view: {}, skipping", viewName);
                continue;
            }

            domains.putIfAbsent(domain, new LinkedHashMap<>());

            Map<String, Object> viewModel = new LinkedHashMap<>();
            viewModel.put("name", viewModelName);
            viewModel.put("source_view", viewName.toLowerCase());
            viewModel.put("resource", toResourceName(viewName));
            viewModel.put("fields", viewFields);

            Map<String, Object> domainData = domains.get(domain);
            List<Map<String, Object>> views = (List<Map<String, Object>>) domainData.computeIfAbsent("views", k -> new ArrayList<>());
            views.add(viewModel);

            logger.debug("Added view {} to domain {}", viewModelName, domain);
            System.out.println("  Parsed view: " + viewName + " -> " + viewModelName + " (domain: " + domain + ")");
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

    private List<Map<String, Object>> parseViewColumns(String selectPart, Map<String, String> aliases,
                                                        String domain, String viewModelName) {
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

            // Resolve neutral type from expression
            String neutralType = resolveExpressionType(expression, aliases);
            boolean nullable = resolveExpressionNullable(expression, aliases);
            Integer precision = resolveExpressionPrecision(expression, aliases);
            Integer scale = resolveExpressionScale(expression, aliases);

            int fieldNumber = getFieldNumber(domain, viewModelName, outputName.toLowerCase());

            Map<String, Object> field = new LinkedHashMap<>();
            field.put("name", outputName.toLowerCase());
            field.put("type", neutralType);
            field.put("nullable", nullable);
            field.put("field_number", fieldNumber);
            if ("decimal".equals(neutralType) && precision != null) {
                field.put("precision", precision);
                field.put("scale", scale != null ? scale : 0);
            }
            fields.add(field);
        }

        return fields;
    }

    private String resolveExpressionType(String expression, Map<String, String> aliases) {
        // Check for aggregate functions
        String upper = expression.toUpperCase().trim();
        if (upper.startsWith("COUNT(")) return "int64";
        if (upper.startsWith("SUM(")) return "decimal";
        if (upper.startsWith("AVG(")) return "float64";
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
                            return ci.neutralType;
                        }
                    }
                }
            }
        }

        return "string"; // fallback
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

    private Integer resolveExpressionPrecision(String expression, Map<String, String> aliases) {
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
                            return ci.precision;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Integer resolveExpressionScale(String expression, Map<String, String> aliases) {
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
                            return ci.scale;
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> parseColumns(String columnDefs, String domain, String modelName,
                                                    Set<String> tableLevelPrimaryKeys) {
        List<Map<String, Object>> fields = new ArrayList<>();

        List<String> columnLines = smartSplit(columnDefs, ',');

        // First pass: detect inline PRIMARY KEY columns
        Set<String> inlinePrimaryKeys = new LinkedHashSet<>();
        for (String columnLine : columnLines) {
            columnLine = columnLine.trim();
            if (columnLine.isEmpty() || isConstraintLine(columnLine)) continue;
            String[] tokens = columnLine.split("\\s+");
            if (tokens.length < 2) continue;
            String colName = tokens[0].toLowerCase();
            for (String token : tokens) {
                if ("KEY".equalsIgnoreCase(token)) {
                    // check if preceded by "PRIMARY"
                    int idx = Arrays.asList(tokens).indexOf(token);
                    if (idx > 0 && "PRIMARY".equalsIgnoreCase(tokens[idx - 1])) {
                        inlinePrimaryKeys.add(colName);
                    }
                    break;
                }
            }
        }

        Set<String> allPrimaryKeys = new LinkedHashSet<>();
        allPrimaryKeys.addAll(tableLevelPrimaryKeys);
        allPrimaryKeys.addAll(inlinePrimaryKeys);

        // Build ordered PK list for position assignment
        List<String> pkList = new ArrayList<>(allPrimaryKeys);

        for (String columnLine : columnLines) {
            columnLine = columnLine.trim();
            if (columnLine.isEmpty() || isConstraintLine(columnLine)) {
                continue;
            }

            Map<String, Object> field = parseColumn(columnLine, domain, modelName, allPrimaryKeys, pkList);
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
            String neutralType = mapSqlTypeToNeutral(baseType);

            boolean isNullable = true;
            for (int i = typeTokenEnd; i < tokens.length - 1; i++) {
                if (tokens[i].toUpperCase().equals("NOT") && tokens[i + 1].toUpperCase().equals("NULL")) {
                    isNullable = false;
                    break;
                }
            }

            // Extract precision and scale for decimal types
            Integer precision = null;
            Integer scale = null;
            if ("decimal".equals(neutralType)) {
                // Reconstruct original type string for regex matching
                String originalTypePart = typeBuilder.toString();
                Matcher m = DECIMAL_PRECISION_PATTERN.matcher(originalTypePart);
                if (m.find()) {
                    precision = Integer.parseInt(m.group(1));
                    scale = Integer.parseInt(m.group(2));
                }
            }

            columns.add(new ColumnInfo(columnName.toLowerCase(), neutralType, isNullable, precision, scale));
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

    private Map<String, Object> parseColumn(String columnLine, String domain, String modelName,
                                             Set<String> allPrimaryKeys, List<String> pkList) {
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
        String neutralType = mapSqlTypeToNeutral(baseType);

        boolean isNullable = true;
        for (int i = typeTokenEnd; i < tokens.length - 1; i++) {
            if (tokens[i].toUpperCase().equals("NOT") && tokens[i + 1].toUpperCase().equals("NULL")) {
                isNullable = false;
                break;
            }
        }

        // Extract precision and scale for decimal types
        Integer precision = null;
        Integer scale = null;
        if ("decimal".equals(neutralType)) {
            Matcher m = DECIMAL_PRECISION_PATTERN.matcher(typeBuilder.toString());
            if (m.find()) {
                precision = Integer.parseInt(m.group(1));
                scale = Integer.parseInt(m.group(2));
            }
        }

        String fieldNameLower = columnName.toLowerCase();
        int fieldNumber = getFieldNumber(domain, modelName, fieldNameLower);

        boolean isPrimaryKey = allPrimaryKeys.contains(fieldNameLower);
        int primaryKeyPosition = 0;
        if (isPrimaryKey) {
            primaryKeyPosition = pkList.indexOf(fieldNameLower) + 1;
        }

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", fieldNameLower);
        field.put("type", neutralType);
        field.put("nullable", isNullable);
        field.put("field_number", fieldNumber);
        if (isPrimaryKey) {
            field.put("primary_key", true);
            field.put("primary_key_position", primaryKeyPosition);
        }
        if ("decimal".equals(neutralType) && precision != null) {
            field.put("precision", precision);
            field.put("scale", scale != null ? scale : 0);
        }

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

    private String mapSqlTypeToNeutral(String sqlType) {
        return SQL_TO_NEUTRAL_TYPE.getOrDefault(sqlType, "string");
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

    /**
     * Convert a table/view name to a REST resource name (kebab-case).
     * Examples:
     *   employees -> employees
     *   salary -> salaries (if the singular logic applied) -- we use the raw name
     *   employee_salary_view -> employee-salary-view
     */
    public static String toResourceName(String tableName) {
        // snake_case to kebab-case: replace underscores with hyphens, lowercase
        return tableName.toLowerCase().replace('_', '-');
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
        yaml.append("schema_version: ").append(SCHEMA_VERSION).append("\n");
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
                    yaml.append("        source_table: ").append(model.get("source_table")).append("\n");
                    yaml.append("        resource: ").append(model.get("resource")).append("\n");
                    yaml.append("        fields:\n");

                    List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");
                    for (Map<String, Object> field : fields) {
                        yaml.append("          - name: ").append(field.get("name")).append("\n");
                        yaml.append("            type: ").append(field.get("type")).append("\n");
                        yaml.append("            nullable: ").append(field.get("nullable")).append("\n");
                        yaml.append("            field_number: ").append(field.get("field_number")).append("\n");
                        if (Boolean.TRUE.equals(field.get("primary_key"))) {
                            yaml.append("            primary_key: true\n");
                            yaml.append("            primary_key_position: ").append(field.get("primary_key_position")).append("\n");
                        }
                        if ("decimal".equals(field.get("type")) && field.get("precision") != null) {
                            yaml.append("            precision: ").append(field.get("precision")).append("\n");
                            yaml.append("            scale: ").append(field.get("scale")).append("\n");
                        }
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
                    yaml.append("        source_view: ").append(view.get("source_view")).append("\n");
                    yaml.append("        resource: ").append(view.get("resource")).append("\n");
                    yaml.append("        fields:\n");

                    List<Map<String, Object>> fields = (List<Map<String, Object>>) view.get("fields");
                    for (Map<String, Object> field : fields) {
                        yaml.append("          - name: ").append(field.get("name")).append("\n");
                        yaml.append("            type: ").append(field.get("type")).append("\n");
                        yaml.append("            nullable: ").append(field.get("nullable")).append("\n");
                        yaml.append("            field_number: ").append(field.get("field_number")).append("\n");
                        if ("decimal".equals(field.get("type")) && field.get("precision") != null) {
                            yaml.append("            precision: ").append(field.get("precision")).append("\n");
                            yaml.append("            scale: ").append(field.get("scale")).append("\n");
                        }
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
        final String neutralType;
        final boolean nullable;
        final Integer precision;
        final Integer scale;

        ColumnInfo(String name, String neutralType, boolean nullable, Integer precision, Integer scale) {
            this.name = name;
            this.neutralType = neutralType;
            this.nullable = nullable;
            this.precision = precision;
            this.scale = scale;
        }
    }
}
