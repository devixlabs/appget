package dev.appget;

import com.google.protobuf.Descriptors;
import com.google.protobuf.MessageOrBuilder;
import dev.appget.model.Rule;
import dev.appget.specification.CompoundSpecification;
import dev.appget.specification.MetadataContext;
import dev.appget.specification.Specification;
import dev.appget.util.DescriptorRegistry;
import dev.appget.util.DefaultDataBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

public class RuleEngine {
    private static final Logger logger = LogManager.getLogger(RuleEngine.class);

    public static void main(String[] args) {
        logger.info("Starting Rule Engine evaluation (Descriptor-Based)");

        try {
            DescriptorRegistry registry = new DescriptorRegistry();
            DefaultDataBuilder defaultData = new DefaultDataBuilder();

            // Load rules from specs.yaml
            Map<String, List<Rule>> rulesByModel = loadRulesFromSpecs("specs.yaml");
            logger.info("Loaded rules for {} model(s)", rulesByModel.size());

            MetadataContext metadata = buildDemoMetadata();

            System.out.println("\n--- Rule Engine Evaluation (Descriptor-Based) ---");

            for (Map.Entry<String, List<Rule>> entry : rulesByModel.entrySet()) {
                String modelName = entry.getKey();
                List<Rule> rules = entry.getValue();

                Descriptors.Descriptor descriptor = registry.getDescriptorByName(modelName);
                if (descriptor == null) {
                    logger.warn("No descriptor found for model: {}", modelName);
                    continue;
                }
                MessageOrBuilder sampleInstance = defaultData.buildSampleMessage(descriptor);

                System.out.println("\nModel: " + modelName + " (" + rules.size() + " rule(s))");
                System.out.println("Sample instance: " + sampleInstance);

                for (Rule rule : rules) {
                    String result = rule.evaluate(sampleInstance, metadata);
                    System.out.printf("  Rule: %-30s | Result: %s%n", rule.getName(), result);
                }
            }

            logger.info("Rule Engine evaluation completed successfully");
        } catch (Exception e) {
            logger.error("Error during Rule Engine evaluation", e);
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Rule>> loadRulesFromSpecs(String specsFile) throws Exception {
        Map<String, List<Rule>> rulesByModel = new LinkedHashMap<>();

        if (!new File(specsFile).exists()) {
            logger.warn("specs.yaml not found at {}, running without rules", specsFile);
            return rulesByModel;
        }

        Yaml yaml = new Yaml();
        Map<String, Object> data;
        try (InputStream in = new FileInputStream(specsFile)) {
            data = yaml.load(in);
        }
        logger.info("Loaded specs from {}", specsFile);

        List<Map<String, Object>> rawRules = (List<Map<String, Object>>) data.get("rules");
        if (rawRules == null) {
            return rulesByModel;
        }

        for (Map<String, Object> raw : rawRules) {
            String ruleName = (String) raw.get("name");
            Map<String, Object> target = (Map<String, Object>) raw.get("target");
            if (target == null) {
                continue;
            }
            String targetName = (String) target.get("name");

            Map<String, String> thenBlock = (Map<String, String>) raw.get("then");
            Map<String, String> elseBlock = (Map<String, String>) raw.get("else");
            String successStatus = thenBlock != null ? thenBlock.get("status") : "APPROVED";
            String failureStatus = elseBlock != null ? elseBlock.get("status") : "REJECTED";

            Object spec = buildSpec(raw.get("conditions"));
            if (spec == null) {
                continue;
            }

            Rule rule = Rule.builder()
                    .name(ruleName)
                    .spec(spec)
                    .successStatus(successStatus)
                    .failureStatus(failureStatus)
                    .build();

            rulesByModel.computeIfAbsent(targetName, k -> new ArrayList<>()).add(rule);
        }
        return rulesByModel;
    }

    @SuppressWarnings("unchecked")
    private static Object buildSpec(Object conditions) {
        if (conditions instanceof Map) {
            Map<String, Object> compound = (Map<String, Object>) conditions;
            String operatorStr = (String) compound.get("operator");
            CompoundSpecification.Logic logic = "OR".equals(operatorStr)
                    ? CompoundSpecification.Logic.OR
                    : CompoundSpecification.Logic.AND;
            List<Map<String, Object>> clauses = (List<Map<String, Object>>) compound.get("clauses");
            List<Specification> specs = new ArrayList<>();
            if (clauses != null) {
                for (Map<String, Object> clause : clauses) {
                    specs.add(new Specification((String) clause.get("field"),
                            (String) clause.get("operator"), String.valueOf(clause.get("value"))));
                }
            }
            return new CompoundSpecification(logic, specs);
        } else if (conditions instanceof List) {
            List<Map<String, Object>> condList = (List<Map<String, Object>>) conditions;
            if (condList.size() == 1) {
                Map<String, Object> cond = condList.get(0);
                return new Specification((String) cond.get("field"),
                        (String) cond.get("operator"), String.valueOf(cond.get("value")));
            }
            List<Specification> specs = new ArrayList<>();
            for (Map<String, Object> cond : condList) {
                specs.add(new Specification((String) cond.get("field"),
                        (String) cond.get("operator"), String.valueOf(cond.get("value"))));
            }
            return new CompoundSpecification(CompoundSpecification.Logic.AND, specs);
        }
        return null;
    }

    private static MetadataContext buildDemoMetadata() {
        return new MetadataContext();
    }
}
