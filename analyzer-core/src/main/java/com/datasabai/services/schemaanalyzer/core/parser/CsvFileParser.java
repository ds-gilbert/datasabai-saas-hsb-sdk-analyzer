package com.datasabai.services.schemaanalyzer.core.parser;

import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.StructureElement;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for CSV files using OpenCSV library.
 * <p>
 * Parses CSV files into a canonical StructureElement model, inferring column types
 * from data samples. Supports various CSV dialects through parser options.
 * </p>
 *
 * <h3>Parser Options:</h3>
 * <ul>
 *   <li><b>delimiter</b>: Column delimiter (default: ",")</li>
 *   <li><b>hasHeader</b>: Whether the first row contains headers (default: "true")</li>
 *   <li><b>encoding</b>: File encoding (default: "UTF-8")</li>
 *   <li><b>quoteChar</b>: Quote character (default: "\"")</li>
 *   <li><b>escapeChar</b>: Escape character (default: "\\")</li>
 *   <li><b>skipLines</b>: Number of lines to skip at the beginning (default: "0")</li>
 *   <li><b>sampleRows</b>: Number of data rows to sample for type inference (default: "100")</li>
 * </ul>
 *
 * <h3>Structure Generated:</h3>
 * <pre>
 * CSV file:
 * ID,Name,Price,InStock
 * 1,Product A,19.99,true
 * 2,Product B,29.99,false
 *
 * Generates:
 * - Root: type="array", name=schemaName
 *   - item: type="object"
 *     - ID: type="integer"
 *     - Name: type="string"
 *     - Price: type="number"
 *     - InStock: type="boolean"
 * </pre>
 *
 * @see FileParser
 * @see TypeInferenceUtil
 */
public class CsvFileParser implements FileParser {
    private static final Logger log = LoggerFactory.getLogger(CsvFileParser.class);

    private static final String DEFAULT_DELIMITER = ",";
    private static final String DEFAULT_HAS_HEADER = "true";
    private static final String DEFAULT_QUOTE_CHAR = "\"";
    private static final String DEFAULT_ESCAPE_CHAR = "\\";
    private static final String DEFAULT_SKIP_LINES = "0";
    private static final String DEFAULT_SAMPLE_ROWS = "100";

    @Override
    public FileType getSupportedFileType() {
        return FileType.CSV;
    }

    @Override
    public boolean canParse(FileAnalysisRequest request) {
        if (request == null || request.getFileType() != FileType.CSV) {
            return false;
        }

        String content = request.getFileContent();
        return content != null && !content.isBlank();
    }

