# Changelog

All notable changes to the `common-translate` module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - 2024-10-26

### Fixed
- 🐛 **Critical: Entity scanning fixed**: Now uses `ClassPathScanningCandidateComponentProvider` instead of `getBeansWithAnnotation`
  - JPA entities are POJOs managed by Hibernate, not Spring beans
  - Previously: `@Translatable` entities were not detected → metadata endpoint returned 404
  - Now: Correctly scans classpath for `@Translatable` entities → metadata endpoint works ✅
  - Logs: `✅ Registered translatable entity: User -> User`

- 🐛 **Metadata endpoint now uses `Accept-Language` header**: No more `?lang=` query parameter
  - **Before**: `GET /api/translate/metadata/User?lang=en`
  - **After**: `GET /api/translate/metadata/User` + `Accept-Language: en` header
  - Consistent with other translation endpoints

- 🐛 **Field names translation fixed**: Now correctly detects that field names are in English
  - **Problem**: Source language = FR, but field names = `firstName`, `lastName` (English!)
  - **Before**: `firstName` → "First Name" → translated FR to FR → stayed "First Name" ❌
  - **After**: `firstName` → "First Name" → translated EN to FR → "Prénom" ✅
  - Field metadata and enum auto-labels now translate from **EN** regardless of source language

- 🐛 **Enum labels always generated**: Now generated even for source language
  - **Problem**: With Accept-Language: FR (source), no `typeUserLabel` was added
  - **Reason**: Field names are in English, but source content is in French
  - **Now**: Enum labels **always** generated, even when `targetLang == sourceLanguage`
  - Example FR: `{"typeUser": "CLIENT", "typeUserLabel": "Client"}` ✅

- 🐛 **Custom enum labels now work correctly**: Fixed translation from configured source language
  - **Config**: `ADMIN: Administrateur système` (FR)
  - **Before**: Used auto-generated "Admin" instead of custom label
  - **After**: Uses "Administrateur système" → translates to "System administrator" (EN) ✅
  - Added detailed debug logs to trace enum label generation

- 🐛 **@NoTranslate no longer excludes from metadata**: Only prevents VALUE translation, not LABEL translation
  - **Problem**: Fields with `@NoTranslate` were excluded from metadata entirely
  - **User need**: Translated labels for forms even if values aren't translated
  - **Example**: Label "Prénom" (translated) for value "Salif" (not translated)
  - **Before**: `firstName` with `@NoTranslate` → not in metadata ❌
  - **After**: `firstName` with `@NoTranslate` → metadata has "First Name" → "Prénom" ✅
  - **Note**: Only `EXCLUDED_FIELDS` (id, email, etc.) are excluded from metadata

### Added
- ✨ **`field-names-language` configuration parameter**: Control the language of field names independently
  ```yaml
  translate:
    source-language: fr            # Language of business content (messages, descriptions)
    field-names-language: en       # Language of variable names (default: en)
  ```
  - **Use case**: Most Java projects use English variable names (`firstName`, `lastName`)
  - **Flexibility**: If you use French variables (`prenom`, `nom`), set `field-names-language: fr`
  - **Default**: `en` (since 99% of Java projects follow English naming conventions)
  - **Impact**: Used for:
    - Field metadata translation (`firstName` → "First Name" → "Prénom")
    - Auto-generated enum labels (`ADMIN` → "Admin" → "Administrateur")

### Changed
- **Translation cache keys** now include source language: `trans:{source}:{target}:{hash}`
  - Supports multi-source translation (EN field names + FR content)
  - Each source-target pair is cached separately

- **`translate()` method** now accepts optional `sourceLangOverride` parameter
  - Allows explicit source language specification
  - Used internally for field names (configured language) vs content (source language)

- **Initialization logs** now show configuration:
  ```
  ✅ AutoTranslationService initialized:
     📝 Source language: fr
     🏷️  Field names language: en
     📦 Translatable entities: 3
     ⏰ Cache TTL: 86400s
  ```

### Enhanced Logging
- **INFO level logs** for enum label generation and LibreTranslate calls (easier debugging):
  - `🔍 Getting enum label: class=User, field=typeUser, enumValue=ADMIN, enumClassName=UserRole, targetLang=en`
  - `✅ Using custom label: UserRole.ADMIN → 'Administrateur système' (from config)`
  - `🌐 Calling LibreTranslate: 'Administrateur système' (fr → en)`
  - `✅ LibreTranslate result: 'Administrateur système' (fr → en) = 'System administrator'`
  - `✅ Custom label translated: 'Administrateur système' (fr → en) = 'System administrator'`
  - `🔄 Auto-generated label: LEVEL_1 → 'Level 1'`
  - `🌐 Calling LibreTranslate: 'Level 1' (en → fr)`
  - `✅ Auto label translated: 'Level 1' (en → fr) = 'Niveau 1'`
  - `📋 Field metadata: firstName → 'First Name' (en) → 'Prénom' (fr)`

