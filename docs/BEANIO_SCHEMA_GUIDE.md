# Guide: JSON Schema BeanIO pour CSV

## Vue d'ensemble

Ce guide explique comment utiliser le générateur de JSON Schema optimisé pour BeanIO afin de créer facilement des fichiers de configuration BeanIO XML pour parser des fichiers CSV complexes.

## Problème résolu

Les fichiers CSV comptables Notilus contiennent plus de 200 colonnes organisées en segments logiques (ACCOUNTS_BATCH, REPORT, ACCOUNT_ENTRY, EXPENSE, PERSON, etc.). Créer manuellement un fichier BeanIO XML pour parser ces données serait fastidieux et sujet aux erreurs.

## Solution

Le `BeanIOJsonSchemaGenerator` analyse automatiquement la structure du CSV et génère un JSON Schema qui :

1. **Groupe les colonnes par segment** basé sur leur préfixe (ex: `ACCOUNTS_BATCH.NUMBER` → segment `ACCOUNTS_BATCH`, champ `NUMBER`)
2. **Préserve les positions** de chaque champ pour le mapping BeanIO
3. **Identifie les champs obligatoires** vs optionnels
4. **Inclut les métadonnées** nécessaires (délimiteur, quote char, etc.)

## Structure du JSON Schema généré

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "urn:csv:accounting:canonical:beanio-mapping",
  "title": "CSV_ACCOUNTING_CANONICAL",
  "description": "BeanIO-optimized JSON Schema for CSV mapping",
  "type": "object",

  "x-beanio-config": {
    "format": "csv",
    "delimiter": ";",
    "quoteChar": "\"",
    "recordName": "csvAccountingCanonical",
    "strict": true
  },

  "x-metadata": {
    "sourceType": "CSV",
    "generatedBy": "File Schema Analyzer - BeanIO Edition",
    "model": "segmented-flat-file"
  },

  "properties": {
    "ACCOUNTS_BATCH": {
      "type": "object",
      "description": "ACCOUNTS_BATCH segment",
      "x-segment": true,
      "properties": {
        "NUMBER": {
          "type": "string",
          "x-position": 0,
          "x-csv-column": "ACCOUNTS_BATCH.NUMBER",
          "description": "Column: ACCOUNTS_BATCH.NUMBER"
        },
        "LEDGER_TYPE": {
          "type": "string",
          "x-position": 1,
          "x-csv-column": "ACCOUNTS_BATCH.LEDGER_TYPE"
        }
        // ... more fields
      },
      "required": ["NUMBER", "LEDGER_TYPE", "POSTING_DATE"]
    },

    "REPORT": {
      "type": "object",
      "x-segment": true,
      "properties": {
        "DOC_NUMBER": {
          "type": "string",
          "x-position": 10,
          "x-csv-column": "REPORT.DOC_NUMBER"
        }
        // ... more fields
      }
    }
    // ... more segments
  },

  "required": ["ACCOUNTS_BATCH", "REPORT", "ACCOUNT_ENTRY"]
}
```

## Métadonnées clés

### x-beanio-config
Configuration BeanIO au niveau du stream :
- **format**: Type de fichier (csv, delimited, fixedlength)
- **delimiter**: Caractère séparateur (`;` pour Notilus)
- **quoteChar**: Caractère de quote (`"`)
- **recordName**: Nom du record BeanIO (converti en camelCase)
- **strict**: Mode strict activé

### x-segment
Indique qu'une propriété représente un segment BeanIO (groupe de champs logiques)

### x-position
Position globale du champ dans le CSV (0-indexed), essentielle pour le mapping BeanIO

### x-csv-column
Nom de la colonne originale dans le CSV

## Utilisation

### 1. Parser le CSV et générer le JSON Schema

```java
import com.datasabai.services.schemaanalyzer.core.parser.CsvFileParser;
import com.datasabai.services.schemaanalyzer.core.generator.BeanIOJsonSchemaGenerator;

// Lire le fichier CSV
String csvContent = Files.readString(Path.of("Notilus_1_6600_17.csv"));

// Parser options
Map<String, String> options = Map.of("delimiter", ";");

// Créer la requête
FileAnalysisRequest request = FileAnalysisRequest.builder()
    .fileType(FileType.CSV)
    .fileContent(csvContent)
    .schemaName("CSV_ACCOUNTING_CANONICAL")
    .parserOptions(options)
    .build();

// Parser la structure CSV
CsvFileParser csvParser = new CsvFileParser();
StructureElement structure = csvParser.parse(request);

// Générer le JSON Schema BeanIO
BeanIOJsonSchemaGenerator schemaGen = new BeanIOJsonSchemaGenerator();
String jsonSchema = schemaGen.generateSchemaAsString(structure, request);

// Sauvegarder le schema
Files.writeString(Path.of("beanio-schema.json"), jsonSchema);
```

### 2. Transformer le JSON Schema en BeanIO XML

Le JSON Schema peut être transformé en fichier BeanIO XML en utilisant les métadonnées :

