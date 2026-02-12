package com.datasabai.services.schemaanalyzer.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Request for file schema analysis.
 * <p>
 * This is the main input to the File Schema Analyzer service.
 * It supports multiple file types and provides configuration options
 * for the analysis process.
 * </p>
 *
 * <h3>Usage Examples:</h3>
 *
 * <h4>XML Analysis:</h4>
 * <pre>{@code
 * FileAnalysisRequest request = FileAnalysisRequest.builder()
 *     .fileType(FileType.XML)
 *     .fileContent("<customer><id>123</id></customer>")
 *     .schemaName("Customer")
 *     .detectArrays(true)
 *     .parserOption("preserveNamespaces", "true")
 *     .build();
 * }</pre>
 *
 * <h4>Excel Analysis (future):</h4>
 * <pre>{@code
 * FileAnalysisRequest request = FileAnalysisRequest.builder()
 *     .fileType(FileType.EXCEL)
 *     .fileBytes(excelFileBytes)
 *     .schemaName("SalesData")
 *     .parserOption("sheetName", "Sheet1")
 *     .parserOption("startRow", "2")
 *     .build();
 * }</pre>
 *
 * <h4>CSV Analysis (future):</h4>
 * <pre>{@code
 * FileAnalysisRequest request = FileAnalysisRequest.builder()
 *     .fileType(FileType.CSV)
 *     .fileContent(csvContent)
 *     .schemaName("Products")
 *     .parserOption("delimiter", ";")
 *     .parserOption("hasHeader", "true")
 *     .build();
 * }</pre>
 */
public class FileAnalysisRequest {
    private FileType fileType;
    private String fileContent;
    private byte[] fileBytes;
    private String schemaName;
    private List<String> sampleFileContents;
    private boolean detectArrays;
    private Map<String, String> parserOptions;
    private Map<String, String> typeOverrides;

    public FileAnalysisRequest() {
        this.sampleFileContents = new ArrayList<>();
        this.detectArrays = true;
        this.parserOptions = new HashMap<>();
        this.typeOverrides = new HashMap<>();
    }

    /**
     * Builder for fluent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates this request.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (fileType == null) {
            throw new IllegalArgumentException("fileType cannot be null");
        }
        if ((fileContent == null || fileContent.isBlank()) && (fileBytes == null || fileBytes.length == 0)) {
            throw new IllegalArgumentException("Either fileContent or fileBytes must be provided");
        }
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("schemaName cannot be null or blank");
        }
    }

    // Getters and setters

    /**
     * Gets the type of file to analyze.
     *
     * @return file type
     */
    public FileType getFileType() {
        return fileType;
    }

    public void setFileType(FileType fileType) {
        this.fileType = fileType;
    }

    /**
     * Gets the file content as a string (for text-based formats like XML, CSV, JSON, TXT).
     *
     * @return file content
     */
    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    /**
     * Gets the file content as bytes (for binary formats like Excel).
     *
     * @return file bytes
     */
    public byte[] getFileBytes() {
        return fileBytes;
    }

    public void setFileBytes(byte[] fileBytes) {
        this.fileBytes = fileBytes;
    }

    /**
     * Gets the name for the generated schema.
     *
     * @return schema name
     */
    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    /**
     * Gets additional sample files of the same type for better schema inference.
     *
     * @return list of sample file contents
     */
    public List<String> getSampleFileContents() {
        return sampleFileContents;
    }

    public void setSampleFileContents(List<String> sampleFileContents) {
        this.sampleFileContents = sampleFileContents != null ? sampleFileContents : new ArrayList<>();
    }

    /**
     * Checks if array detection is enabled.
     *
     * @return true if should detect arrays
     */
    public boolean isDetectArrays() {
        return detectArrays;
    }

    public void setDetectArrays(boolean detectArrays) {
        this.detectArrays = detectArrays;
    }


    /**
     * Gets parser-specific options.
     * <p>
     * Examples:
     * <ul>
     *   <li>XML: "preserveNamespaces", "includeAttributes"</li>
     *   <li>CSV: "delimiter", "hasHeader", "encoding"</li>
     *   <li>Excel: "sheetName", "startRow"</li>
     * </ul>
     *
     * @return parser options map
     */
    public Map<String, String> getParserOptions() {
        return parserOptions;
    }

    public void setParserOptions(Map<String, String> parserOptions) {
        this.parserOptions = parserOptions != null ? parserOptions : new HashMap<>();
    }

    /**
     * Gets type overrides for manual type specification.
     * <p>
     * Key: field path (e.g., "customer.orders.order.id")
     * Value: type (e.g., "integer", "string", "date")
     * </p>
     *
     * @return type overrides map
     */
    public Map<String, String> getTypeOverrides() {
        return typeOverrides;
    }

    public void setTypeOverrides(Map<String, String> typeOverrides) {
        this.typeOverrides = typeOverrides != null ? typeOverrides : new HashMap<>();
    }

    /**
     * Gets a parser option value.
     *
     * @param key option key
     * @return option value or null
     */
    public String getParserOption(String key) {
        return parserOptions != null ? parserOptions.get(key) : null;
    }

    /**
     * Gets a parser option value with default.
     *
     * @param key option key
     * @param defaultValue default value if not found
     * @return option value or default
     */
    public String getParserOption(String key, String defaultValue) {
        String value = getParserOption(key);
        return value != null ? value : defaultValue;
    }

    // Builder class
    public static class Builder {
        private final FileAnalysisRequest request;

        private Builder() {
            this.request = new FileAnalysisRequest();
        }

        public Builder fileType(FileType fileType) {
            request.fileType = fileType;
            return this;
        }

        public Builder fileContent(String fileContent) {
            request.fileContent = fileContent;
            return this;
        }

        public Builder fileBytes(byte[] fileBytes) {
            request.fileBytes = fileBytes;
            return this;
        }

        public Builder schemaName(String schemaName) {
            request.schemaName = schemaName;
            return this;
        }

        public Builder sampleFileContents(List<String> sampleFileContents) {
            request.sampleFileContents = sampleFileContents;
            return this;
        }

        public Builder addSampleFile(String sampleContent) {
            if (request.sampleFileContents == null) {
                request.sampleFileContents = new ArrayList<>();
            }
            request.sampleFileContents.add(sampleContent);
            return this;
        }

        public Builder detectArrays(boolean detectArrays) {
            request.detectArrays = detectArrays;
            return this;
        }

        public Builder parserOptions(Map<String, String> parserOptions) {
            request.parserOptions = parserOptions;
            return this;
        }

        public Builder parserOption(String key, String value) {
            if (request.parserOptions == null) {
                request.parserOptions = new HashMap<>();
            }
            request.parserOptions.put(key, value);
            return this;
        }

        public Builder typeOverrides(Map<String, String> typeOverrides) {
            request.typeOverrides = typeOverrides;
            return this;
        }

        public Builder typeOverride(String path, String type) {
            if (request.typeOverrides == null) {
                request.typeOverrides = new HashMap<>();
            }
            request.typeOverrides.put(path, type);
            return this;
        }

        public FileAnalysisRequest build() {
            request.validate();
            return request;
        }
    }
}
