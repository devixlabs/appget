package dev.appget;

import com.google.protobuf.Descriptors;
import com.google.protobuf.MessageOrBuilder;
import dev.appget.model.Rule;
import dev.appget.specification.CompoundSpecification;
import dev.appget.specification.MetadataContext;
import dev.appget.specification.Specification;
import dev.appget.specification.context.SsoContext;
import dev.appget.specification.context.RolesContext;
import dev.appget.specification.context.UserContext;
import dev.appget.specification.context.OauthContext;
import dev.appget.specification.context.ApiContext;
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
        logger.info("Starting Rule Engine verification (Descriptor-Based)");

        List<String> errors = new ArrayList<>();

        try {
            DescriptorRegistry registry = new DescriptorRegistry();
            DefaultDataBuilder defaultData = new DefaultDataBuilder();

            // Load rules from specs.yaml (now includes blocking flag and metadata requirements)
            Map<String, List<Rule>> rulesByModel = loadRulesFromSpecs("specs.yaml");
            logger.info("Loaded rules for {} model(s)", rulesByModel.size());

            if (rulesByModel.isEmpty()) {
                errors.add("No rules loaded from specs.yaml — pipeline may be broken");
            }

            // Build demo metadata with realistic values that satisfy all requires: blocks
            MetadataContext happyMetadata = buildDemoMetadata();
            MetadataContext emptyMetadata = new MetadataContext();

            int totalRules = 0;
            int blockingCount = 0;
            int metadataAwareCount = 0;
            for (List<Rule> rules : rulesByModel.values()) {
                for (Rule rule : rules) {
                    totalRules++;
                    if (rule.isBlocking()) {
                        blockingCount++;
                    }
                    if (rule.hasMetadataRequirements()) {
                        metadataAwareCount++;
                    }
                }
            }

            printHeader(totalRules, rulesByModel.size(), blockingCount, metadataAwareCount);

            int passCount = 0;
            int failCount = 0;
            int metadataDeniedCount = 0;
            int missingDescriptors = 0;
            List<String[]> summaryRows = new ArrayList<>();

            for (Map.Entry<String, List<Rule>> entry : rulesByModel.entrySet()) {
                String modelName = entry.getKey();
                List<Rule> rules = entry.getValue();

                Descriptors.Descriptor descriptor = registry.getDescriptorByName(modelName);
                if (descriptor == null) {
                    logger.warn("No descriptor found for model: {}", modelName);
                    errors.add("Missing descriptor for model: " + modelName);
                    missingDescriptors++;
                    continue;
                }
                MessageOrBuilder sampleInstance = defaultData.buildSampleMessage(descriptor);

                System.out.println();
                System.out.println("  Model: " + modelName + " (" + rules.size() + " rule(s))");

                for (Rule rule : rules) {
                    boolean hasMetadata = rule.hasMetadataRequirements();

                    // Evaluate with happy-path metadata
                    String happyResult = rule.evaluate(sampleInstance, happyMetadata);
                    boolean happyIsSuccess = happyResult.equals(rule.getSuccessStatus());

                    // For rules with metadata requirements, also evaluate with empty metadata
                    // to demonstrate the auth gate
                    String deniedResult = null;
                    if (hasMetadata) {
                        deniedResult = rule.evaluate(sampleInstance, emptyMetadata);
                        metadataDeniedCount++;
                    }

                    if (happyIsSuccess) {
                        passCount++;
                    } else {
                        failCount++;
                    }

                    // Print detailed line
                    printRuleLine(rule, happyResult, deniedResult);

                    // Collect for summary
                    summaryRows.add(new String[]{
                            rule.getName(),
                            modelName,
                            rule.isBlocking() ? "Y" : "N",
                            hasMetadata ? "Y" : "N",
                            hasMetadata ? "Y" : "-",
                            happyResult,
                            happyIsSuccess ? "PASS" : "FAIL"
                    });
                }
            }

            // Verify auth gates: happy-path metadata must satisfy all requires: blocks
            List<String> authGateFailures = verifyAuthGates(rulesByModel, happyMetadata);
            errors.addAll(authGateFailures);

            printSummaryTable(summaryRows);
            printFooter(totalRules, passCount, failCount, metadataDeniedCount, missingDescriptors, authGateFailures.size());
            printVerdict(errors);

            logger.info("Rule Engine verification completed");
        } catch (Exception e) {
            logger.error("Error during Rule Engine verification", e);
            errors.add("Exception: " + e.getMessage());
            printVerdict(errors);
        }

        if (!errors.isEmpty()) {
            System.exit(1);
        }
    }

    private static void printHeader(int totalRules, int modelCount, int blockingCount, int metadataAwareCount) {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.println("  Rule Engine Diagnostic Report (Descriptor-Based)");
        System.out.println("==========================================================================");
        System.out.println("  Total rules:          " + totalRules);
        System.out.println("  Target models/views:  " + modelCount);
        System.out.println("  Blocking rules:       " + blockingCount);
        System.out.println("  Metadata-aware rules: " + metadataAwareCount);
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("  Evaluation Mode: Happy-path metadata (all auth gates satisfied)");
        System.out.println("                   Metadata-aware rules also shown with empty metadata");
        System.out.println("==========================================================================");
    }

    private static void printRuleLine(Rule rule, String happyResult, String deniedResult) {
        String blockingTag = rule.isBlocking() ? "[BLOCKING]" : "[INFO]";
        String metadataTag = rule.hasMetadataRequirements() ? "[AUTH]" : "";

        System.out.printf("    %-10s %-6s %-35s -> %s%n",
                blockingTag, metadataTag, rule.getName(), happyResult);

        if (deniedResult != null) {
            System.out.printf("    %10s %-6s %-35s -> %s  (no metadata)%n",
                    "", "", "", deniedResult);
        }
    }

    private static void printSummaryTable(List<String[]> rows) {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.println("  Summary Table");
        System.out.println("==========================================================================");
        System.out.printf("  %-35s %-8s %-6s %-6s %-25s %-6s%n",
                "Rule", "Blocking", "Auth", "Meta", "Result", "Status");
        System.out.println("  " + "-".repeat(86));

        for (String[] row : rows) {
            System.out.printf("  %-35s %-8s %-6s %-6s %-25s %-6s%n",
                    truncate(row[0], 35), row[2], row[3], row[4], truncate(row[5], 25), row[6]);
        }
    }

    private static void printFooter(int totalRules, int passCount, int failCount,
                                     int metadataDeniedCount, int missingDescriptors, int authGateFailures) {
        System.out.println();
        System.out.println("==========================================================================");
        System.out.println("  Results (with happy-path metadata)");
        System.out.println("--------------------------------------------------------------------------");
        System.out.println("  Passed:               " + passCount + "/" + totalRules);
        System.out.println("  Failed (conditions):  " + failCount + "/" + totalRules + "  (expected with default protobuf data)");
        System.out.println("  Auth gates tested:    " + metadataDeniedCount + " (denied with empty metadata)");
        if (missingDescriptors > 0) {
            System.out.println("  Missing descriptors:  " + missingDescriptors + "  ** ERROR **");
        }
        if (authGateFailures > 0) {
            System.out.println("  Auth gate failures:   " + authGateFailures + "  ** ERROR — buildDemoMetadata out of sync **");
        }
        System.out.println("==========================================================================");
    }

    static List<String> verifyAuthGates(Map<String, List<Rule>> rulesByModel, MetadataContext happyMetadata) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, List<Rule>> entry : rulesByModel.entrySet()) {
            for (Rule rule : entry.getValue()) {
                if (!rule.hasMetadataRequirements()) {
                    continue;
                }
                Map<String, List<Specification>> reqs = rule.getMetadataRequirements();
                for (Map.Entry<String, List<Specification>> reqEntry : reqs.entrySet()) {
                    String category = reqEntry.getKey();
                    Object context = happyMetadata.get(category);
                    if (context == null) {
                        String msg = "Auth gate FAILED: rule '" + rule.getName() + "' requires category '"
                                + category + "' but buildDemoMetadata() does not provide it";
                        failures.add(msg);
                        continue;
                    }
                    for (Specification spec : reqEntry.getValue()) {
                        if (!spec.isSatisfiedBy(context)) {
                            String msg = "Auth gate FAILED: rule '" + rule.getName() + "' requires "
                                    + category + "." + spec.getField() + " " + spec.getOperator()
                                    + " " + spec.getValue() + " but happy-path metadata does not satisfy it";
                            failures.add(msg);
                        }
                    }
                }
            }
        }
        return failures;
    }

    private static void printVerdict(List<String> errors) {
        System.out.println();
        if (errors.isEmpty()) {
            System.out.println("  VERDICT: PASS — Rule engine pipeline verified successfully");
        } else {
            System.out.println("  VERDICT: FAIL — " + errors.size() + " error(s) detected:");
            for (String error : errors) {
                System.out.println("    - " + error);
            }
        }
        System.out.println();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 2) + "..";
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<Rule>> loadRulesFromSpecs(String specsFile) throws Exception {
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

            // Parse blocking flag (defaults to false)
            Boolean blockingObj = (Boolean) raw.get("blocking");
            boolean blocking = blockingObj != null && blockingObj;

            // Parse requires: block into metadataRequirements
            Map<String, List<Specification>> metadataRequirements = parseRequires(raw.get("requires"));

            Object spec = buildSpec(raw.get("conditions"));
            if (spec == null) {
                continue;
            }

            Rule rule = Rule.builder()
                    .name(ruleName)
                    .spec(spec)
                    .successStatus(successStatus)
                    .failureStatus(failureStatus)
                    .blocking(blocking)
                    .targetType(targetName)
                    .metadataRequirements(metadataRequirements)
                    .build();

            rulesByModel.computeIfAbsent(targetName, k -> new ArrayList<>()).add(rule);
        }
        return rulesByModel;
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<Specification>> parseRequires(Object requiresObj) {
        if (requiresObj == null) {
            return null;
        }
        if (!(requiresObj instanceof Map)) {
            logger.warn("Unexpected requires format: {}", requiresObj.getClass().getName());
            return null;
        }

        Map<String, Object> requiresMap = (Map<String, Object>) requiresObj;
        Map<String, List<Specification>> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : requiresMap.entrySet()) {
            String category = entry.getKey();
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) entry.getValue();
            List<Specification> specs = new ArrayList<>();
            for (Map<String, Object> cond : conditions) {
                specs.add(new Specification(
                        (String) cond.get("field"),
                        (String) cond.get("operator"),
                        cond.get("value")
                ));
            }
            result.put(category, specs);
        }

        return result.isEmpty() ? null : result;
    }

    @SuppressWarnings("unchecked")
    static Object buildSpec(Object conditions) {
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

    /**
     * Builds a happy-path MetadataContext with realistic values that satisfy all
     * requires: blocks in specs.yaml. This enables the demo to exercise the full
     * metadata-aware authorization path.
     *
     * The values are chosen to satisfy all current rules:
     * - AdminAuthenticationRequired: requires sso.authenticated == true, roles.role_level >= 3
     * - ModerationAuthorizationCheck: requires roles.is_admin == true, sso.authenticated == true
     * - UserRoleAssignmentValid: requires roles.role_level >= 4
     */
    static MetadataContext buildDemoMetadata() {
        logger.info("Building happy-path demo metadata for all enabled categories");

        SsoContext sso = SsoContext.builder()
                .authenticated(true)
                .sessionId("demo-session-001")
                .provider("appget-idp")
                .build();

        RolesContext roles = RolesContext.builder()
                .roleName("Admin")
                .roleLevel(5)
                .isAdmin(true)
                .build();

        UserContext user = UserContext.builder()
                .userId("demo-user-001")
                .email("admin@appget.dev")
                .username("demo_admin")
                .build();

        OauthContext oauth = OauthContext.builder()
                .accessToken("demo-token-abc123")
                .scope("read write admin")
                .expiresIn(3600)
                .provider("appget-oauth")
                .build();

        ApiContext api = ApiContext.builder()
                .apiKey("demo-api-key-xyz789")
                .rateLimitTier(3)
                .isActive(true)
                .build();

        MetadataContext metadata = new MetadataContext()
                .with("sso", sso)
                .with("roles", roles)
                .with("user", user)
                .with("oauth", oauth)
                .with("api", api);

        logger.info("Demo metadata loaded with 5 categories: sso, roles, user, oauth, api");
        return metadata;
    }
}
