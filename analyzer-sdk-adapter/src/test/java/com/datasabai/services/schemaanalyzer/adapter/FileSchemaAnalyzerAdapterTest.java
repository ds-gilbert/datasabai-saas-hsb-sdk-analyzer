package com.datasabai.services.schemaanalyzer.adapter;

import com.datasabai.hsb.sdk.core.SdkContext;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.SchemaGenerationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for FileSchemaAnalyzerAdapter.
 */
class FileSchemaAnalyzerAdapterTest {

    private FileSchemaAnalyzerAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FileSchemaAnalyzerAdapter();
    }

    @Test
    void shouldHaveCorrectName() {
        assertThat(adapter.name()).isEqualTo("file-schema-analyzer");
    }

    @Test
    void shouldHaveDescription() {
        assertThat(adapter.description()).isNotBlank();
        assertThat(adapter.description()).contains("schema");
        assertThat(adapter.description()).contains("CSV");
        assertThat(adapter.description()).contains("JSON");
    }

    @Test
    void shouldHaveVersion() {
        assertThat(adapter.version()).isNotBlank();
    }

    @Test
    void shouldHaveInputType() {
        assertThat(adapter.inputType()).isEqualTo(FileAnalysisRequest.class);
    }

    @Test
    void shouldHaveOutputType() {
        assertThat(adapter.outputType()).isEqualTo(SchemaGenerationResult.class);
    }

    @Test
    void shouldHaveConfigurationSchema() {
        Map<String, String> schema = adapter.configurationSchema();

        assertThat(schema).isNotEmpty();
        assertThat(schema).containsKeys(
                "detectArrays",
                "parserOptions.delimiter",
                "parserOptions.hasHeader",
                "parserOptions.strictMode",
                "parserOptions.fieldDefinitions",
                "parserOptions.tagValuePairs"
        );
        // Verify NO BeanIO config
        assertThat(schema).doesNotContainKey("optimizeForBeanIO");
    }

    @Test
    void shouldExecuteCsvAnalysis() throws Exception {
        String csvContent = """
                ID,Name,Price
                1,Product A,19.99
                2,Product B,29.99
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Product")
                .build();

        SdkContext context = SdkContext.builder().build();

        SchemaGenerationResult result = adapter.execute(request, context);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSchemaName()).isEqualTo("Product");
        assertThat(result.getJsonSchema()).isNotNull();
    }

    @Test
    void shouldExecuteJsonAnalysis() throws Exception {
        String jsonContent = """
                {
                    "id": 123,
                    "name": "Product A",
                    "price": 19.99
                }
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.JSON)
                .fileContent(jsonContent)
                .schemaName("Product")
                .build();

        SdkContext context = SdkContext.builder().build();

        SchemaGenerationResult result = adapter.execute(request, context);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSchemaName()).isEqualTo("Product");
        assertThat(result.getJsonSchema()).isNotNull();
    }

    @Test
    void shouldExecuteFixedLengthAnalysis() throws Exception {
        String descriptor = """
                [
                  {"name": "id", "start": 0, "length": 5, "type": "integer"},
                  {"name": "name", "start": 5, "length": 15, "type": "string"}
                ]
                """;

        String fileContent = """
                00001Product A
                00002Product B
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.FIXED_LENGTH)
                .fileContent(fileContent)
                .schemaName("Product")
                .parserOption("fieldDefinitions", descriptor)
                .build();

        SdkContext context = SdkContext.builder().build();

        SchemaGenerationResult result = adapter.execute(request, context);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSchemaName()).isEqualTo("Product");
        assertThat(result.getJsonSchema()).isNotNull();
    }

    @Test
    void shouldExecuteVariableLengthAnalysis() throws Exception {
        String fileContent = """
                001|Product A|19.99
                002|Product B|29.99
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.VARIABLE_LENGTH)
                .fileContent(fileContent)
                .schemaName("Product")
                .build();

        SdkContext context = SdkContext.builder().build();

        SchemaGenerationResult result = adapter.execute(request, context);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSchemaName()).isEqualTo("Product");
        assertThat(result.getJsonSchema()).isNotNull();
    }

    @Test
    void shouldApplyConfigurationFromContext() throws Exception {
        String csvContent = "ID,Name\n1,Product A\n2,Product B";

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Product")
                .build();

        SdkContext context = SdkContext.builder()
                .config("detectArrays", "false")
                .config("optimizeForBeanIO", "false")
                .config("parserOptions.hasHeader", "true")
                .config("parserOptions.delimiter", ",")
                .build();

        SchemaGenerationResult result = adapter.execute(request, context);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldApplyParserOptionsFromContext() throws Exception {
        String jsonContent = """
                {
                    // This is a comment
                    "id": 123,
                    "name": "Product A"
                }
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.JSON)
                .fileContent(jsonContent)
                .schemaName("Product")
                .build();

        SdkContext context = SdkContext.builder()
                .config("parserOptions.allowComments", "true")
                .build();

        SchemaGenerationResult result = adapter.execute(request, context);

        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldThrowExceptionForNullInput() {
        SdkContext context = SdkContext.builder().build();

        assertThatThrownBy(() -> adapter.execute(null, context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Input request cannot be null");
    }

    @Test
    void shouldThrowExceptionForNullAnalyzerInConstructor() {
        assertThatThrownBy(() -> new FileSchemaAnalyzerAdapter(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FileSchemaAnalyzer cannot be null");
    }

    @Test
    void shouldProvideAccessToAnalyzer() {
        assertThat(adapter.getAnalyzer()).isNotNull();
    }
}
