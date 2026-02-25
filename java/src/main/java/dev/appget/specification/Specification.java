package dev.appget.specification;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
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
        if (target instanceof MessageOrBuilder) {
            MessageOrBuilder mob = (MessageOrBuilder) target;
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
            Object actual = getFieldValueViaReflection(target, field);
            logger.debug("Retrieved field value via reflection: {} = {}", field, actual);
            boolean result = compare(actual, value, operator);
            logger.debug("Specification evaluation result: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("Error evaluating specification for field: {}", field, e);
            return false;
        }
    }

    private <T> Object getFieldValueViaReflection(T target, String fieldName) throws Exception {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        Method getter;
        try {
            getter = target.getClass().getMethod(getterName);
        } catch (NoSuchMethodException e) {
            logger.debug("Getter method {} not found, trying is* prefix", getterName);
            try {
                getter = target.getClass().getMethod("is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
            } catch (NoSuchMethodException e2) {
                // Lombok boolean fields starting with "is" (e.g., isAdmin) generate isAdmin() directly
                if (fieldName.length() > 2 && fieldName.startsWith("is") && Character.isUpperCase(fieldName.charAt(2))) {
                    logger.debug("Trying direct field name as getter: {}", fieldName);
                    getter = target.getClass().getMethod(fieldName);
                } else {
                    throw e2;
                }
            }
        }
        return getter.invoke(target);
    }

    private boolean compare(Object actual, Object expected, String operator) {
        if (actual == null || expected == null) {
            return compareNulls(actual, expected, operator);
        }

        // Handle protobuf Decimal message (appget.common.Decimal)
        // Detect by message type name to avoid hard dependency on the generated class
        if (actual instanceof Message) {
            Message msg = (Message) actual;
            if ("Decimal".equals(msg.getDescriptorForType().getName())) {
                BigDecimal bd = decimalMessageToBigDecimal(msg);
                if (bd != null) {
                    return compareBigDecimals(bd, expected, operator);
                }
            }
        }

        if (actual instanceof BigDecimal || expected instanceof BigDecimal) {
            return compareBigDecimals(actual, expected, operator);
        }

        if (actual instanceof Boolean || expected instanceof Boolean) {
            return compareBooleans(actual, expected, operator);
        }

        if (actual instanceof Number && expected instanceof Number) {
            return compareNumbers(actual, expected, operator);
        }

        return compareStrings(actual, expected, operator);
    }

    /**
     * Convert an appget.common.Decimal protobuf message to BigDecimal.
     * The Decimal message has: bytes unscaled = 1; int32 scale = 2;
     */
    private BigDecimal decimalMessageToBigDecimal(Message decimalMsg) {
        try {
            Descriptors.Descriptor desc = decimalMsg.getDescriptorForType();
            Descriptors.FieldDescriptor unscaledField = desc.findFieldByName("unscaled");
            Descriptors.FieldDescriptor scaleField = desc.findFieldByName("scale");
            if (unscaledField == null || scaleField == null) {
                return null;
            }
            Object unscaledObj = decimalMsg.getField(unscaledField);
            Object scaleObj = decimalMsg.getField(scaleField);
            if (!(unscaledObj instanceof ByteString) || !(scaleObj instanceof Integer)) {
                return null;
            }
            ByteString unscaledBytes = (ByteString) unscaledObj;
            int scale = (Integer) scaleObj;
            if (unscaledBytes.isEmpty()) {
                return BigDecimal.ZERO.setScale(scale);
            }
            BigInteger unscaled = new BigInteger(unscaledBytes.toByteArray());
            return new BigDecimal(unscaled, scale);
        } catch (Exception e) {
            logger.warn("Failed to convert Decimal message to BigDecimal: {}", e.getMessage());
            return null;
        }
    }

    private boolean compareNulls(Object actual, Object expected, String operator) {
        if (operator.equals("==") || operator.equals("equals")) {
            return actual == expected;
        } else if (operator.equals("!=")) {
            return actual != expected;
        }
        return false;
    }

    private boolean compareBigDecimals(Object actual, Object expected, String operator) {
        BigDecimal actualBD = toBigDecimal(actual);
        BigDecimal expectedBD = toBigDecimal(expected);
        if (actualBD != null && expectedBD != null) {
            int cmp = actualBD.compareTo(expectedBD);
            return evalComparison(cmp, operator);
        }
        return false;
    }

    private boolean compareBooleans(Object actual, Object expected, String operator) {
        boolean a = toBoolean(actual);
        boolean b = toBoolean(expected);
        if (operator.equals("==") || operator.equals("equals")) {
            return a == b;
        } else if (operator.equals("!=")) {
            return a != b;
        }
        return false;
    }

    private boolean compareNumbers(Object actual, Object expected, String operator) {
        double a = ((Number) actual).doubleValue();
        double b = ((Number) expected).doubleValue();
        int cmp = Double.compare(a, b);
        return evalComparison(cmp, operator);
    }

    private boolean compareStrings(Object actual, Object expected, String operator) {
        String actualStr = actual.toString();
        String expectedStr = expected.toString();
        if (operator.equals("==") || operator.equals("equals")) {
            return actualStr.equals(expectedStr);
        } else if (operator.equals("!=")) {
            return !actualStr.equals(expectedStr);
        } else if (operator.equals(">")) {
            return actualStr.compareTo(expectedStr) > 0;
        } else if (operator.equals("<")) {
            return actualStr.compareTo(expectedStr) < 0;
        } else if (operator.equals(">=")) {
            return actualStr.compareTo(expectedStr) >= 0;
        } else if (operator.equals("<=")) {
            return actualStr.compareTo(expectedStr) <= 0;
        }
        return false;
    }

    private boolean evalComparison(int cmp, String operator) {
        if (operator.equals(">")) {
            return cmp > 0;
        } else if (operator.equals("<")) {
            return cmp < 0;
        } else if (operator.equals(">=")) {
            return cmp >= 0;
        } else if (operator.equals("<=")) {
            return cmp <= 0;
        } else if (operator.equals("==") || operator.equals("equals")) {
            return cmp == 0;
        } else if (operator.equals("!=")) {
            return cmp != 0;
        }
        return false;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        if (obj instanceof Number) {
            Number n = (Number) obj;
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean toBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        }
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
