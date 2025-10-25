# common-translate

Automatic translation module for Spring Boot microservices using LibreTranslate and Redis cache.

## Features

- 🌍 **Automatic translation** of REST API responses based on `Accept-Language` header
- ⚡ **High performance** with Redis caching (24h TTL)
- 🎯 **Zero code changes** in controllers - just add an annotation
- 🔒 **Smart exclusions** - names, emails, UUIDs, dates automatically excluded
- 🌐 **Multi-language support** - fr, en, es, de, it, pt
- 📦 **Configurable source language** - works for any project language

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.crm-bancaire</groupId>
    <artifactId>common-translate</artifactId>
    <version>1.0.0</version>
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
```

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

## Performance

**First request (cache miss):**
- Business cache hit: 5ms
- Translation (LibreTranslate): 100-300ms
- Redis cache save: 1ms
- **Total: ~310ms**

**Subsequent requests (cache hit):**
- Business cache hit: 5ms
- Translation (Redis cache): 2ms
- **Total: ~7-10ms** 🚀

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
