package dev.appget;

import com.google.protobuf.Descriptors;
import com.google.protobuf.MessageOrBuilder;
import dev.appget.model.Rule;
import dev.appget.specification.MetadataContext;
import dev.appget.specification.RuleInterceptor;
import dev.appget.util.DescriptorRegistry;
import dev.appget.util.TestDataBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RuleEngine {
    private static final Logger logger = LogManager.getLogger(RuleEngine.class);

    public static void main(String[] args) {
        logger.debug("Entering main method");
        logger.info("Starting Rule Engine evaluation (Descriptor-Based)");

        try {
            DescriptorRegistry registry = new DescriptorRegistry();
            TestDataBuilder testData = new TestDataBuilder();

            // Discover rules from protobuf descriptors via RuleInterceptor
            Map<String, List<Rule>> rulesByModel = new HashMap<>();
            for (String modelName : registry.getAllModelNames()) {
                Descriptors.Descriptor descriptor = registry.getDescriptorByName(modelName);
                List<Rule> rules = RuleInterceptor.getRulesForMessage(descriptor);
                if (!rules.isEmpty()) {
                    rulesByModel.put(modelName, rules);
                }
            }

            logger.info("Discovered {} model(s) with rules", rulesByModel.size());

            MetadataContext metadata = buildDemoMetadata();

            System.out.println("\n--- Rule Engine Evaluation (Descriptor-Based) ---");

            for (Map.Entry<String, List<Rule>> entry : rulesByModel.entrySet()) {
                String modelName = entry.getKey();
                List<Rule> rules = entry.getValue();

                Descriptors.Descriptor descriptor = registry.getDescriptorByName(modelName);
                MessageOrBuilder testInstance = testData.buildSampleMessage(descriptor);

                System.out.println("\nModel: " + modelName + " (" + rules.size() + " rule(s))");
                System.out.println("Test instance: " + testInstance);

                for (Rule rule : rules) {
                    String result = rule.evaluate(testInstance, metadata);
                    System.out.printf("  Rule: %-30s | Result: %s%n", rule.getName(), result);
                }
            }

            logger.info("Rule Engine evaluation completed successfully");
        } catch (Exception e) {
            logger.error("Error during Rule Engine evaluation", e);
            e.printStackTrace();
        }
        logger.debug("Exiting main method");
    }

    private static MetadataContext buildDemoMetadata() {
        return new MetadataContext();
    }
}
