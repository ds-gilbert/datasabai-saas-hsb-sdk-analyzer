package com.datasabai.services.schemaanalyzer.core.parser;

import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.StructureElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CsvFileParser}.
 */
public class CsvFileParserTest {

    private CsvFileParser parser;

    @BeforeEach
    public void setUp() {
        parser = new CsvFileParser();
    }

    @Test
    public void testGetSupportedFileType() {
        assertThat(parser.getSupportedFileType()).isEqualTo(FileType.CSV);
    }

    @Test
    public void testCanParse_ValidCsv() {
        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent("ID,Name\n1,Product A\n2,Product B")
                .schemaName("TestSchema")
                .build();

        assertThat(parser.canParse(request)).isTrue();
    }

    @Test
    public void testCanParse_InvalidFileType() {
        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.JSON)
                .fileContent("ID,Name\n1,Product A")
                .schemaName("TestSchema")
                .build();

        assertThat(parser.canParse(request)).isFalse();
    }

    @Test
    public void testCanParse_NullContent() {
        // Use fileBytes to bypass builder validation, then test canParse
        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileBytes(new byte[]{1}) // Dummy bytes to pass builder validation
                .schemaName("TestSchema")
                .build();

        // Set fileContent to null after building to test canParse logic
        request.setFileContent(null);

        assertThat(parser.canParse(request)).isFalse();
    }

    @Test
    public void testCanParse_BlankContent() {
        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileBytes(new byte[]{1}) // Dummy bytes to pass builder validation
                .schemaName("TestSchema")
                .build();

        // Set fileContent to blank after building to test canParse logic
        request.setFileContent("   ");

        assertThat(parser.canParse(request)).isFalse();
    }

    @Test
    public void testParse_BasicCsvWithHeaders() throws AnalyzerException {
        String csvContent = """
                ID,Name,Price,InStock
                1,Product A,19.99,true
                2,Product B,29.99,false
                3,Product C,39.99,true
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .build();

        StructureElement root = parser.parse(request);

        // Verify root structure
        assertThat(root.getName()).isEqualTo("Products");
        assertThat(root.getType()).isEqualTo("array");
        assertThat(root.isArray()).isTrue();
        assertThat(root.getChildren()).hasSize(1);

        // Verify item structure
        StructureElement item = root.getChildren().get(0);
        assertThat(item.getName()).isEqualTo("item");
        assertThat(item.getType()).isEqualTo("object");
        assertThat(item.getChildren()).hasSize(4);

        // Verify columns - all types are now "string" since type inference cannot reliably determine semantic types
        StructureElement idColumn = item.findChild("ID");
        assertThat(idColumn).isNotNull();
        assertThat(idColumn.getType()).isEqualTo("string");

        StructureElement nameColumn = item.findChild("Name");
        assertThat(nameColumn).isNotNull();
        assertThat(nameColumn.getType()).isEqualTo("string");

        StructureElement priceColumn = item.findChild("Price");
        assertThat(priceColumn).isNotNull();
        assertThat(priceColumn.getType()).isEqualTo("string");

        StructureElement inStockColumn = item.findChild("InStock");
        assertThat(inStockColumn).isNotNull();
        assertThat(inStockColumn.getType()).isEqualTo("string");
    }

    @Test
    public void testParse_CsvWithoutHeaders() throws AnalyzerException {
        String csvContent = """
                1,Product A,19.99
                2,Product B,29.99
                3,Product C,39.99
                """;

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("hasHeader", "false");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .parserOptions(parserOptions)
                .build();

        StructureElement root = parser.parse(request);

        // Verify root structure
        assertThat(root.getName()).isEqualTo("Products");
        assertThat(root.getType()).isEqualTo("array");
        assertThat(root.isArray()).isTrue();

        // Verify item structure with generated column names
        StructureElement item = root.getChildren().get(0);
        assertThat(item.getChildren()).hasSize(3);

        // Verify generated column names - all types are "string"
        assertThat(item.findChild("column1")).isNotNull();
        assertThat(item.findChild("column1").getType()).isEqualTo("string");
        assertThat(item.findChild("column2")).isNotNull();
        assertThat(item.findChild("column2").getType()).isEqualTo("string");
        assertThat(item.findChild("column3")).isNotNull();
        assertThat(item.findChild("column3").getType()).isEqualTo("string");
    }

    @Test
    public void testParse_TypeInference() throws AnalyzerException {
        String csvContent = """
                IntCol,NumberCol,StringCol,BoolCol,MixedCol
                123,-456,hello,true,100
                789,12.34,world,false,text
                0,0.5,test,TRUE,200
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("TypeTest")
                .build();

        StructureElement root = parser.parse(request);
        StructureElement item = root.getChildren().get(0);

        // All columns are now inferred as "string" since we cannot reliably determine types from raw data
        assertThat(item.findChild("IntCol").getType()).isEqualTo("string");
        assertThat(item.findChild("NumberCol").getType()).isEqualTo("string");
        assertThat(item.findChild("StringCol").getType()).isEqualTo("string");
        assertThat(item.findChild("BoolCol").getType()).isEqualTo("string");
        assertThat(item.findChild("MixedCol").getType()).isEqualTo("string");
    }

    @Test
    public void testParse_MultipleRowsDetectsArray() throws AnalyzerException {
        String csvContent = """
                ID,Name
                1,First
                2,Second
                3,Third
                4,Fourth
                5,Fifth
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Items")
                .build();

        StructureElement root = parser.parse(request);

        // Root should be an array (CSV always produces array structure)
        assertThat(root.getType()).isEqualTo("array");
        assertThat(root.isArray()).isTrue();
        assertThat(root.getChildren()).hasSize(1);

        // Item should be object
        StructureElement item = root.getChildren().get(0);
        assertThat(item.getType()).isEqualTo("object");
    }

    @Test
    public void testParse_CustomDelimiter() throws AnalyzerException {
        String csvContent = """
                ID;Name;Price
                1;Product A;19.99
                2;Product B;29.99
                """;

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .parserOptions(parserOptions)
                .build();

        StructureElement root = parser.parse(request);
        StructureElement item = root.getChildren().get(0);

        // Should parse correctly with semicolon delimiter
        assertThat(item.getChildren()).hasSize(3);
        assertThat(item.findChild("ID")).isNotNull();
        assertThat(item.findChild("Name")).isNotNull();
        assertThat(item.findChild("Price")).isNotNull();
    }

    @Test
    public void testParse_QuotedFieldsWithDelimiters() throws AnalyzerException {
        String csvContent = """
                ID,Name,Description
                1,"Product A","A simple, basic product"
                2,"Product B","An advanced, premium product"
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .build();

        StructureElement root = parser.parse(request);
        StructureElement item = root.getChildren().get(0);

        // Should handle quoted fields correctly
        assertThat(item.getChildren()).hasSize(3);
        assertThat(item.findChild("ID")).isNotNull();
        assertThat(item.findChild("Name")).isNotNull();
        assertThat(item.findChild("Description")).isNotNull();
        assertThat(item.findChild("Description").getType()).isEqualTo("string");
    }

    @Test
    public void testMergeStructures_SingleStructure() throws AnalyzerException {
        String csvContent = """
                ID,Name
                1,Product A
                """;

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .build();

        StructureElement structure = parser.parse(request);
        StructureElement merged = parser.mergeStructures(List.of(structure));

        // Should return the same structure
        assertThat(merged.getName()).isEqualTo("Products");
        assertThat(merged.getType()).isEqualTo("array");
    }

    @Test
    public void testMergeStructures_MultipleStructures() throws AnalyzerException {
        // First CSV: ID (integer), Name (string)
        String csv1 = """
                ID,Name
                1,Product A
                2,Product B
                """;

        // Second CSV: ID (integer), Name (string), Price (number)
        String csv2 = """
                ID,Name,Price
                3,Product C,39.99
                4,Product D,49.99
                """;

        // Third CSV: ID (string - mixed type), Name (string), Price (number), InStock (boolean)
        String csv3 = """
                ID,Name,Price,InStock
                ABC,Product E,59.99,true
                DEF,Product F,69.99,false
                """;

        FileAnalysisRequest request1 = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csv1)
                .schemaName("Products")
                .build();

        FileAnalysisRequest request2 = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csv2)
                .schemaName("Products")
                .build();

        FileAnalysisRequest request3 = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csv3)
                .schemaName("Products")
                .build();

        StructureElement structure1 = parser.parse(request1);
        StructureElement structure2 = parser.parse(request2);
        StructureElement structure3 = parser.parse(request3);

        StructureElement merged = parser.mergeStructures(List.of(structure1, structure2, structure3));

        // Verify merged structure
        assertThat(merged.getName()).isEqualTo("Products");
        assertThat(merged.getType()).isEqualTo("array");
        assertThat(merged.isArray()).isTrue();

        StructureElement item = merged.getChildren().get(0);
        assertThat(item.getChildren()).hasSize(4); // ID, Name, Price, InStock

        // ID: integer + integer + string → string (widened)
        assertThat(item.findChild("ID").getType()).isEqualTo("string");

        // Name: string in all
        assertThat(item.findChild("Name").getType()).isEqualTo("string");

        // Price: number in csv2 and csv3
        assertThat(item.findChild("Price").getType()).isEqualTo("string");

        // InStock: all types are now "string"
        assertThat(item.findChild("InStock").getType()).isEqualTo("string");
    }

    @Test
    public void testParse_SkipLines() throws AnalyzerException {
        String csvContent = """
                # This is a comment line to skip
                # Another comment
                ID,Name
                1,Product A
                2,Product B
                """;

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("skipLines", "2");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .parserOptions(parserOptions)
                .build();

        StructureElement root = parser.parse(request);
        StructureElement item = root.getChildren().get(0);

        // Should skip first 2 lines and parse correctly
        assertThat(item.getChildren()).hasSize(2);
        assertThat(item.findChild("ID")).isNotNull();
        assertThat(item.findChild("Name")).isNotNull();
    }

    @Test
    public void testParse_EmptyCsv() {
        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileBytes(new byte[]{1}) // Dummy bytes to pass builder validation
                .schemaName("Empty")
                .build();

        // Set fileContent to empty after building to test parse error handling
        request.setFileContent("");

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(AnalyzerException.class)
                .hasMessageContaining("No file content provided");
    }

    @Test
    public void testParse_InvalidNumericOption() {
        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("skipLines", "not-a-number");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent("ID,Name\n1,Test")
                .schemaName("Test")
                .parserOptions(parserOptions)
                .build();

        assertThatThrownBy(() -> parser.parse(request))
                .isInstanceOf(AnalyzerException.class)
                .hasMessageContaining("Invalid numeric option value");
    }

    @Test
    public void testMergeStructures_EmptyList() {
        assertThatThrownBy(() -> parser.mergeStructures(List.of()))
                .isInstanceOf(AnalyzerException.class)
                .hasMessageContaining("No structures to merge");
    }

    @Test
    public void testMergeStructures_NullList() {
        assertThatThrownBy(() -> parser.mergeStructures(null))
                .isInstanceOf(AnalyzerException.class)
                .hasMessageContaining("No structures to merge");
    }

    @Test
    public void testGetAvailableOptions() {
        Map<String, String> options = parser.getAvailableOptions();

        assertThat(options).isNotNull();
        assertThat(options).containsKeys(
                "delimiter",
                "hasHeader",
                "encoding",
                "quoteChar",
                "escapeChar",
                "skipLines",
                "sampleRows"
        );
    }

    @Test
    public void testParse_WithBOM() throws AnalyzerException {
        // CSV content with UTF-8 BOM character at the beginning
        String csvContent = "\uFEFFID;Name;Price\n1;Product A;19.99\n2;Product B;29.99";

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .parserOptions(parserOptions)
                .build();

        StructureElement root = parser.parse(request);

        // Verify root structure
        assertThat(root.getName()).isEqualTo("Products");
        assertThat(root.getType()).isEqualTo("array");
        assertThat(root.isArray()).isTrue();
        assertThat(root.getChildren()).hasSize(1);

        // Verify item structure
        StructureElement item = root.getChildren().get(0);
        assertThat(item.getName()).isEqualTo("item");
        assertThat(item.getType()).isEqualTo("object");
        assertThat(item.getChildren()).hasSize(3);

        // Verify that the first column name is "ID" and not "﻿ID" (with BOM)
        StructureElement idColumn = item.findChild("ID");
        assertThat(idColumn).isNotNull();
        assertThat(idColumn.getName()).isEqualTo("ID");
        assertThat(idColumn.getType()).isEqualTo("string");

        StructureElement nameColumn = item.findChild("Name");
        assertThat(nameColumn).isNotNull();

        StructureElement priceColumn = item.findChild("Price");
        assertThat(priceColumn).isNotNull();
    }

    @Test
    public void testParse_WithQuotedFieldsAndSemicolon() throws AnalyzerException {
        String csvContent = """
                "ID";"Name";"Description"
                "1";"Product A";"A simple, basic product"
                "2";"Product B";"An advanced, premium product"
                """;

        Map<String, String> parserOptions = new HashMap<>();
        parserOptions.put("delimiter", ";");

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("Products")
                .parserOptions(parserOptions)
                .build();

        StructureElement root = parser.parse(request);
        StructureElement item = root.getChildren().get(0);

        // Should handle quoted fields with semicolon delimiter correctly
        assertThat(item.getChildren()).hasSize(3);
        assertThat(item.findChild("ID")).isNotNull();
        assertThat(item.findChild("Name")).isNotNull();
        assertThat(item.findChild("Description")).isNotNull();
    }
}
