package dev.appget.naming;

/**
 * Java naming conventions for field resolution.
 * Pure static functions — no instances, no state, thread-safe by design.
 */
public final class JavaNaming {
    private JavaNaming() {}

    public static String toFieldAccessor(String snakeCaseField) {
        if (snakeCaseField == null || snakeCaseField.isEmpty() || !snakeCaseField.contains("_")) {
            return snakeCaseField;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (int i = 0; i < snakeCaseField.length(); i++) {
            char c = snakeCaseField.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
