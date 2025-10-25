# Configuration Guide - common-translate

## Table of Contents
1. [Basic Configuration](#basic-configuration)
2. [All Configuration Options](#all-configuration-options)
3. [Environment-Specific Configuration](#environment-specific-configuration)
4. [Redis Configuration](#redis-configuration)
5. [LibreTranslate Configuration](#libretranslate-configuration)
6. [Performance Tuning](#performance-tuning)

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

- 📖 Read [Usage Guide](./USAGE.md)
- 📝 Check [Examples](../examples/)
- 🔧 See [Troubleshooting Guide](./TROUBLESHOOTING.md)
