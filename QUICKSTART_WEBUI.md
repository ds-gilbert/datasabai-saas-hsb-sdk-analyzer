# 🚀 Démarrage Rapide - WebUI

## Vue d'ensemble

L'application dispose maintenant d'une **interface web complète** qui permet de :
- ✅ Uploader des fichiers (CSV, JSON, Fixed-Length, Variable-Length)
- ✅ Configurer les options d'analyse
- ✅ Générer des JSON Schemas (standard ou BeanIO optimisé)
- ✅ Visualiser et télécharger les résultats

## 🎯 Démarrer en 3 étapes

### 1️⃣ Lancer l'application

```bash
cd /home/gilbert/datasabai-saas-hsb-sdk-analyzer/analyzer-quarkus-app
mvn quarkus:dev
```

**Attendez le message** : `Listening on: http://localhost:8080`

### 2️⃣ Ouvrir la WebUI

Dans votre navigateur, accédez à :
```
http://localhost:8080
```

Vous devriez voir l'interface **File Schema Analyzer** avec un design moderne violet/bleu.

### 3️⃣ Analyser un fichier

#### Test avec les fichiers CSV Notilus

1. **Cliquer** sur la zone d'upload
2. **Sélectionner** : `/home/gilbert/datasabai-saas-hsb-sdk-analyzer/testFiles/Notilus_1_6600_17.csv`
3. **Type** : CSV (auto-détecté)
4. **Schema Name** : `CSV_ACCOUNTING_CANONICAL`
5. **Cocher** : ✅ Optimize for BeanIO
6. **Parser Options** :
   - `delimiter` : `;`
   - `hasHeader` : `true`
7. **Cliquer** : 🔍 Analyze File

**Résultat attendu** :
- JSON Schema avec 23 segments
- 208 champs mappés
- Structure BeanIO optimisée
- Bouton de téléchargement activé

## 📊 Captures d'écran attendues

### Interface principale
```
┌─────────────────────────────────────────────────────────────────┐
│  🔍 File Schema Analyzer                                        │
│  Generate JSON Schema from CSV, JSON, Fixed-Length files        │
├─────────────────────────┬───────────────────────────────────────┤
│  📁 Input File          │  📄 Generated JSON Schema             │
│  [Select a file]        │  [JSON Schema will appear here...]    │
│                         │                                        │
│  File Type: [CSV ▼]     │                                        │
│  Schema Name: [...]     │                                        │
│                         │                                        │
│  ✅ Detect Arrays       │                                        │
│  ✅ Optimize for BeanIO │                                        │
│                         │                                        │
│  [🔍 Analyze File]      │  [⬇️ Download JSON Schema]           │
└─────────────────────────┴───────────────────────────────────────┘
```

## 🎨 Fonctionnalités de l'interface

### Zone d'upload
- **Clic** pour sélectionner un fichier
- **Drag & Drop** pour déposer un fichier
- **Badge** affichant le type de fichier sélectionné
- **Taille** du fichier affichée

### Configuration
- **Type de fichier** : Auto-détection ou sélection manuelle
- **Nom du schéma** : Personnalisable
- **Options d'analyse** : Checkboxes pour activer/désactiver les fonctionnalités
- **Options de parser** : Champs dynamiques selon le type de fichier

### Affichage du résultat
- **Coloration syntaxique** du JSON
- **Scroll** pour les gros schemas
- **Bouton téléchargement** pour sauvegarder
- **Badge** indiquant le format détecté

### Messages de statut
- **🟢 Succès** : Schema généré (disparaît après 3s)
- **🔴 Erreur** : Message d'erreur détaillé
- **⏳ Loading** : Indicateur de progression avec spinner

## 🧪 Tests rapides

### Test 1 : CSV avec BOM (Notilus)
```
Fichier : testFiles/Notilus_1_6600_17.csv
Type    : CSV
Options : delimiter=";" hasHeader="true"
BeanIO  : ✅ Activé

Résultat attendu :
- 23 segments détectés
- Structure hiérarchique ACCOUNTS_BATCH → REPORT → ACCOUNT_ENTRY
- Métadonnées x-beanio-config présentes
```

### Test 2 : CSV avec champs quotés
```
Fichier : testFiles/Notilus_1_8860_37_20260129130301685.csv
Type    : CSV
Options : delimiter=";"
BeanIO  : ✅ Activé

Résultat attendu :
- 202 colonnes parsées
- Champs entre guillemets correctement gérés
```

### Test 3 : Créer un CSV simple
Créez un fichier `test.csv` :
```csv
ID;Name;Price
1;Product A;19.99
2;Product B;29.99
```