- **Error logs** when LibreTranslate fails:
  - `❌ LibreTranslate failed for 'First Name' (en → fr): Connection refused`
  - `   Check if LibreTranslate is running at: http://localhost:5000`

### Technical Details
- **Entity scanning**: `ClassPathScanningCandidateComponentProvider` scans classpath for `@Translatable`
- **Base package detection**: Finds `@SpringBootApplication` class to determine scan package
- **Field name language**: Configurable via `field-names-language` (default: `en`)
- **Content language**: Uses configured `translate.source-language` for actual content
- **Enum label logic**:
  1. Check custom config → translate from source language
  2. Fallback to auto-capitalization → translate from field-names-language
  3. Always add `{fieldName}Label` field, even for source language
- **List/Page translation**: Now detects actual element class from first non-null item
  - **Before**: Used `Object.class` → couldn't find enum fields → custom config ignored
  - **After**: Uses real class (e.g., `User.class`) → enum fields found → custom config works ✅

## [1.0.2] - 2024-10-26

### Fixed
- 🐛 **Jackson LocalDateTime support**: Added `jackson-datatype-jsr310` dependency to handle Java 8 date/time types
- 🐛 **Enum translation issue**: Implemented smart enum detection to prevent translation of enum values

### Added
- ✨ **JsonNode-based translation**: No more round-trip conversion! Object → JsonNode → Translate → Return JsonNode
- 🧠 **Smart enum detection**: Automatically detects and skips UPPERCASE enum values (CLIENT, ADMIN, etc.)
- 🏷️ **Automatic enum label generation**: Adds `{fieldName}Label` fields for enums with translated labels
  - Example: `typeUser: "ADMIN"` → adds `typeUserLabel: "Administrator"` (translated)
  - Hybrid approach: Auto-capitalizes (ADMIN → Admin) or uses custom mappings from config
- 🗂️ **Metadata endpoint**: `/api/translate/metadata/{entity}?lang=en` for field labels
  - Returns translated field labels: `{"firstName": "First Name", "email": "Email"}`
  - Cached in Redis for performance
  - Works with entities annotated with `@Translatable`
- 📦 **Full object support**: Translates UserResponse, ModuleResponse, List, Page without breaking enums/dates
- 🔧 **Custom enum label mappings**: Optional YAML configuration for precise enum labels
- 🎯 **@Translatable annotation**: Mark entities for metadata generation

### Changed
- Translation now returns JsonNode instead of reconverted objects (avoids deserialization errors)
- Enum values (>70% uppercase) are automatically skipped but get translated label fields
- Dates, IDs, emails remain unchanged via smart detection
- AutoTranslationService now scans for @Translatable entities at startup

**What Gets Translated**:
- ✅ String messages: `"Utilisateur créé"` → `"User created"`
- ✅ Object descriptions/titles: `{title: "Gestion",...}` → `{title: "Management",...}`
- ✅ Lists and Pages: All items translated
- ✅ Enum labels: `typeUser: "ADMIN"` → adds `typeUserLabel: "Administrator"` (translated)
- ❌ Enum values: `"CLIENT"`, `"ADMIN"` → Unchanged (preserved for logic)
- ❌ Dates: `"2024-10-25"` → Unchanged (auto-detected)
- ❌ Personal data: `firstName`, `lastName`, `email` → Unchanged (@NoTranslate / excluded fields)

### Technical Details
- Object → JsonNode → Translate strings in-place → Add enum labels → Return JsonNode
- No round-trip conversion = no enum/date errors
- Enum detection: checks for >70% uppercase letters, no spaces, length < 50
- Enum label generation: ADMIN → Admin → Translate to "Administrator", "Administrateur", etc.
- Custom enum mappings via `translate.enum-labels` in application.yml
- Metadata generation with Redis caching
- Field label auto-generation: camelCase → Title Case (firstName → First Name)
- Works with all response types: Single objects, List, Page

### New Components
- `@Translatable`: Annotation for entities with metadata
- `EnumLabelConfig`: Configuration for custom enum label mappings
- `FieldLabelGenerator`: Utility for camelCase → Title Case conversion
- `TranslationMetadataController`: REST endpoints for field labels

## [1.0.0] - 2024-10-25

### Added
- 🎯 **Automatic translation** of REST API responses based on `Accept-Language` header
- ⚡ **Redis caching** for translated content (configurable TTL)
- 🌍 **Multi-language support**: French, English, Spanish, German, Italian, Portuguese
- 🏷️ **@NoTranslate annotation** to exclude specific fields from translation
- 🧠 **Smart exclusion** of emails, UUIDs, URLs, dates, and personal data
- 📦 **@EnableAutoTranslate annotation** for easy activation
- 🔧 **Configurable source language** - works with any project language
- 🎨 **Support for multiple response types**:
  - Single objects (UserResponse, AccountResponse, etc.)
  - Collections (List<T>)
  - Paginated responses (Page<T>)
  - Plain strings
