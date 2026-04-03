package dev.appget.codegen;

import java.util.List;
import java.util.Map;

/**
 * Carries per-entity (model or view) information from AppServerGenerator to a ServerEmitter.
 *
 * Fields use the same conventions as AppServerGenerator's ModelInfo inner class:
 * package-private, no getters/setters, plain Java. snake_case names come from
 * models.yaml; all derived forms (pascalName, resourcePath, etc.) are pre-computed
 * by the generator so the emitter performs no naming logic.
 */
public class EntityContext {

    /** snake_case entity name as stored in models.yaml (e.g., "users", "post_detail_view"). */
    String name;

    /** PascalCase entity name for use in class/type references (e.g., "Users", "PostDetailView"). */
    String pascalName;

    /** Domain assignment from the SQL comment (e.g., "auth", "social"). */
    String domain;

    /** Fully-qualified Java package for this entity (e.g., "dev.appget.auth"). */
    String namespace;

    /** Raw field definitions from models.yaml, each map contains at minimum "name" and "type". */
    List<Map<String, Object>> fields;

    /** True when this entity is a database view (read-only); false for regular tables. */
    boolean isView;

    /** True when the entity has more than one primary key field (composite PK). */
    boolean compositeKey;

    /** True when the entity has an explicit "id" field (single-column primary key pattern). */
    boolean hasIdField;

    /**
     * Primary key field definitions sorted by primary_key_position.
     * Each map contains at minimum "name" and "type".
     * Corresponds to the output of AppServerGenerator.getPrimaryKeyFields().
     */
    List<Map<String, Object>> primaryKeyFields;

    /**
     * Kebab-case URL path segment for this entity.
     * Models: "users" → "users", "user_role" → "user-role".
     * Views: "post_detail_view" → strip "_view" → "post_detail" → "post-detail".
     */
    String resourcePath;

    /** Method parameter list for service/repository IDs (no annotations). e.g., "String id" or "String teamId, String userId". */
    String idParams;

    /** Argument list for passing IDs between methods. e.g., "id" or "teamId, userId". */
    String idArgs;

    /** Composite key expression for in-memory lookup from method params. e.g., "teamId + \":\" + userId". */
    String compositeKeyExpr;

    /** Composite key expression extracted from an entity instance for save. e.g., "entity.getTeamId() + \":\" + entity.getUserId()". */
    String entityCompositeKeyExpr;

    /** Log-format pattern string for key fields. e.g., "id: {}" or "teamId: {}, userId: {}". */
    String logPattern;

    /** Log argument list matching the logPattern placeholders. e.g., "id" or "teamId, userId". */
    String logArgs;

    /** ResourceNotFoundException message expression. e.g., "\"users not found: \" + id". */
    String notFoundMsg;

    /** URL path variable segment for @GetMapping etc. e.g., "/{id}" or "/{teamId}/{userId}". */
    String pathVarSegment;

    /** Spring @PathVariable parameter list. e.g., "@PathVariable String id" or "@PathVariable String teamId, @PathVariable String userId". */
    String pathVarParams;

    /**
     * Constructs an EntityContext with all fields supplied by the generator.
     *
     * @param name                    snake_case entity name
     * @param pascalName              PascalCase entity name
     * @param domain                  domain name
     * @param namespace               fully-qualified Java package
     * @param fields                  field definitions from models.yaml
     * @param isView                  true if this entity is a view
     * @param compositeKey            true if 2+ primary key fields
     * @param hasIdField              true if an explicit "id" field exists
     * @param primaryKeyFields        sorted PK field definitions
     * @param resourcePath            kebab-case URL path segment
     * @param idParams                method param list for IDs (no annotations)
     * @param idArgs                  argument list for passing IDs
     * @param compositeKeyExpr        key expression from method params
     * @param entityCompositeKeyExpr  key expression from entity fields
     * @param logPattern              log format pattern for key fields
     * @param logArgs                 log argument list for key fields
     * @param notFoundMsg             ResourceNotFoundException message expression
     * @param pathVarSegment          URL path variable segment
     * @param pathVarParams           Spring @PathVariable parameter list
     */
    EntityContext(
            String name,
            String pascalName,
            String domain,
            String namespace,
            List<Map<String, Object>> fields,
            boolean isView,
            boolean compositeKey,
            boolean hasIdField,
            List<Map<String, Object>> primaryKeyFields,
            String resourcePath,
            String idParams,
            String idArgs,
            String compositeKeyExpr,
            String entityCompositeKeyExpr,
            String logPattern,
            String logArgs,
            String notFoundMsg,
            String pathVarSegment,
            String pathVarParams) {
        this.name = name;
        this.pascalName = pascalName;
        this.domain = domain;
        this.namespace = namespace;
        this.fields = fields;
        this.isView = isView;
        this.compositeKey = compositeKey;
        this.hasIdField = hasIdField;
        this.primaryKeyFields = primaryKeyFields;
        this.resourcePath = resourcePath;
        this.idParams = idParams;
        this.idArgs = idArgs;
        this.compositeKeyExpr = compositeKeyExpr;
        this.entityCompositeKeyExpr = entityCompositeKeyExpr;
        this.logPattern = logPattern;
        this.logArgs = logArgs;
        this.notFoundMsg = notFoundMsg;
        this.pathVarSegment = pathVarSegment;
        this.pathVarParams = pathVarParams;
    }
}
