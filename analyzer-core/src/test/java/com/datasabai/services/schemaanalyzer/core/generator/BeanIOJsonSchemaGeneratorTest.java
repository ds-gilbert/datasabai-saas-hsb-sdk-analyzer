package com.datasabai.services.schemaanalyzer.core.generator;

import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.StructureElement;
import com.datasabai.services.schemaanalyzer.core.parser.CsvFileParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BeanIOJsonSchemaGenerator}.
 */
public class BeanIOJsonSchemaGeneratorTest {

    private CsvFileParser parser;
    private BeanIOJsonSchemaGenerator schemaGenerator;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        parser = new CsvFileParser();
        schemaGenerator = new BeanIOJsonSchemaGenerator();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testGenerateBeanIOSchema_FromNotilusCSV() throws IOException, AnalyzerException {
        // Read the actual Notilus CSV file
        Path testFilePath = Paths.get("..", "testFiles", "Notilus_1_6600_17.csv");

        if (!Files.exists(testFilePath)) {
            System.out.println("Skipping test - file not found: " + testFilePath);
            return;
        }

        String csvContent = Files.readString(testFilePath);

        // Configure parser for semicolon delimiter
        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        // Create request
        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("CSV_ACCOUNTING_CANONICAL")
                .parserOptions(parserOptions)
                .build();

        // Parse CSV structure
        StructureElement structure = parser.parse(request);

        // Generate BeanIO-optimized JSON Schema
        String jsonSchemaStr = schemaGenerator.generateSchemaAsString(structure, request);
        JsonNode jsonSchema = objectMapper.readTree(jsonSchemaStr);

        // Print the generated JSON Schema
        System.out.println("Generated BeanIO-Optimized JSON Schema:");
        System.out.println("==========================================");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));

        // Verify JSON Schema structure
        assertThat(jsonSchema.has("$schema")).isTrue();
        assertThat(jsonSchema.get("$schema").asText()).isEqualTo("http://json-schema.org/draft-07/schema#");
        assertThat(jsonSchema.has("title")).isTrue();
        assertThat(jsonSchema.get("title").asText()).isEqualTo("CSV_ACCOUNTING_CANONICAL");
        assertThat(jsonSchema.has("type")).isTrue();
        assertThat(jsonSchema.get("type").asText()).isEqualTo("object");

        // Verify BeanIO config
        assertThat(jsonSchema.has("x-beanio-config")).isTrue();
        JsonNode beanioConfig = jsonSchema.get("x-beanio-config");
        assertThat(beanioConfig.get("format").asText()).isEqualTo("csv");
        assertThat(beanioConfig.get("delimiter").asText()).isEqualTo(";");
        assertThat(beanioConfig.get("recordName").asText()).isEqualTo("csvAccountingCanonical");

        // Verify segments exist
        assertThat(jsonSchema.has("properties")).isTrue();
        JsonNode properties = jsonSchema.get("properties");

        // Verify key segments
        assertThat(properties.has("ACCOUNTS_BATCH")).isTrue();
        assertThat(properties.has("REPORT")).isTrue();
        assertThat(properties.has("ACCOUNT_ENTRY")).isTrue();
        assertThat(properties.has("EXPENSE")).isTrue();
        assertThat(properties.has("PERSON")).isTrue();

        // Verify segment structure
        JsonNode accountsBatch = properties.get("ACCOUNTS_BATCH");
        assertThat(accountsBatch.get("type").asText()).isEqualTo("object");
        assertThat(accountsBatch.get("x-segment").asBoolean()).isTrue();
        assertThat(accountsBatch.has("properties")).isTrue();

        // Verify field structure with position
        JsonNode batchProperties = accountsBatch.get("properties");
        assertThat(batchProperties.has("NUMBER")).isTrue();
        JsonNode numberField = batchProperties.get("NUMBER");
        assertThat(numberField.has("x-position")).isTrue();
        assertThat(numberField.has("x-csv-column")).isTrue();
        assertThat(numberField.get("x-csv-column").asText()).isEqualTo("ACCOUNTS_BATCH.NUMBER");

        // Count segments
        int segmentCount = properties.size();
        System.out.println("\n✅ Successfully generated BeanIO-optimized JSON Schema");
        System.out.println("   - Schema type: object (segmented structure)");
        System.out.println("   - Total segments: " + segmentCount);
        System.out.println("   - Format: CSV with delimiter ';'");
        System.out.println("   - Record name: csvAccountingCanonical");
        System.out.println("   - Ready for BeanIO XML generation");
    }

    @Test
    public void testSimpleCsvToBeanIOSchema() throws AnalyzerException {
        String csvContent = """
                HEADER.ID;HEADER.NAME;ITEM.CODE;ITEM.QUANTITY;FOOTER.TOTAL
                1;Order A;PROD1;10;100.50
                2;Order B;PROD2;5;50.25
                """;

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("ORDER_DOCUMENT")
                .parserOptions(parserOptions)
                .build();

        StructureElement structure = parser.parse(request);
        Map<String, Object> schema = schemaGenerator.generateSchema(structure, request);

        // Verify basic structure
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(schema.get("title")).isEqualTo("ORDER_DOCUMENT");

        // Verify BeanIO config
        @SuppressWarnings("unchecked")
        Map<String, Object> beanioConfig = (Map<String, Object>) schema.get("x-beanio-config");
        assertThat(beanioConfig.get("recordName")).isEqualTo("orderDocument");
        assertThat(beanioConfig.get("delimiter")).isEqualTo(";");

        // Verify segments
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("HEADER", "ITEM", "FOOTER");

        // Verify HEADER segment
        @SuppressWarnings("unchecked")
        Map<String, Object> headerSegment = (Map<String, Object>) properties.get("HEADER");
        assertThat(headerSegment.get("type")).isEqualTo("object");
        assertThat(headerSegment.get("x-segment")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> headerProps = (Map<String, Object>) headerSegment.get("properties");
        assertThat(headerProps).containsKeys("ID", "NAME");

        // Verify field positions
        @SuppressWarnings("unchecked")
        Map<String, Object> idField = (Map<String, Object>) headerProps.get("ID");
        assertThat(idField.get("x-position")).isEqualTo(0);
        assertThat(idField.get("x-csv-column")).isEqualTo("HEADER.ID");

        @SuppressWarnings("unchecked")
        Map<String, Object> nameField = (Map<String, Object>) headerProps.get("NAME");
        assertThat(nameField.get("x-position")).isEqualTo(1);

        System.out.println("\n✅ Simple CSV to BeanIO schema conversion successful");
        System.out.println("   - 3 segments: HEADER, ITEM, FOOTER");
        System.out.println("   - Fields mapped with positions");
    }
}
