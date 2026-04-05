package dev.appget.codegen;

import java.util.List;
import java.util.Map;

/**
 * Carries rule and specification registry information from AppServerGenerator
 * to a ServerEmitter.
 *
 * Pre-filters view-targeting rules so emitters for {@code RuleService} and
 * {@code SpecificationRegistry} need only iterate {@code modelRules} without
 * additional filtering. The two lookup maps are pre-built by the generator for
 * the same reason.
 *
 * Each rule is represented as a plain {@link RuleEntry} inner class that mirrors
 * the fields of {@link RuleInfo} needed by the emitters.
 */
public class RuleEmitContext {

    /**
     * A single rule's data as needed by emitters.
     * Mirrors the fields of {@code AppServerGenerator.RuleInfo} relevant to
     * code generation (view-targeting rules are excluded before population).
     */
    static class RuleEntry {
        /** Unique rule name (PascalCase, e.g., "UserEmailValidation"). */
        final String name;

        /** snake_case name of the target model (e.g., "users"). */
        final String targetName;

        /** Domain of the target model (e.g., "auth"). */
        final String targetDomain;

        /** True when the rule's spec class requires a MetadataContext argument. */
        final boolean requiresMetadata;

        /** True when an unsatisfied rule causes a 422 rejection. */
        final boolean blocking;

        /**
         * Constructs a RuleEntry.
         *
         * @param name            rule name (PascalCase)
         * @param targetName      snake_case model name
         * @param targetDomain    domain name
         * @param requiresMetadata true if MetadataContext is needed
         * @param blocking        true if this rule is blocking
         */
        RuleEntry(
                String name,
                String targetName,
                String targetDomain,
                boolean requiresMetadata,
                boolean blocking) {
            this.name = name;
            this.targetName = targetName;
            this.targetDomain = targetDomain;
            this.requiresMetadata = requiresMetadata;
            this.blocking = blocking;
        }
    }

    /**
     * Non-view-targeting rules only, in the order they appear in specs.yaml.
     * View-targeting rules have been filtered out before this context is built.
     */
    final List<RuleEntry> modelRules;

    /**
     * Maps rule name → true/false indicating whether the rule is blocking.
     * Pre-built from {@code modelRules} so emitters can use a direct map lookup
     * rather than scanning the list.
     */
    final Map<String, Boolean> blockingMap;

    /**
     * Maps rule name → PascalCase target model name.
     * Resolved against the model index so the emitter does not need access to
     * the full model list. Example: "UserEmailValidation" → "Users".
     */
    final Map<String, String> ruleTargetMap;

    /**
     * Constructs a RuleEmitContext with all fields supplied by the generator.
     *
     * @param modelRules    pre-filtered list of non-view rules
     * @param blockingMap   rule name → blocking flag
     * @param ruleTargetMap rule name → PascalCase model name
     */
    RuleEmitContext(
            List<RuleEntry> modelRules,
            Map<String, Boolean> blockingMap,
            Map<String, String> ruleTargetMap) {
        this.modelRules = List.copyOf(modelRules);
        this.blockingMap = Map.copyOf(blockingMap);
        this.ruleTargetMap = Map.copyOf(ruleTargetMap);
    }
}
