package com.datasabai.services.schemaanalyzer.app;

import com.datasabai.services.schemaanalyzer.core.FileSchemaAnalyzer;
import com.datasabai.services.schemaanalyzer.core.model.*;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for the File Schema Analyzer.
 * <p>
 * Provides endpoints for analyzing files and generating JSON Schemas.
 * </p>
 */
@Path("/api/analyzer")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnalyzerResource {
    private static final Logger log = LoggerFactory.getLogger(AnalyzerResource.class);

    @Inject
    FileSchemaAnalyzer analyzer;

    /**
     * Analyzes a file from JSON request body.
     *
     * POST /api/analyzer/analyze
     * Body: FileAnalysisRequest
     * Returns: SchemaGenerationResult
     */
    @POST
    @Path("/analyze")
    public Response analyze(FileAnalysisRequest request) {
        log.info("Analyzing file: {} (type: {})", request.getSchemaName(), request.getFileType());

        try {
            SchemaGenerationResult result = analyzer.analyze(request);
            return Response.ok(result).build();

        } catch (UnsupportedOperationException e) {
            log.warn("Unsupported file type: {}", request.getFileType());
            return Response.status(Response.Status.NOT_IMPLEMENTED)
                    .entity(Map.of(
                            "error", "UNSUPPORTED_FILE_TYPE",
                            "message", e.getMessage(),
                            "fileType", request.getFileType().name(),
                            "availableTypes", analyzer.getAvailableFileTypes()
                    ))
                    .build();

        } catch (AnalyzerException e) {
            log.error("Analysis failed: {}", e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", e.getErrorCode(),
                            "message", e.getMessage(),
                            "fileType", e.getFileType() != null ? e.getFileType().name() : "UNKNOWN"
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "INTERNAL_ERROR",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * Analyzes a file from multipart upload.
     *
     * POST /api/analyzer/analyze-file
     * Content-Type: multipart/form-data
     *
     * Form parameters:
     * - file: The file to analyze (required)
     * - schemaName: Name for the generated schema (optional)
     * - fileType: Type of file (CSV, JSON, FIXED_LENGTH, VARIABLE_LENGTH) (optional - auto-detected if not provided)
     * - detectArrays: Whether to detect arrays (default: true)
     * - descriptorFile: JSON descriptor file for FIXED_LENGTH files (optional)
     * - fieldDefinitions: Inline JSON field definitions for FIXED_LENGTH files (optional)
     * - parserOptions: Additional parser options as JSON (optional)
     *
     * Returns: SchemaGenerationResult
     */
    @POST
    @Path("/analyze-file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response analyzeFile(
            @RestForm("file") FileUpload file,
            @RestForm("schemaName") String schemaName,
            @RestForm("fileType") String fileTypeStr,
            @RestForm("detectArrays") @DefaultValue("true") boolean detectArrays,
            @RestForm("descriptorFile") FileUpload descriptorFile,
            @RestForm("fieldDefinitions") String fieldDefinitions,
            @RestForm("parserOptions") String parserOptionsJson
    ) {
        log.info("Analyzing uploaded file: {}", file.fileName());

        try {
            // Determine file type
            FileType fileType;
            if (fileTypeStr != null && !fileTypeStr.isBlank()) {
                fileType = FileType.valueOf(fileTypeStr.toUpperCase());
            } else {
                // Try to infer from file extension
                String fileName = file.fileName();
                String extension = fileName.substring(fileName.lastIndexOf(".") + 1);
                fileType = FileType.fromExtension(extension);
            }

            // Read file content
            byte[] fileBytes = Files.readAllBytes(file.uploadedFile());
            String fileContent = null;

            // For text-based formats, convert to string
            // All supported file types (CSV, JSON, FIXED_LENGTH, VARIABLE_LENGTH) are text-based
            fileContent = new String(fileBytes);

            // Build parser options
            Map<String, String> parserOptions = new HashMap<>();

            // Add descriptor file content if provided
            if (descriptorFile != null && descriptorFile.uploadedFile() != null) {
                String descriptorContent = Files.readString(descriptorFile.uploadedFile());
                parserOptions.put("descriptorFile", descriptorContent);
                log.debug("Descriptor file provided: {} bytes", descriptorContent.length());
            }

            // Add inline field definitions if provided
            if (fieldDefinitions != null && !fieldDefinitions.isBlank()) {
                parserOptions.put("fieldDefinitions", fieldDefinitions);
                log.debug("Inline field definitions provided");
            }

            // Parse additional parser options from JSON if provided
            if (parserOptionsJson != null && !parserOptionsJson.isBlank()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, String> additionalOptions = mapper.readValue(parserOptionsJson, Map.class);
                    parserOptions.putAll(additionalOptions);
                    log.debug("Additional parser options provided: {}", additionalOptions.keySet());
                } catch (Exception e) {
                    log.warn("Failed to parse parserOptions JSON, ignoring: {}", e.getMessage());
                }
            }

            // Build request
            FileAnalysisRequest.Builder requestBuilder = FileAnalysisRequest.builder()
                    .fileType(fileType)
                    .schemaName(schemaName != null ? schemaName : "GeneratedSchema")
                    .detectArrays(detectArrays);

            if (fileContent != null) {
                requestBuilder.fileContent(fileContent);
            } else {
                requestBuilder.fileBytes(fileBytes);
            }

            // Add parser options if any
            if (!parserOptions.isEmpty()) {
                requestBuilder.parserOptions(parserOptions);
            }

            FileAnalysisRequest request = requestBuilder.build();

            // Analyze
            SchemaGenerationResult result = analyzer.analyze(request);
            return Response.ok(result).build();

        } catch (UnsupportedOperationException e) {
            log.warn("Unsupported file type");
            return Response.status(Response.Status.NOT_IMPLEMENTED)
                    .entity(Map.of(
                            "error", "UNSUPPORTED_FILE_TYPE",
                            "message", e.getMessage(),
                            "availableTypes", analyzer.getAvailableFileTypes()
                    ))
                    .build();

        } catch (IOException e) {
            log.error("Failed to read file", e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "FILE_READ_ERROR",
                            "message", "Failed to read uploaded file: " + e.getMessage()
                    ))
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of(
                            "error", "INTERNAL_ERROR",
                            "message", e.getMessage()
                    ))
                    .build();
        }
    }

    /**
     * Gets list of supported file types.
     *
     * GET /api/analyzer/supported-types
     * Returns: List<FileType>
     */
    @GET
    @Path("/supported-types")
    public Response getSupportedTypes() {
        List<FileType> availableTypes = analyzer.getAvailableFileTypes();
        List<FileType> registeredTypes = analyzer.getRegisteredFileTypes();

        Map<String, Object> response = new HashMap<>();
        response.put("available", availableTypes);
        response.put("registered", registeredTypes);
        response.put("availableCount", availableTypes.size());
        response.put("registeredCount", registeredTypes.size());

        return Response.ok(response).build();
    }

    /**
     * Gets parser options for a specific file type.
     *
     * GET /api/analyzer/parser-options/{type}
     * Returns: Map<String, String>
     */
    @GET
    @Path("/parser-options/{type}")
    public Response getParserOptions(@PathParam("type") String typeStr) {
        try {
            FileType fileType = FileType.valueOf(typeStr.toUpperCase());
            Map<String, String> options = analyzer.getParserOptions(fileType);

            return Response.ok(Map.of(
                    "fileType", fileType.name(),
                    "options", options
            )).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "INVALID_FILE_TYPE",
                            "message", "Unknown file type: " + typeStr,
                            "availableTypes", FileType.values()
                    ))
                    .build();
        }
    }

    /**
     * Validates a JSON Schema.
     *
     * POST /api/analyzer/validate-schema
     * Body: { "schema": {...} }
     * Returns: { "valid": true/false, "errors": [...] }
     */
    @POST
    @Path("/validate-schema")
    public Response validateSchema(Map<String, Object> request) {
        if (!request.containsKey("schema")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "MISSING_SCHEMA",
                            "message", "Request must contain a 'schema' field"
                    ))
                    .build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) request.get("schema");

        // Basic validation
        List<String> errors = new java.util.ArrayList<>();

        if (!schema.containsKey("$schema")) {
            errors.add("Missing '$schema' field");
        }
        if (!schema.containsKey("type") && !schema.containsKey("properties")) {
            errors.add("Missing 'type' or 'properties' field");
        }

        boolean valid = errors.isEmpty();

        return Response.ok(Map.of(
                "valid", valid,
                "errors", errors
        )).build();
    }

    /**
     * Health check endpoint.
     *
     * GET /api/analyzer/health
     * Returns: { "status": "UP", "supportedTypes": [...] }
     */
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
                "status", "UP",
                "service", "File Schema Analyzer",
                "version", "1.0.0-SNAPSHOT",
                "supportedTypes", analyzer.getAvailableFileTypes(),
                "registeredTypes", analyzer.getRegisteredFileTypes()
        )).build();
    }
}
