# common-translate

Automatic translation module for Spring Boot microservices using LibreTranslate and Redis cache.

## Features

- 🌍 **Automatic translation** of REST API responses based on `Accept-Language` header
- ⚡ **High performance** with Redis caching (24h TTL)
- 🎯 **Zero code changes** in controllers - just add an annotation
- 🏷️ **Automatic enum label generation** - adds translated labels for enum values
- 🗂️ **Field metadata endpoint** - get translated field labels for frontend forms
- 🔒 **Smart exclusions** - names, emails, UUIDs, dates automatically excluded
- 🌐 **Multi-language support** - fr, en, es, de, it, pt
- 📦 **Configurable source language** - works for any project language
- 🔧 **Custom enum mappings** - optional YAML configuration for precise labels

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.crm-bancaire</groupId>
    <artifactId>common-translate</artifactId>
    <version>1.0.2</version>
</dependency>
```

### 2. Enable Translation

Add `@EnableAutoTranslate` to your Spring Boot application:

```java
@SpringBootApplication
@EnableAutoTranslate
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

### 3. Configure

Add to your `application.yml`:

```yaml
translate:
  enabled: true
  source-language: fr  # Language of your source code
  libretranslate:
    url: http://libretranslate:5000
```

**That's it!** Your REST API responses will be automatically translated based on the `Accept-Language` header.

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

**⚠️ IMPORTANT:** Custom enum labels must be in your **SOURCE language** (defined by `source-language`). They will be automatically translated to target languages by LibreTranslate.

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

### Mark Entities for Metadata

Add `@Translatable` annotation to your entity classes:

```java
@Entity
@Translatable(name = "User")
@Data
public class User {
    private String id;
    private String firstName;
    private String lastName;
    private String email;
    private String telephone;
    private UserRole typeUser;
}
```

### Get Field Labels

**Request:**
```bash
GET /api/translate/metadata/User?lang=en
```

**Response:**
```json
{
  "firstName": "First Name",
  "lastName": "Last Name",
  "telephone": "Telephone",
  "typeUser": "Type User"
}
```

**Request:**
```bash
GET /api/translate/metadata/User?lang=fr
```

**Response:**
```json
{
  "firstName": "Prénom",
  "lastName": "Nom",
  "telephone": "Téléphone",
  "typeUser": "Type Utilisateur"
}
```

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

## Performance & Caching

### How Caching Works

**First translation (cache MISS):**
```
Request: typeUser="ADMIN" with Accept-Language: en
→ Auto-generate: "Admin" (or custom: "Administrateur système")
→ Check Redis cache: "trans:en:Admin" → NOT FOUND
→ Call LibreTranslate API (~100-300ms)
→ Store in Redis: "trans:en:Admin" = "Administrator"
→ Return: "Administrator"
```

**Subsequent translations (cache HIT):**
```
Request: typeUser="ADMIN" with Accept-Language: en
→ Auto-generate: "Admin"
→ Check Redis cache: "trans:en:Admin" → FOUND!
→ Return from cache (~1-2ms) ✅ NO LibreTranslate call
```

### Performance Metrics

**First request (cache miss):**
- Business cache hit: 5ms
- Translation (LibreTranslate API): 100-300ms
- Redis cache save: 1ms
- **Total: ~310ms**

**Subsequent requests (cache hit):**
- Business cache hit: 5ms
- Translation (Redis cache): 1-2ms
- **Total: ~7-10ms** 🚀

**Cache hit rate in production:** >99% for stable content

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
