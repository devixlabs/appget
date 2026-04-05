package dev.appget.codegen;

/**
 * Strategy interface for server code emission.
 *
 * Each method corresponds to exactly one output file that AppServerGenerator
 * produces. The generator calls these methods and writes the returned strings
 * to disk; the emitter is responsible only for generating the file content.
 *
 * Implementations follow the same grouping used by AppServerGenerator:
 * <ul>
 *   <li><b>Group A — Pure Infrastructure</b>: files with no dependency on
 *       domain models or specs (Application class, YAML config, build file, etc.)</li>
 *   <li><b>Group B — Middleware and DTOs</b>: files that depend on specs.yaml
 *       (MetadataExtractor, RuleService, SpecificationRegistry) plus shared
 *       DTO and exception classes.</li>
 *   <li><b>Group C — Per-Entity CRUD</b>: files generated once per model
 *       (repository interface, in-memory repository, service, controller) and
 *       per-view read-only variants.</li>
 * </ul>
 *
 * All {@code String basePackage} parameters receive the fully-qualified root
 * package of the generated server (e.g., {@code "dev.appget.server"}).
 */
public interface ServerEmitter {

    // -------------------------------------------------------------------------
    // Group A: Pure Infrastructure
    // -------------------------------------------------------------------------

    /**
     * Emits the Spring Boot entry-point class ({@code Application.java}).
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code Application.java}
     */
    String emitApplicationClass(String basePackage);

    /**
     * Emits the Spring Boot configuration file ({@code application.yaml}).
     *
     * @return complete YAML content for {@code application.yaml}
     */
    String emitApplicationYaml();

    /**
     * Emits the Log4j2 properties configuration file ({@code log4j2.properties}).
     *
     * @return complete properties file content
     */
    String emitLog4j2Properties();

    /**
     * Emits the Gradle build file ({@code build.gradle}) for the generated server.
     *
     * @return complete Groovy build script content
     */
    String emitBuildGradle();

    /**
     * Emits the Jackson {@code DecimalModule} class for BigDecimal serialization
     * ({@code DecimalModule.java}).
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code DecimalModule.java}
     */
    String emitDecimalModule(String basePackage);

    // -------------------------------------------------------------------------
    // Group B: Middleware
    // -------------------------------------------------------------------------

    /**
     * Emits {@code MetadataExtractor.java}, which reads typed HTTP headers into
     * a {@code MetadataContext} using the enabled metadata categories and their
     * field definitions.
     *
     * @param basePackage root package of the generated server
     * @param ctx         metadata categories and field definitions
     * @return complete Java source for {@code MetadataExtractor.java}
     */
    String emitMetadataExtractor(String basePackage, MetadataEmitContext ctx);

    /**
     * Emits {@code RuleService.java}, which evaluates business rules against
     * model instances using pre-compiled specification classes looked up via
     * {@code SpecificationRegistry}.
     *
     * @param basePackage root package of the generated server
     * @param ctx         rule list, blocking map, and target map
     * @return complete Java source for {@code RuleService.java}
     */
    String emitRuleService(String basePackage, RuleEmitContext ctx);

    /**
     * Emits {@code SpecificationRegistry.java}, which registers and exposes all
     * compiled specification class instances keyed by rule name.
     *
     * @param basePackage root package of the generated server
     * @param ctx         rule list, blocking map, and target map
     * @return complete Java source for {@code SpecificationRegistry.java}
     */
    String emitSpecificationRegistry(String basePackage, RuleEmitContext ctx);

    /**
     * Emits {@code GlobalExceptionHandler.java} ({@code @ControllerAdvice}) with
     * handlers for rule violations, not-found errors, metadata parsing errors,
     * malformed JSON, and general exceptions.
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code GlobalExceptionHandler.java}
     */
    String emitGlobalExceptionHandler(String basePackage);

    // -------------------------------------------------------------------------
    // Group B: DTOs
    // -------------------------------------------------------------------------

    /**
     * Emits {@code RuleAwareResponse.java}, the generic API response wrapper that
     * includes rule evaluation results alongside the entity payload.
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code RuleAwareResponse.java}
     */
    String emitRuleAwareResponse(String basePackage);

    /**
     * Emits {@code RuleEvaluationResult.java}, which aggregates the list of rule
     * outcomes and the overall pass/fail flag.
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code RuleEvaluationResult.java}
     */
    String emitRuleEvaluationResult(String basePackage);

    /**
     * Emits {@code RuleOutcome.java}, representing the result of evaluating a
     * single rule (rule name, status string, satisfied flag).
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code RuleOutcome.java}
     */
    String emitRuleOutcome(String basePackage);

