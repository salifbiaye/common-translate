# common-translate v1.0.3

**Module de traduction automatique pour microservices Spring Boot**

Module réutilisable qui ajoute la traduction automatique des réponses API HTTP avec LibreTranslate. Transforme automatiquement vos APIs REST en APIs multilingues sans modifier le code des contrôleurs.

## 🌟 Fonctionnalités

### Traduction Automatique
- ✅ **Interception HTTP transparente** - Traduit automatiquement les réponses API
- ✅ **Détection de langue** - Via header `Accept-Language` (en, fr, es, de, it, pt)
- ✅ **Support complet** - Objets simples, listes, pages paginées
- ✅ **Annotations intelligentes** - `@NoTranslate` pour exclure des champs

### Labels d'Énumérations
- ✅ **Génération automatique** - `ADMIN` → `adminLabel: "Admin"`
- ✅ **Configuration personnalisée** - Labels custom via YAML
- ✅ **Multilingue** - Labels traduits selon `Accept-Language`

### Métadonnées de Formulaires
- ✅ **API de métadonnées** - Génère les labels de champs traduits
- ✅ **Détection automatique** - Scan des entités `@Translatable`
- ✅ **Chemins configurables** - `/api/users/translate/metadata` par service

### Performance Optimisée (✨ NOUVEAU v1.0.3)
- ✅ **Cache à deux niveaux** - L1 (Caffeine local) + L2 (Redis distribué)
- ✅ **Protection anti-stampede** - Synchronisation automatique des requêtes concurrentes
- ✅ **1 seul appel API** - Même si 10+ requêtes arrivent en même temps
- ✅ **@NoTranslate amélioré** - Empêche la traduction des VALEURS mais génère quand même les labels

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.crm-bancaire</groupId>
    <artifactId>common-translate</artifactId>
    <version>1.0.3</version>
</dependency>
```

### 2. Activer le Module

```java
@SpringBootApplication
@EnableAutoTranslate(basePackages = "com.mycompany.myservice")
public class MyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyServiceApplication.class, args);
    }
}
```

### 3. Configuration

```yaml
translate:
  enabled: true
  source-language: fr              # Langue du contenu métier
  field-names-language: en         # Langue des noms de variables (firstName, lastName)
  metadata:
    base-path: /api/users          # Chemin de base pour métadonnées
  libretranslate:
    url: http://localhost:5000
  cache:
    ttl: 86400                     # 24h en secondes
  enum-labels:
    UserRole:
      ADMIN: Administrateur système
      USER: Utilisateur standard
```

**C'est tout!** Vos réponses API seront automatiquement traduites selon le header `Accept-Language`.

## How It Works

```
Frontend → Accept-Language: en
    ↓
Controller returns (in French):
{
  "firstName": "Jean",
  "lastName": "Dupont",
  "typeUser": "CLIENT",
  "bio": "Passionné de technologie"
}
    ↓
TranslationResponseAdvice (automatic)
    ↓ Check Redis cache
    ↓ Translate (if not cached)
    ↓
Frontend receives (in English):
{
  "firstName": "Jean",          // Not translated (@NoTranslate)
  "lastName": "Dupont",         // Not translated (@NoTranslate)
  "typeUser": "Customer",       // Translated
  "bio": "Technology enthusiast" // Translated
}
```

## Usage Examples

### Basic Usage - No Code Changes

Your existing controllers work without any modifications:

```java
@GetMapping("/{id}")
public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
    return userService.getUserById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
}
```

**French request:**
```bash
curl -H "Accept-Language: fr" http://localhost:8080/api/users/123
# Response: "CLIENT", "Passionné de technologie"
```

**English request:**
```bash
curl -H "Accept-Language: en" http://localhost:8080/api/users/123
# Response: "Customer", "Technology enthusiast"
```

**Spanish request:**
```bash
curl -H "Accept-Language: es" http://localhost:8080/api/users/123
# Response: "Cliente", "Apasionado por la tecnología"
```

### Exclude Fields from Translation

Use `@NoTranslate` annotation on fields that should never be translated:

```java
@Entity
@Data
public class User {
    @NoTranslate
    private String id;

