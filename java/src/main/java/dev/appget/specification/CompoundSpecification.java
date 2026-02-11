package dev.appget.specification;

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CompoundSpecification {

    private static final Logger logger = LogManager.getLogger(CompoundSpecification.class);

    public enum Logic { AND, OR }

    private final Logic logic;
    private final List<Specification> specifications;

    public CompoundSpecification(Logic logic, List<Specification> specifications) {
        logger.debug("Creating CompoundSpecification with logic={} and {} specifications", logic, specifications.size());
        this.logic = logic;
        this.specifications = specifications;
    }

    public <T> boolean isSatisfiedBy(T target) {
        logger.debug("Evaluating compound specification with logic={} for target: {}", logic, target.getClass().getName());
        if (logic == Logic.AND) {
            logger.debug("Using AND logic with {} specifications", specifications.size());
            boolean result = specifications.stream().allMatch(s -> s.isSatisfiedBy(target));
            logger.debug("AND evaluation result: {}", result);
            return result;
        } else {
            logger.debug("Using OR logic with {} specifications", specifications.size());
            boolean result = specifications.stream().anyMatch(s -> s.isSatisfiedBy(target));
            logger.debug("OR evaluation result: {}", result);
            return result;
        }
    }

    public Logic getLogic() { return logic; }
    public List<Specification> getSpecifications() { return specifications; }

    @Override
    public String toString() {
        return logic + " compound: " + specifications;
    }
}
