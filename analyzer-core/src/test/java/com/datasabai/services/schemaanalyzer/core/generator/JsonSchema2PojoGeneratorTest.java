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
 * Tests for {@link JsonSchema2PojoGenerator}.
 */
public class JsonSchema2PojoGeneratorTest {

    private CsvFileParser parser;
    private JsonSchema2PojoGenerator schemaGenerator;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        parser = new CsvFileParser();
        schemaGenerator = new JsonSchema2PojoGenerator();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testGenerateJsonSchema2Pojo_SimpleExample() throws AnalyzerException {
        String csvContent = """
                ACCOUNTS_BATCH.NUMBER;REPORT.DOC_NUMBER;ACCOUNT_ENTRY.ID;ACCOUNT_ENTRY.AMOUNT
                B001;R123;1;100.50
                B001;R123;2;200.75
                """;

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("OrdersDocument")
                .parserOptions(parserOptions)
                .build();

        StructureElement structure = parser.parse(request);
        Map<String, Object> schema = schemaGenerator.generateSchema(structure, request);

        // Verify root structure
        assertThat(schema.get("$schema")).isEqualTo("http://json-schema.org/draft-07/schema#");
        assertThat(schema.get("title")).isEqualTo("OrdersDocument");
        assertThat(schema.get("type")).isEqualTo("object");

        // Verify root properties: header + records
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("header", "records");

        // header is a $ref
        @SuppressWarnings("unchecked")
        Map<String, Object> headerProp = (Map<String, Object>) properties.get("header");
        assertThat(headerProp.get("$ref")).isEqualTo("#/$defs/Header");

        // records is array with $ref items
        @SuppressWarnings("unchecked")
        Map<String, Object> recordsProp = (Map<String, Object>) properties.get("records");
        assertThat(recordsProp.get("type")).isEqualTo("array");
        @SuppressWarnings("unchecked")
        Map<String, Object> recordsItems = (Map<String, Object>) recordsProp.get("items");
        assertThat(recordsItems.get("$ref")).isEqualTo("#/$defs/Record");

        // Verify $defs
        @SuppressWarnings("unchecked")
        Map<String, Object> defs = (Map<String, Object>) schema.get("$defs");
        assertThat(defs).containsKeys("Header", "Record");

        // Header def has title + all fields flat
        @SuppressWarnings("unchecked")
        Map<String, Object> headerDef = (Map<String, Object>) defs.get("Header");
        assertThat(headerDef.get("title")).isEqualTo("Header");
        assertThat(headerDef.get("type")).isEqualTo("object");
        @SuppressWarnings("unchecked")
        Map<String, Object> headerFields = (Map<String, Object>) headerDef.get("properties");
        assertThat(headerFields).containsKeys(
                "accountsBatchNumber", "reportDocNumber", "accountEntryId", "accountEntryAmount");

        // Record def has same fields
        @SuppressWarnings("unchecked")
        Map<String, Object> recordDef = (Map<String, Object>) defs.get("Record");
        assertThat(recordDef.get("title")).isEqualTo("Record");
        @SuppressWarnings("unchecked")
        Map<String, Object> recordFields = (Map<String, Object>) recordDef.get("properties");
        assertThat(recordFields).containsKeys(
                "accountsBatchNumber", "reportDocNumber", "accountEntryId", "accountEntryAmount");

        // Verify NO BeanIO metadata
        assertThat(schema).doesNotContainKey("x-beanio-config");
        assertThat(schema).doesNotContainKey("x-beanio");

        System.out.println("\n=== Header + Records schema with $defs ===");
        System.out.println("   - " + headerFields.size() + " fields in Header and Record");
    }

    @Test
    public void testGenerateJsonSchema2Pojo_FromNotilusCSV() throws IOException, AnalyzerException {
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
                .schemaName("CsvAccountingCanonical")
                .parserOptions(parserOptions)
                .build();

        StructureElement structure = parser.parse(request);
        String jsonSchemaStr = schemaGenerator.generateSchemaAsString(structure, request);
        JsonNode jsonSchema = objectMapper.readTree(jsonSchemaStr);

        System.out.println("Generated Header+Records JSON Schema:");
        System.out.println("======================================");
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema));

        // Verify root
        assertThat(jsonSchema.get("$schema").asText()).isEqualTo("http://json-schema.org/draft-07/schema#");
        assertThat(jsonSchema.get("title").asText()).isEqualTo("CsvAccountingCanonical");

        // Verify $defs with Header and Record
        JsonNode defs = jsonSchema.get("$defs");
        assertThat(defs.has("Header")).isTrue();
        assertThat(defs.has("Record")).isTrue();
        assertThat(defs.get("Header").get("title").asText()).isEqualTo("Header");
        assertThat(defs.get("Record").get("title").asText()).isEqualTo("Record");

        // Both have all fields
        JsonNode headerProps = defs.get("Header").get("properties");
        JsonNode recordProps = defs.get("Record").get("properties");
        assertThat(headerProps.size()).isEqualTo(recordProps.size());
        assertThat(headerProps.size()).isGreaterThan(200);

        // Verify camelCase fields
        assertThat(headerProps.has("accountsBatchNumber")).isTrue();
        assertThat(headerProps.has("reportDocNumber")).isTrue();

        // Verify duplicate handling: departmentDescription + departmentDescription2
        assertThat(headerProps.has("departmentDescription")).isTrue();
        assertThat(headerProps.has("departmentDescription2")).isTrue();

        // Verify slash handling: EXPENSE.TYPE_TOWN/CITY -> expenseTypeTownCity
        assertThat(headerProps.has("expenseTypeTownCity")).isTrue();

        // Verify NO BeanIO metadata
        assertThat(jsonSchema.toString()).doesNotContain("x-beanio");
        assertThat(jsonSchema.toString()).doesNotContain("x-position");

        System.out.println("\n=== Notilus CSV: Header+Records with $defs ===");
        System.out.println("   - " + headerProps.size() + " fields in each definition");
    }

    @Test
    public void testCamelCaseConversion() throws Exception {
        String csvContent = """
                ACCOUNTS_BATCH.BATCH_NUMBER;ACCOUNT_ENTRY.ENTRY_ID
                B001;1
                """;

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("TestDocument")
                .parserOptions(parserOptions)
                .build();

        StructureElement structure = parser.parse(request);
        String jsonSchemaStr = schemaGenerator.generateSchemaAsString(structure, request);
        JsonNode jsonSchema = objectMapper.readTree(jsonSchemaStr);

        // Fields in $defs/Header
        JsonNode headerProps = jsonSchema.get("$defs").get("Header").get("properties");
        assertThat(headerProps.has("accountsBatchBatchNumber")).isTrue();
        assertThat(headerProps.has("accountEntryEntryId")).isTrue();

        // Same in $defs/Record
        JsonNode recordProps = jsonSchema.get("$defs").get("Record").get("properties");
        assertThat(recordProps.has("accountsBatchBatchNumber")).isTrue();
        assertThat(recordProps.has("accountEntryEntryId")).isTrue();

        System.out.println("\n=== CamelCase conversion (Header+Records) ===");
        System.out.println("   - ACCOUNTS_BATCH.BATCH_NUMBER -> accountsBatchBatchNumber");
        System.out.println("   - ACCOUNT_ENTRY.ENTRY_ID -> accountEntryEntryId");
    }
}