    @NoTranslate
    private String firstName;  // Never translated

    @NoTranslate
    private String lastName;   // Never translated

    @NoTranslate
    private String email;      // Never translated

    private UserRole typeUser; // Translated (CLIENT → Customer)

    private String bio;        // Translated
}
```

## Automatic Exclusions

The following are **automatically excluded** from translation:

- **Email addresses**: john@example.com
- **UUIDs**: 123e4567-e89b-12d3-a456-426614174000
- **URLs**: http://example.com
- **Pure numbers**: 12345
- **ISO dates**: 2024-10-25T10:30:00
- **Common fields**: id, uuid, email, username, password, firstName, lastName, telephone, keycloakId

## Configuration Options

```yaml
translate:
  enabled: true                       # Enable/disable translation (default: true)
  source-language: fr                 # Source code language (fr, en, es, de, it, pt)
  field-names-language: en            # Language of field names (default: en)
  cache:
    ttl: 86400                        # Cache TTL in seconds (default: 24h)
  libretranslate:
    url: http://libretranslate:5000   # LibreTranslate server URL
  enum-labels:                        # Optional: Custom enum label mappings (use SOURCE language!)
    UserRole:
      ADMIN: Administrateur système   # ✅ French (if source is fr)
      CLIENT: Client bancaire         # ✅ French → "Banking customer" (en)
      CONSEILLER: Conseiller financier
    UserLevel:
      LEVEL_1: Niveau Un
      LEVEL_2: Niveau Deux
```

**⚠️ IMPORTANT:**
- **`source-language`**: Language of your business content (messages, descriptions, etc.)
- **`field-names-language`**: Language of your variable names (default: `en`)
  - Most Java projects use English variable names: `firstName`, `lastName`, `typeUser`
  - If you use French variables: `prenom`, `nom`, `typeUtilisateur` → set to `fr`
- **Custom enum labels** must be in your **SOURCE language** (defined by `source-language`). They will be automatically translated to target languages by LibreTranslate.

## Enum Label Generation

**v1.0.2** automatically adds translated label fields for enum values!

### How It Works

When the system detects an enum field (like `typeUser: "ADMIN"`), it automatically adds a label field:

**Input (French source code):**
```json
{
  "id": "123",
  "firstName": "Jean",
  "typeUser": "ADMIN"
}
```

**Output (Accept-Language: en):**
```json
{
  "id": "123",
  "firstName": "Jean",
  "typeUser": "ADMIN",           // Preserved for logic
  "typeUserLabel": "Administrator"  // Added for display (translated)
}
```

**Output (Accept-Language: es):**
```json
{
  "id": "123",
  "firstName": "Jean",
  "typeUser": "ADMIN",
  "typeUserLabel": "Administrador"  // Spanish translation
}
```

### Auto-Capitalization vs Custom Mapping

**Default behavior (auto-capitalize):**
- `ADMIN` → `Admin` → Translated to "Admin", "Administrador", etc.
- `SUPER_USER` → `Super User` → Translated appropriately

**Custom mapping (optional):**
```yaml
translate:
  enum-labels:
    UserRole:
      ADMIN: Administrator  # More precise than "Admin"
      CLIENT: Customer      # Instead of just "Client"
```

With custom mapping:
- `ADMIN` → `Administrator` → Translated to "Administrator", "Administrateur", "Administrador"

### Why Both Value and Label?

- **Enum value** (`typeUser: "ADMIN"`) - Used for logic, APIs, database queries
- **Enum label** (`typeUserLabel: "Administrator"`) - Used for display in UI (dropdowns, tables, etc.)

## Field Metadata Endpoint

Get translated field labels for building multilingual forms and UIs.

### 1. Annoter vos Entités

```java
@Entity
@Translatable(name = "User", description = "Entité utilisateur")
@Data
public class User {
    @NoTranslate
    private String id;

    @NoTranslate
    private String firstName;      // Valeur non traduite

    @NoTranslate
    private String lastName;

    @Enumerated(EnumType.STRING)
    private UserRole typeUser;     // Génère typeUserLabel
}
```

### 2. Annoter vos DTOs

```java
@Data
public class UserResponse {
    @NoTranslate
    private String firstName;      // IMPORTANT: Annoter le DTO aussi!

