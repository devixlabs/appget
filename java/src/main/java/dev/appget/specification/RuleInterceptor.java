package dev.appget.specification;

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.MessageOrBuilder;
import dev.appget.model.Rule;
import dev.appget.rules.BusinessRule;
import dev.appget.rules.CompoundRule;
import dev.appget.rules.FieldCondition;
import dev.appget.rules.MetadataRequirement;
import dev.appget.rules.RuleSet;
import dev.appget.rules.RulesProto;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reads business rules from protobuf custom options at runtime.
 * Replaces YAML-based rule loading with protobuf descriptor-driven rules.
 *
 * Rules are embedded in .proto files as message-level custom options
 * (generated from specs.yaml by SchemaToProtoConverter).
 */
public class RuleInterceptor {

    private static final Logger logger = LogManager.getLogger(RuleInterceptor.class);

    /**
     * Extract rules from a protobuf message class's descriptor.
     * The message must have been compiled with rules.proto custom options.
     */
    public static List<Rule> getRulesForMessage(Descriptors.Descriptor descriptor) {
        logger.debug("Getting rules for message: {}", descriptor.getName());
        List<Rule> rules = new ArrayList<>();

        if (!descriptor.getOptions().hasExtension(RulesProto.ruleSet)) {
            logger.debug("No rule_set option found for {}", descriptor.getName());
            return rules;
        }

        RuleSet ruleSet = descriptor.getOptions().getExtension(RulesProto.ruleSet);
        logger.info("Found {} rule(s) for message {}", ruleSet.getRulesCount(), descriptor.getName());

        for (BusinessRule br : ruleSet.getRulesList()) {
            Object spec = buildSpecification(br);
            Map<String, List<Specification>> metadataReqs = buildMetadataRequirements(br);
            String targetType = descriptor.getName();

            Rule rule = Rule.builder()
                    .name(br.getName())
                    .spec(spec)
                    .successStatus(br.getSuccessStatus())
                    .failureStatus(br.getFailureStatus())
                    .targetType(targetType)
                    .metadataRequirements(metadataReqs)
                    .build();

            rules.add(rule);
            logger.debug("Created rule '{}' for target '{}'", br.getName(), targetType);
        }

        return rules;
    }

    /**
     * Extract rules from a protobuf message instance.
     */
    public static List<Rule> getRulesForMessage(GeneratedMessage message) {
        return getRulesForMessage(message.getDescriptorForType());
    }

    /**
     * Check if a message has a blocking rule defined.
     */
    public static boolean hasBlockingRules(Descriptors.Descriptor descriptor) {
        if (!descriptor.getOptions().hasExtension(RulesProto.ruleSet)) {
            return false;
        }
        RuleSet ruleSet = descriptor.getOptions().getExtension(RulesProto.ruleSet);
        return ruleSet.getRulesList().stream().anyMatch(BusinessRule::getBlocking);
    }

    /**
     * Get the domain from a message's custom options.
     */
    public static String getDomain(Descriptors.Descriptor descriptor) {
        if (descriptor.getOptions().hasExtension(RulesProto.domain)) {
            return descriptor.getOptions().getExtension(RulesProto.domain);
        }
        return null;
    }

    private static Object buildSpecification(BusinessRule br) {
        if (br.hasCompoundConditions() && br.getCompoundConditions().getClausesCount() > 0) {
            CompoundRule compound = br.getCompoundConditions();
            CompoundSpecification.Logic logic = "OR".equalsIgnoreCase(compound.getLogic())
                    ? CompoundSpecification.Logic.OR
                    : CompoundSpecification.Logic.AND;

            List<Specification> specs = new ArrayList<>();
            for (FieldCondition fc : compound.getClausesList()) {
                specs.add(new Specification(fc.getField(), fc.getOperator(), parseValue(fc.getValue())));
            }

            logger.debug("Built compound specification ({}) with {} clause(s)", logic, specs.size());
            return new CompoundSpecification(logic, specs);
        } else if (br.getSimpleConditionsCount() > 0) {
            if (br.getSimpleConditionsCount() == 1) {
                FieldCondition fc = br.getSimpleConditions(0);
                logger.debug("Built simple specification: {} {} {}", fc.getField(), fc.getOperator(), fc.getValue());
                return new Specification(fc.getField(), fc.getOperator(), parseValue(fc.getValue()));
            } else {
                List<Specification> specs = new ArrayList<>();
                for (FieldCondition fc : br.getSimpleConditionsList()) {
                    specs.add(new Specification(fc.getField(), fc.getOperator(), parseValue(fc.getValue())));
                }
                logger.debug("Built compound specification (AND) from {} simple condition(s)", specs.size());
                return new CompoundSpecification(CompoundSpecification.Logic.AND, specs);
            }
        }

        logger.warn("No conditions found in business rule");
        return null;
    }

    private static Map<String, List<Specification>> buildMetadataRequirements(BusinessRule br) {
        if (br.getMetadataRequirementsCount() == 0) {
            return null;
        }

        Map<String, List<Specification>> result = new LinkedHashMap<>();
        for (MetadataRequirement mr : br.getMetadataRequirementsList()) {
            List<Specification> specs = new ArrayList<>();
            for (FieldCondition fc : mr.getConditionsList()) {
                specs.add(new Specification(fc.getField(), fc.getOperator(), parseValue(fc.getValue())));
            }
            result.put(mr.getCategory(), specs);
        }

        logger.debug("Built {} metadata requirement category(ies)", result.size());
        return result;
    }

    private static Object parseValue(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Not an integer
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            // Not a double
        }
        return value;
    }
}
