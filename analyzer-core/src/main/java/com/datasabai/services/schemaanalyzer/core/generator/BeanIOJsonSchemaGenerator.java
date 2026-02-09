package com.datasabai.services.schemaanalyzer.core.generator;

import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.StructureElement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generates a BeanIO-optimized JSON Schema from CSV structure.
 * <p>
 * This generator creates a JSON Schema that groups CSV columns by their segment prefix
 * (e.g., ACCOUNTS_BATCH, REPORT, ACCOUNT_ENTRY) to facilitate BeanIO XML mapping generation.
 * </p>
 *
 * <h3>Key Features:</h3>
 * <ul>
 *   <li>Groups fields by segment prefix (e.g., "ACCOUNTS_BATCH.NUMBER" → segment "ACCOUNTS_BATCH", field "NUMBER")</li>
 *   <li>Preserves field position information for BeanIO mapping</li>
 *   <li>Includes CSV format metadata (delimiter, quote char, etc.)</li>
 *   <li>Generates structure ready for BeanIO XML generation</li>
 * </ul>
 *
 * <h3>Example Output Structure:</h3>
 * <pre>{@code
 * {
 *   "$schema": "http://json-schema.org/draft-07/schema#",
 *   "title": "CSV_ACCOUNTING_CANONICAL",
 *   "type": "object",
 *   "x-beanio-config": {
 *     "format": "csv",
 *     "delimiter": ";",
 *     "quoteChar": "\"",
 *     "recordName": "accountEntry"
 *   },
 *   "properties": {
 *     "ACCOUNTS_BATCH": {
 *       "type": "object",
 *       "x-segment": true,
 *       "properties": {
 *         "NUMBER": { "type": "string", "x-position": 0 },
 *         "LEDGER_TYPE": { "type": "string", "x-position": 1 }
 *       }
 *     },
 *     "REPORT": {
 *       "type": "object",
 *       "x-segment": true,
 *       "properties": {
 *         "DOC_NUMBER": { "type": "string", "x-position": 3 }
 *       }
 *     }
 *   }
 * }
 * }</pre>
 */
public class BeanIOJsonSchemaGenerator {
    private static final Logger log = LoggerFactory.getLogger(BeanIOJsonSchemaGenerator.class);
    private static final String SCHEMA_VERSION = "http://json-schema.org/draft-07/schema#";

    private final ObjectMapper objectMapper;

    public BeanIOJsonSchemaGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Generates a BeanIO-optimized JSON Schema from a CSV structure.
     *
     * @param root root structure element (should be array with item children)
     * @param request original analysis request
     * @return JSON Schema as a map
     * @throws AnalyzerException if generation fails
     */
    public Map<String, Object> generateSchema(StructureElement root, FileAnalysisRequest request)
            throws AnalyzerException {
        if (root == null) {
            throw new AnalyzerException("GENERATION_ERROR", "Root element cannot be null");
        }

        log.debug("Generating BeanIO-optimized JSON Schema for: {}", request.getSchemaName());

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", SCHEMA_VERSION);
        schema.put("$id", "urn:csv:accounting:canonical:beanio-mapping");
        schema.put("title", request.getSchemaName());
        schema.put("description", "BeanIO-optimized JSON Schema for CSV mapping");
        schema.put("type", "object");

        // Add BeanIO configuration metadata
        Map<String, Object> beanioConfig = new LinkedHashMap<>();
        beanioConfig.put("format", "csv");
        beanioConfig.put("delimiter", request.getParserOption("delimiter", ","));
        beanioConfig.put("quoteChar", request.getParserOption("quoteChar", "\""));
        beanioConfig.put("recordName", toRecordName(request.getSchemaName()));
        beanioConfig.put("strict", true);
        schema.put("x-beanio-config", beanioConfig);

        // Add source metadata
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", request.getFileType().name());
        metadata.put("generatedBy", "File Schema Analyzer - BeanIO Edition");
        metadata.put("model", "segmented-flat-file");
        schema.put("x-metadata", metadata);

        // Get the item element (CSV row structure)
        if (!root.hasChildren() || root.getChildren().isEmpty()) {
            throw new AnalyzerException("GENERATION_ERROR", "No structure found in CSV");
        }

        StructureElement item = root.getChildren().get(0);
        if (!"object".equals(item.getType())) {
            throw new AnalyzerException("GENERATION_ERROR", "Expected object type for CSV row");
        }

        // Group fields by segment and generate properties
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Map<String, FieldInfo>> segments = groupFieldsBySegment(item);

        int globalPosition = 0;
        for (Map.Entry<String, Map<String, FieldInfo>> segmentEntry : segments.entrySet()) {
            String segmentName = segmentEntry.getKey();
            Map<String, FieldInfo> fields = segmentEntry.getValue();

            Map<String, Object> segmentSchema = new LinkedHashMap<>();
            segmentSchema.put("type", "object");
            segmentSchema.put("description", segmentName + " segment");
            segmentSchema.put("x-segment", true);

            Map<String, Object> segmentProperties = new LinkedHashMap<>();
            List<String> requiredFields = new ArrayList<>();

            for (Map.Entry<String, FieldInfo> fieldEntry : fields.entrySet()) {
                String fieldName = fieldEntry.getKey();
                FieldInfo fieldInfo = fieldEntry.getValue();

                Map<String, Object> fieldSchema = new LinkedHashMap<>();
                fieldSchema.put("type", fieldInfo.type);
                fieldSchema.put("x-position", globalPosition);
                fieldSchema.put("x-csv-column", fieldInfo.originalName);

                // Add field description if available
                fieldSchema.put("description", "Column: " + fieldInfo.originalName);

                segmentProperties.put(fieldName, fieldSchema);

                // Mark non-null fields as required
                if (!"null".equals(fieldInfo.type)) {
                    requiredFields.add(fieldName);
                }

                globalPosition++;
            }

            segmentSchema.put("properties", segmentProperties);
            if (!requiredFields.isEmpty()) {
                segmentSchema.put("required", requiredFields);
            }

            properties.put(segmentName, segmentSchema);
        }

        schema.put("properties", properties);

        // Add required segments (segments with at least one non-null field)
        List<String> requiredSegments = new ArrayList<>();
        for (Map.Entry<String, Map<String, FieldInfo>> segmentEntry : segments.entrySet()) {
            boolean hasNonNullFields = segmentEntry.getValue().values().stream()
                    .anyMatch(field -> !"null".equals(field.type));
            if (hasNonNullFields) {
                requiredSegments.add(segmentEntry.getKey());
            }
        }
        if (!requiredSegments.isEmpty()) {
            schema.put("required", requiredSegments);
        }

        log.debug("BeanIO JSON Schema generated successfully - {} segments, {} total fields",
                segments.size(), globalPosition);

        return schema;
    }