Upload avec :
- Type : CSV
- delimiter : `;`
- BeanIO : ⬜ Désactivé

Résultat attendu :
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "GeneratedSchema",
  "type": "array",
  "items": {
    "type": "object",
    "properties": {
      "ID": { "type": "string" },
      "Name": { "type": "string" },
      "Price": { "type": "string" }
    }
  }
}
```

## 🔧 Options de parser par type

### CSV
| Option | Description | Exemple |
|--------|-------------|---------|
| `delimiter` | Séparateur de colonnes | `;`, `,`, `\|` |
| `hasHeader` | Première ligne = headers | `true`, `false` |
| `quoteChar` | Caractère de quote | `"`, `'` |
| `escapeChar` | Caractère d'échappement | `\` |
| `skipLines` | Lignes à ignorer au début | `0`, `1`, `2` |

### Fixed Length
| Option | Description | Exemple |
|--------|-------------|---------|
| `lineLength` | Longueur de chaque ligne | `100`, `256` |
| `fieldDefinitions` | Définition des champs (JSON) | `[{"name":"id","start":0,"length":10}]` |

### JSON
Pas d'options spécifiques - détection automatique de la structure.

## 🎯 Modes de génération

### Mode Standard (BeanIO désactivé)
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "MySchema",
  "type": "array",
  "items": {
    "type": "object",
    "properties": { ... }
  }
}
```

**Usage** : Validation JSON Schema classique, documentation

### Mode BeanIO Optimisé (BeanIO activé)
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "urn:csv:accounting:canonical:beanio-mapping",
  "title": "MySchema",
  "type": "object",
  "x-beanio-config": {
    "format": "csv",
    "delimiter": ";",
    "recordName": "mySchema"
  },
  "properties": {
    "SEGMENT_NAME": {
      "type": "object",
      "x-segment": true,
      "properties": {
        "FIELD_NAME": {
          "type": "string",
          "x-position": 0,
          "x-csv-column": "SEGMENT_NAME.FIELD_NAME"
        }
      }
    }
  }
}
```

**Usage** : Génération de mapping BeanIO XML, parsing avec BeanIO

## 📝 API REST (équivalent)

L'interface web utilise l'API REST en arrière-plan. Vous pouvez aussi appeler directement :

```bash
curl -X POST http://localhost:8080/api/analyzer/analyze-file \
  -F "file=@testFiles/Notilus_1_6600_17.csv" \
  -F "schemaName=CSV_ACCOUNTING_CANONICAL" \
  -F "fileType=CSV" \
  -F "detectArrays=true" \
  -F "optimizeForBeanIO=true" \
  -F 'parserOptions={"delimiter": ";", "hasHeader": "true"}'
```

## 🐛 Dépannage

### La page ne se charge pas
```bash
# Vérifier que Quarkus a démarré
curl http://localhost:8080/api/analyzer/health

# Devrait retourner : {"status":"UP",...}
```

### Erreur 404 sur les fichiers statiques
```bash
# Vérifier que les fichiers existent
ls -la analyzer-quarkus-app/src/main/resources/META-INF/resources/

# Devrait afficher : index.html, app.js
```

### L'analyse échoue
1. **Vérifier les logs** dans le terminal Quarkus
2. **Tester l'API** directement avec curl
3. **Vérifier le type de fichier** sélectionné

### Le JSON Schema est vide
- **Vérifier** : Le fichier a bien été uploadé (regarder la taille affichée)
- **Vérifier** : Les options de parser sont correctes pour le type de fichier

## 🚀 Prochaines étapes

1. **Tester** avec vos propres fichiers
2. **Comparer** les modes Standard vs BeanIO
3. **Générer** un mapping BeanIO XML à partir du schema
4. **Intégrer** dans votre pipeline de traitement

## 📚 Documentation complète

- [Guide WebUI détaillé](docs/WEBUI_GUIDE.md)
- [Guide BeanIO](docs/BEANIO_SCHEMA_GUIDE.md)
- [Documentation API](analyzer-quarkus-app/README.md)

## ✅ Checklist de validation

- [ ] Quarkus démarre sans erreur
- [ ] WebUI accessible sur http://localhost:8080
- [ ] Upload de fichier fonctionne (clic ou drag&drop)
- [ ] Type de fichier détecté automatiquement
- [ ] Options de parser apparaissent selon le type
- [ ] Analyse génère un JSON Schema valide
- [ ] Coloration syntaxique fonctionne
- [ ] Téléchargement du schema fonctionne
- [ ] Messages de succès/erreur s'affichent
- [ ] Mode BeanIO génère la structure segmentée

---

**🎉 Vous êtes prêt !** Votre File Schema Analyzer avec WebUI est opérationnel.
