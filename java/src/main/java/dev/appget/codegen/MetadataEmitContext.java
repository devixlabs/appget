package dev.appget.codegen;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Carries metadata extraction context from AppServerGenerator to a ServerEmitter.
 *
 * Mirrors the generator's internal {@code metadataCategories} and
 * {@code metadataFieldDefinitions} fields, extracted from specs.yaml by
 * {@code FeatureToSpecsConverter}. An emitter uses this to generate
 * {@code MetadataExtractor.java} and its context POJO imports.
 */
public class MetadataEmitContext {

    /**
     * Ordered set of enabled metadata category names (e.g., "sso", "user", "roles").
     * Iteration order matches the order they appear in specs.yaml so generated
     * code is deterministic.
     */
    final Set<String> categories;

    /**
     * Per-category field definitions from specs.yaml.
     * Key: category name (matches an entry in {@code categories}).
     * Value: ordered list of field maps, each containing at minimum "name" and "type".
     */
    final Map<String, List<Map<String, Object>>> fieldDefinitions;

    /**
     * Constructs a MetadataEmitContext with all fields supplied by the generator.
     *
     * @param categories       ordered set of enabled category names
     * @param fieldDefinitions per-category field definitions
     */
    MetadataEmitContext(
            Set<String> categories,
            Map<String, List<Map<String, Object>>> fieldDefinitions) {
        // LinkedHashSet preserves insertion order required for deterministic codegen
        this.categories = Collections.unmodifiableSet(new LinkedHashSet<>(categories));
        this.fieldDefinitions = Map.copyOf(fieldDefinitions);
    }
}
