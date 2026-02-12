package com.datasabai.services.schemaanalyzer.adapter;

import com.datasabai.hsb.sdk.core.SdkContext;
import com.datasabai.hsb.sdk.core.SdkModule;
import com.datasabai.hsb.sdk.core.SdkException;
import com.datasabai.hsb.sdk.core.capabilities.*;
import com.datasabai.services.schemaanalyzer.core.FileSchemaAnalyzer;
import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.SchemaGenerationResult;

import java.util.HashMap;
import java.util.Map;

/**
 * SDK Adapter for the File Schema Analyzer service.
 * <p>
 * <b>PURE JAVA IMPLEMENTATION - NO FRAMEWORK ANNOTATIONS</b>
 * </p>
 * <p>
 * This adapter integrates the File Schema Analyzer with the Datasabai HSB SDK
 * following the "Model B - Adapters" architecture. It implements the
 * {@link SdkModule} interface to provide CLI, REST, and UI access through
 * the SDK runtime.
 * </p>
 *
 * <h3>Architecture:</h3>
 * <pre>
 * SDK Runtime (Quarkus)
 *        ↓
 * FileSchemaAnalyzerAdapter (Pure Java)
 *        ↓
 * FileSchemaAnalyzer (Pure Java)
 *        ↓
 * Parsers (Pure Java)
 * </pre>
 *
 * <h3>Configuration via SdkContext:</h3>
 * <ul>
 *   <li><b>detectArrays</b>: Override default array detection (true/false)</li>
 *   <li><b>optimizeForBeanIO</b>: Override BeanIO optimization (true/false)</li>
 *   <li><b>parserOptions.*</b>: Parser-specific options</li>
 *     <ul>
 *       <li>parserOptions.delimiter (CSV, Variable-Length)</li>
 *       <li>parserOptions.hasHeader (CSV)</li>
 *       <li>parserOptions.strictMode (JSON)</li>
 *       <li>parserOptions.fieldDefinitions (Fixed-Length)</li>
 *       <li>parserOptions.tagValuePairs (Variable-Length)</li>
 *     </ul>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * // From SDK Runtime
 * SdkModule<FileAnalysisRequest, SchemaGenerationResult> module =
 *     new FileSchemaAnalyzerAdapter();
 *
 * FileAnalysisRequest request = FileAnalysisRequest.builder()
 *     .fileType(FileType.CSV)
 *     .fileContent("ID,Name,Price\n1,Product A,19.99\n2,Product B,29.99")
 *     .schemaName("Product")
 *     .build();
 *
 * SdkContext context = SdkContext.builder().build();
 * SchemaGenerationResult result = module.execute(request, context);
 * }</pre>
 *
 * <h3>Thread Safety:</h3>
 * <p>
 * This adapter is thread-safe. Multiple threads can call {@link #execute(FileAnalysisRequest, SdkContext)}
 * concurrently.
 * </p>
 *
 * @see SdkModule
 * @see FileSchemaAnalyzer
 */
