# Configuration Guide - common-translate

## Table of Contents
1. [Basic Configuration](#basic-configuration)
2. [All Configuration Options](#all-configuration-options)
3. [Enum Label Configuration](#enum-label-configuration-v102)
4. [Field Metadata Configuration](#field-metadata-configuration-v102)
5. [Environment-Specific Configuration](#environment-specific-configuration)
6. [Redis Configuration](#redis-configuration)
7. [LibreTranslate Configuration](#libretranslate-configuration)
8. [Performance Tuning](#performance-tuning)

---

## Basic Configuration

### Minimal Configuration

```yaml
translate:
  enabled: true
  source-language: fr
  libretranslate:
    url: http://localhost:5000
```

This is all you need to get started!

---

## All Configuration Options

### Complete Configuration Reference

```yaml
translate:
  # Enable/disable translation globally
  # Default: true
  enabled: true

  # Source language of your code (fr, en, es, de, it, pt)
  # This is the language you write your code in
  # Default: fr
  source-language: fr

  # LibreTranslate server configuration
  libretranslate:
    # URL of LibreTranslate service
    # Local: http://localhost:5000
    # Docker: http://libretranslate:5000
    # Required
    url: http://localhost:5000

  # Cache configuration
  cache:
    # Time-to-live in seconds
    # Default: 86400 (24 hours)
    ttl: 86400

    # Cache key prefix (advanced)
    # Default: "trans"
    # prefix: "trans"

  # Custom enum label mappings (v1.0.2+)
  # Optional: Override default enum label generation
  enum-labels:
    # Enum class name → enum value mappings
    UserRole:
      ADMIN: Administrator
      CLIENT: Customer
      CONSEILLER: Advisor
    UserLevel:
      LEVEL_1: Level One
      LEVEL_2: Level Two
      LEVEL_3: Level Three

# Redis configuration (shared with your app)
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      database: 0  # Use database 0 (default)
```

---

## Enum Label Configuration (v1.0.2+)

### Auto-Capitalization (Default)

By default, enum values are automatically capitalized for labels:

```yaml
# No configuration needed - automatic!
```

**Examples:**
- `ADMIN` → `Admin` → Translated to "Admin", "Administrador", etc.
- `SUPER_USER` → `Super User` → Translated appropriately
- `LEVEL_1` → `Level 1` → Translated

### Custom Enum Mappings

For more precise labels, configure custom mappings **in your SOURCE language**:

```yaml
translate:
  source-language: fr  # IMPORTANT: Your code language

  enum-labels:
    # Format: EnumClassName → EnumValue → CustomLabel (in SOURCE language!)
    UserRole:
      ADMIN: Administrateur système    # ✅ French (source) → will be translated
      CLIENT: Client bancaire          # ✅ French (source) → "Banking customer" (en)
      CONSEILLER: Conseiller financier # ✅ French (source) → "Financial advisor" (en)

    AccountStatus:
      ACTIF: Compte actif      # ✅ French → "Active account" (en)
      INACTIF: Compte inactif
      SUSPENDU: Compte suspendu

    TransactionType:
      VIREMENT: Virement bancaire  # ✅ French → "Bank transfer" (en)
      DEPOT: Dépôt
      RETRAIT: Retrait
```

**⚠️ IMPORTANT: Use your SOURCE language for custom labels!**

❌ **WRONG (if source is French):**
```yaml
translate:
  source-language: fr
  enum-labels:
    UserRole:
      ADMIN: Administrator  # ❌ English! LibreTranslate will be confused
```

✅ **CORRECT (if source is French):**
```yaml
translate:
  source-language: fr
  enum-labels:
    UserRole:
      ADMIN: Administrateur système  # ✅ French! Will translate to "System administrator" (en)
```

**How it works:**
1. System detects enum field: `typeUser: "ADMIN"`
2. Checks for custom mapping in config
3. If found: Uses `"Administrateur système"` → LibreTranslate translates to target language
   - Accept-Language: **en** → `"System administrator"`
   - Accept-Language: **es** → `"Administrador del sistema"`
   - Accept-Language: **fr** → `"Administrateur système"` (no translation, same as source)
4. If not found: Auto-capitalizes `ADMIN` → `"Admin"` → LibreTranslate translates

**Result in API response:**
```json
{
  "typeUser": "ADMIN",              // Preserved for logic
  "typeUserLabel": "Administrator"   // Translated label (en)
}
```

### When to Use Custom Mappings

**✅ RECOMMENDED for these cases:**

| Enum Value | Without Config | With Config | Why Config is Better |
|------------|---------------|-------------|---------------------|
| `CLIENT` | `"Client"` → `"Client"` ❌ (same word in FR/EN) | `"Client bancaire"` → `"Banking customer"` ✅ | Adds context for better translation |
| `ADMIN` | `"Admin"` → `"Admin"` ❌ (too short) | `"Administrateur système"` → `"System administrator"` ✅ | More precise translation |
| `NUM_COMPTE` | `"Num Compte"` → `"Num Account"` ❌ | `"Numéro de compte"` → `"Account number"` ✅ | Expands abbreviations |

**❌ NOT NEEDED for these cases:**

| Enum Value | Without Config | Result | Why It Works |
|------------|---------------|--------|--------------|
| `CONSEILLER` | `"Conseiller"` → `"Advisor"` ✅ | Works well | French word translates correctly |
| `ACTIF` | `"Actif"` → `"Active"` ✅ | Works well | Clear French word |
| `SUSPENDU` | `"Suspendu"` → `"Suspended"` ✅ | Works well | Clear French word |
| `SUPER_USER` | `"Super User"` → `"Super User"` ✅ | Works well | International term |

**Best Practice:**
- Use custom mappings for: **abbreviations**, **ambiguous words**, **business terms**
- Skip custom mappings for: **clear French words**, **international terms**

**Why add context?**
- `"Client"` alone → LibreTranslate doesn't know if it's "Client", "Customer", "Patron"
- `"Client bancaire"` → More context = Better translation = `"Banking customer"`

---

## Field Metadata Configuration (v1.0.2+)

### Enabling Metadata for Entities

Mark entities with `@Translatable` to expose field labels via API:

```java
import com.crm_bancaire.common.translate.annotation.Translatable;

@Entity
@Translatable(name = "User", description = "User management entity")
public class User {
    private String firstName;  // → "First Name"
    private String lastName;   // → "Last Name"
    private String telephone;  // → "Telephone"
    private UserRole typeUser; // → "Type User"
}
```

### Metadata Endpoints

Once configured, these endpoints become available:

**Get field labels:**
```bash
GET /api/translate/metadata/User?lang=en
Response: {"firstName": "First Name", "lastName": "Last Name", ...}

GET /api/translate/metadata/User?lang=fr
Response: {"firstName": "Prénom", "lastName": "Nom", ...}
```

**List all entities:**
```bash
GET /api/translate/metadata/entities
Response: ["User", "Account", "Module"]
```

**Health check:**
```bash
GET /api/translate/metadata/health
Response: {
  "enabled": true,
  "sourceLanguage": "fr",
  "entitiesCount": 3,
  "entities": ["User", "Account", "Module"]
}
```

### Metadata Caching

Metadata is cached in Redis with the same TTL as translations:

```yaml
translate:
  cache:
    ttl: 86400  # Metadata cached for 24h
```

**Cache keys:**
- Translation cache: `trans:{lang}:{hashcode}`
- Metadata cache: `metadata:{entity}:{lang}`

### Excluding Fields from Metadata

Use `@NoTranslate` to exclude fields:

```java
@Entity
@Translatable(name = "User")
public class User {
    @NoTranslate
    private String id;  // Won't appear in metadata

    @NoTranslate
    private String password;  // Won't appear in metadata

    private String firstName;  // Will appear in metadata
}
```

### Best Practices for Field Names

**⚠️ IMPORTANT: Use full French words for best translation quality!**

**✅ GOOD (French field names with source-language: fr):**
```java
@Entity
@Translatable(name = "User")
public class User {
    private String numeroClient;      // → "Numero Client" → "Customer Number" ✅
    private String prenomUtilisateur; // → "Prenom Utilisateur" → "First Name User" ✅
    private String dateNaissance;     // → "Date Naissance" → "Birth Date" ✅
    private String adresseEmail;      // → "Adresse Email" → "Email Address" ✅
}
```

**❌ AVOID (Abbreviations or mixed languages):**
```java
@Entity
@Translatable(name = "User")
public class User {
    private String numClient;    // → "Num Client" → "Num Client" ❌ (abbr. not translated well)
    private String firstName;    // → "First Name" → "First Name" ⚠️ (already English, won't translate)
    private String emailAddr;    // → "Email Addr" → "Email Addr" ❌ (abbr. not translated well)
    private String dtNaiss;      // → "Dt Naiss" → "Dt Naiss" ❌ (abbr. not translated well)
}
```

**Translation Results:**

| Field Name | Auto-Generated Label | LibreTranslate fr→en | Result Quality |
|------------|---------------------|---------------------|----------------|
| `numeroClient` | "Numero Client" | "Customer Number" | ✅ Excellent |
| `numClient` | "Num Client" | "Num Client" | ❌ Poor (abbreviation) |
| `prenomUtilisateur` | "Prenom Utilisateur" | "First Name User" | ✅ Good |
| `firstName` | "First Name" | "First Name" | ⚠️ Already English |
| `dateCreation` | "Date Creation" | "Creation Date" | ✅ Good |
| `isActive` | "Is Active" | "Is Active" | ⚠️ Already English |

**Recommendation:**
- Use **full French words** in field names when `source-language: fr`
- Avoid abbreviations (`num`, `dt`, `addr`)
- This ensures high-quality automatic translation without custom config

---

## Environment-Specific Configuration

### Development Environment

**`application-dev.yml`:**
```yaml
translate:
  enabled: true
  source-language: fr
  libretranslate:
    url: http://localhost:5000  # Local LibreTranslate
  cache:
    ttl: 3600  # 1 hour cache (faster development)

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### Docker Environment

**`application-docker.yml`:**
```yaml
translate:
  enabled: true
  source-language: fr
  libretranslate:
    url: http://libretranslate:5000  # Docker service name
  cache:
    ttl: 86400  # 24 hours

spring:
  data:
    redis:
      host: redis  # Docker service name
      port: 6379
```

### Production Environment

**`application-prod.yml`:**
```yaml
translate:
  enabled: true
  source-language: fr
  libretranslate:
    url: http://libretranslate-prod.internal:5000
  cache:
    ttl: 172800  # 48 hours for better performance

spring:
  data:
    redis:
      host: redis-prod.internal
      port: 6379
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 5
```

### Testing Environment

**`application-test.yml`:**
```yaml
translate:
  enabled: false  # Disable translation in tests

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

## Redis Configuration

### Single Redis Instance (Recommended)

Use the same Redis for caching, sessions, and translations:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
      database: 0

  cache:
    type: redis
    redis:
      time-to-live: 10m  # Business cache: 10 minutes
```

Translation cache uses **different keys** (`trans:*`) so there's no conflict with your business cache.

### Redis Cluster (Advanced)

For high-availability setups:

```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-node1:6379
          - redis-node2:6379
          - redis-node3:6379
      timeout: 5000ms
```

### Redis Sentinel (Advanced)

For failover support:

```yaml
spring:
  data:
    redis:
      sentinel:
        master: mymaster
        nodes:
          - sentinel1:26379
          - sentinel2:26379
          - sentinel3:26379
```

---

## LibreTranslate Configuration

### Basic Setup

**docker-compose.yml:**
```yaml
services:
  libretranslate:
    image: libretranslate/libretranslate:latest
    container_name: libretranslate
    ports:
      - "5000:5000"
    environment:
      # Load only required languages (faster startup, less memory)
      LT_LOAD_ONLY: fr,en,es,de,it,pt

      # Disable suggestions feature
      LT_SUGGESTIONS: "false"

      # Enable/disable web UI
      LT_DISABLE_WEB_UI: "false"
    restart: unless-stopped
```

### Resource Requirements

Minimum recommended resources:

```yaml
services:
  libretranslate:
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '2'
        reservations:
          memory: 1G
          cpus: '1'
```

### Custom Models (Advanced)

For specific translation models:

```yaml
services:
  libretranslate:
    environment:
      LT_LOAD_ONLY: fr,en,es
      # Use custom model path
      LT_MODELS_DIR: /models
    volumes:
      - ./custom-models:/models
```

---

## Performance Tuning

### Cache TTL Optimization

Choose TTL based on your needs:

| Use Case | Recommended TTL | Reason |
|----------|----------------|--------|
| **Development** | 1-4 hours (3600-14400s) | Frequent changes, shorter cache |
| **Staging** | 12-24 hours (43200-86400s) | Balance between performance and freshness |
| **Production** | 24-48 hours (86400-172800s) | Maximum performance, stable content |
| **Static Content** | 7 days (604800s) | Rarely changing enums, labels |

**Example:**
```yaml
translate:
  cache:
    ttl: 172800  # 48 hours for production
```

### Redis Connection Pool

For high-traffic applications:

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 100  # Maximum active connections
          max-idle: 50     # Maximum idle connections
          min-idle: 10     # Minimum idle connections
          max-wait: 3000ms # Wait time for connection
```

### LibreTranslate Scaling

For high load, run multiple LibreTranslate instances:

```yaml
services:
  libretranslate-1:
    image: libretranslate/libretranslate:latest
    ports:
      - "5001:5000"

  libretranslate-2:
    image: libretranslate/libretranslate:latest
    ports:
      - "5002:5000"

  # Load balancer (nginx, haproxy, etc.)
  nginx:
    image: nginx:latest
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    ports:
      - "5000:80"
```

**nginx.conf:**
```nginx
upstream libretranslate {
    server libretranslate-1:5000;
    server libretranslate-2:5000;
}

server {
    listen 80;
    location / {
        proxy_pass http://libretranslate;
    }
}
```

### RestTemplate Timeout

Adjust HTTP timeouts if needed:

```java
@Configuration
public class CustomTranslationConfig {
    @Bean
    public RestTemplate translationRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
```

---

## Configuration by Source Language

### French Project (Default)

```yaml
translate:
  source-language: fr
```

Code written in French is sent as-is when `Accept-Language: fr`.

### English Project

```yaml
translate:
  source-language: en
```

Code written in English is sent as-is when `Accept-Language: en`.

### Spanish Project

```yaml
translate:
  source-language: es
```

Code written in Spanish is sent as-is when `Accept-Language: es`.

**The module works with ANY source language!**

---

## Disabling Translation

### Globally

```yaml
translate:
  enabled: false
```

### For Specific Profiles

```yaml
# application-test.yml
translate:
  enabled: false
```

### For Specific Endpoints

Use custom interceptor logic or remove `@EnableAutoTranslate` from specific modules.

---

## Monitoring Configuration

### Enable Actuator Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### Custom Health Indicator

```java
@Component
@RequiredArgsConstructor
public class TranslationHealthIndicator implements HealthIndicator {
    private final AutoTranslationService translationService;

    @Override
    public Health health() {
        try {
            String test = translationService.translate("test", "en");
            return Health.up()
                    .withDetail("libretranslate", "connected")
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("libretranslate", "unreachable")
                    .withException(e)
                    .build();
        }
    }
}
```

---

## Troubleshooting Configuration

### Check Active Configuration

Add logging to see active configuration:

```yaml
logging:
  level:
    com.crm_bancaire.common.translate: DEBUG
```

Logs will show:
```
DEBUG TranslationAutoConfiguration : Creating RestTemplate for LibreTranslate API calls
DEBUG TranslationAutoConfiguration : Initializing AutoTranslationService
DEBUG AutoTranslationService       : Source language: fr
DEBUG AutoTranslationService       : LibreTranslate URL: http://localhost:5000
DEBUG AutoTranslationService       : Cache TTL: 86400 seconds
```

### Verify Redis Connection

```bash
redis-cli PING
# Should return: PONG

redis-cli KEYS "trans:*"
# Shows cached translations
```

### Verify LibreTranslate

```bash
curl http://localhost:5000/languages
# Should return JSON with available languages
```

---

## Next Steps

- 📖 Read [Usage Guide](USAGE.md)
- 📝 Check [Examples](../examples/)
- 🔧 See [Troubleshooting Guide](./TROUBLESHOOTING.md)
