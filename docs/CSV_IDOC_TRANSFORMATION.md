# CSV to IDoc-like Transformation

## Overview

This document explains how to transform flat CSV accounting data into a hierarchical IDoc-inspired JSON structure.

## Current CSV Parser Output

The current `CsvFileParser` generates a simple array-of-objects structure:

```json
{
  "name": "Notilus_Accounting",
  "type": "array",
  "children": [
    {
      "name": "item",
      "type": "object",
      "children": [
        { "name": "ACCOUNTS_BATCH.NUMBER", "type": "string" },
        { "name": "ACCOUNTS_BATCH.LEDGER_TYPE", "type": "string" },
        { "name": "REPORT.DOC_NUMBER", "type": "string" },
        { "name": "ACCOUNT_ENTRY.ENTRY_NUMBER", "type": "string" },
        // ... 200+ more columns
      ]
    }
  ]
}
```

## Target IDoc-like Structure

The target structure groups data hierarchically based on accounting batch, document, and entry levels:

```json
{
  "CSV_DOCUMENT": {
    "CONTROL": {
      "SOURCE": "Notilus",
      "FILE_NAME": "Notilus_1_6600_17.csv",
      "EXTRACTION_DATE": "2025-07-24",
      "FORMAT": "CSV"
    },
    "HEADER_GRP": [
      {
        "HEADER": {
          "NUMBER": "17",
          "LEDGER_TYPE": "1",
          "POSTING_DATE": "20250724",
          "COMPANY_CODE": "6600",
          "COMPANY_DESCRIPTION": "TFL Thailand Co., Ltd."
        },
        "DOCUMENT": {
          "DOC_NUMBER": "969",
          "TYPE": "EXPENSE STANDARD THAILAND",
          "DESCRIPTION": "",
          "ALLOCATION_DATE": "20250731"
        },
        "ITEM_GRP": [
          {
            "ITEM": {
              "ENTRY_NUMBER": "1",
              "LINE_NUMBER": "1",
              "LINE_TYPE": "1",
              "ACCOUNT_CODE": "4350400",
              "ACCOUNT_DESCRIPTION": "Promotional expenses",
              "DIRECTION": "D",
              "DEBIT": "233.00",
              "CREDIT": "0.00",
              "AMOUNT": "233.00"
            },
            "EXPENSE": {
              "NUMBER": "1",
              "DATE": "20250702",
              "COMMENT": ".",
              "CURRENCY_CODE": "THB"
            },
            "TYPE": {
              "CODE": "PROM_EXP",
              "DESCRIPTION": "Promotional expenses"
            },
            "PERSON": {
              "NUMBER": "TH-011",
              "LOGIN": "ONONOV",
              "SURNAME": "ONON",
              "FIRST_NAME": "Ovanee"
            }
          }
        ]
      }
    ]
  }
}
```

## Transformation Logic

### Step 1: Identify Grouping Keys

The CSV rows need to be grouped by:

1. **HEADER_GRP level**: `ACCOUNTS_BATCH.NUMBER` (batch number)
2. **DOCUMENT level**: `REPORT.DOC_NUMBER` (document number within batch)
3. **ITEM_GRP level**: `ACCOUNT_ENTRY.ENTRY_NUMBER` (entry number within document)

### Step 2: Column Mapping

Map CSV column names to IDoc segment fields:

#### HEADER Segment
- `ACCOUNTS_BATCH.NUMBER` → `HEADER.NUMBER`
- `ACCOUNTS_BATCH.LEDGER_TYPE` → `HEADER.LEDGER_TYPE`
- `ACCOUNTS_BATCH.POSTING_DATE` → `HEADER.POSTING_DATE`
- `ACCOUNTS_BATCH.COMPANY_CODE` → `HEADER.COMPANY_CODE`
- etc.

#### DOCUMENT Segment
- `REPORT.DOC_NUMBER` → `DOCUMENT.DOC_NUMBER`
- `REPORT.TYPE` → `DOCUMENT.TYPE`
- `REPORT.DESCRIPTION` → `DOCUMENT.DESCRIPTION`
- etc.

