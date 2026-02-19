package dev.appget.codegen;

/**
 * Interface for per-language type registries.
 *
 * Each language implementation provides its own TypeRegistry that maps
 * language-neutral types (from models.yaml) to language-specific representations.
 *
 * Neutral type set: string, int32, int64, float64, bool, date, datetime, decimal
 */
public interface TypeRegistry {

    /**
     * Map a neutral type to its proto type.
     * Example: "decimal" -> "appget.common.Decimal"
     */
    String neutralToProto(String neutralType);

    /**
     * Map a neutral type to its OpenAPI [type, format] pair.
     * The format element may be null if no format applies.
     * Example: "int32" -> ["integer", "int32"]
     */
    String[] neutralToOpenApi(String neutralType);

    /**
     * Map a neutral type to its Java type.
     * Example: "decimal" -> "BigDecimal"
     */
    String neutralToJava(String neutralType);

    /**
     * Map a neutral type to its Java type, considering nullability.
     * Primitive types use boxed forms when nullable.
     * Example: "int32" + nullable=true -> "Integer"
     */
    String neutralToJava(String neutralType, boolean nullable);

    /**
     * Return true if this neutral type maps to google.protobuf.Timestamp.
     */
    boolean isTimestampType(String neutralType);

    /**
     * Return true if this neutral type requires the Decimal message from appget_common.proto.
     */
    boolean isDecimalType(String neutralType);
}