public class FileSchemaAnalyzerAdapter implements SdkModule<FileAnalysisRequest, SchemaGenerationResult>,
            DescribableModule,
            VersionedModule,
            TypedModule<FileAnalysisRequest, SchemaGenerationResult>,
            ConfigurableModule {

    private final FileSchemaAnalyzer analyzer;

    /**
     * Creates a new adapter with a default FileSchemaAnalyzer instance.
     * <p>
     * NO DEPENDENCY INJECTION - Pure constructor initialization.
     * </p>
     */
    public FileSchemaAnalyzerAdapter() {
        this.analyzer = new FileSchemaAnalyzer();
    }

    /**
     * Creates a new adapter with a custom FileSchemaAnalyzer instance.
     * <p>
     * Useful for testing or custom configurations.
     * </p>
     *
     * @param analyzer file schema analyzer instance
     */
    public FileSchemaAnalyzerAdapter(FileSchemaAnalyzer analyzer) {
        if (analyzer == null) {
            throw new IllegalArgumentException("FileSchemaAnalyzer cannot be null");
        }
        this.analyzer = analyzer;
    }

    @Override
    public String name() {
        return "file-schema-analyzer";
    }

    @Override
    public String description() {
        return "Analyzes file schemas (CSV, JSON, Fixed-Length, Variable-Length) and generates JSON Schemas for BeanIO configuration";
    }

    @Override
    public String version() {
        return "1.0.0-SNAPSHOT";
    }

    @Override
    public SchemaGenerationResult execute(FileAnalysisRequest input, SdkContext context) throws SdkException {
        if (input == null) {
            throw new IllegalArgumentException("Input request cannot be null");
        }

        // Apply configuration from SdkContext if provided
        if (context != null) {
            applyContextConfiguration(input, context);
        }

        try {
            // Execute analysis
            return analyzer.analyze(input);

        } catch (AnalyzerException e) {
            // Wrap in Exception for SDK compatibility
            throw new SdkException(
                    "File schema analysis failed: " + e.getMessage(),
                    e
            );

        } catch (UnsupportedOperationException e) {
            // Handle stub parsers
            throw new SdkException(
                    "File type not yet supported: " + e.getMessage() +
                    " Available types: " + analyzer.getAvailableFileTypes(),
                    e
            );
        }
    }

    @Override
    public Class<FileAnalysisRequest> inputType() {
        return FileAnalysisRequest.class;
    }

    @Override
    public Class<SchemaGenerationResult> outputType() {
        return SchemaGenerationResult.class;
    }

    @Override
    public Map<String, String> configurationSchema() {
        Map<String, String> schema = new HashMap<>();

        // General configuration
        schema.put("detectArrays", "Enable automatic array detection (true/false, default: true)");

        // CSV parser options
        schema.put("parserOptions.delimiter", "CSV: Column delimiter (default: ,)");
        schema.put("parserOptions.hasHeader", "CSV: First row is header (true/false, default: true)");
        schema.put("parserOptions.encoding", "CSV: File encoding (default: UTF-8)");
        schema.put("parserOptions.quoteChar", "CSV: Quote character (default: \")");
        schema.put("parserOptions.escapeChar", "CSV: Escape character (default: \\)");
        schema.put("parserOptions.skipLines", "CSV: Lines to skip at beginning (default: 0)");
        schema.put("parserOptions.sampleRows", "CSV: Rows to sample for type inference (default: 100)");

        // JSON parser options
        schema.put("parserOptions.strictMode", "JSON: Enable strict validation (true/false, default: true)");
        schema.put("parserOptions.allowComments", "JSON: Allow comments (true/false, default: false)");
        schema.put("parserOptions.allowTrailingCommas", "JSON: Allow trailing commas (true/false, default: false)");

        // Fixed-Length parser options
        schema.put("parserOptions.descriptorFile", "Fixed-Length: Descriptor JSON content (default: null)");
        schema.put("parserOptions.fieldDefinitions", "Fixed-Length: Inline field definitions JSON (default: null)");
        schema.put("parserOptions.trimFields", "Fixed-Length: Trim whitespace (true/false, default: true)");
        schema.put("parserOptions.recordLength", "Fixed-Length: Expected record length (default: null)");

        // Variable-Length parser options
        schema.put("parserOptions.tagValuePairs", "Variable-Length: Enable tag-value mode (true/false, default: false)");
        schema.put("parserOptions.tagValueDelimiter", "Variable-Length: Tag-value delimiter (default: =)");

        return schema;
    }

    /**
     * Applies configuration from SdkContext to the request.
     * <p>
     * This method reads configuration values from the context and applies them
     * to the request if they are not already set.
     * </p>
     *
     * @param request request to configure
     * @param context SDK context with configuration
     */
    private void applyContextConfiguration(FileAnalysisRequest request, SdkContext context) {
        // Apply detectArrays if configured
        context.getConfig("detectArrays").ifPresent(value ->
            request.setDetectArrays(Boolean.parseBoolean(value))
        );

        // Apply parser options
        Map<String, String> parserOptions = new HashMap<>();
        if (request.getParserOptions() != null) {
            parserOptions.putAll(request.getParserOptions());
        }

        // Extract all parserOptions.* from context
        context.getAllConfigs().forEach((key, value) -> {
            if (key.startsWith("parserOptions.")) {
                String optionName = key.substring("parserOptions.".length());
                parserOptions.put(optionName, value);
            }
        });

        if (!parserOptions.isEmpty()) {
            request.setParserOptions(parserOptions);
        }
    }

    /**
     * Gets the underlying FileSchemaAnalyzer instance.
     * <p>
     * Useful for advanced usage or testing.
     * </p>
     *
     * @return file schema analyzer
     */
    public FileSchemaAnalyzer getAnalyzer() {
        return analyzer;
    }
}
