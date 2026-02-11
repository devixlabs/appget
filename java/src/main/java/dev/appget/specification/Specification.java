package dev.appget.specification;

import com.google.protobuf.Descriptors;
import com.google.protobuf.MessageOrBuilder;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Specification {
    private static final Logger logger = LogManager.getLogger(Specification.class);
    private String field;
    private String operator;
    private Object value;

    public Specification(String field, String operator, Object value) {
        logger.debug("Creating Specification: field={}, operator={}, value={}", field, operator, value);
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public <T> boolean isSatisfiedBy(T target) {
        logger.debug("Entering isSatisfiedBy for target class: {}", target.getClass().getName());
        if (target instanceof MessageOrBuilder mob) {
            return isSatisfiedByDescriptor(mob);
        }
        return isSatisfiedByReflection(target);
    }

    private boolean isSatisfiedByDescriptor(MessageOrBuilder target) {
        Descriptors.Descriptor descriptor = target.getDescriptorForType();
        Descriptors.FieldDescriptor fieldDesc = descriptor.findFieldByName(field);

        if (fieldDesc == null) {
            logger.error("Field '{}' not found in message type '{}'", field, descriptor.getName());
            return false;
        }

        Object actual = target.getField(fieldDesc);
        logger.debug("Retrieved field value via descriptor: {} = {}", field, actual);
        boolean result = compare(actual, value, operator);
        logger.debug("Specification evaluation result: {}", result);
        return result;
    }

    private <T> boolean isSatisfiedByReflection(T target) {
        try {
            String getterName = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
            Method getter;
            try {
                getter = target.getClass().getMethod(getterName);
            } catch (NoSuchMethodException e) {
                logger.debug("Getter method {} not found, trying is* prefix", getterName);
                getter = target.getClass().getMethod("is" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
            }
            Object actual = getter.invoke(target);
            logger.debug("Retrieved field value via reflection: {} = {}", field, actual);
            boolean result = compare(actual, value, operator);
            logger.debug("Specification evaluation result: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("Error evaluating specification for field: {}", field, e);
            return false;
        }
    }

    private boolean compare(Object actual, Object expected, String operator) {
        if (actual == null || expected == null) {
            if ("==".equals(operator) || "equals".equals(operator)) {
                return actual == expected;
            } else if ("!=".equals(operator)) {
                return actual != expected;
            }
            return false;
        }

        // BigDecimal comparison
        if (actual instanceof BigDecimal || expected instanceof BigDecimal) {
            BigDecimal actualBD = toBigDecimal(actual);
            BigDecimal expectedBD = toBigDecimal(expected);
            if (actualBD != null && expectedBD != null) {
                int cmp = actualBD.compareTo(expectedBD);
                return evalComparison(cmp, operator);
            }
            return false;
        }

        // Boolean comparison
        if (actual instanceof Boolean || expected instanceof Boolean) {
            boolean a = toBoolean(actual);
            boolean b = toBoolean(expected);
            return switch (operator) {
                case "==", "equals" -> a == b;
                case "!=" -> a != b;
                default -> false;
            };
        }

        // Number comparison (int, long, double, Integer, Long, Double)
        if (actual instanceof Number && expected instanceof Number) {
            double a = ((Number) actual).doubleValue();
            double b = ((Number) expected).doubleValue();
            int cmp = Double.compare(a, b);
            return evalComparison(cmp, operator);
        }

        // String / fallback comparison
        String actualStr = actual.toString();
        String expectedStr = expected.toString();
        return switch (operator) {
            case "==" , "equals" -> actualStr.equals(expectedStr);
            case "!=" -> !actualStr.equals(expectedStr);
            case ">" -> actualStr.compareTo(expectedStr) > 0;
            case "<" -> actualStr.compareTo(expectedStr) < 0;
            case ">=" -> actualStr.compareTo(expectedStr) >= 0;
            case "<=" -> actualStr.compareTo(expectedStr) <= 0;
            default -> false;
        };
    }

    private boolean evalComparison(int cmp, String operator) {
        return switch (operator) {
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case "==", "equals" -> cmp == 0;
            case "!=" -> cmp != 0;
            default -> false;
        };
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal bd) return bd;
        if (obj instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean toBoolean(Object obj) {
        if (obj instanceof Boolean b) return b;
        return Boolean.parseBoolean(obj.toString());
    }

    public String getField() { return field; }
    public String getOperator() { return operator; }
    public Object getValue() { return value; }

    @Override
    public String toString() {
        return "field '" + field + "' " + operator + " " + value;
    }
}
