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
 * Generates a comprehensive bash script from OpenAPI specification.
 *
 * Features:
 * - Parses openapi.yaml to extract all endpoints
 * - Generates sample data for each schema
 * - Creates curl commands for all CRUD operations
 * - Includes metadata headers for rule validation
 *
 * Usage: java -cp <classpath> dev.appget.codegen.OpenAPIDefaultScriptGenerator <openapi.yaml> <output-script.sh>
 */
public class OpenAPIDefaultScriptGenerator {

    private static final Logger logger = LogManager.getLogger(OpenAPIDefaultScriptGenerator.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private Map<String, Map<String, Object>> schemas = new LinkedHashMap<>();
    private List<EndpointInfo> endpoints = new ArrayList<>();
    private String baseUrl = DEFAULT_BASE_URL;

    public static void main(String[] args) {
        logger.debug("Entering main method with {} arguments", args.length);
        if (args.length < 1) {
            logger.error("Invalid argument count. Usage: OpenAPIDefaultScriptGenerator <openapi.yaml> [output-script.sh]");
            System.err.println("Usage: OpenAPIDefaultScriptGenerator <openapi.yaml> [output-script.sh]");
            System.exit(1);
        }

        String openapiPath = args[0];
        String outputScript = args.length > 1 ? args[1] : "test-api.sh";

        logger.info("Starting OpenAPIDefaultScriptGenerator with openapiPath={}, outputScript={}", openapiPath, outputScript);

        try {
            new OpenAPIDefaultScriptGenerator().generateDefaultScript(openapiPath, outputScript);
            logger.info("Successfully generated script: {}", outputScript);
            System.out.println("✓ Successfully generated script: " + outputScript);
        } catch (Exception e) {
            logger.error("Failed to generate script", e);
            System.err.println("✗ Failed to generate script: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        logger.debug("Exiting main method");
    }

    @SuppressWarnings("unchecked")
    public void generateDefaultScript(String openapiPath, String outputScript) throws IOException {
        logger.debug("Loading OpenAPI spec from: {}", openapiPath);

        // Load OpenAPI spec
        Yaml yaml = new Yaml();
        Map<String, Object> openapi;
        try (InputStream in = new FileInputStream(new File(openapiPath))) {
            openapi = yaml.load(in);
        }

        // Extract base URL
        List<Map<String, Object>> servers = (List<Map<String, Object>>) openapi.get("servers");
        if (servers != null && !servers.isEmpty()) {
            baseUrl = (String) servers.get(0).get("url");
        }
        logger.debug("Using base URL: {}", baseUrl);

        // Extract schemas
        Map<String, Object> components = (Map<String, Object>) openapi.get("components");
        if (components != null) {
            Map<String, Object> schemasRaw = (Map<String, Object>) components.get("schemas");
            if (schemasRaw != null) {
                for (Map.Entry<String, Object> entry : schemasRaw.entrySet()) {
                    schemas.put(entry.getKey(), (Map<String, Object>) entry.getValue());
                }
            }
        }
        logger.info("Loaded {} schemas", schemas.size());

        // Extract paths and operations
        Map<String, Object> paths = (Map<String, Object>) openapi.get("paths");
        if (paths != null) {
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, Object> operations = (Map<String, Object>) pathEntry.getValue();

                for (Map.Entry<String, Object> opEntry : operations.entrySet()) {
                    String method = opEntry.getKey().toUpperCase();
                    Map<String, Object> operation = (Map<String, Object>) opEntry.getValue();

                    EndpointInfo endpoint = new EndpointInfo();
                    endpoint.path = path;
                    endpoint.method = method;
                    endpoint.operationId = (String) operation.get("operationId");
                    endpoint.summary = (String) operation.get("summary");

                    List<String> tags = (List<String>) operation.get("tags");
                    if (tags != null && !tags.isEmpty()) {
                        endpoint.tag = tags.get(0);
                    }

                    // Extract request body schema
                    Map<String, Object> requestBody = (Map<String, Object>) operation.get("requestBody");
                    if (requestBody != null) {
                        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
                        if (content != null) {
                            Map<String, Object> appJson = (Map<String, Object>) content.get("application/json");
                            if (appJson != null) {
                                Map<String, Object> schema = (Map<String, Object>) appJson.get("schema");
                                if (schema != null) {
                                    String ref = (String) schema.get("$ref");
                                    if (ref != null) {
                                        endpoint.schemaRef = ref.substring(ref.lastIndexOf('/') + 1);
                                    }
                                }
                            }
                        }
                    }

                    endpoints.add(endpoint);
                }
            }
        }
        logger.info("Loaded {} endpoints", endpoints.size());

        // Generate bash script
        generateBashScript(outputScript);
    }

    private void generateBashScript(String outputScript) throws IOException {
        StringBuilder script = new StringBuilder();

        // Header
        script.append("#!/bin/bash\n");
        script.append("# Auto-generated API test script from openapi.yaml\n");
        script.append("# Generated: ").append(new java.util.Date()).append("\n\n");

        script.append("FAILURES=0\n\n");

        script.append("BASE_URL=\"").append(baseUrl).append("\"\n");
        script.append("\n");

        script.append("# Colors for output\n");
        script.append("GREEN='\\033[0;32m'\n");
        script.append("RED='\\033[0;31m'\n");
        script.append("BLUE='\\033[0;34m'\n");
        script.append("NC='\\033[0m' # No Color\n\n");

        script.append("echo -e \"${BLUE}========================================${NC}\"\n");
        script.append("echo -e \"${BLUE}API Test Script${NC}\"\n");
        script.append("echo -e \"${BLUE}Base URL: $BASE_URL${NC}\"\n");
        script.append("echo -e \"${BLUE}========================================${NC}\"\n");
        script.append("echo \"\"\n\n");

        // Group endpoints by tag (model)
        Map<String, List<EndpointInfo>> endpointsByTag = new LinkedHashMap<>();
        for (EndpointInfo endpoint : endpoints) {
            endpointsByTag.computeIfAbsent(endpoint.tag, k -> new ArrayList<>()).add(endpoint);
        }

        // Generate test sections for each model
        for (Map.Entry<String, List<EndpointInfo>> entry : endpointsByTag.entrySet()) {
            String tag = entry.getKey();
            List<EndpointInfo> modelEndpoints = entry.getValue();

            script.append("# ").append("=".repeat(60)).append("\n");
            script.append("# ").append(tag).append(" Tests\n");
            script.append("# ").append("=".repeat(60)).append("\n\n");

            // Find POST endpoint first (to create a resource)
            EndpointInfo postEndpoint = modelEndpoints.stream()
                .filter(e -> "POST".equals(e.method))
                .findFirst()
                .orElse(null);

            if (postEndpoint != null) {
                generatePostTest(script, postEndpoint, tag);
            }

            // Then GET list
            EndpointInfo getListEndpoint = modelEndpoints.stream()
                .filter(e -> "GET".equals(e.method) && !e.path.contains("{id}"))
                .findFirst()
                .orElse(null);

            if (getListEndpoint != null) {
                generateGetListTest(script, getListEndpoint, tag);
            }

            // Then GET by ID
            EndpointInfo getByIdEndpoint = modelEndpoints.stream()
                .filter(e -> "GET".equals(e.method) && e.path.contains("{id}"))
                .findFirst()
                .orElse(null);

            if (getByIdEndpoint != null) {
                generateGetByIdTest(script, getByIdEndpoint, tag);
            }

            // Then PUT
            EndpointInfo putEndpoint = modelEndpoints.stream()
                .filter(e -> "PUT".equals(e.method))
                .findFirst()
                .orElse(null);

            if (putEndpoint != null) {
                generatePutTest(script, putEndpoint, tag);
            }

            // Finally DELETE
            EndpointInfo deleteEndpoint = modelEndpoints.stream()
                .filter(e -> "DELETE".equals(e.method))
                .findFirst()
                .orElse(null);

            if (deleteEndpoint != null) {
                generateDeleteTest(script, deleteEndpoint, tag);
            }

            script.append("\n");
        }

        // Footer with conditional exit code
        script.append("if [ \"$FAILURES\" -gt 0 ]; then\n");
        script.append("  echo -e \"${RED}========================================${NC}\"\n");
        script.append("  echo -e \"${RED}$FAILURES test(s) failed!${NC}\"\n");
        script.append("  echo -e \"${RED}========================================${NC}\"\n");
        script.append("  exit 1\n");
        script.append("fi\n");
        script.append("echo -e \"${GREEN}========================================${NC}\"\n");
        script.append("echo -e \"${GREEN}All tests completed successfully!${NC}\"\n");
        script.append("echo -e \"${GREEN}========================================${NC}\"\n");

        // Write script file
        Path scriptPath = Paths.get(outputScript);
        Files.writeString(scriptPath, script.toString());

        // Make executable
        scriptPath.toFile().setExecutable(true);

        logger.info("Generated script with {} sections", endpointsByTag.size());
    }

    private void generatePostTest(StringBuilder script, EndpointInfo endpoint, String tag) {
        script.append("echo -e \"${BLUE}Testing: ").append(endpoint.summary).append("${NC}\"\n");

        String sampleJson = generateSampleJson(endpoint.schemaRef);

        script.append("RESPONSE=$(curl -s -w \"\\n%{http_code}\" -X POST \"$BASE_URL").append(endpoint.path).append("\" \\\n");
        script.append("  -H 'Content-Type: application/json' \\\n");

        // Add metadata headers for rule validation
        script.append("  -H 'X-Sso-Authenticated: true' \\\n");
        script.append("  -H 'X-Roles-Role-Level: 5' \\\n");
        script.append("  -H 'X-Roles-Is-Admin: true' \\\n");
        script.append("  -H 'X-Api-Is-Active: true' \\\n");

        script.append("  -d '").append(sampleJson).append("')\n");
        script.append("HTTP_CODE=$(echo \"$RESPONSE\" | tail -n1)\n");
        script.append("BODY=$(echo \"$RESPONSE\" | sed '$d')\n");
        script.append("if [ \"$HTTP_CODE\" -eq 201 ]; then\n");
        script.append("  echo -e \"${GREEN}✓ POST ").append(endpoint.path).append(" - Created (201)${NC}\"\n");
        script.append("  echo \"$BODY\" | python3 -m json.tool 2>/dev/null || echo \"$BODY\"\n");
        script.append("  # Extract ID for later tests\n");
        script.append("  ID=$(echo \"$BODY\" | python3 -c \"import sys, json; data=json.load(sys.stdin); print(data.get('data', {}).get('id', '') if 'data' in data else list(data.values())[0] if data else '')\" 2>/dev/null || echo \"test-id\")\n");
        script.append("  ").append(tag.toUpperCase()).append("_ID=\"$ID\"\n");
        script.append("else\n");
        script.append("  echo -e \"${RED}✗ POST ").append(endpoint.path).append(" - Failed ($HTTP_CODE)${NC}\"\n");
        script.append("  echo \"$BODY\"\n");
        script.append("  FAILURES=$((FAILURES + 1))\n");
        script.append("fi\n");
        script.append("echo \"\"\n\n");
    }

    private void generateGetListTest(StringBuilder script, EndpointInfo endpoint, String tag) {
        script.append("echo -e \"${BLUE}Testing: ").append(endpoint.summary).append("${NC}\"\n");
        script.append("RESPONSE=$(curl -s -w \"\\n%{http_code}\" -X GET \"$BASE_URL").append(endpoint.path).append("\")\n");
        script.append("HTTP_CODE=$(echo \"$RESPONSE\" | tail -n1)\n");
        script.append("BODY=$(echo \"$RESPONSE\" | sed '$d')\n");
        script.append("if [ \"$HTTP_CODE\" -eq 200 ]; then\n");
        script.append("  echo -e \"${GREEN}✓ GET ").append(endpoint.path).append(" - Success (200)${NC}\"\n");
        script.append("  echo \"$BODY\" | python3 -m json.tool 2>/dev/null || echo \"$BODY\"\n");
        script.append("else\n");
        script.append("  echo -e \"${RED}✗ GET ").append(endpoint.path).append(" - Failed ($HTTP_CODE)${NC}\"\n");
        script.append("  echo \"$BODY\"\n");
        script.append("  FAILURES=$((FAILURES + 1))\n");
        script.append("fi\n");
        script.append("echo \"\"\n\n");
    }

    private void generateGetByIdTest(StringBuilder script, EndpointInfo endpoint, String tag) {
        script.append("if [ -n \"$").append(tag.toUpperCase()).append("_ID\" ]; then\n");
        script.append("  echo -e \"${BLUE}Testing: ").append(endpoint.summary).append("${NC}\"\n");
        String path = endpoint.path.replace("{id}", "$" + tag.toUpperCase() + "_ID");
        script.append("  RESPONSE=$(curl -s -w \"\\n%{http_code}\" -X GET \"$BASE_URL").append(path).append("\")\n");
        script.append("  HTTP_CODE=$(echo \"$RESPONSE\" | tail -n1)\n");
        script.append("  BODY=$(echo \"$RESPONSE\" | sed '$d')\n");
        script.append("  if [ \"$HTTP_CODE\" -eq 200 ]; then\n");
        script.append("    echo -e \"${GREEN}✓ GET ").append(endpoint.path).append(" - Success (200)${NC}\"\n");
        script.append("    echo \"$BODY\" | python3 -m json.tool 2>/dev/null || echo \"$BODY\"\n");
        script.append("  else\n");
        script.append("    echo -e \"${RED}✗ GET ").append(endpoint.path).append(" - Failed ($HTTP_CODE)${NC}\"\n");
        script.append("    echo \"$BODY\"\n");
        script.append("    FAILURES=$((FAILURES + 1))\n");
        script.append("  fi\n");
        script.append("  echo \"\"\n");
        script.append("fi\n\n");
    }

    private void generatePutTest(StringBuilder script, EndpointInfo endpoint, String tag) {
        script.append("if [ -n \"$").append(tag.toUpperCase()).append("_ID\" ]; then\n");
        script.append("  echo -e \"${BLUE}Testing: ").append(endpoint.summary).append("${NC}\"\n");

        String sampleJson = generateSampleJson(endpoint.schemaRef, "Updated");
        String path = endpoint.path.replace("{id}", "$" + tag.toUpperCase() + "_ID");

        script.append("  RESPONSE=$(curl -s -w \"\\n%{http_code}\" -X PUT \"$BASE_URL").append(path).append("\" \\\n");
        script.append("    -H 'Content-Type: application/json' \\\n");
        script.append("    -H 'X-Sso-Authenticated: true' \\\n");
        script.append("    -H 'X-Roles-Role-Level: 5' \\\n");
        script.append("    -H 'X-Roles-Is-Admin: true' \\\n");
        script.append("    -H 'X-Api-Is-Active: true' \\\n");
        script.append("    -d '").append(sampleJson).append("')\n");
        script.append("  HTTP_CODE=$(echo \"$RESPONSE\" | tail -n1)\n");
        script.append("  BODY=$(echo \"$RESPONSE\" | sed '$d')\n");
        script.append("  if [ \"$HTTP_CODE\" -eq 200 ]; then\n");
        script.append("    echo -e \"${GREEN}✓ PUT ").append(endpoint.path).append(" - Success (200)${NC}\"\n");
        script.append("    echo \"$BODY\" | python3 -m json.tool 2>/dev/null || echo \"$BODY\"\n");
        script.append("  else\n");
        script.append("    echo -e \"${RED}✗ PUT ").append(endpoint.path).append(" - Failed ($HTTP_CODE)${NC}\"\n");
        script.append("    echo \"$BODY\"\n");
        script.append("    FAILURES=$((FAILURES + 1))\n");
        script.append("  fi\n");
        script.append("  echo \"\"\n");
        script.append("fi\n\n");
    }

    private void generateDeleteTest(StringBuilder script, EndpointInfo endpoint, String tag) {
        script.append("if [ -n \"$").append(tag.toUpperCase()).append("_ID\" ]; then\n");
        script.append("  echo -e \"${BLUE}Testing: ").append(endpoint.summary).append("${NC}\"\n");
        String path = endpoint.path.replace("{id}", "$" + tag.toUpperCase() + "_ID");
        script.append("  RESPONSE=$(curl -s -w \"\\n%{http_code}\" -X DELETE \"$BASE_URL").append(path).append("\")\n");
        script.append("  HTTP_CODE=$(echo \"$RESPONSE\" | tail -n1)\n");
        script.append("  if [ \"$HTTP_CODE\" -eq 204 ]; then\n");
        script.append("    echo -e \"${GREEN}✓ DELETE ").append(endpoint.path).append(" - Success (204)${NC}\"\n");
        script.append("  else\n");
        script.append("    echo -e \"${RED}✗ DELETE ").append(endpoint.path).append(" - Failed ($HTTP_CODE)${NC}\"\n");
        script.append("    FAILURES=$((FAILURES + 1))\n");
        script.append("  fi\n");
        script.append("  echo \"\"\n");
        script.append("fi\n\n");
    }

    @SuppressWarnings("unchecked")
    private String generateSampleJson(String schemaName) {
        return generateSampleJson(schemaName, "");
    }

    @SuppressWarnings("unchecked")
    private String generateSampleJson(String schemaName, String prefix) {
        if (schemaName == null || !schemas.containsKey(schemaName)) {
            return "{}";
        }

        Map<String, Object> schema = schemas.get(schemaName);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return "{}";
        }

        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> propEntry : properties.entrySet()) {
            if (!first) json.append(", ");
            first = false;

            String propName = propEntry.getKey();
            Map<String, Object> propDef = (Map<String, Object>) propEntry.getValue();
            String type = (String) propDef.get("type");
            String format = (String) propDef.get("format");

            json.append("\"").append(propName).append("\": ");

            if ("string".equals(type) && "decimal".equals(format)) {
                json.append("\"99.99\"");
            } else if ("string".equals(type) && "date-time".equals(format)) {
                json.append("\"2024-01-15T10:30:00Z\"");
            } else if ("string".equals(type) && "date".equals(format)) {
                json.append("\"2024-01-15\"");
            } else if ("string".equals(type)) {
                json.append("\"").append(prefix).append(capitalize(propName)).append("\"");
            } else if ("integer".equals(type)) {
                json.append(propName.toLowerCase().contains("id") ? "1" : "42");
            } else if ("number".equals(type)) {
                json.append("99.99");
            } else if ("boolean".equals(type)) {
                json.append(propName.toLowerCase().contains("deleted") ? "false" : "true");
            } else {
                json.append("\"sample\"");
            }
        }

        json.append("}");
        return json.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static class EndpointInfo {
        String path;
        String method;
        String operationId;
        String summary;
        String tag;
        String schemaRef;
    }
}