- 🚀 **Zero-code integration** - no controller modifications required
- 📊 **High performance** with double-layer caching (business + translation)
- 🔄 **LibreTranslate integration** for open-source translation
- 📝 **Comprehensive documentation** with guides and examples

### Features

#### Core Components
- `AutoTranslationService`: Main service for translation with Redis cache
- `TranslationResponseAdvice`: HTTP interceptor for automatic response translation
- `TranslationAutoConfiguration`: Spring Boot auto-configuration
- `@NoTranslate`: Annotation to mark non-translatable fields
- `@EnableAutoTranslate`: Activation annotation

#### Smart Exclusions
- Automatic exclusion patterns:
  - Email addresses (contains `@` and `.`)
  - UUIDs (standard UUID format)
  - URLs (starts with `http://` or `https://`)
  - Pure numbers (only digits)
  - ISO date formats (YYYY-MM-DD patterns)
- Field name exclusions: id, uuid, email, username, password, firstName, lastName, telephone, keycloakId, and more

#### Configuration Options
- `translate.enabled`: Enable/disable translation (default: true)
- `translate.source-language`: Source code language (default: fr)
- `translate.libretranslate.url`: LibreTranslate server URL
- `translate.cache.ttl`: Cache time-to-live in seconds (default: 86400)

#### Performance
- First translation: ~300ms (LibreTranslate API call)
- Cached translations: ~1-2ms (Redis)
- Combined with business cache: ~10ms total response time

### Technical Details
- **Java**: 17+
- **Spring Boot**: 3.5.5
- **Spring Framework**: 6.2.x
- **Dependencies**:
  - spring-boot-starter-web
  - spring-boot-starter-data-redis
  - spring-boot-starter-cache
  - jackson-databind
  - lombok
  - slf4j-api

### Infrastructure Requirements
- Redis server (any version compatible with Spring Data Redis)
- LibreTranslate server (latest version recommended)

### Documentation
- 📖 [Installation Guide](docs/INSTALLATION.md)
- 📖 [Usage Guide](docs/USAGE.md)
- 📖 [Configuration Guide](docs/CONFIGURATION.md)
- 📖 [Complete Example](docs/examples/UserServiceExample.java)
- 📖 [README](README.md)

### Known Limitations
- Translations are text-based only (no image/video content)
- Requires LibreTranslate server (self-hosted or external)
- Nested object translation relies on Jackson serialization
- First translation request is slower due to model loading

### Migration Guide
This is the first release. No migration needed.

---

## [Unreleased]

### Planned Features
- [ ] Translation cache preloading/warming on startup
- [ ] Fallback to other translation services (Google Translate, DeepL API)
- [ ] Translation statistics and monitoring
- [ ] Custom translation rules and overrides
- [ ] Batch translation API
- [ ] Translation memory for consistency
- [ ] Admin UI for cache management
- [ ] Metrics and health indicators
- [ ] Support for more languages
- [ ] Performance optimizations for large objects

---

## Release Notes

### v1.0.0 - Initial Release

This is the first stable release of `common-translate`. The module has been tested in production-like environments and is ready for use in microservices architectures.

**Highlights:**
- Zero-code integration with existing Spring Boot services
- Automatic translation of REST responses
- High-performance caching with Redis
- Smart field exclusion to preserve data integrity
- Comprehensive documentation and examples

**Use Cases:**
- Banking CRM systems (original use case)
- E-commerce platforms
- Multi-language SaaS applications
- International microservices
- Any Spring Boot API requiring localization

**Getting Started:**
```xml
<dependency>
    <groupId>com.crm-bancaire</groupId>
    <artifactId>common-translate</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
@SpringBootApplication
@EnableAutoTranslate
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

```yaml
translate:
  enabled: true
  source-language: fr
  libretranslate:
    url: http://localhost:5000
```

That's it! Your API responses are now automatically translated.

**Contributors:**
- Salif Biaye (@salifbiaye)

**License:**
This module is part of the SIB Banking CRM project.

---

## Support

For issues, questions, or contributions:
- 🐛 Report bugs: [GitHub Issues]
- 💬 Discussions: [GitHub Discussions]
- 📧 Contact: [Your contact]

---

## Semantic Versioning

This project follows [Semantic Versioning](https://semver.org/):
- **MAJOR** version: Incompatible API changes
- **MINOR** version: Backwards-compatible new features
- **PATCH** version: Backwards-compatible bug fixes
