# Guide WebUI - File Schema Analyzer

## Vue d'ensemble

L'interface web permet d'utiliser facilement le File Schema Analyzer sans avoir besoin de coder. Elle supporte tous les formats de fichiers (CSV, JSON, Fixed-Length, Variable-Length) et génère des JSON Schemas optimisés pour BeanIO.

## Démarrage rapide

### 1. Lancer l'application Quarkus

```bash
cd analyzer-quarkus-app
mvn quarkus:dev
```

L'application démarre sur http://localhost:8080

### 2. Ouvrir la WebUI

Ouvrez votre navigateur et accédez à :
```
http://localhost:8080
```

## Utilisation

### Étape 1 : Sélectionner un fichier

1. Cliquez sur la zone "📎 Click to select a file or drag & drop"
2. **OU** Glissez-déposez votre fichier directement dans la zone

**Formats supportés** :
- `.csv` - Fichiers CSV (délimiteur configurable)
- `.json` - Fichiers JSON
- `.txt` - Fichiers à longueur fixe ou variable
- `.dat` - Fichiers de données

### Étape 2 : Configurer l'analyse

#### Type de fichier
- **Auto-detect** : Le système détecte automatiquement le type basé sur l'extension
- **CSV** : Fichiers délimités par virgule/point-virgule
- **JSON** : Fichiers JSON structurés
- **Fixed Length** : Fichiers à longueur fixe
- **Variable Length** : Fichiers à longueur variable

#### Nom du schéma
- Définissez un nom pour votre schéma (ex: `CustomerData`, `OrderSchema`)
- Ce nom sera utilisé dans le JSON Schema généré

#### Options d'analyse
- **✅ Detect Arrays** : Active la détection automatique des tableaux
- **✅ Optimize for BeanIO** : Génère un schema optimisé pour BeanIO
  - Structure segmentée (groupement par préfixe)
  - Positions des champs préservées
  - Métadonnées BeanIO incluses

#### Options de parser (dynamiques)
En fonction du type de fichier sélectionné, des options spécifiques apparaissent :

**Pour CSV** :
- `delimiter` : Caractère séparateur (ex: `;`, `,`, `|`)
- `hasHeader` : Le fichier a-t-il une ligne d'en-tête (`true`/`false`)
- `quoteChar` : Caractère de quote (par défaut `"`)
- `escapeChar` : Caractère d'échappement (par défaut `\`)

**Pour Fixed Length** :
- `lineLength` : Longueur totale de chaque ligne
- `fieldDefinitions` : Définition des champs (format JSON)

### Étape 3 : Analyser

Cliquez sur le bouton **🔍 Analyze File**

L'application :
1. Upload le fichier vers le serveur
2. Analyse la structure du fichier
3. Génère le JSON Schema
4. Affiche le résultat avec coloration syntaxique

### Étape 4 : Télécharger le résultat

Une fois le schema généré, cliquez sur **⬇️ Download JSON Schema** pour sauvegarder le fichier JSON.

Le fichier sera nommé `{nom-du-schema}.json` (ex: `CustomerData.json`)

## Exemples d'utilisation

### Exemple 1 : Analyser un CSV Notilus avec BOM

1. **Sélectionner** : `Notilus_1_6600_17.csv`
2. **Type** : CSV
3. **Schema Name** : `CSV_ACCOUNTING_CANONICAL`
4. **Options** :
   - ✅ Detect Arrays
   - ✅ Optimize for BeanIO
5. **Parser Options** :
   - `delimiter` : `;`
   - `hasHeader` : `true`
6. **Cliquer** : Analyze File

**Résultat** : JSON Schema avec 23 segments (ACCOUNTS_BATCH, REPORT, ACCOUNT_ENTRY, etc.)

### Exemple 2 : Analyser un fichier JSON

1. **Sélectionner** : `customers.json`
2. **Type** : JSON
3. **Schema Name** : `CustomerSchema`
4. **Options** :
   - ✅ Detect Arrays
   - ⬜ Optimize for BeanIO (désactivé pour JSON simple)
5. **Cliquer** : Analyze File

**Résultat** : JSON Schema avec structure hiérarchique détectée

### Exemple 3 : Fichier Fixed-Length

1. **Sélectionner** : `transactions.dat`
2. **Type** : Fixed Length
3. **Schema Name** : `TransactionSchema`
4. **Parser Options** :
   - `lineLength` : `100`
5. **Cliquer** : Analyze File

## Structure du JSON Schema généré

