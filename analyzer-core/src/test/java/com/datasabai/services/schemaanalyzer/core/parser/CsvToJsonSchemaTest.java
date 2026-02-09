package com.datasabai.services.schemaanalyzer.core.parser;

import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.StructureElement;
import com.datasabai.services.schemaanalyzer.core.generator.JsonSchemaGenerator;
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
 * Test to demonstrate CSV to JSON Schema generation with hierarchical IDoc-like structure.
 */
public class CsvToJsonSchemaTest {

    private CsvFileParser parser;
    private JsonSchemaGenerator schemaGenerator;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        parser = new CsvFileParser();
        schemaGenerator = new JsonSchemaGenerator();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testGenerateJsonSchema_FromNotilusCsvWithBOM() throws IOException, AnalyzerException {
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

        // Generate JSON Schema from structure
        String jsonSchemaStr = schemaGenerator.generateSchemaAsString(structure, request);
        JsonNode jsonSchema = objectMapper.readTree(jsonSchemaStr);

        // Print the generated JSON Schema
        System.out.println("Generated JSON Schema:");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));

        // Verify JSON Schema structure
        assertThat(jsonSchema.has("$schema")).isTrue();
        assertThat(jsonSchema.get("$schema").asText()).isEqualTo("http://json-schema.org/draft-07/schema#");
        assertThat(jsonSchema.has("title")).isTrue();
        assertThat(jsonSchema.get("title").asText()).isEqualTo("CSV_ACCOUNTING_CANONICAL");
        assertThat(jsonSchema.has("type")).isTrue();
        assertThat(jsonSchema.get("type").asText()).isEqualTo("array");

        // Verify that items contain the CSV columns
        assertThat(jsonSchema.has("items")).isTrue();
        JsonNode items = jsonSchema.get("items");
        assertThat(items.has("type")).isTrue();
        assertThat(items.get("type").asText()).isEqualTo("object");
        assertThat(items.has("properties")).isTrue();

        JsonNode properties = items.get("properties");

        // Verify some key columns from Notilus CSV
        assertThat(properties.has("ACCOUNTS_BATCH.NUMBER")).isTrue();
        assertThat(properties.has("REPORT.DOC_NUMBER")).isTrue();
        assertThat(properties.has("ACCOUNT_ENTRY.ENTRY_NUMBER")).isTrue();
        assertThat(properties.has("ACCOUNT_ENTRY.ACCOUNT_CODE")).isTrue();
        assertThat(properties.has("PERSON.NUMBER")).isTrue();

        System.out.println("\n✅ Successfully generated JSON Schema from Notilus CSV with BOM");
        System.out.println("   - Parsed " + properties.size() + " columns");
        System.out.println("   - Detected and removed BOM character");
        System.out.println("   - Generated valid JSON Schema draft-07");
    }

    @Test
    public void testAnalyzeCsvColumnStructure() throws IOException, AnalyzerException {
        // Read the actual Notilus CSV file
        Path testFilePath = Paths.get("..", "testFiles", "Notilus_1_6600_17.csv");

        if (!Files.exists(testFilePath)) {
            System.out.println("Skipping test - file not found: " + testFilePath);
            return;
        }

        String csvContent = Files.readString(testFilePath);

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Notilus_Analysis")
                .parserOptions(parserOptions)
                .build();

        StructureElement structure = parser.parse(request);

        // Analyze column name patterns to identify segments
        StructureElement item = structure.getChildren().get(0);
        Map<String, Integer> segmentCounts = new HashMap<>();

        for (StructureElement column : item.getChildren()) {
            String columnName = column.getName();

            // Extract segment prefix (e.g., "ACCOUNTS_BATCH" from "ACCOUNTS_BATCH.NUMBER")
            if (columnName.contains(".")) {
                String segment = columnName.substring(0, columnName.indexOf("."));
                segmentCounts.put(segment, segmentCounts.getOrDefault(segment, 0) + 1);
            }
        }

        System.out.println("\nCSV Column Structure Analysis:");
        System.out.println("==============================");
        System.out.println("Total columns: " + item.getChildren().size());
        System.out.println("\nSegment breakdown:");

        segmentCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .forEach(entry -> {
                    System.out.printf("  - %-30s: %3d columns\n", entry.getKey(), entry.getValue());
                });

        // Verify we found the expected segments
        assertThat(segmentCounts).containsKeys(
                "ACCOUNTS_BATCH",
                "REPORT",
                "ACCOUNT_ENTRY",
                "EXPENSE",
                "TYPE",
                "PERSON"
        );

        System.out.println("\n✅ Successfully analyzed CSV column structure");
        System.out.println("   - Identified " + segmentCounts.size() + " distinct segments");
        System.out.println("   - These segments could be used to create an IDoc-like hierarchy");
    }
}
