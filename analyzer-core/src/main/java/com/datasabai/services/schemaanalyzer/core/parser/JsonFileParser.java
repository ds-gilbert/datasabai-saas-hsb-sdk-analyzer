package com.datasabai.services.schemaanalyzer.core.parser;

import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.StructureElement;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for JSON files using Jackson library.
 * <p>
 * Directly maps JSON structures to the canonical StructureElement model.
 * Supports various JSON dialects through parser options.
 * </p>
 *
 * <h3>Parser Options:</h3>
 * <ul>
 *   <li><b>strictMode</b>: Enable strict JSON validation (default: "true")</li>
 *   <li><b>allowComments</b>: Allow single-line and multi-line comments (default: "false")</li>
 *   <li><b>allowTrailingCommas</b>: Allow trailing commas in arrays and objects (default: "false")</li>
 * </ul>
 *
 * <h3>Type Mapping:</h3>
 * <ul>
 *   <li>JSON Object maps to StructureElement type="object"</li>
 *   <li>JSON Array maps to StructureElement type="array"</li>
 *   <li>JSON String uses TypeInferenceUtil for refined types</li>
 *   <li>JSON Number (integer) maps to type="integer"</li>
 *   <li>JSON Number (decimal) maps to type="number"</li>
 *   <li>JSON Boolean maps to type="boolean"</li>
 *   <li>JSON Null maps to type="null"</li>
 * </ul>
 *
 * <h3>Structure Example:</h3>
 * <pre>{@code
 * JSON:
 * {
 *   "id": 123,
 *   "name": "Product A",
 *   "price": 19.99,
 *   "tags": ["electronics", "gadget"]
 * }
 *
 * Generates:
 * - Root: type="object", name=schemaName
 *   - id: type="integer"
 *   - name: type="string"
 *   - price: type="number"
 *   - tags: type="array"
 *     - item: type="string"
 * }</pre>
 *
 * @see FileParser
 * @see TypeInferenceUtil
 */
public class JsonFileParser implements FileParser {
    private static final Logger log = LoggerFactory.getLogger(JsonFileParser.class);

    private static final String DEFAULT_STRICT_MODE = "true";
    private static final String DEFAULT_ALLOW_COMMENTS = "false";
    private static final String DEFAULT_ALLOW_TRAILING_COMMAS = "false";

    @Override
    public FileType getSupportedFileType() {
        return FileType.JSON;
    }

