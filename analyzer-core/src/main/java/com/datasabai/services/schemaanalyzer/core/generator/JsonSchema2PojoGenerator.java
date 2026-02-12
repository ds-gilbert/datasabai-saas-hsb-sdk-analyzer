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
 * Generates JSON Schema for jsonschema2pojo with Header + Records structure.
 * <p>
 * All CSV columns appear in both Header and Record definitions (flat, no nesting).
 * Uses {@code $defs} with {@code $ref} for jsonschema2pojo to generate 3 POJOs:
 * root, Header, and Record.
 * </p>
 *
 * <h3>Structure Generated:</h3>
 * <pre>{@code
 * {
 *   "$schema": "http://json-schema.org/draft-07/schema#",
 *   "title": "MySchema",
 *   "type": "object",
 *   "properties": {
 *     "header": { "$ref": "#/$defs/Header" },
 *     "records": { "type": "array", "items": { "$ref": "#/$defs/Record" } }
 *   },
 *   "$defs": {
 *     "Header": { "title": "Header", "type": "object", "properties": { ... } },
 *     "Record": { "title": "Record", "type": "object", "properties": { ... } }
 *   }
 * }
 * }</pre>
 */
public class JsonSchema2PojoGenerator {
    private static final Logger log = LoggerFactory.getLogger(JsonSchema2PojoGenerator.class);
    private static final String SCHEMA_VERSION = "http://json-schema.org/draft-07/schema#";

    private final ObjectMapper objectMapper;

    public JsonSchema2PojoGenerator() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Generates JSON Schema with Header + Records structure for jsonschema2pojo.
     * All CSV columns appear in both Header and Record.
     */
    public Map<String, Object> generateSchema(StructureElement root, FileAnalysisRequest request)
            throws AnalyzerException {
        if (root == null) {
            throw new AnalyzerException("GENERATION_ERROR", "Root element cannot be null");
        }

        log.debug("Generating jsonschema2pojo schema for: {}", request.getSchemaName());

        // Get CSV structure
        if (!root.hasChildren() || root.getChildren().isEmpty()) {
            throw new AnalyzerException("GENERATION_ERROR", "No structure found in CSV");
        }

        StructureElement item = root.getChildren().get(0);
        if (!"object".equals(item.getType())) {
            throw new AnalyzerException("GENERATION_ERROR", "Expected object type for CSV row");
        }

        // Build flat properties from all fields (shared by Header and Record)
        Map<String, Object> flatProperties = buildFlatProperties(item);

        // Root schema
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", SCHEMA_VERSION);
        schema.put("title", request.getSchemaName());
        schema.put("type", "object");

        // Root properties: header + records
        Map<String, Object> rootProperties = new LinkedHashMap<>();

        Map<String, Object> headerRef = new LinkedHashMap<>();
        headerRef.put("$ref", "#/$defs/Header");
        rootProperties.put("header", headerRef);

        Map<String, Object> recordsProperty = new LinkedHashMap<>();
        recordsProperty.put("type", "array");
        Map<String, Object> recordRef = new LinkedHashMap<>();
        recordRef.put("$ref", "#/$defs/Record");
        recordsProperty.put("items", recordRef);
        rootProperties.put("records", recordsProperty);

        schema.put("properties", rootProperties);

        // $defs: Header and Record with same flat properties
        Map<String, Object> defs = new LinkedHashMap<>();

        Map<String, Object> headerDef = new LinkedHashMap<>();
        headerDef.put("title", "Header");
        headerDef.put("type", "object");
        headerDef.put("properties", flatProperties);
        defs.put("Header", headerDef);

        Map<String, Object> recordDef = new LinkedHashMap<>();
        recordDef.put("title", "Record");
        recordDef.put("type", "object");
        recordDef.put("properties", flatProperties);
        defs.put("Record", recordDef);

        schema.put("$defs", defs);

        log.debug("Schema generated with {} properties in Header and Record", flatProperties.size());
        return schema;
    }

    /**
     * Builds flat properties map from all CSV columns.
     * Handles duplicates by appending a number (e.g., departmentDescription2).
     */
    private Map<String, Object> buildFlatProperties(StructureElement item) {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Integer> fieldNameCounts = new HashMap<>();

        for (StructureElement field : item.getChildren()) {
            String columnName = field.getName();
            String baseName = columnNameToCamelCase(columnName);

            int count = fieldNameCounts.getOrDefault(baseName, 0) + 1;
            fieldNameCounts.put(baseName, count);
            String flatFieldName = count == 1 ? baseName : baseName + count;

            Map<String, Object> fieldDef = new LinkedHashMap<>();
            fieldDef.put("type", mapJsonSchemaType(field.getType()));
            fieldDef.put("description", "CSV column: " + columnName);

            properties.put(flatFieldName, fieldDef);
        }

        return properties;
    }

    /**
     * Generates schema as formatted JSON string.
     */
    public String generateSchemaAsString(StructureElement root, FileAnalysisRequest request)
            throws AnalyzerException {
        Map<String, Object> schema = generateSchema(root, request);
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new AnalyzerException(
                    "SERIALIZATION_ERROR",
                    "Failed to serialize jsonschema2pojo schema: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Converts a CSV column name to camelCase.
     * Examples:
     *   ACCOUNTS_BATCH.NUMBER -> accountsBatchNumber
     *   REPORT.DOC_NUMBER -> reportDocNumber
     *   EXPENSE.TYPE_TOWN/CITY -> expenseTypeTownCity
     */
    private String columnNameToCamelCase(String columnName) {
        String normalized = columnName.replaceAll("[^A-Za-z0-9_]", "_");
        return toCamelCase(normalized);
    }

    private String mapJsonSchemaType(String internalType) {
        if (internalType == null) {
            return "string";
        }
        return switch (internalType) {
            case "integer" -> "integer";
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "null" -> "string";
            default -> "string";
        };
    }

    private String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        String[] parts = str.split("[_\\s]+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase();
            if (i == 0) {
                result.append(part);
            } else if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1));
                }
            }
        }
        return result.toString();
    }
}
