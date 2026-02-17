package dev.appget.codegen;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.github.jknack.handlebars.Handlebars;

/**
 * Generates DescriptorRegistry class from models.yaml.
 * Auto-generates protobuf descriptor registration for all models and views.
 *
 * Usage: java -cp <classpath> dev.appget.codegen.DescriptorRegistryGenerator <models.yaml> <output-dir>
 */
public class DescriptorRegistryGenerator {

    private static final Logger logger = LogManager.getLogger(DescriptorRegistryGenerator.class);
    private final TemplateEngine templateEngine;

    public DescriptorRegistryGenerator() {
        this.templateEngine = new TemplateEngine();
    }

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
        logger.debug("Generating DescriptorRegistry class from {} entries", entries.size());

        Set<String> imports = new LinkedHashSet<>();
        List<Map<String, String>> entryMaps = new ArrayList<>();
        for (RegistryEntry entry : entries) {
            imports.add(entry.importPath);
            Map<String, String> entryMap = new HashMap<>();
            entryMap.put("name", entry.name);
            entryMap.put("importPath", entry.importPath);
            entryMaps.add(entryMap);
        }

        Map<String, Object> context = new HashMap<>();
        context.put("packageName", "dev.appget.util");
        context.put("imports", imports);
        context.put("entries", entryMaps);
        context.put("hasEntries", !entries.isEmpty());

        logger.debug("Rendering DescriptorRegistry template with {} imports and {} entries",
                imports.size(), entries.size());
        return templateEngine.render("descriptor/DescriptorRegistry.java", context);
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