#### ITEM Segment
- `ACCOUNT_ENTRY.ENTRY_NUMBER` → `ITEM.ENTRY_NUMBER`
- `ACCOUNT_ENTRY.LINE_NUMBER` → `ITEM.LINE_NUMBER`
- `ACCOUNT_ENTRY.ACCOUNT_CODE` → `ITEM.ACCOUNT_CODE`
- etc.

#### EXPENSE Segment
- `EXPENSE.NUMBER` → `EXPENSE.NUMBER`
- `EXPENSE.DATE` → `EXPENSE.DATE`
- `EXPENSE.COMMENT` → `EXPENSE.COMMENT`
- etc.

### Step 3: Transformation Algorithm

```java
public class CsvToIdocTransformer {

    public JsonNode transform(List<Map<String, String>> csvRows, String fileName) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode csvDocument = root.putObject("CSV_DOCUMENT");

        // Add CONTROL metadata
        ObjectNode control = csvDocument.putObject("CONTROL");
        control.put("SOURCE", "Notilus");
        control.put("FILE_NAME", fileName);
        control.put("EXTRACTION_DATE", extractDate(csvRows));
        control.put("FORMAT", "CSV");

        // Group rows by batch number
        Map<String, List<Map<String, String>>> batchGroups =
            csvRows.stream().collect(
                Collectors.groupingBy(row -> row.get("ACCOUNTS_BATCH.NUMBER"))
            );

        ArrayNode headerGrpArray = csvDocument.putArray("HEADER_GRP");

        for (Map.Entry<String, List<Map<String, String>>> batchEntry : batchGroups.entrySet()) {
            ObjectNode headerGrp = headerGrpArray.addObject();

            // Create HEADER from first row in batch
            Map<String, String> firstRow = batchEntry.getValue().get(0);
            ObjectNode header = createHeader(firstRow);
            headerGrp.set("HEADER", header);

            // Group by document number within batch
            Map<String, List<Map<String, String>>> docGroups =
                batchEntry.getValue().stream().collect(
                    Collectors.groupingBy(row -> row.get("REPORT.DOC_NUMBER"))
                );

            // Create DOCUMENT from first row in document group
            ObjectNode document = createDocument(firstRow);
            headerGrp.set("DOCUMENT", document);

            ArrayNode itemGrpArray = headerGrp.putArray("ITEM_GRP");

            // Group by entry number within document
            for (List<Map<String, String>> docRows : docGroups.values()) {
                Map<String, List<Map<String, String>>> entryGroups =
                    docRows.stream().collect(
                        Collectors.groupingBy(row -> row.get("ACCOUNT_ENTRY.ENTRY_NUMBER"))
                    );

                for (List<Map<String, String>> entryRows : entryGroups.values()) {
                    ObjectNode itemGrp = itemGrpArray.addObject();

                    Map<String, String> entryRow = entryRows.get(0);

                    // Create segments from row data
                    itemGrp.set("ITEM", createItem(entryRow));
                    itemGrp.set("EXPENSE", createExpense(entryRow));
                    itemGrp.set("VAT", createVat(entryRow));
                    itemGrp.set("TYPE", createType(entryRow));
                    itemGrp.set("PERSON", createPerson(entryRow));

                    // Optional segments (only if data present)
                    if (hasMissionData(entryRow)) {
                        itemGrp.set("MISSION", createMission(entryRow));
                    }
                    if (hasAdvancePaymentData(entryRow)) {
                        itemGrp.set("ADVANCEPAYMENT", createAdvancePayment(entryRow));
                    }
                }
            }
        }

        return root;
    }

    private ObjectNode createHeader(Map<String, String> row) {
        ObjectNode header = JsonNodeFactory.instance.objectNode();
        header.put("NUMBER", row.get("ACCOUNTS_BATCH.NUMBER"));
        header.put("LEDGER_TYPE", row.get("ACCOUNTS_BATCH.LEDGER_TYPE"));
        header.put("POSTING_DATE", row.get("ACCOUNTS_BATCH.POSTING_DATE"));
        header.put("COMPANY_CODE", row.get("ACCOUNTS_BATCH.COMPANY_CODE"));
        header.put("COMPANY_DESCRIPTION", row.get("ACCOUNTS_BATCH.COMPANY_DESCRIPTION"));
        // Add more fields...
        return header;
    }

    // Similar methods for other segments...
}
```

