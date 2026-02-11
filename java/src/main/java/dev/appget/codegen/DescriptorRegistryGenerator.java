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
 * Generates DescriptorRegistry class from models.yaml.
 * Auto-generates protobuf descriptor registration for all models and views.
 *
 * Usage: java -cp <classpath> dev.appget.codegen.DescriptorRegistryGenerator <models.yaml> <output-dir>
 */
public class DescriptorRegistryGenerator {

    private static final Logger logger = LogManager.getLogger(DescriptorRegistryGenerator.class);

    public static void main(String[] args) {
        logger.debug("Entering main method with {} arguments", args.length);
        if (args.length < 2) {
            logger.error("Invalid argument count. Usage: DescriptorRegistryGenerator <models.yaml> <output-dir>");
            System.err.println("Usage: DescriptorRegistryGenerator <models.yaml> <output-dir>");
            System.exit(1);
        }

        String modelsPath = args[0];
        String outputDir = args[1];
        logger.info("Starting DescriptorRegistryGenerator with modelsPath={}, outputDir={}", modelsPath, outputDir);

        try {
            DescriptorRegistryGenerator gen = new DescriptorRegistryGenerator();
            gen.generateRegistry(modelsPath, outputDir);
            logger.info("Successfully generated DescriptorRegistry to: {}", outputDir);
            System.out.println("✓ Successfully generated DescriptorRegistry to: " + outputDir);
        } catch (Exception e) {
            logger.error("Failed to generate DescriptorRegistry", e);
            System.err.println("✗ Failed to generate DescriptorRegistry: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        logger.debug("Exiting main method");
    }

    @SuppressWarnings("unchecked")
    public void generateRegistry(String modelsPath, String outputDir) throws IOException {
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File(modelsPath))) {
            data = yaml.load(in);
        }

        Map<String, Object> domains = (Map<String, Object>) data.get("domains");
        if (domains == null || domains.isEmpty()) {
            logger.warn("No domains found in {}", modelsPath);
            System.out.println("Warning: No domains found in " + modelsPath);
            return;
        }

        // Collect all models and views across domains
        List<RegistryEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Object> domainEntry : domains.entrySet()) {
            String domainName = domainEntry.getKey();
            Map<String, Object> domainConfig = (Map<String, Object>) domainEntry.getValue();
            String namespace = (String) domainConfig.get("namespace");

            logger.debug("Processing domain: {} with namespace: {}", domainName, namespace);

            // Process models
            List<Map<String, Object>> models = (List<Map<String, Object>>) domainConfig.get("models");
            if (models != null) {
                for (Map<String, Object> model : models) {
                    String name = (String) model.get("name");
                    String importPath = namespace + ".model." + name;
                    entries.add(new RegistryEntry(name, importPath));
                    logger.debug("Added model: {} with import: {}", name, importPath);
                }
            }

            // Process views
            List<Map<String, Object>> views = (List<Map<String, Object>>) domainConfig.get("views");
            if (views != null) {
                for (Map<String, Object> view : views) {
                    String name = (String) view.get("name");
                    String importPath = namespace + ".view." + name;
                    entries.add(new RegistryEntry(name, importPath));
                    logger.debug("Added view: {} with import: {}", name, importPath);
                }
            }
        }

        logger.info("Collected {} registry entries (models + views)", entries.size());
        String javaCode = generateRegistryClass(entries);

        // Create output directory
        Path packagePath = Paths.get(outputDir, "dev", "appget", "util");
        Files.createDirectories(packagePath);

        Path outputFile = packagePath.resolve("DescriptorRegistry.java");
        Files.writeString(outputFile, javaCode);
        logger.info("Written DescriptorRegistry to: {}", outputFile);
        System.out.println("  Generated: DescriptorRegistry.java (" + entries.size() + " entries)");
    }

    private String generateRegistryClass(List<RegistryEntry> entries) {
        Set<String> imports = new LinkedHashSet<>();
        imports.add("com.google.protobuf.Descriptors");

        // Collect unique import paths
        for (RegistryEntry entry : entries) {
            imports.add(entry.importPath);
        }

        StringBuilder code = new StringBuilder();
        code.append("package dev.appget.util;\n\n");

        for (String imp : imports) {
            code.append("import ").append(imp).append(";\n");
        }
        code.append("\n");

        code.append("import java.util.HashMap;\n");
        code.append("import java.util.Map;\n");
        code.append("import java.util.Set;\n\n");

        code.append("/**\n");
        code.append(" * Auto-generated protobuf descriptor registry.\n");
        code.append(" * DO NOT EDIT MANUALLY - Generated from models.yaml\n");
        code.append(" */\n");
        code.append("public class DescriptorRegistry {\n");
        code.append("    private final Map<String, Descriptors.Descriptor> descriptors = new HashMap<>();\n\n");

        code.append("    public DescriptorRegistry() {\n");

        if (!entries.isEmpty()) {
            code.append("        // Models and Views (from models.yaml)\n");
            for (RegistryEntry entry : entries) {
                code.append("        register(\"").append(entry.name).append("\", ")
                    .append(entry.name).append(".getDescriptor());\n");
            }
        }

        code.append("    }\n\n");

        code.append("    private void register(String name, Descriptors.Descriptor descriptor) {\n");
        code.append("        descriptors.put(name, descriptor);\n");
        code.append("    }\n\n");

        code.append("    public Descriptors.Descriptor getDescriptorByName(String simpleName) {\n");
        code.append("        return descriptors.get(simpleName);\n");
        code.append("    }\n\n");

        code.append("    public Set<String> getAllModelNames() {\n");
        code.append("        return descriptors.keySet();\n");
        code.append("    }\n");
        code.append("}\n");

        return code.toString();
    }

    private static class RegistryEntry {
        final String name;
        final String importPath;

        RegistryEntry(String name, String importPath) {
            this.name = name;
            this.importPath = importPath;
        }
    }
}
