package com.datasabai.services.schemaanalyzer.core;

import com.datasabai.services.schemaanalyzer.core.generator.JsonSchemaGenerator;
import com.datasabai.services.schemaanalyzer.core.generator.JsonSchema2PojoGenerator;
import com.datasabai.services.schemaanalyzer.core.model.*;
import com.datasabai.services.schemaanalyzer.core.parser.FileParser;
import com.datasabai.services.schemaanalyzer.core.parser.ParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main service for analyzing file schemas and generating JSON Schemas.
 * <p>
 * This service orchestrates the entire analysis process:
 * </p>
 *
 * <h3>Analysis Process (7 Steps):</h3>
 * <ol>
 *   <li><b>Validation</b>: Validate input request (file type, content, schema name)</li>
 *   <li><b>Parser Selection</b>: Select appropriate parser using ParserFactory</li>
 *   <li><b>File Parsing</b>: Parse the main file into StructureElement tree</li>
 *   <li><b>Sample Fusion</b>: Parse and merge sample files for better inference</li>
 *   <li><b>Schema Generation</b>: Generate JSON Schema from structure</li>
 *   <li><b>Validation</b>: Validate the generated schema</li>
 *   <li><b>Result Construction</b>: Build result with metadata and statistics</li>
 * </ol>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * FileSchemaAnalyzer analyzer = new FileSchemaAnalyzer();
 *
 * FileAnalysisRequest request = FileAnalysisRequest.builder()
 *     .fileType(FileType.XML)
 *     .fileContent("<customer><id>123</id></customer>")
 *     .schemaName("Customer")
 *     .build();
 *
 * SchemaGenerationResult result = analyzer.analyze(request);
 *
 * if (result.isSuccess()) {
 *     System.out.println(result.getJsonSchemaAsString());
 * }
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * This class is thread-safe. Multiple threads can call {@link #analyze(FileAnalysisRequest)}
 * concurrently.
 * </p>
 */
public class FileSchemaAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(FileSchemaAnalyzer.class);

    private final ParserFactory parserFactory;
    private final JsonSchemaGenerator schemaGenerator;

    /**
     * Creates a new FileSchemaAnalyzer with default components.
     */
    public FileSchemaAnalyzer() {
        this.parserFactory = new ParserFactory();
        this.schemaGenerator = new JsonSchemaGenerator();
    }

    /**
     * Creates a new FileSchemaAnalyzer with custom components.
     * <p>
     * Useful for testing or custom configurations.
     * </p>
     *
     * @param parserFactory parser factory
     * @param schemaGenerator schema generator
     */
    public FileSchemaAnalyzer(
            ParserFactory parserFactory,
            JsonSchemaGenerator schemaGenerator
    ) {
        this.parserFactory = parserFactory;
        this.schemaGenerator = schemaGenerator;
    }

    /**
     * Analyzes a file and generates a JSON Schema.
     * <p>
     * This is the main entry point for the service. It performs all 8 steps
     * of the analysis process.
     * </p>
     *
     * @param request analysis request
     * @return schema generation result
     * @throws AnalyzerException if analysis fails
     */
    public SchemaGenerationResult analyze(FileAnalysisRequest request) throws AnalyzerException {
        long startTime = System.currentTimeMillis();

        log.info("Starting schema analysis for: {} (type: {})",
                request.getSchemaName(), request.getFileType());

        try {
            // Step 1: VALIDATION
            validateRequest(request);

            // Step 2: PARSER SELECTION
            FileParser parser = selectParser(request);

            // Step 3: FILE PARSING
            StructureElement structure = parseMainFile(request, parser);

            // Step 4: SAMPLE FUSION
            if (hasSamples(request)) {
                structure = fuseSamples(request, parser, structure);
            }

            // Step 5: SCHEMA GENERATION
            Map<String, Object> jsonSchema = generateSchema(request, structure);

            // Step 6: VALIDATION
            validateSchema(jsonSchema);

            // Step 8: RESULT CONSTRUCTION
            SchemaGenerationResult result = buildResult(
                    request,
                    structure,
                    jsonSchema,
                    startTime
            );

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Schema analysis completed successfully in {}ms", totalTime);

            return result;

        } catch (AnalyzerException e) {
            log.error("Schema analysis failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during schema analysis", e);
            throw new AnalyzerException(
                    "ANALYSIS_ERROR",
                    request.getFileType(),
                    "Analysis failed: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Gets available file types that can be analyzed.
     *
     * @return list of available file types
     */
    public List<FileType> getAvailableFileTypes() {
        return parserFactory.getAvailableFileTypes();
    }

    /**
     * Gets all registered file types (including stubs).
     *
     * @return list of all registered file types
     */
    public List<FileType> getRegisteredFileTypes() {
        return parserFactory.getRegisteredFileTypes();
    }

    /**
     * Gets parser options for a specific file type.
     *
     * @param fileType file type
     * @return map of option name to description
     */
    public Map<String, String> getParserOptions(FileType fileType) {
        return parserFactory.getParserOptions(fileType);
    }

    /**
     * Gets the parser factory (for advanced usage).
     *
     * @return parser factory
     */
    public ParserFactory getParserFactory() {
        return parserFactory;
    }

    // Private helper methods for each step

    /**
     * Step 1: Validates the analysis request.
     */
    private void validateRequest(FileAnalysisRequest request) throws AnalyzerException {
        log.debug("Step 1: Validating request");

        if (request == null) {
            throw new AnalyzerException("VALIDATION_ERROR", "Request cannot be null");
        }

        try {
            request.validate();
        } catch (IllegalArgumentException e) {
            throw new AnalyzerException("VALIDATION_ERROR", e.getMessage(), e);
        }

        if (request.getFileType() == null) {
            throw new AnalyzerException("VALIDATION_ERROR", "File type cannot be null");
        }

        log.debug("Request validation passed");
    }

    /**
     * Step 2: Selects the appropriate parser for the file type.
     */
    private FileParser selectParser(FileAnalysisRequest request) throws AnalyzerException {
        log.debug("Step 2: Selecting parser for file type: {}", request.getFileType());

        try {
            FileParser parser = parserFactory.getParser(request.getFileType());

            if (!parser.canParse(request)) {
                throw new AnalyzerException(
                        "PARSER_ERROR",
                        request.getFileType(),
                        "Parser cannot handle the provided file content"
                );
            }

            log.debug("Selected parser: {}", parser.getClass().getSimpleName());
            return parser;

        } catch (IllegalArgumentException e) {
            throw new AnalyzerException(
                    "PARSER_NOT_FOUND",
                    request.getFileType(),
                    e.getMessage(),
                    e
            );
        }
    }

    /**
     * Step 3: Parses the main file.
     */
    private StructureElement parseMainFile(FileAnalysisRequest request, FileParser parser)
            throws AnalyzerException {
        log.debug("Step 3: Parsing main file");

        StructureElement structure = parser.parse(request);

        if (structure == null) {
            throw new AnalyzerException(
                    "PARSE_ERROR",
                    request.getFileType(),
                    "Parser returned null structure"
            );
        }

        log.debug("Main file parsed successfully: {} elements", countElements(structure));
        return structure;
    }

    /**
     * Step 4: Fuses sample files with the main structure.
     */
    private StructureElement fuseSamples(
            FileAnalysisRequest request,
            FileParser parser,
            StructureElement mainStructure
    ) throws AnalyzerException {
        log.debug("Step 4: Fusing {} sample files", request.getSampleFileContents().size());

        List<StructureElement> allStructures = new ArrayList<>();
        allStructures.add(mainStructure);

        // Parse each sample file
        for (int i = 0; i < request.getSampleFileContents().size(); i++) {
            String sampleContent = request.getSampleFileContents().get(i);

            // Create a temporary request for the sample
            FileAnalysisRequest sampleRequest = FileAnalysisRequest.builder()
                    .fileType(request.getFileType())
                    .fileContent(sampleContent)
                    .schemaName(request.getSchemaName() + "_sample" + i)
                    .parserOptions(request.getParserOptions())
                    .detectArrays(request.isDetectArrays())
                    .build();

            try {
                StructureElement sampleStructure = parser.parse(sampleRequest);
                allStructures.add(sampleStructure);
                log.debug("Parsed sample {} successfully", i + 1);
            } catch (Exception e) {
                log.warn("Failed to parse sample {}: {}", i + 1, e.getMessage());
                // Continue with other samples
            }
        }

        // Merge all structures
        StructureElement mergedStructure = parser.mergeStructures(allStructures);
        log.debug("Sample fusion completed: {} total structures merged", allStructures.size());

        return mergedStructure;
    }

    /**
     * Step 5: Generates JSON Schema from structure.
     */
    private Map<String, Object> generateSchema(FileAnalysisRequest request, StructureElement structure)
            throws AnalyzerException {
        log.debug("Step 5: Generating JSON Schema");

        // For CSV files, use JsonSchema2PojoGenerator for Header+Records structure
        Map<String, Object> schema;
        if (request.getFileType() == FileType.CSV) {
            log.debug("Using JsonSchema2PojoGenerator for CSV (Header+Records structure)");
            JsonSchema2PojoGenerator csvGenerator = new JsonSchema2PojoGenerator();
            schema = csvGenerator.generateSchema(structure, request);
        } else {
            schema = schemaGenerator.generateSchema(structure, request);
        }

        if (schema == null || schema.isEmpty()) {
            throw new AnalyzerException(
                    "GENERATION_ERROR",
                    request.getFileType(),
                    "Schema generator returned null or empty schema"
            );
        }

        log.debug("JSON Schema generated: {} properties", schemaGenerator.countProperties(schema));
        return schema;
    }

    /**
     * Step 6: Validates the generated schema.
     */
    private void validateSchema(Map<String, Object> schema) throws AnalyzerException {
        log.debug("Step 6: Validating generated schema");

        if (!schemaGenerator.validateSchema(schema)) {
            throw new AnalyzerException(
                    "VALIDATION_ERROR",
                    "Generated schema is not valid"
            );
        }

        log.debug("Schema validation passed");
    }

    /**
     * Step 7: Builds the result object.
     */
    private SchemaGenerationResult buildResult(
            FileAnalysisRequest request,
            StructureElement structure,
            Map<String, Object> jsonSchema,
            long startTime
    ) throws AnalyzerException {
        log.debug("Step 7: Building result");

        // Generate schema as string using appropriate generator
        String jsonSchemaString;
        if (request.getFileType() == FileType.CSV) {
            JsonSchema2PojoGenerator csvGenerator = new JsonSchema2PojoGenerator();
            jsonSchemaString = csvGenerator.generateSchemaAsString(structure, request);
        } else {
            jsonSchemaString = schemaGenerator.generateSchemaAsString(structure, request);
        }

        // Build metadata
        SchemaMetadata metadata = SchemaMetadata.builder()
                .schemaVersion("http://json-schema.org/draft-07/schema#")
                .rootElement(structure.getName())
                .sourceFileType(request.getFileType())
                .generatedAt(LocalDateTime.now())
                .totalElements(countElements(structure))
                .totalAttributes(countAttributes(structure))
                .arrayElements(countArrays(structure))
                .build();

        // Collect detected array fields
        List<String> arrayFields = collectArrayFields(structure);

        // Build result
        SchemaGenerationResult result = SchemaGenerationResult.builder()
                .schemaName(request.getSchemaName())
                .sourceFileType(request.getFileType())
                .jsonSchema(jsonSchema)
                .jsonSchemaAsString(jsonSchemaString)
                .metadata(metadata)
                .detectedArrayFields(arrayFields)
                .elementsAnalyzed(countElements(structure))
                .analysisTimeMs(System.currentTimeMillis() - startTime)
                .success(true)
                .build();

        log.debug("Result built successfully");
        return result;
    }

    // Utility methods

    private boolean hasSamples(FileAnalysisRequest request) {
        return request.getSampleFileContents() != null &&
               !request.getSampleFileContents().isEmpty();
    }

    private int countElements(StructureElement element) {
        if (element == null) {
            return 0;
        }

        int count = 1;
        if (element.hasChildren()) {
            for (StructureElement child : element.getChildren()) {
                count += countElements(child);
            }
        }
        return count;
    }

    private int countAttributes(StructureElement element) {
        if (element == null) {
            return 0;
        }

        int count = element.hasAttributes() ? element.getAttributes().size() : 0;
        if (element.hasChildren()) {
            for (StructureElement child : element.getChildren()) {
                count += countAttributes(child);
            }
        }
        return count;
    }

    private int countArrays(StructureElement element) {
        if (element == null) {
            return 0;
        }

        int count = element.isArray() ? 1 : 0;
        if (element.hasChildren()) {
            for (StructureElement child : element.getChildren()) {
                count += countArrays(child);
            }
        }
        return count;
    }

    private List<String> collectArrayFields(StructureElement element) {
        List<String> arrayFields = new ArrayList<>();
        collectArrayFieldsRecursive(element, "", arrayFields);
        return arrayFields;
    }

    private void collectArrayFieldsRecursive(
            StructureElement element,
            String path,
            List<String> arrayFields
    ) {
        if (element == null) {
            return;
        }

        String currentPath = path.isEmpty() ? element.getName() : path + "." + element.getName();

        if (element.isArray()) {
            arrayFields.add(currentPath);
        }

        if (element.hasChildren()) {
            for (StructureElement child : element.getChildren()) {
                collectArrayFieldsRecursive(child, currentPath, arrayFields);
            }
        }
    }
}
