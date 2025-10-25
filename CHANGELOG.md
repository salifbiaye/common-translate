# Changelog

All notable changes to the `common-translate` module will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
