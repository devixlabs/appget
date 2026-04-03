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
 * Generator that produces static HTML CRUD pages from models.yaml and specs.yaml.
 *
 * Reads domain models and optionally business rules, then emits one directory
 * per model (4 pages: index, create, edit, view) and one directory per view
 * (1 page: index), plus a root navigation index.html.
 *
 * Form actions match REST routes produced by SpringBootEmitter:
 *   Models: /{resource}     Views: /views/{resource-without-view-suffix}
 *
 * Usage: java -cp <classpath> dev.appget.codegen.HtmlCrudGenerator
 *             <models.yaml> <specs.yaml> <output-dir>
 */
public class HtmlCrudGenerator {

    private static final Logger logger = LogManager.getLogger(HtmlCrudGenerator.class);

    // Deterministic iteration order — domain insertion order preserved
    private Map<String, DomainInfo> domainIndex = new LinkedHashMap<>();
    private Map<String, ModelInfo> modelIndex = new LinkedHashMap<>();
    private Map<String, ModelInfo> viewIndex = new LinkedHashMap<>();
    private List<RuleInfo> rules = new ArrayList<>();

    public static void main(String[] args) {
        logger.debug("Entering main method with {} arguments", args.length);
        if (args.length < 1) {
            logger.error("Invalid argument count. Usage: HtmlCrudGenerator <models.yaml> [specs.yaml] [output-dir]");
            System.err.println("Usage: HtmlCrudGenerator <models.yaml> [specs.yaml] [output-dir]");
            System.exit(1);
        }

        String modelsPath = args[0];
        String specsPath = args.length > 1 ? args[1] : null;
        String outputDir = args.length > 2 ? args[2] : "generated-html";

        logger.info("Starting HtmlCrudGenerator: modelsPath={}, specsPath={}, outputDir={}",
                modelsPath, specsPath, outputDir);

        try {
            new HtmlCrudGenerator().generateHtml(modelsPath, specsPath, outputDir);
            logger.info("Successfully generated HTML CRUD pages to: {}", outputDir);
            System.out.println("✓ Successfully generated HTML CRUD pages to: " + outputDir);
        } catch (Exception e) {
            logger.error("Failed to generate HTML", e);
            System.err.println("✗ Failed to generate HTML: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        logger.debug("Exiting main method");
    }

    @SuppressWarnings("unchecked")
    public void generateHtml(String modelsPath, String specsPath, String outputDir) throws IOException {
        // Clean output directory to avoid stale files
        Path outPath = Paths.get(outputDir);
        if (Files.exists(outPath)) {
            deleteDirectory(outPath);
            logger.debug("Cleaned output directory: {}", outputDir);
        }

        loadModels(modelsPath);
        loadRules(specsPath);

        // Root navigation index
        writeFile(outputDir, "index.html", generateRootIndexHtml());

        // Per-model: 4 pages each
        for (ModelInfo model : modelIndex.values()) {
            String dir = model.resource;
            writeFile(outputDir, dir + "/index.html", generateListHtml(model));
            writeFile(outputDir, dir + "/create.html", generateCreateHtml(model));
            writeFile(outputDir, dir + "/edit.html", generateEditHtml(model));
            writeFile(outputDir, dir + "/view.html", generateDetailHtml(model));
        }

        // Per-view: 1 page each
        for (ModelInfo view : viewIndex.values()) {
            String viewResource = toViewResource(view.resource);
            String dir = "views/" + viewResource;
            writeFile(outputDir, dir + "/index.html", generateViewListHtml(view));
        }

        int modelCount = modelIndex.size();
        int viewCount = viewIndex.size();
        int total = 1 + (modelCount * 4) + viewCount;
        System.out.println("  Generated: 1 root index");
        System.out.println("  Generated: " + modelCount + " model directories (" + (modelCount * 4) + " pages)");
        System.out.println("  Generated: " + viewCount + " view directories (" + viewCount + " pages)");
        System.out.println("  Total: " + total + " HTML files");
    }

    // ---- YAML Loading ----

    @SuppressWarnings("unchecked")
    private void loadModels(String yamlPath) throws IOException {
        logger.debug("Loading models from: {}", yamlPath);
        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(new File(yamlPath))) {
            data = yaml.load(in);
        }

        Map<String, Object> domains = (Map<String, Object>) data.get("domains");
        if (domains == null) {
            logger.warn("No domains found in {}", yamlPath);
            return;
        }

        for (Map.Entry<String, Object> domainEntry : domains.entrySet()) {
            String domainName = domainEntry.getKey();
            Map<String, Object> domainConfig = (Map<String, Object>) domainEntry.getValue();

            DomainInfo domain = new DomainInfo();
            domain.name = domainName;
            domainIndex.put(domainName, domain);

            List<Map<String, Object>> models = (List<Map<String, Object>>) domainConfig.get("models");
            if (models != null) {
                for (Map<String, Object> model : models) {
                    String modelName = (String) model.get("name");
                    String resource = (String) model.get("resource");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) model.get("fields");

                    ModelInfo info = new ModelInfo();
                    info.name = modelName;
                    info.resource = resource != null ? resource : modelName.replace('_', '-');
                    info.domain = domainName;
                    info.isView = false;
                    info.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();

                    modelIndex.put(modelName, info);
                    domain.models.add(info);
                    logger.debug("Loaded model: {} (resource: {})", modelName, info.resource);
                }
            }

            List<Map<String, Object>> viewList = (List<Map<String, Object>>) domainConfig.get("views");
            if (viewList != null) {
                for (Map<String, Object> view : viewList) {
                    String viewName = (String) view.get("name");
                    String resource = (String) view.get("resource");
                    List<Map<String, Object>> fields = (List<Map<String, Object>>) view.get("fields");

                    ModelInfo info = new ModelInfo();
                    info.name = viewName;
                    info.resource = resource != null ? resource : viewName.replace('_', '-');
                    info.domain = domainName;
                    info.isView = true;
                    info.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();

                    viewIndex.put(viewName, info);
                    domain.views.add(info);
                    logger.debug("Loaded view: {} (resource: {})", viewName, info.resource);
                }
            }
        }

        logger.info("Loaded {} models and {} views across {} domains",
                modelIndex.size(), viewIndex.size(), domainIndex.size());
    }

