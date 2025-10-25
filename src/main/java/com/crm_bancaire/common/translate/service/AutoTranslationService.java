package com.crm_bancaire.common.translate.service;

import com.crm_bancaire.common.translate.annotation.NoTranslate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for automatic translation of text and objects using LibreTranslate.
 * Uses Redis cache to avoid repeated translations and improve performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoTranslationService {
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${translate.source-language:fr}")
    private String sourceLanguage;

    @Value("${translate.libretranslate.url}")
    private String translateUrl;

    @Value("${translate.cache.ttl:86400}")
    private long cacheTtl; // Default 24 hours

    /**
     * Fields that should never be translated (common names)
     */
    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "id", "uuid", "email", "username", "password", "token",
            "firstName", "lastName", "fullName", "keycloakId",
            "createdAt", "updatedAt", "dateCreation", "dateModification",
            "telephone", "phone", "url", "uri", "sub", "iss"
    );

    /**
     * Translates a single text from source language to target language.
     * Uses Redis cache for performance.
     *
     * @param text       Text to translate
     * @param targetLang Target language code (en, es, de, it, pt)
     * @return Translated text or original if translation fails
     */
    public String translate(String text, String targetLang) {
        // No translation needed if target language is same as source
        if (sourceLanguage.equals(targetLang) || text == null || text.trim().isEmpty()) {
            return text;
        }

        // Don't translate non-translatable content
        if (isNonTranslatable(text)) {
            log.debug("Skipping non-translatable text: {}", text);
            return text;
        }

        // Check Redis cache first
        String cacheKey = buildCacheKey(targetLang, text);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT for: {} -> {}", text, cached);
            return cached;
        }

        // Call LibreTranslate API
        try {
            log.debug("Translating '{}' from {} to {}", text, sourceLanguage, targetLang);

            Map<String, String> request = Map.of(
                    "q", text,
                    "source", sourceLanguage,
                    "target", targetLang,
                    "format", "text"
            );

            @SuppressWarnings("unchecked")
            Map<String, String> response = restTemplate.postForObject(
                    translateUrl + "/translate",
                    request,
                    Map.class
            );

            if (response != null && response.containsKey("translatedText")) {
                String translated = response.get("translatedText");

                // Cache the result
                redisTemplate.opsForValue().set(cacheKey, translated, cacheTtl, TimeUnit.SECONDS);

                log.debug("Translated '{}' -> '{}'", text, translated);
                return translated;
            }

            log.warn("Translation response missing 'translatedText' field");
            return text;

        } catch (Exception e) {
            log.error("Translation failed for '{}': {}", text, e.getMessage());
            return text; // Fallback to original text
        }
    }

    /**
     * Translates all String fields in an object recursively.
     * Respects @NoTranslate annotations and field exclusion list.
     *
     * @param obj        Object to translate
     * @param targetLang Target language code
     * @param <T>        Type of the object
     * @return Translated object
     */
    public <T> T translateObject(T obj, String targetLang) {
        if (sourceLanguage.equals(targetLang) || obj == null) {
            return obj;
        }

        try {
            JsonNode node = objectMapper.valueToTree(obj);
            translateNode(node, targetLang, obj.getClass());
            return objectMapper.treeToValue(node, (Class<T>) obj.getClass());
        } catch (Exception e) {
            log.error("Object translation failed", e);
            return obj;
        }
    }

    /**
     * Recursively translates String fields in a JSON node.
     */
    private void translateNode(JsonNode node, String targetLang, Class<?> clazz) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();

                // Skip excluded fields
                if (EXCLUDED_FIELDS.contains(fieldName)) {
                    log.trace("Skipping excluded field: {}", fieldName);
                    return;
                }

                // Check @NoTranslate annotation
                if (hasNoTranslateAnnotation(clazz, fieldName)) {
                    log.trace("Skipping @NoTranslate field: {}", fieldName);
                    return;
                }

                // Translate text fields
                if (value.isTextual()) {
                    String text = value.asText();
                    if (!isNonTranslatable(text)) {
                        String translated = translate(text, targetLang);
                        ((ObjectNode) node).put(fieldName, translated);
                    }
                }
                // Recursively handle nested objects and arrays
                else if (value.isObject() || value.isArray()) {
                    translateNode(value, targetLang, clazz);
                }
            });
        } else if (node.isArray()) {
            node.forEach(item -> translateNode(item, targetLang, clazz));
        }
    }

    /**
     * Checks if a field has @NoTranslate annotation.
     */
    private boolean hasNoTranslateAnnotation(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return field.isAnnotationPresent(NoTranslate.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Determines if text should not be translated based on patterns.
     */
    private boolean isNonTranslatable(String text) {
        if (text == null || text.trim().isEmpty()) {
            return true;
        }

        // Email pattern
        if (text.contains("@") && text.contains(".")) {
            return true;
        }

        // UUID pattern (8-4-4-4-12)
        if (text.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return true;
        }

        // URL pattern
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return true;
        }

        // Pure numbers
        if (text.matches("^[0-9]+$")) {
            return true;
        }

        // ISO date format (2024-01-15 or 2024-01-15T10:30:00)
        if (text.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return true;
        }

        return false;
    }

    /**
     * Builds a cache key for Redis.
     */
    private String buildCacheKey(String targetLang, String text) {
        return String.format("trans:%s:%d", targetLang, text.hashCode());
    }
}
