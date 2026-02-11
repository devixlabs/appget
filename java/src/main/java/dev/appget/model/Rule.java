package dev.appget.model;

import dev.appget.specification.CompoundSpecification;
import dev.appget.specification.MetadataContext;
import dev.appget.specification.Specification;

import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Rule {
    private static final Logger logger = LogManager.getLogger(Rule.class);
    private String name;
    private Object spec; // Specification or CompoundSpecification
    private String successStatus;
    private String failureStatus;
    private String targetType;
    private Map<String, List<Specification>> metadataRequirements;

    public Rule(String name, Specification spec, String successStatus, String failureStatus) {
        logger.debug("Creating Rule '{}' with Specification, successStatus={}, failureStatus={}", name, successStatus, failureStatus);
        this.name = name;
        this.spec = spec;
        this.successStatus = successStatus;
        this.failureStatus = failureStatus;
    }

    public Rule(String name, CompoundSpecification spec, String successStatus, String failureStatus) {
        logger.debug("Creating Rule '{}' with CompoundSpecification, successStatus={}, failureStatus={}", name, successStatus, failureStatus);
        this.name = name;
        this.spec = spec;
        this.successStatus = successStatus;
        this.failureStatus = failureStatus;
    }

    public Rule(String name, Object spec, String successStatus, String failureStatus,
                String targetType, Map<String, List<Specification>> metadataRequirements) {
        logger.debug("Creating Rule '{}' with targetType={}, metadataRequirements={}, successStatus={}, failureStatus={}",
                name, targetType, metadataRequirements != null ? metadataRequirements.size() : 0, successStatus, failureStatus);
        this.name = name;
        this.spec = spec;
        this.successStatus = successStatus;
        this.failureStatus = failureStatus;
        this.targetType = targetType;
        this.metadataRequirements = metadataRequirements;
    }

    public <T> String evaluate(T target) {
        logger.debug("Evaluating rule '{}' without metadata", name);
        return evaluate(target, null);
    }

    public <T> String evaluate(T target, MetadataContext metadata) {
        logger.debug("Entering evaluate for rule '{}', target type: {}", name, target.getClass().getName());

        // Check metadata requirements first
        if (metadataRequirements != null && !metadataRequirements.isEmpty()) {
            logger.debug("Rule '{}' has {} metadata requirement(s)", name, metadataRequirements.size());
            if (metadata == null) {
                logger.debug("Metadata required for rule '{}' but none provided, returning failure status", name);
                return failureStatus;
            }
            for (Map.Entry<String, List<Specification>> entry : metadataRequirements.entrySet()) {
                String category = entry.getKey();
                Object context = metadata.get(category);
                if (context == null) {
                    logger.debug("Metadata category '{}' not found in context for rule '{}', returning failure status", category, name);
                    return failureStatus;
                }
                logger.debug("Evaluating metadata requirement '{}' for rule '{}'", category, name);
                for (Specification req : entry.getValue()) {
                    if (!req.isSatisfiedBy(context)) {
                        logger.debug("Metadata requirement '{}' failed for rule '{}', returning failure status", category, name);
                        return failureStatus;
                    }
                }
            }
            logger.debug("All metadata requirements passed for rule '{}'", name);
        }

        // Evaluate the main spec
        logger.debug("Evaluating main specification for rule '{}'", name);
        boolean satisfied;
        if (spec instanceof Specification s) {
            logger.debug("Rule '{}' has Specification, evaluating...", name);
            satisfied = s.isSatisfiedBy(target);
        } else if (spec instanceof CompoundSpecification cs) {
            logger.debug("Rule '{}' has CompoundSpecification, evaluating...", name);
            satisfied = cs.isSatisfiedBy(target);
        } else {
            logger.debug("Rule '{}' has unknown spec type, returning false", name);
            satisfied = false;
        }

        String result = satisfied ? successStatus : failureStatus;
        logger.debug("Rule '{}' evaluation result: {} -> {}", name, satisfied, result);
        logger.debug("Exiting evaluate for rule '{}'", name);
        return result;
    }

    public String getName() { return name; }
    public Object getSpec() { return spec; }
    public String getTargetType() { return targetType; }
}
