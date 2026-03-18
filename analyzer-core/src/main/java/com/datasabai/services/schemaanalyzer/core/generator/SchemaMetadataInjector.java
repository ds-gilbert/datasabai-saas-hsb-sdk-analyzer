package com.datasabai.services.schemaanalyzer.core.generator;

import com.datasabai.services.schemaanalyzer.core.model.XSchemaMetadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility to inject {@code x-schemaMetadata} into a generated JSON Schema map.
 * <p>
 * Inserts the metadata at the correct position in the LinkedHashMap:
 * after {@code $schema}, {@code title}, {@code description} and before {@code type}.
 * </p>
 */
public final class SchemaMetadataInjector {

    private static final String X_SCHEMA_METADATA = "x-schemaMetadata";
    private static final Set<String> HEADER_KEYS = Set.of("$schema", "title", "description");

    private SchemaMetadataInjector() {
    }

    /**
     * Injects {@code x-schemaMetadata} into the schema map.
     *
     * @param schema   the JSON Schema as a LinkedHashMap (order-preserving)
     * @param metadata the metadata to inject, or null to skip
     * @return the schema map with metadata injected, or the original map if metadata is null
     */
    public static Map<String, Object> inject(Map<String, Object> schema, XSchemaMetadata metadata) {
        if (metadata == null || schema == null) {
            return schema;
        }

        metadata.populateRagFields();

        Map<String, Object> metadataMap = metadata.toMap();
        if (metadataMap.isEmpty()) {
            return schema;
        }

        // Rebuild the map with x-schemaMetadata inserted at the correct position
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        boolean inserted = false;

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String key = entry.getKey();

            // Insert metadata after all header keys and before the first non-header key
            if (!inserted && !HEADER_KEYS.contains(key)) {
                result.put(X_SCHEMA_METADATA, metadataMap);
                inserted = true;
            }

            result.put(key, entry.getValue());
        }

        // If all keys were header keys (unlikely), append at the end
        if (!inserted) {
            result.put(X_SCHEMA_METADATA, metadataMap);
        }

        return result;
    }
}