## Implementation Approaches

### Approach 1: Post-Processing Transformer

Create a separate transformer that takes the CSV parser output and reorganizes it:

1. Use `CsvFileParser` to parse the CSV into flat structure
2. Apply `CsvToIdocTransformer` to reorganize into hierarchical structure
3. Generate JSON Schema from the transformed structure

**Pros:**
- Separation of concerns (parsing vs transformation)
- Reusable transformer for different CSV formats
- Easier to test and maintain

**Cons:**
- Two-step process
- More complex integration

### Approach 2: Custom CSV Parser

Create a new parser `CsvIdocParser` that directly produces the hierarchical structure:

1. Extend `CsvFileParser`
2. Override `buildStructure()` to create IDoc-like hierarchy
3. Add configuration for grouping keys and segment mappings

**Pros:**
- Single-step process
- Direct integration with existing parser framework

**Cons:**
- Less flexible
- Mixes parsing and transformation logic

## Recommended Approach

**Use Approach 1 (Post-Processing Transformer)** because:

1. **Flexibility**: Can apply different transformations to same CSV data
2. **Testability**: Each component (parser and transformer) can be tested independently
3. **Reusability**: Transformer can be applied to CSV data from different sources
4. **Maintainability**: Clear separation between data extraction and data transformation

## Next Steps

1. Implement `CsvToIdocTransformer` class
2. Create configuration schema for defining:
   - Grouping keys (batch, document, entry levels)
   - Column to segment field mappings
   - Optional segment inclusion rules
3. Add integration tests with real Notilus CSV files
4. Generate JSON Schema from transformed structure
5. Document transformation configuration format

## Example Configuration

```yaml
transformation:
  grouping:
    - level: HEADER_GRP
      key: ACCOUNTS_BATCH.NUMBER
    - level: DOCUMENT
      key: REPORT.DOC_NUMBER
    - level: ITEM_GRP
      key: ACCOUNT_ENTRY.ENTRY_NUMBER

  segments:
    HEADER:
      source: ACCOUNTS_BATCH
      fields:
        NUMBER: NUMBER
        LEDGER_TYPE: LEDGER_TYPE
        POSTING_DATE: POSTING_DATE
        COMPANY_CODE: COMPANY_CODE
        COMPANY_DESCRIPTION: COMPANY_DESCRIPTION

    DOCUMENT:
      source: REPORT
      fields:
        DOC_NUMBER: DOC_NUMBER
        TYPE: TYPE
        DESCRIPTION: DESCRIPTION

    ITEM:
      source: ACCOUNT_ENTRY
      fields:
        ENTRY_NUMBER: ENTRY_NUMBER
        LINE_NUMBER: LINE_NUMBER
        ACCOUNT_CODE: ACCOUNT_CODE
        DIRECTION: DIRECTION
        DEBIT: DEBIT
        CREDIT: CREDIT

    EXPENSE:
      source: EXPENSE
      optional: true
      fields:
        NUMBER: NUMBER
        DATE: DATE
        COMMENT: COMMENT
        CURRENCY_CODE: CURRENCY_CODE
```

## BOM Handling

✅ **Fixed**: The CSV parser now automatically detects and removes UTF-8 BOM (Byte Order Mark) characters at the beginning of files.

This ensures that column names are parsed correctly even when the CSV file contains a BOM marker (common in files exported from Windows applications).

**Technical Details:**
- BOM character: U+FEFF (UTF-8: EF BB BF)
- Detection: Checks first character of file content
- Removal: Automatically strips BOM before parsing
- Impact: Ensures first column name is parsed correctly (e.g., "ACCOUNTS_BATCH.NUMBER" instead of "﻿ACCOUNTS_BATCH.NUMBER")
