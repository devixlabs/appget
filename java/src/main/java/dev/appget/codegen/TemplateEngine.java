package dev.appget.codegen;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.appget.codegen.CodeGenUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TemplateEngine {
    private static final Logger log = LogManager.getLogger(TemplateEngine.class);
    private final Handlebars handlebars;

    /**
     * Creates a TemplateEngine that resolves templates from the default location:
     * {@code $CWD/src/main/resources/templates}. Delegates to {@link #TemplateEngine(Path)}.
     */
    public TemplateEngine() {
        this(defaultTemplateDir());
    }

    /**
     * Creates a TemplateEngine that resolves templates from the given directory.
     * Prefer this constructor when the JVM working directory may differ from the
     * project root (e.g., tests, multi-module builds).
     *
     * @param templateDir absolute or relative path to the directory containing {@code .hbs} files
     * @throws IllegalArgumentException if {@code templateDir} is null
     * @throws IllegalStateException    if {@code templateDir} does not exist on disk
     */
    // Per EJ Item 1: consider static factories, but here constructor injection is the
    // right contract — callers own the path, TemplateEngine owns the Handlebars instance.
    public TemplateEngine(final Path templateDir) {
        if (templateDir == null) {
            throw new IllegalArgumentException("templateDir must not be null");
        }
        if (!Files.exists(templateDir)) {
            throw new IllegalStateException("Template directory not found at: " + templateDir);
        }
        log.debug("Initializing TemplateEngine with Handlebars, templateDir={}", templateDir);
        TemplateLoader loader = new FileTemplateLoader(templateDir.toString(), ".hbs");
        this.handlebars = new Handlebars(loader);
        registerHelpers();
        log.debug("TemplateEngine initialized successfully");
    }

    private static Path defaultTemplateDir() {
        final String cwd = System.getProperty("user.dir");
        final Path templatePath = Paths.get(cwd, "src", "main", "resources", "templates");
        log.debug("Resolved default template directory: {}", templatePath);
        return templatePath;
    }

    private void registerHelpers() {
        log.debug("Registering Handlebars helper functions");

        handlebars.registerHelper("lowerFirst", (context, options) ->
            CodeGenUtils.lowerFirst(context.toString()));

        handlebars.registerHelper("capitalize", (context, options) ->
            CodeGenUtils.capitalize(context.toString()));

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
