# File Schema Analyzer - Quarkus Web Application

Application web Quarkus avec interface utilisateur pour générer des JSON Schemas à partir de fichiers CSV, JSON, Fixed-Length et Variable-Length.

## 🚀 Démarrage rapide

### Prérequis
- Java 21+
- Maven 3.9+

### Lancer l'application

```bash
# En mode développement (avec hot reload)
mvn quarkus:dev

# Accéder à la WebUI
open http://localhost:8080
```

## 📱 Interface Web

L'interface web permet de :
- ✅ **Upload de fichiers** par clic ou drag & drop
- ✅ **Auto-détection** du type de fichier
- ✅ **Configuration** des options de parsing
- ✅ **Visualisation** du JSON Schema avec coloration syntaxique
- ✅ **Téléchargement** du résultat en JSON

### Formats supportés
- **CSV** - Fichiers délimités (virgule, point-virgule, etc.)
- **JSON** - Fichiers JSON structurés
- **Fixed Length** - Fichiers à longueur fixe
- **Variable Length** - Fichiers à longueur variable

### Modes de génération
1. **Standard** : JSON Schema classique (array/object)
2. **BeanIO Optimized** : JSON Schema avec métadonnées BeanIO
   - Structure segmentée
   - Positions des champs
   - Configuration de parsing incluse

## 🔧 API REST

### Analyser un fichier
```bash
curl -X POST http://localhost:8080/api/analyzer/analyze-file \
  -F "file=@testFiles/Notilus_1_6600_17.csv" \
  -F "schemaName=CSV_ACCOUNTING_CANONICAL" \
  -F "fileType=CSV" \
  -F "detectArrays=true" \
  -F "optimizeForBeanIO=true" \
  -F 'parserOptions={"delimiter": ";", "hasHeader": "true"}'
```

### Obtenir les types supportés
```bash
curl http://localhost:8080/api/analyzer/supported-types
```

### Obtenir les options de parser
```bash
curl http://localhost:8080/api/analyzer/parser-options/CSV
```

### Health check
```bash
curl http://localhost:8080/api/analyzer/health
```

## 📚 Documentation

- **[Guide WebUI](../docs/WEBUI_GUIDE.md)** - Guide complet de l'interface web
- **[Guide BeanIO](../docs/BEANIO_SCHEMA_GUIDE.md)** - Utilisation des schemas BeanIO
- **[API Documentation](../docs/API.md)** - Documentation complète de l'API REST

## 🏗️ Build et Déploiement

### Build pour la production

```bash
# Build JAR standard
mvn clean package

# Build uber-JAR (tout-en-un)
mvn clean package -Dquarkus.package.type=uber-jar

# Build native (GraalVM requis)
mvn clean package -Pnative
```

### Lancer en production

```bash
# JAR standard
java -jar target/quarkus-app/quarkus-run.jar

# Uber-JAR
java -jar target/analyzer-quarkus-app-1.0.0-SNAPSHOT-runner.jar

# Native
./target/analyzer-quarkus-app-1.0.0-SNAPSHOT-runner
```

## 🐳 Docker

```bash
# Build image Docker
docker build -f src/main/docker/Dockerfile.jvm -t file-schema-analyzer .

# Lancer le container
docker run -i --rm -p 8080:8080 file-schema-analyzer

# Accéder à l'application
open http://localhost:8080
```

## ⚙️ Configuration

Fichier : `src/main/resources/application.properties`

```properties
# Port HTTP
quarkus.http.port=8080

# CORS (pour développement)
quarkus.http.cors=true

# Taille max des fichiers
quarkus.http.limits.max-body-size=10M

# Logs
quarkus.log.level=INFO
quarkus.log.category."com.datasabai.services.schemaanalyzer".level=DEBUG
```

## 🧪 Tests

```bash
# Lancer tous les tests
mvn test

# Tests d'intégration
mvn verify
```

## 📝 Exemples

### Exemple 1 : CSV avec BOM
```bash
curl -X POST http://localhost:8080/api/analyzer/analyze-file \
  -F "file=@../testFiles/Notilus_1_6600_17.csv" \
  -F "schemaName=NotilusAccounting" \
  -F "optimizeForBeanIO=true" \
  -F 'parserOptions={"delimiter": ";"}'
```

**Résultat** : JSON Schema avec 23 segments, 208 champs, positions préservées

### Exemple 2 : JSON simple
```bash
curl -X POST http://localhost:8080/api/analyzer/analyze-file \
  -F "file=@data.json" \
  -F "schemaName=MyDataSchema" \
  -F "detectArrays=true"
```

## 🔍 Dev UI

En mode développement, accédez au Dev UI de Quarkus :
```
http://localhost:8080/q/dev
```

Features :
- Configuration runtime
- Health checks
- Metrics
- OpenAPI/Swagger UI
- Logs en temps réel

## 🎯 Points d'entrée

| URL | Description |
|-----|-------------|
| `/` | Interface web principale |
| `/api/analyzer/analyze-file` | Endpoint d'analyse (multipart) |
| `/api/analyzer/analyze` | Endpoint d'analyse (JSON) |
| `/api/analyzer/supported-types` | Types de fichiers supportés |
| `/api/analyzer/health` | Health check de l'application |
| `/q/health` | Quarkus health check |
| `/q/dev` | Dev UI (dev mode only) |

## 🛠️ Technologies

- **Quarkus 3.x** - Framework Java supersonic
- **Jakarta REST (JAX-RS)** - API REST
- **Jackson** - Sérialisation JSON
- **RESTEasy Reactive** - Serveur HTTP réactif
- **SmallRye Health** - Health checks

## 📦 Structure du projet

```
analyzer-quarkus-app/
├── src/main/
│   ├── java/
│   │   └── com/datasabai/services/schemaanalyzer/app/
│   │       ├── AnalyzerApplication.java
│   │       └── AnalyzerResource.java
│   └── resources/
│       ├── META-INF/resources/
│       │   ├── index.html          # WebUI principale
│       │   └── app.js              # Logic JavaScript
│       └── application.properties   # Configuration
└── pom.xml
```

## 🚨 Résolution de problèmes

### Port déjà utilisé
```bash
# Changer le port
mvn quarkus:dev -Dquarkus.http.port=8081
```

### Fichier trop gros
```bash
# Augmenter la limite
mvn quarkus:dev -Dquarkus.http.limits.max-body-size=50M
```

### Hot reload ne fonctionne pas
```bash
# Nettoyer et relancer
mvn clean quarkus:dev
```

## 📄 Licence

Copyright © 2024 DataSabai HSB

## 🤝 Support

Pour toute question :
- Consulter la [documentation](../docs/)
- Vérifier les [logs](#configuration)
- Tester l'endpoint `/api/analyzer/health`