### Mode Standard
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CustomerSchema",
  "type": "array",
  "items": {
    "type": "object",
    "properties": {
      "id": { "type": "string" },
      "name": { "type": "string" }
    }
  }
}
```

### Mode BeanIO (Optimized)
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "urn:csv:accounting:canonical:beanio-mapping",
  "title": "CSV_ACCOUNTING_CANONICAL",
  "type": "object",
  "x-beanio-config": {
    "format": "csv",
    "delimiter": ";",
    "recordName": "csvAccountingCanonical"
  },
  "properties": {
    "ACCOUNTS_BATCH": {
      "type": "object",
      "x-segment": true,
      "properties": {
        "NUMBER": {
          "type": "string",
          "x-position": 0,
          "x-csv-column": "ACCOUNTS_BATCH.NUMBER"
        }
      }
    }
  }
}
```

## Fonctionnalités avancées

### Coloration syntaxique
Le JSON affiché dans la WebUI utilise une coloration syntaxique pour faciliter la lecture :
- **Violet** : Clés d'objets
- **Bleu foncé** : Valeurs string
- **Bleu** : Booléens (true/false)
- **Vert** : Nombres
- **Gris** : null

### Messages de statut
- **🟢 Success** : Schema généré avec succès (disparaît après 3 secondes)
- **🔴 Error** : Erreur lors de l'analyse (reste affiché)
- **🔵 Loading** : Analyse en cours...

### Informations en temps réel
- **Badge type** : Affiche l'extension du fichier sélectionné
- **Badge résultat** : Affiche le type de format détecté après analyse
- **Taille fichier** : Affichée lors de la sélection

## API REST (pour les développeurs)

La WebUI utilise l'API REST suivante :

### Analyser un fichier
```http
POST /api/analyzer/analyze-file
Content-Type: multipart/form-data

file: [binary]
schemaName: "MySchema"
fileType: "CSV"
detectArrays: true
optimizeForBeanIO: true
parserOptions: {"delimiter": ";", "hasHeader": "true"}
```

### Types supportés
```http
GET /api/analyzer/supported-types
```

### Options de parser
```http
GET /api/analyzer/parser-options/{type}
```

### Health check
```http
GET /api/analyzer/health
```

## Dépannage

### Le fichier ne s'upload pas
- **Vérifier** : La taille du fichier (max 10MB par défaut)
- **Solution** : Augmenter `quarkus.http.limits.max-body-size` dans `application.properties`

### Erreur "Unsupported file type"
- **Vérifier** : Le type de fichier sélectionné correspond au contenu
- **Solution** : Essayer "Auto-detect" ou sélectionner manuellement le bon type

### Le JSON Schema n'est pas BeanIO-optimisé
- **Vérifier** : La case "Optimize for BeanIO" est cochée
- **Vérifier** : Le fichier CSV utilise des colonnes préfixées (ex: `SEGMENT.FIELD`)

### Options de parser non visibles
- **Cause** : Aucun type de fichier sélectionné
- **Solution** : Sélectionner un type de fichier dans le dropdown

## Configuration

### Modifier le port de l'application
Dans `application.properties` :
```properties
quarkus.http.port=8080
```

### Augmenter la taille max des fichiers
```properties
quarkus.http.limits.max-body-size=50M
```

### Activer les logs debug
```properties
quarkus.log.category."com.datasabai.services.schemaanalyzer".level=DEBUG
```

## Développement

### Mode développement
```bash
mvn quarkus:dev
```

Features du mode dev :
- ⚡ Hot reload automatique
- 🎨 Dev UI disponible sur http://localhost:8080/q/dev
- 📊 Logs colorés dans le terminal

### Build production
```bash
mvn clean package -Dquarkus.package.type=uber-jar
```

Lancer en production :
```bash
java -jar target/analyzer-quarkus-app-1.0.0-SNAPSHOT-runner.jar
```

## Raccourcis clavier

| Touche | Action |
|--------|--------|
| `Ctrl/Cmd + O` | Ouvrir sélecteur de fichier |
| `Ctrl/Cmd + Enter` | Analyser le fichier |
| `Ctrl/Cmd + S` | Télécharger le schema |

## Support

Pour toute question ou problème :
1. Consulter les [logs de l'application](#configuration)
2. Vérifier la [documentation API](../README.md)
3. Tester l'endpoint `/api/analyzer/health`

## Ressources

- [Guide BeanIO](BEANIO_SCHEMA_GUIDE.md)
- [Documentation API REST](../README.md)
- [Exemples de schemas](example-beanio-schema.json)