    @NoTranslate
    private String lastName;

    private UserRole typeUser;
}
```

### 3. Configurer le Chemin des Métadonnées

```yaml
# user-service
translate:
  metadata:
    base-path: /api/users
# → Endpoint: /api/users/translate/metadata/User

# customer-service
translate:
  metadata:
    base-path: /api/customers
# → Endpoint: /api/customers/translate/metadata/Customer
```

### 4. Utiliser l'API de Métadonnées

**Requête avec header Accept-Language:**
```bash
curl -H "Accept-Language: fr" \
  http://localhost:8089/api/users/translate/metadata/User
```

**Réponse (français):**
```json
{
  "firstName": "Prénom",
  "lastName": "Nom",
  "email": "Email",
  "telephone": "Téléphone",
  "typeUser": "Rôle"
}
```

**Requête en anglais:**
```bash
curl -H "Accept-Language: en" \
  http://localhost:8089/api/users/translate/metadata/User
```

**Réponse (anglais):**
```json
{
  "firstName": "First Name",
  "lastName": "Last Name",
  "email": "Email",
  "telephone": "Telephone",
  "typeUser": "Type User"
}
```

**⚠️ IMPORTANT:** Depuis v1.0.3, utilisez `Accept-Language` header au lieu de `?lang=` query parameter!

### Use Cases

**Frontend form labels:**
```jsx
// React example
const { data: labels } = useQuery('/api/translate/metadata/User?lang=en');

<Form>
  <label>{labels.firstName}</label>
  <input name="firstName" />

  <label>{labels.email}</label>
  <input name="email" />
</Form>
```

**Dynamic table headers:**
```jsx
const columns = Object.keys(labels).map(field => ({
  field: field,
  headerName: labels[field], // Translated!
  sortable: true
}));
```

### List All Translatable Entities

```bash
GET /api/translate/metadata/entities
```

**Response:**
```json
["User", "Account", "Module", "Product"]
```

### Performance

- First request: Metadata generated from entity class
- Subsequent requests: Served from Redis cache (2-5ms)
- Cache TTL: Same as translation cache (default 24h)

## Multi-User Scenarios

The module handles concurrent requests from users with different languages:

```
User A (France) → Accept-Language: fr → Response in French
User B (USA)    → Accept-Language: en → Response in English  ← Same time!
User C (Spain)  → Accept-Language: es → Response in Spanish  ← Same time!
```

Each request is independent with its own cache key:
- `trans:en:CLIENT` → "Customer"
- `trans:es:CLIENT` → "Cliente"
- `trans:de:CLIENT` → "Kunde"

## 🏗️ Architecture du Cache à Deux Niveaux (v1.0.3)

### Stratégie de Cache

```
Requête HTTP → L1 (Caffeine local) → L2 (Redis distribué) → LibreTranslate
               ↑ 30min, 10K entrées  ↑ 24h, distribué         ↑ API externe
```

**L1 - Cache Local (Caffeine):**
- In-memory, ultra-rapide (< 1ms)
- Max 10,000 entrées
- Expire après 30 minutes
- **Synchronisation automatique des threads**

**L2 - Cache Distribué (Redis):**
- Partagé entre toutes les instances du service
- Expire après 24 heures (configurable)
- Persistant entre redémarrages

### 🛡️ Protection Anti-Stampede

**Problème:** Sans synchronisation, si 10 requêtes concurrentes arrivent pour un texte non en cache:
```
Thread 1: Check cache → MISS → Call LibreTranslate
Thread 2: Check cache → MISS → Call LibreTranslate  ❌
Thread 3: Check cache → MISS → Call LibreTranslate  ❌
...
Thread 10: Check cache → MISS → Call LibreTranslate ❌
Résultat: 10 appels API pour le même texte! 😱
```

**Solution (v1.0.3):** Caffeine synchronise automatiquement les requêtes concurrentes:
```
Thread 1:
  Check L1 → MISS → Acquire LOCK → Check L2 → MISS → Call LibreTranslate (200ms)
  → Store in L2 (Redis) → Store in L1 → Release LOCK → Return "Administrator"