    @SuppressWarnings("unchecked")
    private void loadRules(String specsPath) {
        if (specsPath == null) {
            logger.warn("No specs path provided — skipping rule loading");
            return;
        }

        File specsFile = new File(specsPath);
        if (!specsFile.exists()) {
            logger.warn("specs.yaml not found at {} — continuing without rules", specsPath);
            return;
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> data;
            try (InputStream in = new FileInputStream(specsFile)) {
                data = yaml.load(in);
            }

            if (data == null) {
                logger.warn("specs.yaml is empty — continuing without rules");
                return;
            }

            List<Map<String, Object>> rawRules = (List<Map<String, Object>>) data.get("rules");
            if (rawRules != null) {
                for (Map<String, Object> raw : rawRules) {
                    String ruleName = (String) raw.get("name");
                    Map<String, Object> target = (Map<String, Object>) raw.get("target");

                    RuleInfo info = new RuleInfo();
                    info.name = ruleName;

                    if (target != null) {
                        info.targetType = (String) target.get("type");
                        info.targetName = (String) target.get("name");
                        info.targetDomain = (String) target.get("domain");
                    }

                    Object blockingVal = raw.get("blocking");
                    info.blocking = blockingVal != null && Boolean.TRUE.equals(blockingVal);

                    rules.add(info);
                }
                logger.info("Loaded {} rules from {}", rules.size(), specsPath);
            }
        } catch (Exception e) {
            logger.warn("Failed to load rules from {} — continuing without rules: {}", specsPath, e.getMessage());
        }
    }

    // ---- Page Generators ----

