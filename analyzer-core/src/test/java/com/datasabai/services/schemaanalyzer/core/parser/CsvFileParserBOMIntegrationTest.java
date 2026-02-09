package com.datasabai.services.schemaanalyzer.core.parser;

import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.StructureElement;
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
 * Integration tests for {@link CsvFileParser} with real files containing BOM.
 */
public class CsvFileParserBOMIntegrationTest {

    private CsvFileParser parser;

    @BeforeEach
    public void setUp() {
        parser = new CsvFileParser();
    }

    @Test
    public void testParse_NotilusFileWithBOM() throws IOException, AnalyzerException {
        // Read the actual file from testFiles directory (relative to project root)
        Path testFilePath = Paths.get("..", "testFiles", "Notilus_1_6600_17.csv");

        if (!Files.exists(testFilePath)) {
            // Skip test if file doesn't exist (e.g., in CI environment)
            System.out.println("Skipping test - file not found: " + testFilePath);
            return;
        }

        String csvContent = Files.readString(testFilePath);

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Notilus_Accounting")
                .parserOptions(parserOptions)
                .build();

        StructureElement root = parser.parse(request);

        // Verify root structure
        assertThat(root.getName()).isEqualTo("Notilus_Accounting");
        assertThat(root.getType()).isEqualTo("array");
        assertThat(root.isArray()).isTrue();

        // Verify item structure
        StructureElement item = root.getChildren().get(0);
        assertThat(item.getName()).isEqualTo("item");
        assertThat(item.getType()).isEqualTo("object");

        // Verify that we have the expected columns (without BOM in the first column name)
        StructureElement batchNumberColumn = item.findChild("ACCOUNTS_BATCH.NUMBER");
        assertThat(batchNumberColumn).isNotNull();
        assertThat(batchNumberColumn.getName()).isEqualTo("ACCOUNTS_BATCH.NUMBER");

        // Verify a few more important columns
        assertThat(item.findChild("ACCOUNTS_BATCH.LEDGER_TYPE")).isNotNull();
        assertThat(item.findChild("REPORT.DOC_NUMBER")).isNotNull();
        assertThat(item.findChild("ACCOUNT_ENTRY.ACCOUNT_CODE")).isNotNull();

        System.out.println("Successfully parsed Notilus file with BOM - found " + item.getChildren().size() + " columns");
    }

    @Test
    public void testParse_NotilusFileWithQuotedFields() throws IOException, AnalyzerException {
        // Read the actual file from testFiles directory (relative to project root)
        Path testFilePath = Paths.get("..", "testFiles", "Notilus_1_8860_37_20260129130301685.csv");

        if (!Files.exists(testFilePath)) {
            // Skip test if file doesn't exist (e.g., in CI environment)
            System.out.println("Skipping test - file not found: " + testFilePath);
            return;
        }

        String csvContent = Files.readString(testFilePath);

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Notilus_Taiwan")
                .parserOptions(parserOptions)
                .build();

        StructureElement root = parser.parse(request);

        // Verify root structure
        assertThat(root.getName()).isEqualTo("Notilus_Taiwan");
        assertThat(root.getType()).isEqualTo("array");
        assertThat(root.isArray()).isTrue();

        // Verify item structure
        StructureElement item = root.getChildren().get(0);
        assertThat(item.getName()).isEqualTo("item");
        assertThat(item.getType()).isEqualTo("object");

        // This file uses quoted fields, verify that parsing works correctly
        assertThat(item.getChildren()).isNotEmpty();

        System.out.println("Successfully parsed Notilus Taiwan file with quoted fields - found " + item.getChildren().size() + " columns");
    }
}
