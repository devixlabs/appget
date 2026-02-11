package dev.appget.specification;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MetadataContext {
    private static final Logger logger = LogManager.getLogger(MetadataContext.class);
    private final Map<String, Object> categories = new HashMap<>();

    public MetadataContext with(String category, Object contextObj) {
        logger.debug("Adding metadata category: {} with type: {}", category,
                contextObj != null ? contextObj.getClass().getName() : "null");
        categories.put(category, contextObj);
        return this;
    }

    public Object get(String category) {
        Object value = categories.get(category);
        logger.debug("Retrieving metadata category: {}, found: {}", category, value != null);
        return value;
    }

    public boolean has(String category) {
        boolean result = categories.containsKey(category);
        logger.debug("Checking if metadata category exists: {} = {}", category, result);
        return result;
    }
}
