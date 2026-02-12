# Guide JSON Schema pour jsonschema2pojo

## Vue d'ensemble

Le générateur `JsonSchema2PojoGenerator` crée un **JSON Schema pur** (draft-07) compatible avec **jsonschema2pojo** pour générer automatiquement des classes Java POJOs.

### Structure pour CSV uniquement

Pour les fichiers CSV, le générateur produit une structure **Header + Records** :

```
Root (objet)
├── header (optionnel) → #/definitions/Header
└── records (requis) → array of #/definitions/Record
```

### Caractéristiques clés

✅ **Pas de métadonnées BeanIO** - Schema JSON pur
✅ **Noms camelCase** - Conversion automatique pour Java
✅ **Segments groupés** - Organisation logique (batch vs détail)
✅ **Compatible jsonschema2pojo** - Prêt pour la génération de POJOs
✅ **Champs requis identifiés** - Validation automatique

## Structure du JSON Schema

### Root Object

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CsvAccountingCanonical",
  "type": "object",
  "properties": {
    "header": {
      "$ref": "#/definitions/Header"
    },
    "records": {
      "type": "array",
      "description": "List of records from CSV",
      "items": {
        "$ref": "#/definitions/CsvAccountingCanonicalRecord"
      },
      "minItems": 1
    }
  },
  "required": ["records"]
}
```

### Definitions

#### Header (Optionnel)
Contient les segments de niveau **batch/document** (colonnes communes) :
- `accountsBatch` (ACCOUNTS_BATCH)
- `report` (REPORT)
- `reportWkf` (REPORT_WKF)
- `loggedInAccountant`
- `usedAccountant`

```json
"Header": {
  "type": "object",
  "description": "Header information (batch/document level)",
  "properties": {
    "accountsBatch": {
      "type": "object",
      "properties": {
        "number": { "type": "string" },
        "ledgerType": { "type": "string" },
        "companyCode": { "type": "string" }
      },
      "required": ["number", "ledgerType", "companyCode"]
    },
    "report": {
      "type": "object",
      "properties": {
        "docNumber": { "type": "string" },
        "type": { "type": "string" }
      },
      "required": ["docNumber", "type"]
    }
  },
  "required": ["accountsBatch", "report"]
}
```

#### Record (Requis)
Contient les segments de niveau **détail** (lignes répétées) :
- `accountEntry` (ACCOUNT_ENTRY)
- `expense` (EXPENSE)
- `person` (PERSON)
- `type` (TYPE)
- `mission` (MISSION)
- ... 18 segments au total

```json
"CsvAccountingCanonicalRecord": {
  "type": "object",
  "description": "Single record line (detail level)",
  "properties": {
    "accountEntry": {
      "type": "object",
      "properties": {
        "entryNumber": { "type": "string" },
        "accountCode": { "type": "string" },
        "debit": { "type": "string" },
        "credit": { "type": "string" }
      },
      "required": ["entryNumber", "accountCode"]
    },
    "expense": { /* ... */ },
    "person": { /* ... */ }
  },
  "required": ["accountEntry", "expense", "person"]
}
```

## Génération des POJOs Java

### Commande jsonschema2pojo

```bash
jsonschema2pojo \
  --source schema.json \
  --target src/main/java \
  --package com.example.model \
  --source-type jsonschema \
  --target-language java \
  --annotation-style jackson2 \
  --use-optional false
