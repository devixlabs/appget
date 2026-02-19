package dev.appget.conformance;

import dev.appget.codegen.FeatureToSpecsConverter;
import dev.appget.codegen.ModelsToProtoConverter;
import dev.appget.codegen.SQLSchemaParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Conformance tests: validate that parsers produce byte-for-byte identical output
 * to committed golden files. This ensures parser parity across language implementations.
 *
 * Golden files are in src/test/resources/conformance/:
 *   inputs/  - source files (schema.sql, views.sql, metadata.yaml, features/)
 *   expected/ - golden output (models.yaml, specs.yaml, proto/*.proto)
 */
@DisplayName("Conformance Tests")
class ConformanceTest {

    // ---- SQL Parser Parity ----

    @Test
    @DisplayName("SQL parser output matches golden models.yaml")
    void sqlParserMatchesGolden(@TempDir Path tempDir) throws Exception {
        Path schemaInput = getResourcePath("conformance/inputs/schema.sql");
        Path viewsInput = getResourcePath("conformance/inputs/views.sql");
        Path goldenModels = getResourcePath("conformance/expected/models.yaml");

        Path actualOutput = tempDir.resolve("models.yaml");
        new SQLSchemaParser().parseAndGenerate(
                schemaInput.toString(),
                viewsInput.toString(),
                actualOutput.toString()
        );

        String actual = normalize(Files.readString(actualOutput));
        String expected = normalize(Files.readString(goldenModels));

        assertEquals(expected, actual,
                "SQLSchemaParser output does not match golden models.yaml.\n" +
                "This indicates a parser regression. Update the golden file if the change is intentional:\n" +
                "  cp " + actualOutput + " " + goldenModels);
    }

    // ---- Gherkin Parser Parity ----

    @Test
    @DisplayName("Gherkin parser output matches golden specs.yaml")
    void gherkinParserMatchesGolden(@TempDir Path tempDir) throws Exception {
        Path featuresDir = getResourcePath("conformance/inputs/features");
        Path metadataInput = getResourcePath("conformance/inputs/metadata.yaml");
        Path goldenSpecs = getResourcePath("conformance/expected/specs.yaml");

        Path actualOutput = tempDir.resolve("specs.yaml");
        new FeatureToSpecsConverter().convert(
                featuresDir.toString(),
                metadataInput.toString(),
                actualOutput.toString()
        );

        String actual = normalize(Files.readString(actualOutput));
        String expected = normalize(Files.readString(goldenSpecs));

        assertEquals(expected, actual,
                "FeatureToSpecsConverter output does not match golden specs.yaml.\n" +
                "This indicates a parser regression. Update the golden file if the change is intentional:\n" +
                "  cp " + actualOutput + " " + goldenSpecs);
    }

    // ---- Proto Generation Parity ----

    @Test
    @DisplayName("Proto generation output matches golden appget_common.proto")
    void protoGenerationMatchesGoldenCommon(@TempDir Path tempDir) throws Exception {
        runProtoGeneration(tempDir);
        assertProtoMatches(tempDir, "appget_common.proto");
    }

    @Test
    @DisplayName("Proto generation output matches golden appget_models.proto")
    void protoGenerationMatchesGoldenAppgetModels(@TempDir Path tempDir) throws Exception {
        runProtoGeneration(tempDir);
        assertProtoMatches(tempDir, "appget_models.proto");
    }

    @Test
    @DisplayName("Proto generation output matches golden hr_models.proto")
    void protoGenerationMatchesGoldenHrModels(@TempDir Path tempDir) throws Exception {
        runProtoGeneration(tempDir);
        assertProtoMatches(tempDir, "hr_models.proto");
    }

    @Test
    @DisplayName("Proto generation output matches golden finance_models.proto")
    void protoGenerationMatchesGoldenFinanceModels(@TempDir Path tempDir) throws Exception {
        runProtoGeneration(tempDir);
        assertProtoMatches(tempDir, "finance_models.proto");
    }

    @Test
    @DisplayName("Proto generation output matches golden appget_views.proto")
    void protoGenerationMatchesGoldenAppgetViews(@TempDir Path tempDir) throws Exception {
        runProtoGeneration(tempDir);
        assertProtoMatches(tempDir, "appget_views.proto");
    }

    @Test
    @DisplayName("Proto generation output matches golden hr_views.proto")
    void protoGenerationMatchesGoldenHrViews(@TempDir Path tempDir) throws Exception {
        runProtoGeneration(tempDir);
        assertProtoMatches(tempDir, "hr_views.proto");
    }

    @Test
    @DisplayName("Proto generation output matches golden appget_services.proto")
    void protoGenerationMatchesGoldenAppgetServices(@TempDir Path tempDir) throws Exception {
        runProtoGeneration(tempDir);
        assertProtoMatches(tempDir, "appget_services.proto");
    }

    // ---- Structure Validation ----

    @Test
    @DisplayName("Golden models.yaml has schema_version: 1")
    void goldenModelsYamlHasSchemaVersion() throws Exception {
        Path goldenModels = getResourcePath("conformance/expected/models.yaml");
        String content = Files.readString(goldenModels);
        assertTrue(content.startsWith("schema_version: 1"),
                "Golden models.yaml must start with schema_version: 1");
    }

    @Test
    @DisplayName("Golden specs.yaml has schema_version: 1")
    void goldenSpecsYamlHasSchemaVersion() throws Exception {
        Path goldenSpecs = getResourcePath("conformance/expected/specs.yaml");
        String content = Files.readString(goldenSpecs);
        assertTrue(content.startsWith("schema_version: 1"),
                "Golden specs.yaml must start with schema_version: 1");
    }

    @Test
    @DisplayName("Golden models.yaml uses neutral types only")
    void goldenModelsYamlUsesNeutralTypes() throws Exception {
        Path goldenModels = getResourcePath("conformance/expected/models.yaml");
        String content = Files.readString(goldenModels);

        // Should NOT contain Java-specific types
        assertFalse(content.contains("type: String"), "Should not use Java type 'String'");
        assertFalse(content.contains("type: Integer"), "Should not use Java type 'Integer'");
        assertFalse(content.contains("type: Long"), "Should not use Java type 'Long'");
        assertFalse(content.contains("type: BigDecimal"), "Should not use Java type 'BigDecimal'");
        assertFalse(content.contains("type: LocalDate"), "Should not use Java type 'LocalDate'");
        assertFalse(content.contains("type: LocalDateTime"), "Should not use Java type 'LocalDateTime'");
        assertFalse(content.contains("type: Boolean"), "Should not use Java type 'Boolean'");

        // Should contain neutral types
        assertTrue(content.contains("type: string"), "Should use neutral type 'string'");
        assertTrue(content.contains("type: int32"), "Should use neutral type 'int32'");
        assertTrue(content.contains("type: decimal"), "Should use neutral type 'decimal'");
        assertTrue(content.contains("type: date"), "Should use neutral type 'date'");
        assertTrue(content.contains("type: float64"), "Should use neutral type 'float64'");
    }

    @Test
    @DisplayName("Golden specs.yaml uses neutral metadata types")
    void goldenSpecsYamlUsesNeutralMetadataTypes() throws Exception {
        Path goldenSpecs = getResourcePath("conformance/expected/specs.yaml");
        String content = Files.readString(goldenSpecs);

        // Metadata types should be neutral
        assertFalse(content.contains("type: boolean"), "Should not use Java type 'boolean' in metadata");
        assertFalse(content.contains("type: int\n"), "Should not use Java type 'int' in metadata");
        assertTrue(content.contains("type: bool"), "Should use neutral type 'bool'");
        assertTrue(content.contains("type: int32"), "Should use neutral type 'int32'");
    }

    @Test
    @DisplayName("Golden proto files use appget.common.Decimal for decimal fields")
    void goldenProtoFilesUseDecimalType() throws Exception {
        Path hrModels = getResourcePath("conformance/expected/proto/hr_models.proto");
        String content = Files.readString(hrModels);
        assertTrue(content.contains("appget.common.Decimal amount"),
                "hr_models.proto should use appget.common.Decimal for amount field");
        assertTrue(content.contains("appget.common.Decimal budget"),
                "hr_models.proto should use appget.common.Decimal for budget field");
    }

    @Test
    @DisplayName("Golden proto files use google.protobuf.Timestamp for date fields")
    void goldenProtoFilesUseTimestampType() throws Exception {
        Path financeModels = getResourcePath("conformance/expected/proto/finance_models.proto");
        String content = Files.readString(financeModels);
        assertTrue(content.contains("google.protobuf.Timestamp issue_date"),
                "finance_models.proto should use google.protobuf.Timestamp for issue_date field");
    }

    @Test
    @DisplayName("Golden proto uses optional keyword for nullable fields")
    void goldenProtoUsesOptionalForNullableFields() throws Exception {
        Path appgetModels = getResourcePath("conformance/expected/proto/appget_models.proto");
        String content = Files.readString(appgetModels);
        assertTrue(content.contains("optional string country_of_origin"),
                "appget_models.proto should use optional for nullable country_of_origin field");
    }

    // ---- Helpers ----

    private void runProtoGeneration(Path tempDir) throws Exception {
        Path schemaInput = getResourcePath("conformance/inputs/schema.sql");
        Path viewsInput = getResourcePath("conformance/inputs/views.sql");

        // First generate models.yaml from inputs
        Path modelsYaml = tempDir.resolve("models.yaml");
        new SQLSchemaParser().parseAndGenerate(
                schemaInput.toString(),
                viewsInput.toString(),
                modelsYaml.toString()
        );

        // Then generate proto files from models.yaml
        new ModelsToProtoConverter().convert(modelsYaml.toString(), tempDir.toString());
    }

    private void assertProtoMatches(Path tempDir, String protoFileName) throws Exception {
        Path actualProto = tempDir.resolve(protoFileName);
        Path goldenProto = getResourcePath("conformance/expected/proto/" + protoFileName);

        assertTrue(Files.exists(actualProto), "Expected generated file: " + protoFileName);
        assertTrue(Files.exists(goldenProto), "Expected golden file: " + protoFileName);

        String actual = normalize(Files.readString(actualProto));
        String expected = normalize(Files.readString(goldenProto));

        assertEquals(expected, actual,
                "Generated " + protoFileName + " does not match golden file.\n" +
                "This indicates a proto generation regression. Update the golden file if the change is intentional:\n" +
                "  cp " + actualProto + " " + goldenProto);
    }

    private Path getResourcePath(String resourcePath) throws URISyntaxException, IOException {
        URL url = getClass().getClassLoader().getResource(resourcePath);
        assertNotNull(url, "Conformance resource not found: " + resourcePath);
        return Paths.get(url.toURI());
    }

    /**
     * Normalize content for comparison: trim trailing whitespace on each line,
     * normalize line endings, and trim leading/trailing blank lines.
     * This avoids false failures from OS-specific line endings or editor whitespace.
     */
    private String normalize(String content) {
        if (content == null) return "";

        List<String> lines = content.lines()
                .map(String::stripTrailing)
                .toList();

        // Remove trailing blank lines
        int last = lines.size() - 1;
        while (last >= 0 && lines.get(last).isEmpty()) {
            last--;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            sb.append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }
}