    @Override
    public boolean canParse(FileAnalysisRequest request) {
        if (request == null || request.getFileType() != FileType.JSON) {
            return false;
        }

        String content = request.getFileContent();
        if (content == null || content.isBlank()) {
            return false;
        }

        // Basic JSON validation - check for opening brace or bracket
        String trimmed = content.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    @Override
    public StructureElement parse(FileAnalysisRequest request) throws AnalyzerException {
        log.debug("Starting JSON parsing for schema: {}", request.getSchemaName());

        try {
            String content = request.getFileContent();
            if (content == null || content.isBlank()) {
                throw new AnalyzerException("INVALID_JSON", FileType.JSON, "No file content provided");
            }

            // Get parser options
            boolean strictMode = Boolean.parseBoolean(request.getParserOption("strictMode", DEFAULT_STRICT_MODE));
            boolean allowComments = Boolean.parseBoolean(request.getParserOption("allowComments", DEFAULT_ALLOW_COMMENTS));
            boolean allowTrailingCommas = Boolean.parseBoolean(request.getParserOption("allowTrailingCommas", DEFAULT_ALLOW_TRAILING_COMMAS));

            log.debug("JSON options - strictMode: {}, allowComments: {}, allowTrailingCommas: {}",
                    strictMode, allowComments, allowTrailingCommas);

            // Configure ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            if (allowComments) {
                objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            }
            if (allowTrailingCommas) {
                objectMapper.enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            }
            if (!strictMode) {
                objectMapper.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
                objectMapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            }

            // Parse JSON
            JsonNode rootNode = objectMapper.readTree(content);

            // Convert to StructureElement
            StructureElement root = convertJsonNode(request.getSchemaName(), rootNode);

            log.debug("JSON parsing completed successfully");
            return root;

        } catch (AnalyzerException e) {
            throw e;
        } catch (Exception e) {
            throw new AnalyzerException("PARSE_ERROR", FileType.JSON,
                    "Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a JsonNode to a StructureElement.
     *
     * @param name name for the element
     * @param node JSON node to convert
     * @return StructureElement representation
     */
    private StructureElement convertJsonNode(String name, JsonNode node) {
        if (node.isObject()) {
            return convertObjectNode(name, node);
        } else if (node.isArray()) {
            return convertArrayNode(name, node);
        } else if (node.isTextual()) {
            return convertTextualNode(name, node);
        } else if (node.isNumber()) {
            return convertNumberNode(name, node);
        } else if (node.isBoolean()) {
            return convertBooleanNode(name, node);
        } else if (node.isNull()) {
            return convertNullNode(name);
        } else {
            // Unknown type - default to string
            return StructureElement.builder()
                    .name(name)
                    .type("string")
                    .build();
        }
    }

    /**
     * Converts a JSON object to StructureElement.
     */
    private StructureElement convertObjectNode(String name, JsonNode objectNode) {
        StructureElement element = StructureElement.builder()
                .name(name)
                .type("object")
                .build();

        // Process all fields
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            StructureElement childElement = convertJsonNode(fieldName, fieldValue);
            element.addChild(childElement);
        }

        return element;
    }

    /**
     * Converts a JSON array to StructureElement.
     */
    private StructureElement convertArrayNode(String name, JsonNode arrayNode) {
        StructureElement element = StructureElement.builder()
                .name(name)
                .type("array")
                .array(true)
                .build();

        // Analyze first element to determine item type
        // Empty arrays are ignored (no items child) to avoid generating empty POJOs
        if (arrayNode.size() > 0) {
            JsonNode firstElement = arrayNode.get(0);
            StructureElement itemElement = convertJsonNode("item", firstElement);
            element.addChild(itemElement);
        }

        return element;
    }

    /**
     * Converts a JSON text node to StructureElement.
     * Uses TypeInferenceUtil to refine the type.
     */
    private StructureElement convertTextualNode(String name, JsonNode textNode) {
        String value = textNode.asText();
        String inferredType = TypeInferenceUtil.inferType(value);

        return StructureElement.builder()
                .name(name)
                .type(inferredType)
                .build();
    }

    /**
     * Converts a JSON number node to StructureElement.
     * All types are inferred as "string" since we cannot reliably determine semantic types from raw data.
     */
    private StructureElement convertNumberNode(String name, JsonNode numberNode) {
        // Return "string" type for all numbers as type inference cannot reliably determine semantic types
        return StructureElement.builder()
                .name(name)
                .type("string")
                .build();
    }

    /**
     * Converts a JSON boolean node to StructureElement.
     * All types are inferred as "string" since we cannot reliably determine semantic types from raw data.
     */
    private StructureElement convertBooleanNode(String name, JsonNode booleanNode) {
        return StructureElement.builder()
                .name(name)
                .type("string")
                .build();
    }

    /**
     * Converts a JSON null node to StructureElement.
     */
    private StructureElement convertNullNode(String name) {
        return StructureElement.builder()
                .name(name)
                .type("null")
                .build();
    }

    @Override
    public StructureElement mergeStructures(List<StructureElement> structures) throws AnalyzerException {
        if (structures == null || structures.isEmpty()) {
            throw new AnalyzerException("MERGE_ERROR", FileType.JSON, "No structures to merge");
        }

        if (structures.size() == 1) {
            return structures.get(0);
        }

        log.debug("Merging {} JSON structures", structures.size());

        try {
            // Take first structure as base
            StructureElement base = structures.get(0);

            // If base is an object, merge all object structures
            if ("object".equals(base.getType())) {
                return mergeObjectStructures(structures);
            }
            // If base is an array, merge array structures
            else if ("array".equals(base.getType()) && base.isArray()) {
                return mergeArrayStructures(structures);
            }
            // For primitives, merge types
            else {
                return mergePrimitiveStructures(structures);
            }

        } catch (Exception e) {
            throw new AnalyzerException("MERGE_ERROR", FileType.JSON,
                    "Failed to merge structures: " + e.getMessage(), e);
        }
    }

    /**
     * Merges multiple object structures.
     */
    private StructureElement mergeObjectStructures(List<StructureElement> structures) {
        StructureElement base = structures.get(0);
        StructureElement merged = StructureElement.builder()
                .name(base.getName())
                .type("object")
                .build();

        // Collect all fields from all structures
        Map<String, StructureElement> allFields = new LinkedHashMap<>();

        for (StructureElement structure : structures) {
            if (!"object".equals(structure.getType())) {
                continue; // Skip non-object structures
            }

            for (StructureElement child : structure.getChildren()) {
                String fieldName = child.getName();

                if (allFields.containsKey(fieldName)) {
                    // Merge field types
                    StructureElement existing = allFields.get(fieldName);
                    StructureElement mergedField = mergeFieldTypes(existing, child);
                    allFields.put(fieldName, mergedField);
                } else {
                    // Add new field
                    allFields.put(fieldName, child);
                }
            }
        }

        // Add all merged fields
        for (StructureElement field : allFields.values()) {
            merged.addChild(field);
        }

        log.debug("Object merging completed - total fields: {}", allFields.size());
        return merged;
    }

    /**
     * Merges multiple array structures.
     */
    private StructureElement mergeArrayStructures(List<StructureElement> structures) {
        StructureElement base = structures.get(0);
        StructureElement merged = StructureElement.builder()
                .name(base.getName())
                .type("array")
                .array(true)
                .build();

        // Collect all item elements
        StructureElement mergedItem = null;

        for (StructureElement structure : structures) {
            if (!"array".equals(structure.getType()) || !structure.isArray()) {
                continue;
            }

            if (!structure.getChildren().isEmpty()) {
                StructureElement item = structure.getChildren().get(0);

                if (mergedItem == null) {
                    mergedItem = item;
                } else {
                    mergedItem = mergeFieldTypes(mergedItem, item);
                }
            }
        }

        if (mergedItem != null) {
            merged.addChild(mergedItem);
        }

        log.debug("Array merging completed");
        return merged;
    }

    /**
     * Merges primitive structures by widening types.
     */
    private StructureElement mergePrimitiveStructures(List<StructureElement> structures) {
        StructureElement base = structures.get(0);
        String mergedType = base.getType();

        for (int i = 1; i < structures.size(); i++) {
            String currentType = structures.get(i).getType();
            mergedType = TypeInferenceUtil.mergeTypes(mergedType, currentType);
        }

        return StructureElement.builder()
                .name(base.getName())
                .type(mergedType)
                .build();
    }

    /**
     * Merges two field structures.
     */
    private StructureElement mergeFieldTypes(StructureElement field1, StructureElement field2) {
        String name = field1.getName();

        // If both are objects, merge recursively
        if ("object".equals(field1.getType()) && "object".equals(field2.getType())) {
            return mergeObjectStructures(List.of(field1, field2));
        }
        // If both are arrays, merge recursively
        else if ("array".equals(field1.getType()) && field1.isArray() &&
                "array".equals(field2.getType()) && field2.isArray()) {
            return mergeArrayStructures(List.of(field1, field2));
        }
        // Otherwise, merge types
        else {
            String mergedType = TypeInferenceUtil.mergeTypes(field1.getType(), field2.getType());
            return StructureElement.builder()
                    .name(name)
                    .type(mergedType)
                    .build();
        }
    }

    @Override
    public Map<String, String> getAvailableOptions() {
        Map<String, String> options = new HashMap<>();
        options.put("strictMode", "Enable strict JSON validation (true/false, default: true)");
        options.put("allowComments", "Allow // and /* */ comments (true/false, default: false)");
        options.put("allowTrailingCommas", "Allow trailing commas in arrays/objects (true/false, default: false)");
        return options;
    }
}