Thread 2-100:
  Check L1 → MISS → Try LOCK → WAIT (Thread 1 has it) ⏳
  → Thread 1 finishes → Get value directly from L1 → Return "Administrator" ✅

Résultat:
- 1 seul appel LibreTranslate ✅
- 0 appel Redis pour threads 2-100 (ils attendent Thread 1) ✅
- Temps total: ~200ms pour tous ✅
```

### Comment ça marche

**Scénario réel: 100 requêtes arrivent en même temps (cache vide)**

```
Request: "Administrator" (fr → en) - 100 requêtes simultanées

⏱️ T=0ms: 100 requêtes arrivent

Thread 1:
  T=0ms:   Check L1 (Caffeine): MISS
  T=1ms:   Acquire LOCK (les 99 autres attendent ici!)
  T=2ms:   Check L2 (Redis): MISS
  T=3ms:   Call LibreTranslate API...
  T=203ms: Receive response: "Administrateur"
  T=204ms: Store in L2 (Redis)
  T=205ms: Store in L1 (Caffeine) & Release LOCK

Threads 2-100:
  T=0-1ms:   Check L1: MISS → Try LOCK → BLOCKED (waiting...)
  T=205ms:   Thread 1 releases LOCK
  T=205ms:   Get value from L1 (Caffeine): "Administrateur" ✅
  T=205ms:   Return immediately

Résultat:
✅ 1 seul appel LibreTranslate (Thread 1)
✅ 0 appel Redis pour Threads 2-100 (synchronisation Caffeine)
✅ Tous les 100 threads reçoivent la réponse en ~205ms
✅ Économie: 99 appels API évités!
```

**Requêtes suivantes (L1 cache chaud):**
```
Request: "Administrator" (fr → en)
  T=0ms: Check L1 (Caffeine): HIT!
  T=0.5ms: Return "Administrateur" ✅

Aucun appel Redis, aucun appel LibreTranslate!
```

### 📊 Performance Metrics

**Scénario: 100 requêtes concurrentes (première fois)**

| Thread | L1 Cache | L2 Cache | LibreTranslate | Temps Total |
|--------|----------|----------|----------------|-------------|
| Thread 1 | MISS | MISS | ✅ Call (~200ms) | ~205ms |
| Thread 2-100 | WAIT → HIT | - | ❌ Skip | ~205ms (waiting) |

**Après la première vague:**
- L1 cache contient la valeur
- L2 cache (Redis) contient la valeur
- Toutes les requêtes suivantes: < 1ms (L1 hit)

**Comparaison avec v1.0.2 (sans Caffeine):**

| Scénario | v1.0.2 | v1.0.3 | Amélioration |
|----------|--------|--------|--------------|
| 100 requêtes concurrentes (cache vide) | 100 appels LibreTranslate | 1 appel | **99% moins d'appels** |
| Requête unique (L1 miss, L2 hit) | 1-2ms (Redis) | 1-2ms (Redis) | Identique |
| Requête unique (L1 hit) | - | < 1ms | ⚡ **Plus rapide** |

**Taux de cache hit en production:** > 99%

### Cache Configuration

```yaml
translate:
  cache:
    ttl: 86400  # 24 hours (default)
    # After 24h, cache expires and LibreTranslate is called again
```

**Best practices:**
- Development: 3600s (1h) for frequent changes
- Production: 86400s (24h) or 172800s (48h) for stable content

## Infrastructure Setup

Add LibreTranslate to your `docker-compose.yml`:

```yaml
services:
  libretranslate:
    image: libretranslate/libretranslate:latest
    container_name: libretranslate
    ports:
      - "5000:5000"
    environment:
      - LT_LOAD_ONLY=fr,en,es,de,it,pt
      - LT_SUGGESTIONS=false
    restart: unless-stopped
```

## Supported Response Types

- ✅ String responses
- ✅ Single objects (UserResponse, AccountResponse, etc.)
- ✅ Collections (List<UserResponse>)
- ✅ Paginated responses (Page<UserResponse>)

## Requirements

- Spring Boot 3.x
- Redis server
- LibreTranslate server
- Java 17+

## License

This module is part of the SIB Banking CRM project.
