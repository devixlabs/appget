package dev.appget.specification;

/**
 * Contract for all generated specification classes.
 *
 * <p>Generated specs are parameterized by a concrete model type (e.g., {@code Users},
 * {@code Posts}), but the server's {@code RuleService} evaluates specs heterogeneously
 * through this untyped interface. Each generated class implements these methods by
 * delegating to its typed {@code evaluate}/{@code getResult} with a cast.
 *
 * <p>This interface eliminates runtime reflection from the generated server:
 * {@code RuleService} invokes {@code evaluate} and {@code getResult} directly
 * instead of scanning {@code getClass().getMethods()}.
 *
 * <p>Per EJ Item 64: refer to objects by their interfaces.
 */
// Per EJ Item 19: design for inheritance or prohibit it — sealed is intentionally
// omitted because the set of implementations is generated at build time and not
// known at compile time of this interface.
public interface EvaluableRule {

    /**
     * Evaluates this rule against the given target with optional metadata context.
     *
     * @param target   the domain object to evaluate (cast to the concrete type internally)
     * @param metadata the metadata context, or {@code null} if no metadata applies
     * @return {@code true} if the rule is satisfied
     */
    boolean evaluate(Object target, MetadataContext metadata);

    /**
     * Returns the status string for this rule's evaluation outcome.
     *
     * @param target   the domain object to evaluate
     * @param metadata the metadata context, or {@code null} if no metadata applies
     * @return the status string (e.g., "ACCOUNT_ACTIVE", "ADMIN_DENIED")
     */
    String getResult(Object target, MetadataContext metadata);
}
