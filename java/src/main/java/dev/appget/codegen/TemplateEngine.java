package dev.appget.codegen;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TemplateEngine {
    private static final Logger log = LogManager.getLogger(TemplateEngine.class);
    private final Handlebars handlebars;

    public TemplateEngine() {
        log.debug("Initializing TemplateEngine with Handlebars");
        String templateDir = findTemplateDir();
        log.debug("Using template directory: {}", templateDir);
        TemplateLoader loader = new FileTemplateLoader(templateDir, ".hbs");
        this.handlebars = new Handlebars(loader);
        registerHelpers();
        log.debug("TemplateEngine initialized successfully");
    }

    private String findTemplateDir() {
        String cwd = System.getProperty("user.dir");
        Path templatePath = Paths.get(cwd, "src", "main", "resources", "templates");
        if (Files.exists(templatePath)) {
            log.debug("Found template directory at: {}", templatePath);
            return templatePath.toString();
        }
        throw new IllegalStateException("Template directory not found at: " + templatePath);
    }

    private void registerHelpers() {
        log.debug("Registering Handlebars helper functions");

        handlebars.registerHelper("lowerFirst", (context, options) -> {
            String str = context.toString();
            return str.isEmpty() ? str : Character.toLowerCase(str.charAt(0)) + str.substring(1);
        });

        handlebars.registerHelper("capitalize", (context, options) -> {
            String str = context.toString();
            return str.isEmpty() ? str : Character.toUpperCase(str.charAt(0)) + str.substring(1);
        });

        handlebars.registerHelper("camelToKebab", (context, options) -> {
            String str = context.toString();
            return str.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
        });

        handlebars.registerHelper("camelToSnake", (context, options) -> {
            String str = context.toString();
            return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        });

        log.debug("Helper functions registered");
    }

    public String render(String templatePath, Object context) {
        try {
            log.debug("Loading and rendering template: {}", templatePath);
            Template template = handlebars.compile(templatePath);
            String result = template.apply(context);
            log.debug("Successfully rendered template {} ({} chars)", templatePath, result.length());
            return result;
        } catch (IOException e) {
            log.error("Failed to render template: {}", templatePath, e);
            throw new UncheckedIOException("Template rendering failed: " + templatePath, e);
        }
    }
}