    /**
     * Generates a JSON Schema and returns it as a formatted string.
     *
     * @param root root structure element
     * @param request original analysis request
     * @return JSON Schema as formatted JSON string
     * @throws AnalyzerException if generation fails
     */
    public String generateSchemaAsString(StructureElement root, FileAnalysisRequest request)
            throws AnalyzerException {
        Map<String, Object> schema = generateSchema(root, request);
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new AnalyzerException(
                    "SERIALIZATION_ERROR",
                    "Failed to serialize BeanIO JSON Schema: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Groups CSV fields by their segment prefix.
     * <p>
     * Example: "ACCOUNTS_BATCH.NUMBER" → segment="ACCOUNTS_BATCH", field="NUMBER"
     * </p>
     */
    private Map<String, Map<String, FieldInfo>> groupFieldsBySegment(StructureElement item) {
        Map<String, Map<String, FieldInfo>> segments = new LinkedHashMap<>();

        for (StructureElement field : item.getChildren()) {
            String columnName = field.getName();
            String segmentName;
            String fieldName;

            // Extract segment and field name
            if (columnName.contains(".")) {
                int dotIndex = columnName.indexOf(".");
                segmentName = columnName.substring(0, dotIndex);
                fieldName = columnName.substring(dotIndex + 1);
            } else {
                // If no dot, treat as a standalone segment
                segmentName = "GENERAL";
                fieldName = columnName;
            }

            // Create segment if not exists
            segments.computeIfAbsent(segmentName, k -> new LinkedHashMap<>());

            // Add field to segment
            FieldInfo fieldInfo = new FieldInfo();
            fieldInfo.originalName = columnName;
            fieldInfo.type = field.getType();

            segments.get(segmentName).put(fieldName, fieldInfo);
        }

        return segments;
    }

    /**
     * Converts schema name to BeanIO record name (camelCase).
     */
    private String toRecordName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            return "record";
        }

        // Convert to camelCase: CSV_ACCOUNTING_CANONICAL → csvAccountingCanonical
        String[] parts = schemaName.split("_");
        StringBuilder recordName = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase();
            if (i == 0) {
                recordName.append(part);
            } else {
                recordName.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    recordName.append(part.substring(1));
                }
            }
        }

        return recordName.toString();
    }

    /**
     * Internal class to hold field information.
     */
    private static class FieldInfo {
        String originalName;
        String type;
    }
}