    @Override
    public StructureElement parse(FileAnalysisRequest request) throws AnalyzerException {
        log.debug("Starting CSV parsing for schema: {}", request.getSchemaName());

        try {
            String content = request.getFileContent();
            if (content == null || content.isBlank()) {
                throw new AnalyzerException("INVALID_CSV", FileType.CSV, "No file content provided");
            }

            // Remove BOM (Byte Order Mark) if present at the beginning of the file
            content = removeBOM(content);

            // Get parser options
            String delimiter = request.getParserOption("delimiter", DEFAULT_DELIMITER);
            boolean hasHeader = Boolean.parseBoolean(request.getParserOption("hasHeader", DEFAULT_HAS_HEADER));
            String quoteChar = request.getParserOption("quoteChar", DEFAULT_QUOTE_CHAR);
            String escapeChar = request.getParserOption("escapeChar", DEFAULT_ESCAPE_CHAR);
            int skipLines = Integer.parseInt(request.getParserOption("skipLines", DEFAULT_SKIP_LINES));
            int sampleRows = Integer.parseInt(request.getParserOption("sampleRows", DEFAULT_SAMPLE_ROWS));

            log.debug("CSV options - delimiter: '{}', hasHeader: {}, skipLines: {}, sampleRows: {}",
                    delimiter, hasHeader, skipLines, sampleRows);

            // Configure OpenCSV parser
            CSVParser csvParser = new CSVParserBuilder()
                    .withSeparator(delimiter.charAt(0))
                    .withQuoteChar(quoteChar.charAt(0))
                    .withEscapeChar(escapeChar.charAt(0))
                    .build();

            // Configure CSV reader
            CSVReader csvReader = new CSVReaderBuilder(new StringReader(content))
                    .withCSVParser(csvParser)
                    .withSkipLines(skipLines)
                    .build();

            // Read all lines
            List<String[]> allLines = csvReader.readAll();
            csvReader.close();

            if (allLines.isEmpty()) {
                throw new AnalyzerException("EMPTY_CSV", FileType.CSV, "CSV file is empty");
            }

            // Extract headers
            List<String> headers;
            int dataStartIndex;

            if (hasHeader) {
                headers = List.of(allLines.get(0));
                dataStartIndex = 1;
            } else {
                // Generate column names: column1, column2, ...
                int columnCount = allLines.get(0).length;
                headers = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    headers.add("column" + (i + 1));
                }
                dataStartIndex = 0;
            }

            log.debug("CSV headers: {}", headers);

            // Sample data rows for type inference
            int dataRowCount = allLines.size() - dataStartIndex;
            int rowsToSample = Math.min(sampleRows, dataRowCount);

            log.debug("Total data rows: {}, sampling: {}", dataRowCount, rowsToSample);

            // Collect values for each column
            Map<String, List<String>> columnValues = new LinkedHashMap<>();
            for (String header : headers) {
                columnValues.put(header, new ArrayList<>());
            }

            // Collect sample values
            for (int i = dataStartIndex; i < dataStartIndex + rowsToSample && i < allLines.size(); i++) {
                String[] row = allLines.get(i);
                for (int j = 0; j < headers.size() && j < row.length; j++) {
                    String value = row[j];
                    if (value != null && !value.isBlank()) {
                        columnValues.get(headers.get(j)).add(value);
                    }
                }
            }

            // Infer type for each column
            Map<String, String> columnTypes = new LinkedHashMap<>();
            for (String header : headers) {
                List<String> values = columnValues.get(header);
                String inferredType = inferColumnType(values);
                columnTypes.put(header, inferredType);
                log.debug("Column '{}' inferred type: {}", header, inferredType);
            }

            // Build StructureElement tree
            StructureElement root = buildStructure(request.getSchemaName(), headers, columnTypes);

            log.debug("CSV parsing completed successfully");
            return root;

        } catch (AnalyzerException e) {
            throw e;
        } catch (NumberFormatException e) {
            throw new AnalyzerException("INVALID_OPTION", FileType.CSV,
                    "Invalid numeric option value: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new AnalyzerException("PARSE_ERROR", FileType.CSV,
                    "Failed to parse CSV: " + e.getMessage(), e);
        }
    }

    /**
     * Removes the BOM (Byte Order Mark) from the beginning of the content if present.
     * The UTF-8 BOM is the character '\uFEFF'.
     *
     * @param content the file content
     * @return content without BOM
     */
    private String removeBOM(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
            log.debug("BOM detected and removed from CSV content");
            return content.substring(1);
        }
        return content;
    }

    /**
     * Infers the type of a column from its sample values.
     *
     * @param values list of sample values
     * @return inferred type (string, integer, number, boolean, null)
     */
    private String inferColumnType(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "null";
        }

        // Infer type from first value, then merge with others
        String mergedType = TypeInferenceUtil.inferType(values.get(0));

        for (int i = 1; i < values.size(); i++) {
            String valueType = TypeInferenceUtil.inferType(values.get(i));
            mergedType = TypeInferenceUtil.mergeTypes(mergedType, valueType);

            // Early exit if type widened to string (most general)
            if ("string".equals(mergedType)) {
                break;
            }
        }

        return mergedType;
    }

    /**
     * Builds the StructureElement tree for CSV.
     * Structure: Root (array) → item (object) → columns
     *
     * @param schemaName name for the root element
     * @param headers column names
     * @param columnTypes inferred types for each column
     * @return root StructureElement
     */
    private StructureElement buildStructure(String schemaName, List<String> headers, Map<String, String> columnTypes) {
        // Root element (array of objects)
        StructureElement root = StructureElement.builder()
                .name(schemaName)
                .type("array")
                .array(true)
                .build();

        // Item element (object representing a single CSV row)
        StructureElement item = StructureElement.builder()
                .name("item")
                .type("object")
                .build();

        // Add column fields to item
        for (String header : headers) {
            String type = columnTypes.get(header);
            StructureElement column = StructureElement.builder()
                    .name(header)
                    .type(type)
                    .build();

            item.addChild(column);
        }

        root.addChild(item);

        return root;
    }

    @Override
    public StructureElement mergeStructures(List<StructureElement> structures) throws AnalyzerException {
        if (structures == null || structures.isEmpty()) {
            throw new AnalyzerException("MERGE_ERROR", FileType.CSV, "No structures to merge");
        }

        if (structures.size() == 1) {
            return structures.get(0);
        }

        log.debug("Merging {} CSV structures", structures.size());

        try {
            // Take first structure as base
            StructureElement base = structures.get(0);
            StructureElement mergedRoot = StructureElement.builder()
                    .name(base.getName())
                    .type("array")
                    .array(true)
                    .build();

            // Collect all columns from all structures
            Map<String, String> allColumns = new LinkedHashMap<>();

            for (StructureElement structure : structures) {
                // Navigate to item element
                if (!structure.getChildren().isEmpty()) {
                    StructureElement item = structure.getChildren().get(0);

                    // Merge columns
                    for (StructureElement column : item.getChildren()) {
                        String columnName = column.getName();
                        String columnType = column.getType();

                        if (allColumns.containsKey(columnName)) {
                            // Merge types
                            String existingType = allColumns.get(columnName);
                            String mergedType = TypeInferenceUtil.mergeTypes(existingType, columnType);
                            allColumns.put(columnName, mergedType);
                        } else {
                            // Add new column
                            allColumns.put(columnName, columnType);
                        }
                    }
                }
            }

            // Build merged item structure
            StructureElement mergedItem = StructureElement.builder()
                    .name("item")
                    .type("object")
                    .build();

            for (Map.Entry<String, String> entry : allColumns.entrySet()) {
                StructureElement column = StructureElement.builder()
                        .name(entry.getKey())
                        .type(entry.getValue())
                        .build();
                mergedItem.addChild(column);
            }

            mergedRoot.addChild(mergedItem);

            log.debug("Structure merging completed - total columns: {}", allColumns.size());
            return mergedRoot;

        } catch (Exception e) {
            throw new AnalyzerException("MERGE_ERROR", FileType.CSV,
                    "Failed to merge structures: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> getAvailableOptions() {
        Map<String, String> options = new HashMap<>();
        options.put("delimiter", "Column delimiter (default: ,)");
        options.put("hasHeader", "Whether the first row contains headers (true/false, default: true)");
        options.put("encoding", "File encoding (default: UTF-8)");
        options.put("quoteChar", "Quote character (default: \")");
        options.put("escapeChar", "Escape character (default: \\)");
        options.put("skipLines", "Number of lines to skip at the beginning (default: 0)");
        options.put("sampleRows", "Number of data rows to sample for type inference (default: 100)");
        return options;
    }
}
