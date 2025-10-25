# Installation Guide - common-translate

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Infrastructure Setup](#infrastructure-setup)
3. [Maven Installation](#maven-installation)
4. [Spring Boot Configuration](#spring-boot-configuration)
5. [Verification](#verification)

---

## Prerequisites

Before installing `common-translate`, ensure you have:

- ✅ **Java 17+** installed
- ✅ **Spring Boot 3.x** application
- ✅ **Redis** server running
- ✅ **Maven** or **Gradle** build tool

---

## Infrastructure Setup

### 1. Add LibreTranslate to Docker Compose

Add the LibreTranslate service to your `docker-compose.yml`:

```yaml
services:
  # Translation Service
  libretranslate:
    image: libretranslate/libretranslate:latest
    container_name: libretranslate
    ports:
      - "5000:5000"
    environment:
      LT_LOAD_ONLY: fr,en,es,de,it,pt  # Load only required languages
      LT_SUGGESTIONS: "false"
      LT_DISABLE_WEB_UI: "false"
    networks:
      - your-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:5000/languages"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 2. Start LibreTranslate

```bash
docker-compose up -d libretranslate
```

Verify it's running:
```bash
curl http://localhost:5000/languages
```

You should see a JSON response with available languages.

---

## Maven Installation

### Step 1: Add Dependency

Add to your service's `pom.xml`:

```xml
<dependencies>
    <!-- Common Translation Module -->
    <dependency>
        <groupId>com.crm-bancaire</groupId>
        <artifactId>common-translate</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### Step 2: Build Your Project

```bash
mvn clean install
```

---

## Spring Boot Configuration

### Step 1: Enable Auto Translation

Add `@EnableAutoTranslate` annotation to your main application class:

```java
package com.yourcompany.yourservice;

import com.crm_bancaire.common.translate.annotation.EnableAutoTranslate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAutoTranslate  // ← Add this
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

### Step 2: Configure application.yml

Add translation configuration to your `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: your-service

  # Redis configuration (if not already configured)
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms

# Translation configuration
translate:
  enabled: true
  source-language: fr  # The language of your source code
  libretranslate:
    url: http://localhost:5000  # Use http://libretranslate:5000 in Docker
  cache:
    ttl: 86400  # Cache TTL in seconds (24h)
```

### Step 3: Docker Environment Configuration (Optional)

If using Docker, create a `application-docker.yml` profile:

```yaml
translate:
  libretranslate:
    url: http://libretranslate:5000  # Docker container name

spring:
  data:
    redis:
      host: redis  # Docker container name
```

---

## Verification

### 1. Start Your Application

```bash
mvn spring-boot:run
```

Or with Docker profile:
```bash
mvn spring-boot:run -Dspring.profiles.active=docker
```

### 2. Check Logs

Look for these initialization messages:

```
INFO  c.c.c.t.c.TranslationAutoConfiguration : Creating RestTemplate for LibreTranslate API calls
INFO  c.c.c.t.c.TranslationAutoConfiguration : Initializing AutoTranslationService
INFO  c.c.c.t.c.TranslationAutoConfiguration : Initializing TranslationResponseAdvice - automatic translation enabled
```

### 3. Test Translation

Make a request with different `Accept-Language` headers:

```bash
# French request (no translation)
curl -H "Accept-Language: fr" http://localhost:8080/api/your-endpoint

# English request (automatic translation)
curl -H "Accept-Language: en" http://localhost:8080/api/your-endpoint

# Spanish request (automatic translation)
curl -H "Accept-Language: es" http://localhost:8080/api/your-endpoint
```

---

## Troubleshooting

### LibreTranslate Connection Error

**Error:** `Connection refused to http://localhost:5000`

**Solution:**
1. Verify LibreTranslate is running: `docker ps | grep libretranslate`
2. Check logs: `docker logs libretranslate`
3. Wait for models to load (first startup takes 1-2 minutes)

### Redis Connection Error

**Error:** `Cannot connect to Redis at localhost:6379`

**Solution:**
1. Verify Redis is running: `docker ps | grep redis`
2. Test connection: `redis-cli ping` (should return `PONG`)
3. Check Redis configuration in `application.yml`

### Translation Not Working

**Checklist:**
- ✅ `@EnableAutoTranslate` annotation present?
- ✅ `translate.enabled: true` in configuration?
- ✅ LibreTranslate service running?
- ✅ Redis service running?
- ✅ `Accept-Language` header in request?

### Performance Issues

If translations are slow (>500ms):
1. Check Redis cache is working: `redis-cli KEYS "trans:*"`
2. Verify LibreTranslate has loaded models
3. Increase cache TTL if needed
4. Consider warming up cache for common translations

---

## Next Steps

- 📖 Read the [Usage Guide](./USAGE.md)
- ⚙️ See [Configuration Options](./CONFIGURATION.md)
- 📝 Check [Examples](../examples/)

---

## Support

For issues or questions:
- GitHub Issues: [Your GitHub repo]
- Documentation: [docs/](../)