    private String generateRootIndexHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head><meta charset=\"UTF-8\"><title>APPGET — Generated CRUD</title></head>\n");
        html.append("<body>\n");
        html.append("<h1>APPGET — Generated CRUD</h1>\n");

        for (DomainInfo domain : domainIndex.values()) {
            html.append("<h2>").append(domain.name).append("</h2>\n");

            if (!domain.models.isEmpty()) {
                html.append("<h3>Models</h3>\n");
                html.append("<ul>\n");
                for (ModelInfo model : domain.models) {
                    html.append("  <li><a href=\"").append(model.resource).append("/index.html\">")
                        .append(model.name).append("</a></li>\n");
                }
                html.append("</ul>\n");
            }

            if (!domain.views.isEmpty()) {
                html.append("<h3>Views</h3>\n");
                html.append("<ul>\n");
                for (ModelInfo view : domain.views) {
                    String viewResource = toViewResource(view.resource);
                    html.append("  <li><a href=\"views/").append(viewResource).append("/index.html\">")
                        .append(view.name).append("</a></li>\n");
                }
                html.append("</ul>\n");
            }
        }

        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private String generateListHtml(ModelInfo model) {
        StringBuilder html = new StringBuilder();
        appendDoctype(html, model.name + " — List");

        html.append("<body>\n");
        html.append("<h1>").append(model.name).append("</h1>\n");
        html.append("<nav><a href=\"../index.html\">Home</a> | ");
        html.append("<a href=\"create.html\">Create New</a></nav>\n");

        html.append("<table>\n");
        html.append("<thead><tr>\n");
        for (Map<String, Object> field : model.fields) {
            String fieldName = (String) field.get("name");
            html.append("  <th>").append(fieldName).append("</th>\n");
        }
        html.append("  <th>Actions</th>\n");
        html.append("</tr></thead>\n");
        html.append("<tbody><!-- Data populated by API --></tbody>\n");
        html.append("</table>\n");

        html.append("<p>API: GET <code>/").append(model.resource).append("</code></p>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private String generateCreateHtml(ModelInfo model) {
        List<RuleInfo> targetRules = rulesForTarget(model.name);

        StringBuilder html = new StringBuilder();
        appendDoctype(html, "Create " + model.name);

        html.append("<body>\n");
        html.append("<h1>Create ").append(model.name).append("</h1>\n");
        html.append("<nav><a href=\"../index.html\">Home</a> | ");
        html.append("<a href=\"index.html\">Back to List</a></nav>\n");

        html.append("<form method=\"POST\" action=\"/").append(model.resource).append("\">\n");
        for (Map<String, Object> field : model.fields) {
            if (shouldOmitOnCreate(field)) {
                continue;
            }
            String name = (String) field.get("name");
            html.append("  <div>\n");
            html.append("    <label for=\"").append(name).append("\">").append(humanize(name)).append("</label>\n");
            html.append("    ").append(fieldToInput(field)).append("\n");
            html.append("  </div>\n");
        }
        html.append("  <button type=\"submit\">Create</button>\n");
        html.append("  <a href=\"index.html\">Cancel</a>\n");
        html.append("</form>\n");

        html.append(renderRulesBlock(targetRules));

        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private String generateEditHtml(ModelInfo model) {
        List<RuleInfo> targetRules = rulesForTarget(model.name);

        StringBuilder html = new StringBuilder();
        appendDoctype(html, "Edit " + model.name);

        html.append("<body>\n");
        html.append("<h1>Edit ").append(model.name).append("</h1>\n");
        html.append("<nav><a href=\"../index.html\">Home</a> | ");
        html.append("<a href=\"index.html\">Back to List</a></nav>\n");

        html.append("<form method=\"POST\" action=\"/").append(model.resource).append("\">\n");
        html.append("  <input type=\"hidden\" name=\"_method\" value=\"PUT\">\n");
        html.append("  <input type=\"hidden\" name=\"id\" id=\"id\">\n");

        for (Map<String, Object> field : model.fields) {
            if (isPrimaryKey(field)) {
                continue; // PK rendered as hidden above
            }
            String name = (String) field.get("name");
            html.append("  <div>\n");
            html.append("    <label for=\"").append(name).append("\">").append(humanize(name)).append("</label>\n");
            html.append("    ").append(fieldToInput(field)).append("\n");
            html.append("  </div>\n");
        }
        html.append("  <button type=\"submit\">Update</button>\n");
        html.append("  <a href=\"index.html\">Cancel</a>\n");
        html.append("</form>\n");

        html.append(renderRulesBlock(targetRules));

        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private String generateDetailHtml(ModelInfo model) {
        StringBuilder html = new StringBuilder();
        appendDoctype(html, model.name + " — Detail");

        html.append("<body>\n");
        html.append("<h1>").append(model.name).append(" — Detail</h1>\n");
        html.append("<nav><a href=\"../index.html\">Home</a> | ");
        html.append("<a href=\"index.html\">Back to List</a></nav>\n");

        html.append("<dl>\n");
        for (Map<String, Object> field : model.fields) {
            String name = (String) field.get("name");
            html.append("  <dt>").append(name).append("</dt>\n");
            html.append("  <dd><!-- value --></dd>\n");
        }
        html.append("</dl>\n");

        html.append("<p>API: GET <code>/").append(model.resource).append("/{id}</code></p>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    private String generateViewListHtml(ModelInfo view) {
        String viewResource = toViewResource(view.resource);

        StringBuilder html = new StringBuilder();
        appendDoctype(html, view.name + " — View");

        html.append("<body>\n");
        html.append("<h1>").append(view.name).append("</h1>\n");
        html.append("<nav><a href=\"../../index.html\">Home</a></nav>\n");

        html.append("<table>\n");
        html.append("<thead><tr>\n");
        for (Map<String, Object> field : view.fields) {
            String fieldName = (String) field.get("name");
            html.append("  <th>").append(fieldName).append("</th>\n");
        }
        html.append("</tr></thead>\n");
        html.append("<tbody><!-- Data populated by API --></tbody>\n");
        html.append("</table>\n");

        html.append("<p>API: GET <code>/views/").append(viewResource).append("</code></p>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        return html.toString();
    }

    // ---- Field → Input Mapping ----

    private String fieldToInput(Map<String, Object> field) {
        String name = (String) field.get("name");
        String type = (String) field.get("type");
        String sqlType = field.get("original_sql_type") != null
                ? ((String) field.get("original_sql_type")).toUpperCase()
                : "";
        boolean nullable = Boolean.TRUE.equals(field.get("nullable"));
        String req = nullable ? "" : " required";

        // TEXT → textarea
        if (sqlType.equals("TEXT")) {
            return "<textarea name=\"" + name + "\" id=\"" + name + "\"" + req + "></textarea>";
        }
        // BOOLEAN → checkbox (never required — unchecked = false is valid)
        if ("bool".equals(type)) {
            return "<input type=\"checkbox\" name=\"" + name + "\" id=\"" + name + "\">";
        }
        // INT/BIGINT → number step=1
        if ("int32".equals(type) || "int64".equals(type)) {
            return "<input type=\"number\" step=\"1\" name=\"" + name + "\" id=\"" + name + "\"" + req + ">";
        }
        // DECIMAL → number step=0.01
        if ("decimal".equals(type)) {
            return "<input type=\"number\" step=\"0.01\" name=\"" + name + "\" id=\"" + name + "\"" + req + ">";
        }
        // FLOAT/DOUBLE → number step=any
        if ("float64".equals(type)) {
            return "<input type=\"number\" step=\"any\" name=\"" + name + "\" id=\"" + name + "\"" + req + ">";
        }
        // DATE → date
        if ("date".equals(type)) {
            return "<input type=\"date\" name=\"" + name + "\" id=\"" + name + "\"" + req + ">";
        }
        // DATETIME/TIMESTAMP → datetime-local
        if ("datetime".equals(type)) {
            return "<input type=\"datetime-local\" name=\"" + name + "\" id=\"" + name + "\"" + req + ">";
        }
        // Default: text
        return "<input type=\"text\" name=\"" + name + "\" id=\"" + name + "\"" + req + ">";
    }

    // ---- Business Rules ----

    private String renderRulesBlock(List<RuleInfo> targetRules) {
        if (targetRules.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        html.append("<details>\n<summary>Business Rules</summary>\n<ul>\n");
        for (RuleInfo rule : targetRules) {
            html.append("  <li>");
            if (rule.blocking) {
                html.append("[BLOCKING] ");
            }
            html.append(rule.name);
            html.append("</li>\n");
        }
        html.append("</ul>\n</details>\n");
        return html.toString();
    }

    private List<RuleInfo> rulesForTarget(String targetName) {
        List<RuleInfo> result = new ArrayList<>();
        for (RuleInfo rule : rules) {
            if (targetName.equals(rule.targetName)) {
                result.add(rule);
            }
        }
        return result;
    }

    // ---- Helpers ----

    private void appendDoctype(StringBuilder html, String title) {
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head><meta charset=\"UTF-8\"><title>").append(title).append("</title></head>\n");
    }

    private String humanize(String snakeName) {
        String[] parts = snakeName.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            if (part.length() > 0) {
                sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private boolean shouldOmitOnCreate(Map<String, Object> field) {
        return isPrimaryKey(field);
    }

    private boolean isPrimaryKey(Map<String, Object> field) {
        return Boolean.TRUE.equals(field.get("primary_key")) || "id".equals(field.get("name"));
    }

    /**
     * Strips the -view suffix from a view resource name to match REST route convention.
     * user-role-view → user-role
     * moderation-queue-view → moderation-queue
     */
    private String toViewResource(String resource) {
        return resource.endsWith("-view") ? resource.substring(0, resource.length() - 5) : resource;
    }

    private void writeFile(String outputDir, String relativePath, String content) throws IOException {
        Path filePath = Paths.get(outputDir, relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        logger.debug("Wrote: {}", filePath);
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(java.io.File::delete);
    }

    // ---- Inner classes ----

    static class DomainInfo {
        String name;
        List<ModelInfo> models = new ArrayList<>();
        List<ModelInfo> views = new ArrayList<>();
    }

    static class ModelInfo {
        String name;
        String resource;
        String domain;
        boolean isView;
        List<Map<String, Object>> fields;
    }

    static class RuleInfo {
        String name;
        String targetType;
        String targetName;
        String targetDomain;
        boolean blocking;
    }
}