```

### Classes générées

#### 1. CsvAccountingCanonical.java (Root)

```java
package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CsvAccountingCanonical {

    @JsonProperty("header")
    private Header header;

    @JsonProperty("records")
    private List<CsvAccountingCanonicalRecord> records;

    // Getters and setters
    public Header getHeader() {
        return header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public List<CsvAccountingCanonicalRecord> getRecords() {
        return records;
    }

    public void setRecords(List<CsvAccountingCanonicalRecord> records) {
        this.records = records;
    }
}
```

#### 2. Header.java

```java
package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Header {

    @JsonProperty("accountsBatch")
    private AccountsBatch accountsBatch;

    @JsonProperty("report")
    private Report report;

    // Getters and setters

    public static class AccountsBatch {
        @JsonProperty("number")
        private String number;

        @JsonProperty("ledgerType")
        private String ledgerType;

        @JsonProperty("postingDate")
        private String postingDate;

        @JsonProperty("companyCode")
        private String companyCode;

        // Getters and setters
    }

    public static class Report {
        @JsonProperty("docNumber")
        private String docNumber;

        @JsonProperty("allocationDate")
        private String allocationDate;

        // Getters and setters
    }
}
```

#### 3. CsvAccountingCanonicalRecord.java

```java
package com.example.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CsvAccountingCanonicalRecord {

    @JsonProperty("accountEntry")
    private AccountEntry accountEntry;

    @JsonProperty("expense")
    private Expense expense;

    @JsonProperty("person")
    private Person person;

    @JsonProperty("type")
    private Type type;

    // Getters and setters

    public static class AccountEntry {
        @JsonProperty("entryNumber")
        private String entryNumber;

        @JsonProperty("accountCode")
        private String accountCode;

        @JsonProperty("debit")
        private String debit;

        @JsonProperty("credit")
        private String credit;

        // Getters and setters
    }

    public static class Expense {
        @JsonProperty("number")
        private String number;

        @JsonProperty("date")
        private String date;

        @JsonProperty("comment")
        private String comment;

        // Getters and setters
    }

    // ... other nested classes
}
```

## Utilisation des POJOs

### Parsing avec Jackson

```java
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper mapper = new ObjectMapper();

// Parse JSON to POJO
CsvAccountingCanonical document = mapper.readValue(
    jsonString,
    CsvAccountingCanonical.class
);

// Access header
Header header = document.getHeader();
String batchNumber = header.getAccountsBatch().getNumber();
String companyCode = header.getAccountsBatch().getCompanyCode();

// Access records
List<CsvAccountingCanonicalRecord> records = document.getRecords();
for (CsvAccountingCanonicalRecord record : records) {
    String accountCode = record.getAccountEntry().getAccountCode();
    String debit = record.getAccountEntry().getDebit();
    String personName = record.getPerson().getSurname();

    System.out.println("Account: " + accountCode + ", Debit: " + debit);
}
```

### Sérialisation vers JSON

```java
// Create object
CsvAccountingCanonical document = new CsvAccountingCanonical();

// Set header
Header header = new Header();
Header.AccountsBatch batch = new Header.AccountsBatch();
batch.setNumber("17");
batch.setCompanyCode("6600");
header.setAccountsBatch(batch);
document.setHeader(header);

// Add records
List<CsvAccountingCanonicalRecord> records = new ArrayList<>();
CsvAccountingCanonicalRecord record = new CsvAccountingCanonicalRecord();
// ... set record fields
records.add(record);
document.setRecords(records);

// Serialize to JSON
String json = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(document);
```

## Conversion des noms

### Segments : UPPER_SNAKE_CASE → camelCase

| CSV Column Prefix | JSON Property |
|-------------------|---------------|
| `ACCOUNTS_BATCH` | `accountsBatch` |
| `REPORT` | `report` |
| `ACCOUNT_ENTRY` | `accountEntry` |
| `INT_GUEST` | `intGuest` |

### Fields : UPPER_SNAKE_CASE → camelCase

| CSV Column | JSON Property |
|------------|---------------|
| `ACCOUNTS_BATCH.BATCH_NUMBER` | `accountsBatch.batchNumber` |
| `ACCOUNT_ENTRY.ENTRY_NUMBER` | `accountEntry.entryNumber` |
| `PERSON.FIRST_NAME` | `person.firstName` |

## Classification des segments

### Segments HEADER (5 segments)

Ces segments sont placés dans `Header` (niveau batch/document) :

| Segment CSV | JSON Property | Description |
|-------------|---------------|-------------|
| `ACCOUNTS_BATCH` | `accountsBatch` | Informations du batch comptable |
| `REPORT` | `report` | Informations du rapport/document |
| `REPORT_WKF` | `reportWkf` | Workflow du rapport |
| `LOGGED_IN_ACCOUNTANT` | `loggedInAccountant` | Comptable connecté |
| `USED_ACCOUNTANT` | `usedAccountant` | Comptable utilisé |

### Segments RECORD (18 segments)

Tous les autres segments sont placés dans `Record` (niveau ligne) :

| Segment CSV | JSON Property | Description |
|-------------|---------------|-------------|
| `ACCOUNT_ENTRY` | `accountEntry` | Écriture comptable |
| `EXPENSE` | `expense` | Détails dépense |
| `PERSON` | `person` | Employé |
| `TYPE` | `type` | Type de dépense |
| `MISSION` | `mission` | Mission |
| ... | ... | 13 autres segments |

## Génération du schema

### Via API

```java
CsvFileParser parser = new CsvFileParser();
JsonSchema2PojoGenerator generator = new JsonSchema2PojoGenerator();

StructureElement structure = parser.parse(request);
String jsonSchema = generator.generateSchemaAsString(structure, request);

// Save to file
Files.writeString(Path.of("schema.json"), jsonSchema);
```

### Via WebUI

1. Upload votre fichier CSV
2. Sélectionner "JSON Schema for jsonschema2pojo"
3. Cliquer "Analyze"
4. Télécharger le schema généré

### Via CLI

```bash
# Generate schema
curl -X POST http://localhost:8080/api/analyzer/analyze-file \
  -F "file=@Notilus.csv" \
  -F "schemaName=CsvAccountingCanonical" \
  -F "fileType=CSV" \
  -F 'parserOptions={"delimiter": ";"}' \
  > schema.json

# Generate POJOs
jsonschema2pojo -s schema.json -t src/main/java -p com.example.model
```

## Comparaison avec BeanIO

| Aspect | jsonschema2pojo | BeanIO |
|--------|----------------|--------|
| **Output** | Classes Java POJOs | Mapping XML + POJOs |
| **Usage** | Jackson (JSON) | BeanIO (CSV parsing) |
| **Métadonnées** | Aucune (pur JSON Schema) | x-beanio-*, x-position |
| **Structure** | Header + Records refs | Flat avec positions |
| **Génération** | Automatique via plugin | Manuel ou custom |

## Exemple complet

### Input CSV
```csv
ACCOUNTS_BATCH.NUMBER;REPORT.DOC_NUMBER;ACCOUNT_ENTRY.ID;ACCOUNT_ENTRY.AMOUNT
17;969;1;233.00
17;969;2;14.00
```

### JSON Schema généré
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Accounting",
  "type": "object",
  "properties": {
    "header": {
      "$ref": "#/definitions/Header"
    },
    "records": {
      "type": "array",
      "items": {
        "$ref": "#/definitions/AccountingRecord"
      },
      "minItems": 1
    }
  },
  "required": ["records"]
}
```

### POJOs générés
```
com.example.model/
├── Accounting.java              (root)
├── Header.java                  (header class)
└── AccountingRecord.java        (record class)
```

### Usage
```java
Accounting doc = mapper.readValue(json, Accounting.class);
String batchNumber = doc.getHeader().getAccountsBatch().getNumber();
List<AccountingRecord> records = doc.getRecords();
```

## Avantages

✅ **JSON Schema standard** - Pas de métadonnées custom
✅ **Génération automatique** - jsonschema2pojo fait tout
✅ **Type-safe** - Classes Java fortement typées
✅ **Jackson ready** - Annotations @JsonProperty incluses
✅ **IDE friendly** - Auto-complétion et validation
✅ **Structure claire** - Header + Records séparés

## Resources

- [jsonschema2pojo](https://www.jsonschema2pojo.org/)
- [JSON Schema Specification](https://json-schema.org/)
- [Jackson Annotations](https://github.com/FasterXML/jackson-annotations/wiki/Jackson-Annotations)

## Conclusion

Le générateur `JsonSchema2PojoGenerator` produit un **JSON Schema pur** compatible jsonschema2pojo avec une structure **Header + Records** pour CSV.

**Utilisez-le si** :
- ✅ Vous voulez générer des POJOs Java automatiquement
- ✅ Vous utilisez Jackson pour JSON
- ✅ Vous voulez un schema JSON standard (pas de BeanIO)
- ✅ Votre CSV a une structure Header + lignes répétées
