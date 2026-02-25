package dev.appget.codegen;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.DataTable;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TableCell;
import io.cucumber.messages.types.TableRow;
import io.cucumber.messages.types.Tag;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Converts Gherkin .feature files + metadata.yaml into specs.yaml.
 *
 * This is the front of the pipeline: human-friendly .feature files are
 * the source of truth for business rules. The generated specs.yaml feeds
 * unchanged into SchemaToProtoConverter, SpecificationGenerator, and
 * SpringBootServerGenerator.
 */
public class FeatureToSpecsConverter {

    private static final Logger logger = LogManager.getLogger(FeatureToSpecsConverter.class);

    // Operator phrase → symbol mapping (longest phrases first to avoid partial matches)
    private static final LinkedHashMap<String, String> OPERATOR_MAP = new LinkedHashMap<>();
    static {
        OPERATOR_MAP.put("does not equal", "!=");
        OPERATOR_MAP.put("is greater than", ">");
        OPERATOR_MAP.put("is less than", "<");
        OPERATOR_MAP.put("is at least", ">=");
        OPERATOR_MAP.put("is at most", "<=");
        OPERATOR_MAP.put("equals", "==");
    }

    // Build regex alternation from operator phrases
    private static final String OPERATOR_ALTERNATION = OPERATOR_MAP.keySet().stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));

    // Regex: field_name <operator_phrase> "quoted_value" (allows empty string "")
    private static final Pattern SIMPLE_CONDITION_QUOTED = Pattern.compile(
            "(\\w+)\\s+(" + OPERATOR_ALTERNATION + ")\\s+\"([^\"]*)\"$");

    // Regex: field_name <operator_phrase> unquoted_value
    private static final Pattern SIMPLE_CONDITION_UNQUOTED = Pattern.compile(
            "(\\w+)\\s+(" + OPERATOR_ALTERNATION + ")\\s+(\\S+)$");

    // Regex: <category> context requires:
    private static final Pattern METADATA_PATTERN = Pattern.compile(
            "(\\w+)\\s+context\\s+requires:?$");

    // Regex: status is "<value>"
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "status\\s+is\\s+\"([^\"]+)\"$");

    // Regex: otherwise status is "<value>"
    private static final Pattern OTHERWISE_PATTERN = Pattern.compile(
            "otherwise\\s+status\\s+is\\s+\"([^\"]+)\"$");

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: FeatureToSpecsConverter <featuresDir> <metadataYaml> <outputSpecsYaml>");
            System.exit(1);
        }
        logger.debug("Entering main method");
        new FeatureToSpecsConverter().convert(args[0], args[1], args[2]);
        logger.debug("Exiting main method");
    }

    public void convert(String featuresDir, String metadataPath, String outputPath) throws IOException {
        logger.info("Converting features from {} with metadata {} to {}", featuresDir, metadataPath, outputPath);

        // Load metadata.yaml content
        String metadataContent = Files.readString(Path.of(metadataPath));
        logger.debug("Loaded metadata from {}", metadataPath);

        // Find and parse feature files in alphabetical order
        List<Path> featureFiles;
        try (Stream<Path> paths = Files.list(Path.of(featuresDir))) {
            featureFiles = paths
                    .filter(p -> p.toString().endsWith(".feature"))
                    .sorted()
                    .toList();
        }
        logger.info("Found {} feature files", featureFiles.size());

        // Parse all feature files into rules
        List<Map<String, Object>> allRules = new ArrayList<>();
        for (Path featureFile : featureFiles) {
            List<Map<String, Object>> rules = parseFeatureFile(featureFile);
            allRules.addAll(rules);
            logger.info("Parsed {} rules from {}", rules.size(), featureFile.getFileName());
        }

        // Write combined specs.yaml
        String output = assembleSpecsYaml(metadataContent, allRules);
        Files.writeString(Path.of(outputPath), output);
        logger.info("Generated {} with {} rules", outputPath, allRules.size());
        System.out.println("Generated " + outputPath + " with " + allRules.size() + " rules");
    }

    List<Map<String, Object>> parseFeatureFile(Path featurePath) throws IOException {
        logger.debug("Parsing feature file: {}", featurePath);

        GherkinParser parser = GherkinParser.builder()
                .includeGherkinDocument(true)
                .includePickles(false)
                .includeSource(false)
                .build();

        List<Envelope> envelopes = parser.parse(featurePath).toList();

        // Check for parse errors
        for (Envelope env : envelopes) {
            if (env.getParseError().isPresent()) {
                String msg = env.getParseError().get().getMessage();
                throw new IOException("Parse error in " + featurePath + ": " + msg);
            }
        }

        // Get the GherkinDocument
        GherkinDocument doc = envelopes.stream()
                .filter(e -> e.getGherkinDocument().isPresent())
                .findFirst()
                .orElseThrow(() -> new IOException("No GherkinDocument in " + featurePath))
                .getGherkinDocument().get();

        Feature feature = doc.getFeature()
                .orElseThrow(() -> new IOException("No Feature in " + featurePath));

        // Extract domain from feature-level tags
        String featureDomain = extractTagValue(feature.getTags(), "domain");
        logger.debug("Feature domain: {}", featureDomain);

        // Parse each scenario into a rule
        List<Map<String, Object>> rules = new ArrayList<>();
        for (FeatureChild child : feature.getChildren()) {
            if (child.getScenario().isPresent()) {
                Map<String, Object> rule = parseScenario(child.getScenario().get(), featureDomain);
                rules.add(rule);
            }
        }

        return rules;
    }

    private Map<String, Object> parseScenario(Scenario scenario, String featureDomain) {
        logger.debug("Parsing scenario: {}", scenario.getName());

        // Parse scenario-level tags
        List<Tag> tags = scenario.getTags();
        String ruleName = extractTagValue(tags, "rule");
        String targetName = extractTagValue(tags, "target");
        boolean isView = hasTag(tags, "view");
        boolean blocking = hasTag(tags, "blocking");

        // Allow scenario-level domain override
        String domain = extractTagValue(tags, "domain");
        if (domain == null) domain = featureDomain;

        String targetType = isView ? "view" : "model";

        // Build rule map (LinkedHashMap preserves insertion order for YAML output)
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", ruleName);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("type", targetType);
        target.put("name", targetName);
        target.put("domain", domain);
        rule.put("target", target);

        if (blocking) {
            rule.put("blocking", true);
        }

        // Parse steps
        Map<String, List<Map<String, Object>>> requires = new LinkedHashMap<>();
        Object conditions = null;
        String thenStatus = null;
        String elseStatus = null;

        for (Step step : scenario.getSteps()) {
            String keyword = step.getKeyword().trim();
            String text = step.getText();

            switch (keyword) {
                case "Given", "And" -> {
                    Matcher m = METADATA_PATTERN.matcher(text);
                    if (m.matches()) {
                        String category = m.group(1);
                        Optional<DataTable> dt = step.getDataTable();
                        if (dt.isPresent()) {
                            requires.put(category, parseConditionTable(dt.get()));
                        }
                    }
                }
                case "When" -> {
                    if (text.startsWith("all conditions are met")) {
                        Optional<DataTable> dt = step.getDataTable();
                        if (dt.isPresent()) {
                            conditions = parseCompoundCondition("AND", dt.get());
                        }
                    } else if (text.startsWith("any condition is met")) {
                        Optional<DataTable> dt = step.getDataTable();
                        if (dt.isPresent()) {
                            conditions = parseCompoundCondition("OR", dt.get());
                        }
                    } else {
                        conditions = parseSimpleCondition(text);
                    }
                }
                case "Then" -> {
                    Matcher m = STATUS_PATTERN.matcher(text);
                    if (m.matches()) {
                        thenStatus = m.group(1);
                    }
                }
                case "But" -> {
                    Matcher m = OTHERWISE_PATTERN.matcher(text);
                    if (m.matches()) {
                        elseStatus = m.group(1);
                    }
                }
            }
        }

        if (!requires.isEmpty()) {
            rule.put("requires", requires);
        }

        rule.put("conditions", conditions);

        Map<String, String> thenMap = new LinkedHashMap<>();
        thenMap.put("status", thenStatus);
        rule.put("then", thenMap);

        Map<String, String> elseMap = new LinkedHashMap<>();
        elseMap.put("status", elseStatus);
        rule.put("else", elseMap);

        logger.debug("Parsed rule: {} (target: {}, blocking: {})", ruleName, targetName, blocking);
        return rule;
    }

    // Parse simple condition from step text like: age is greater than 18
    List<Map<String, Object>> parseSimpleCondition(String text) {
        // Try quoted value first
        Matcher m = SIMPLE_CONDITION_QUOTED.matcher(text);
        if (m.matches()) {
            String field = m.group(1);
            String operatorPhrase = m.group(2);
            String value = m.group(3); // String value (was quoted)
            return List.of(buildCondition(field, OPERATOR_MAP.get(operatorPhrase), value));
        }

        // Try unquoted value
        m = SIMPLE_CONDITION_UNQUOTED.matcher(text);
        if (m.matches()) {
            String field = m.group(1);
            String operatorPhrase = m.group(2);
            String rawValue = m.group(3);
            Object value = coerceValue(rawValue);
            return List.of(buildCondition(field, OPERATOR_MAP.get(operatorPhrase), value));
        }

        logger.warn("Could not parse simple condition: {}", text);
        return List.of();
    }

    // Parse compound condition from data table
    private Map<String, Object> parseCompoundCondition(String logic, DataTable dataTable) {
        Map<String, Object> compound = new LinkedHashMap<>();
        compound.put("operator", logic);
        compound.put("clauses", parseConditionTable(dataTable));
        return compound;
    }

    // Parse a data table into a list of condition maps, skipping the header row
    private List<Map<String, Object>> parseConditionTable(DataTable dataTable) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        List<TableRow> rows = dataTable.getRows();

        // Skip header row (field | operator | value)
        for (int i = 1; i < rows.size(); i++) {
            List<TableCell> cells = rows.get(i).getCells();
            if (cells.size() >= 3) {
                String field = cells.get(0).getValue().trim();
                String operator = cells.get(1).getValue().trim();
                String rawValue = cells.get(2).getValue().trim();
                Object value = coerceValue(rawValue);
                conditions.add(buildCondition(field, operator, value));
            }
        }
        return conditions;
    }

    private Map<String, Object> buildCondition(String field, String operator, Object value) {
        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("field", field);
        condition.put("operator", operator);
        condition.put("value", value);
        return condition;
    }

    // Coerce a raw string value to the appropriate type
    Object coerceValue(String raw) {
        if (raw == null) return null;
        // Strip surrounding double-quotes (e.g., "" → empty string, "Manager" → Manager)
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }
        // Try integer
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            // Not an integer
        }
        // Try boolean
        if ("true".equals(raw) || "false".equals(raw)) {
            return Boolean.parseBoolean(raw);
        }
        // Default to string
        return raw;
    }

    // Extract a tag value like @domain:appget → "appget"
    private String extractTagValue(List<Tag> tags, String prefix) {
        String fullPrefix = "@" + prefix + ":";
        for (Tag tag : tags) {
            if (tag.getName().startsWith(fullPrefix)) {
                return tag.getName().substring(fullPrefix.length());
            }
        }
        return null;
    }

    // Check for presence of a simple tag like @blocking or @view
    private boolean hasTag(List<Tag> tags, String name) {
        String fullName = "@" + name;
        return tags.stream().anyMatch(t -> t.getName().equals(fullName));
    }

    // ---- YAML Output ----

    /**
     * Map Java/legacy metadata types to neutral types for specs.yaml output.
     * boolean → bool, int → int32, long → int64, double/float → float64, String → string
     */
    private static String toNeutralMetadataType(String rawType) {
        if (rawType == null) return "string";
        if ("boolean".equals(rawType) || "Boolean".equals(rawType)) return "bool";
        if ("int".equals(rawType) || "Integer".equals(rawType)) return "int32";
        if ("long".equals(rawType) || "Long".equals(rawType)) return "int64";
        if ("double".equals(rawType) || "Double".equals(rawType) || "float".equals(rawType) || "Float".equals(rawType)) return "float64";
        if ("String".equals(rawType) || "string".equals(rawType)) return "string";
        // Already neutral
        return rawType;
    }

    /**
     * Infer value_type from the Java type of a value (for conditions).
     */
    private static String inferValueType(Object value) {
        if (value instanceof Boolean) return "bool";
        if (value instanceof Integer) return "int32";
        if (value instanceof Long) return "int64";
        if (value instanceof Double || value instanceof Float) return "float64";
        return "string";
    }

    String assembleSpecsYaml(String metadataContent, List<Map<String, Object>> rules) {
        StringBuilder sb = new StringBuilder();

        // schema_version at top
        sb.append("schema_version: 1\n");

        // Metadata section (re-emitted with neutral types)
        emitNeutralMetadata(sb, metadataContent);
        sb.append("\n\nrules:\n");

        // Rules section
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) sb.append("\n");
            writeRule(sb, rules.get(i));
        }

        return sb.toString();
    }

    /**
     * Parse metadata.yaml content and re-emit with neutral types.
     * Replaces legacy types (boolean, int, String, float) with neutral equivalents (bool, int32, string, float64).
     */
    @SuppressWarnings("unchecked")
    private void emitNeutralMetadata(StringBuilder sb, String metadataContent) {
        // Parse the metadata.yaml with SnakeYAML to get structured data
        org.yaml.snakeyaml.Yaml yamlParser = new org.yaml.snakeyaml.Yaml();
        Map<String, Object> parsed = yamlParser.load(metadataContent);
        if (parsed == null) return;

        Map<String, Object> metadata = (Map<String, Object>) parsed.get("metadata");
        if (metadata == null) return;

        sb.append("metadata:\n");
        for (Map.Entry<String, Object> catEntry : metadata.entrySet()) {
            String category = catEntry.getKey();
            Map<String, Object> catData = (Map<String, Object>) catEntry.getValue();
            sb.append("  ").append(category).append(":\n");
            sb.append("    fields:\n");
            List<Map<String, Object>> fields = (List<Map<String, Object>>) catData.get("fields");
            if (fields != null) {
                for (Map<String, Object> field : fields) {
                    String fieldName = (String) field.get("name");
                    String rawType = (String) field.get("type");
                    String neutralType = toNeutralMetadataType(rawType);
                    sb.append("      - name: ").append(fieldName).append("\n");
                    sb.append("        type: ").append(neutralType).append("\n");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void writeRule(StringBuilder sb, Map<String, Object> rule) {
        sb.append("  - name: ").append(rule.get("name")).append("\n");

        // target
        Map<String, Object> target = (Map<String, Object>) rule.get("target");
        sb.append("    target:\n");
        sb.append("      type: ").append(target.get("type")).append("\n");
        sb.append("      name: ").append(target.get("name")).append("\n");
        sb.append("      domain: ").append(target.get("domain")).append("\n");

        // blocking (only if true)
        if (Boolean.TRUE.equals(rule.get("blocking"))) {
            sb.append("    blocking: true\n");
        }

        // requires (metadata) - evaluates BEFORE conditions
        Map<String, List<Map<String, Object>>> requires =
                (Map<String, List<Map<String, Object>>>) rule.get("requires");
        if (requires != null && !requires.isEmpty()) {
            sb.append("    requires:\n");
            for (Map.Entry<String, List<Map<String, Object>>> entry : requires.entrySet()) {
                sb.append("      ").append(entry.getKey()).append(":\n");
                for (Map<String, Object> cond : entry.getValue()) {
                    sb.append("        - field: ").append(cond.get("field")).append("\n");
                    sb.append("          operator: ").append(formatYamlValue(cond.get("operator"))).append("\n");
                    sb.append("          value: ").append(formatYamlValue(cond.get("value"))).append("\n");
                    sb.append("          value_type: ").append(inferValueType(cond.get("value"))).append("\n");
                }
            }
        }

        // conditions
        Object conditions = rule.get("conditions");
        sb.append("    conditions:\n");
        if (conditions instanceof List) {
            List<Map<String, Object>> condList = (List<Map<String, Object>>) conditions;
            for (Map<String, Object> cond : condList) {
                sb.append("      - field: ").append(cond.get("field")).append("\n");
                sb.append("        operator: ").append(formatYamlValue(cond.get("operator"))).append("\n");
                sb.append("        value: ").append(formatYamlValue(cond.get("value"))).append("\n");
                sb.append("        value_type: ").append(inferValueType(cond.get("value"))).append("\n");
            }
        } else if (conditions instanceof Map) {
            Map<String, Object> compound = (Map<String, Object>) conditions;
            sb.append("      operator: ").append(compound.get("operator")).append("\n");
            sb.append("      clauses:\n");
            List<Map<String, Object>> clauses = (List<Map<String, Object>>) compound.get("clauses");
            for (Map<String, Object> clause : clauses) {
                sb.append("        - field: ").append(clause.get("field")).append("\n");
                sb.append("          operator: ").append(formatYamlValue(clause.get("operator"))).append("\n");
                sb.append("          value: ").append(formatYamlValue(clause.get("value"))).append("\n");
                sb.append("          value_type: ").append(inferValueType(clause.get("value"))).append("\n");
            }
        }

        // then / else
        Map<String, String> thenMap = (Map<String, String>) rule.get("then");
        sb.append("    then:\n");
        sb.append("      status: ").append(formatYamlValue(thenMap.get("status"))).append("\n");

        Map<String, String> elseMap = (Map<String, String>) rule.get("else");
        sb.append("    else:\n");
        sb.append("      status: ").append(formatYamlValue(elseMap.get("status"))).append("\n");
    }

    // Format a value for YAML output: strings get double-quoted, numbers/booleans do not
    String formatYamlValue(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Double) {
            return String.valueOf(value);
        }
        if (value instanceof Boolean) {
            return String.valueOf(value);
        }
        // String: always double-quote
        return "\"" + value + "\"";
    }
}