    /**
     * Emits {@code ErrorResponse.java}, the standard error envelope returned by
     * {@code GlobalExceptionHandler} (timestamp, status, message, path).
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code ErrorResponse.java}
     */
    String emitErrorResponse(String basePackage);

    // -------------------------------------------------------------------------
    // Group B: Exceptions
    // -------------------------------------------------------------------------

    /**
     * Emits {@code RuleViolationException.java}, thrown when a blocking rule is
     * unsatisfied; results in a 422 Unprocessable Entity response.
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code RuleViolationException.java}
     */
    String emitRuleViolationException(String basePackage);

    /**
     * Emits {@code ResourceNotFoundException.java}, thrown when a requested
     * entity does not exist; results in a 404 Not Found response.
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code ResourceNotFoundException.java}
     */
    String emitResourceNotFoundException(String basePackage);

    /**
     * Emits {@code MetadataParsingException.java}, thrown when a metadata HTTP
     * header contains a value that cannot be parsed to the expected type; results
     * in a 400 Bad Request response.
     *
     * @param basePackage root package of the generated server
     * @return complete Java source for {@code MetadataParsingException.java}
     */
    String emitMetadataParsingException(String basePackage);

    // -------------------------------------------------------------------------
    // Group C: Per-Entity CRUD (models)
    // -------------------------------------------------------------------------

    /**
     * Emits the repository interface for a model entity
     * (e.g., {@code UsersRepository.java}).
     * Declares CRUD operations: findAll, findById, save, deleteById.
     *
     * @param basePackage root package of the generated server
     * @param ctx         entity metadata (name, pascal name, PK fields, etc.)
     * @return complete Java source for the repository interface
     */
    String emitRepositoryInterface(String basePackage, EntityContext ctx);

    /**
     * Emits the in-memory {@code ConcurrentHashMap}-backed repository implementation
     * for a model entity (e.g., {@code InMemoryUsersRepository.java}).
     *
     * @param basePackage root package of the generated server
     * @param ctx         entity metadata
     * @return complete Java source for the in-memory repository
     */
    String emitInMemoryRepository(String basePackage, EntityContext ctx);

    /**
     * Emits the service class for a model entity (e.g., {@code UsersService.java}).
     * Orchestrates rule evaluation via {@code RuleService} before persistence.
     *
     * @param basePackage root package of the generated server
     * @param ctx         entity metadata
     * @return complete Java source for the service class
     */
    String emitService(String basePackage, EntityContext ctx);

    /**
     * Emits the REST controller for a model entity
     * (e.g., {@code UsersController.java}).
     * Generates POST, GET (list + by-id), PUT, and DELETE endpoints.
     *
     * @param basePackage root package of the generated server
     * @param ctx         entity metadata
     * @return complete Java source for the REST controller
     */
    String emitController(String basePackage, EntityContext ctx);

    // -------------------------------------------------------------------------
    // Group C: Per-View (read-only)
    // -------------------------------------------------------------------------

    /**
     * Emits the read-only repository interface for a view entity
     * (e.g., {@code PostDetailViewRepository.java}).
     * Declares only save (for seeding), findById, and findAll operations.
     *
     * @param basePackage root package of the generated server
     * @param ctx         view metadata ({@code ctx.isView} will be {@code true})
     * @return complete Java source for the view repository interface
     */
    String emitViewRepositoryInterface(String basePackage, EntityContext ctx);

    /**
     * Emits the in-memory {@code ConcurrentHashMap}-backed repository implementation
     * for a view entity (e.g., {@code InMemoryPostDetailViewRepository.java}).
     * Read-only view storage with save (for seeding), findById, and findAll.
     *
     * @param basePackage root package of the generated server
     * @param ctx         view metadata ({@code ctx.isView} will be {@code true})
     * @return complete Java source for the in-memory view repository
     */
    String emitInMemoryViewRepository(String basePackage, EntityContext ctx);

    /**
     * Emits the service class for a view entity
     * (e.g., {@code PostDetailViewService.java}).
     * GET-only operations — no rule evaluation, no metadata context.
     *
     * @param basePackage root package of the generated server
     * @param ctx         view metadata
     * @return complete Java source for the view service class
     */
    String emitViewService(String basePackage, EntityContext ctx);

    /**
     * Emits the REST controller for a view entity
     * (e.g., {@code PostDetailViewController.java}).
     * Generates GET-only endpoints ({@code /views/{kebab-name}} list and
     * {@code /views/{kebab-name}/{id}} by id). No POST, PUT, or DELETE.
     *
     * @param basePackage root package of the generated server
     * @param ctx         view metadata
     * @return complete Java source for the view controller
     */
    String emitViewController(String basePackage, EntityContext ctx);
}