```java
public class BeanIOXmlGenerator {

    public String generateBeanIOXml(JsonNode schema) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<beanio xmlns=\"http://www.beanio.org/2012/03\">\n\n");

        // Extract config
        JsonNode config = schema.get("x-beanio-config");
        String format = config.get("format").asText();
        String delimiter = config.get("delimiter").asText();
        String recordName = config.get("recordName").asText();

        // Stream definition
        xml.append("  <stream name=\"accountingData\" format=\"")
           .append(format).append("\" strict=\"true\">\n\n");

        // Parser config
        xml.append("    <parser>\n");
        xml.append("      <property name=\"delimiter\" value=\"")
           .append(escapeXml(delimiter)).append("\"/>\n");
        xml.append("    </parser>\n\n");

        // Record
        xml.append("    <record name=\"").append(recordName)
           .append("\" class=\"com.example.").append(capitalize(recordName))
           .append("\">\n\n");

        // Segments
        JsonNode properties = schema.get("properties");
        properties.fields().forEachRemaining(entry -> {
            String segmentName = entry.getKey();
            JsonNode segment = entry.getValue();

            if (segment.has("x-segment") && segment.get("x-segment").asBoolean()) {
                xml.append("      <segment name=\"")
                   .append(toCamelCase(segmentName))
                   .append("\" class=\"com.example.segment.")
                   .append(capitalize(segmentName))
                   .append("\">\n");

                // Fields
                JsonNode fields = segment.get("properties");
                fields.fields().forEachRemaining(fieldEntry -> {
                    String fieldName = fieldEntry.getKey();
                    JsonNode field = fieldEntry.getValue();

                    int position = field.get("x-position").asInt();
                    String type = field.get("type").asText();
                    boolean required = isRequired(segment, fieldName);

                    xml.append("        <field name=\"")
                       .append(toCamelCase(fieldName))
                       .append("\" position=\"").append(position).append("\"");

                    if (!required) {
                        xml.append(" minOccurs=\"0\"");
                    }

                    xml.append("/>\n");
                });

                xml.append("      </segment>\n\n");
            }
        });

        xml.append("    </record>\n");
        xml.append("  </stream>\n");
        xml.append("</beanio>\n");

        return xml.toString();
    }

    private boolean isRequired(JsonNode segment, String fieldName) {
        if (!segment.has("required")) return false;
        JsonNode required = segment.get("required");
        for (JsonNode req : required) {
            if (req.asText().equals(fieldName)) return true;
        }
        return false;
    }

    // Utility methods: toCamelCase, capitalize, escapeXml...
}
```

### 3. Utiliser le fichier BeanIO XML

```java
import org.beanio.StreamFactory;
import org.beanio.BeanReader;

// Créer le factory BeanIO
StreamFactory factory = StreamFactory.newInstance();
factory.load("beanio-mapping.xml");

// Créer un reader
BeanReader reader = factory.createReader("accountingData",
                                          new FileReader("Notilus_1_6600_17.csv"));

// Lire les entrées
Object record;
while ((record = reader.read()) != null) {
    CsvAccountingCanonical entry = (CsvAccountingCanonical) record;

    // Accéder aux segments
    AccountsBatch batch = entry.getAccountsBatch();
    Report report = entry.getReport();
    AccountEntry accountEntry = entry.getAccountEntry();
    Expense expense = entry.getExpense();
    Person person = entry.getPerson();

    // Traiter les données...
    System.out.println("Batch: " + batch.getNumber());
    System.out.println("Doc: " + report.getDocNumber());
    System.out.println("Account: " + accountEntry.getAccountCode());
}

reader.close();
```

## Avantages de cette approche

### 1. **Automatisation**
- Génération automatique à partir du CSV source
- Pas de mapping manuel ligne par ligne
- Réduction des erreurs humaines

### 2. **Structure claire**
- Organisation en segments logiques
- Positions préservées pour référence
- Champs obligatoires vs optionnels identifiés

### 3. **Maintenabilité**
- Si le format CSV change, re-générer le schema
- JSON Schema comme source de vérité
- Documentation incluse dans le schema

### 4. **Évolutivité**
- Fonctionne pour n'importe quel CSV avec colonnes préfixées
- Adaptable à différents formats (délimiteur, quotes, etc.)
- Peut générer des classes Java à partir du schema

## Exemple complet : Notilus CSV

### Structure identifiée (23 segments)
1. **ACCOUNTS_BATCH** (10 champs) - Informations batch comptable
2. **REPORT** (21 champs) - Données du rapport
3. **ACCOUNT_ENTRY** (21 champs) - Écriture comptable
4. **EXPENSE** (53 champs) - Détails de la dépense
5. **PERSON** (23 champs) - Informations employé
6. **TYPE** (12 champs) - Type de dépense
7. **MISSION** (12 champs) - Mission / déplacement
8. **ADVANCEPAYMENT** (7 champs) - Avance de paiement
9. ... 15 autres segments

### Mapping BeanIO généré
- **208 colonnes** mappées avec leur position exacte
- **23 segments** organisés logiquement
- **Champs typés** (string, null pour optionnels)
- **Configuration CSV** (délimiteur `;`, quotes `"`)

## Génération des classes Java

Le JSON Schema peut aussi servir à générer les classes Java pour BeanIO :

```bash
# Utiliser jsonschema2pojo ou similaire
jsonschema2pojo \
  --source beanio-schema.json \
  --target src/main/java \
  --package com.datasabai.hsb.model \
  --use-optional-for-getters
```

## Conclusion

Le **BeanIOJsonSchemaGenerator** simplifie drastiquement la création de mappings BeanIO pour des CSV complexes :

1. ✅ **Parse le CSV** avec gestion du BOM et délimiteurs personnalisés
2. ✅ **Génère un JSON Schema structuré** avec segments et positions
3. ✅ **Facilite la création du XML BeanIO** via transformation automatisée
4. ✅ **Permet la génération de classes Java** pour un mapping type-safe

**Temps de développement** : De plusieurs heures de mapping manuel à quelques minutes automatisées !

## Prochaines étapes

1. Créer un générateur XML BeanIO à partir du JSON Schema
2. Ajouter la génération automatique des classes Java
3. Créer des tests d'intégration avec BeanIO
4. Supporter d'autres formats (fixed-length, XML, etc.)

## Ressources

- [BeanIO Documentation](http://beanio.org/)
- [JSON Schema Specification](https://json-schema.org/)
- [Example BeanIO XML](./BEANIO_MAPPING_EXAMPLE.xml)
