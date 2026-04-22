package com.datasabai.services.schemaanalyzer.core.parser;

import com.datasabai.services.schemaanalyzer.core.FileSchemaAnalyzer;
import com.datasabai.services.schemaanalyzer.core.model.AnalyzerException;
import com.datasabai.services.schemaanalyzer.core.model.FileAnalysisRequest;
import com.datasabai.services.schemaanalyzer.core.model.FileType;
import com.datasabai.services.schemaanalyzer.core.model.SchemaGenerationResult;
import com.datasabai.services.schemaanalyzer.core.model.XSchemaMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for CSV schema generation from orders_sample.csv.
 * <p>
 * Ensures each CSV column becomes its own property in the Header and Record
 * definitions (not concatenated into a single property).
 */
public class CsvOrdersSampleTest {

    private static final List<String> EXPECTED_FIELDS = List.of(
            "orderNumber", "orderDate", "customerCode", "customerName",
            "shipToName", "shipToAddress1", "shipToAddress2", "shipToCity",
            "shipToState", "shipToZip", "shipToCountry", "shipMethod",
            "lineNumber", "sku", "productName", "quantity", "unitPrice", "currency"
    );

    @Test
    public void generateSchema_fromOrdersSampleCsv_producesOnePropertyPerColumn()
            throws IOException, AnalyzerException {
        Path csvPath = Paths.get("..", "testFiles", "orders_sample.csv");
        assertThat(Files.exists(csvPath))
                .as("orders_sample.csv must exist at %s", csvPath.toAbsolutePath())
                .isTrue();

        String csvContent = Files.readString(csvPath);

        XSchemaMetadata metadata = XSchemaMetadata.builder()
                .version("1.0")
                .representation("flat")
                .specification("csv")
                .specVersion("v1")
                .documentCode("ORDERS")
                .documentType("Ecommerce")
                .modelName("OrderCSV")
                .category("standard")
                .build();

        FileAnalysisRequest request = FileAnalysisRequest.builder()
                .fileType(FileType.CSV)
                .fileContent(csvContent)
                .schemaName("flat_csv_v1_orders_ecommerce")
                .xSchemaMetadata(metadata)
                .build();

        SchemaGenerationResult result = new FileSchemaAnalyzer().analyze(request);
        assertThat(result.isSuccess()).isTrue();

        JsonNode schema = new ObjectMapper().readTree(result.getJsonSchemaAsString());

        assertThat(schema.path("$defs").path("Header").path("properties").isObject()).isTrue();
        assertThat(schema.path("$defs").path("Record").path("properties").isObject()).isTrue();

        JsonNode headerProps = schema.path("$defs").path("Header").path("properties");
        JsonNode recordProps = schema.path("$defs").path("Record").path("properties");

        assertThat(headerProps.size())
                .as("Header should have one property per CSV column")
                .isEqualTo(EXPECTED_FIELDS.size());
        assertThat(recordProps.size())
                .as("Record should have one property per CSV column")
                .isEqualTo(EXPECTED_FIELDS.size());

        for (String field : EXPECTED_FIELDS) {
            assertThat(headerProps.has(field))
                    .as("Header must contain property '%s'", field)
                    .isTrue();
            assertThat(recordProps.has(field))
                    .as("Record must contain property '%s'", field)
                    .isTrue();
        }

        assertThat(headerProps.path("orderNumber").path("description").asText())
                .isEqualTo("CSV column: OrderNumber");
        assertThat(headerProps.path("shipToAddress1").path("description").asText())
                .isEqualTo("CSV column: ShipToAddress1");
        assertThat(recordProps.path("currency").path("description").asText())
                .isEqualTo("CSV column: Currency");
    }
}
