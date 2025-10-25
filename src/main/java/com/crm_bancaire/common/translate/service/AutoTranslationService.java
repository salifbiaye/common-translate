package com.crm_bancaire.common.translate.service;

import com.crm_bancaire.common.translate.annotation.NoTranslate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.List;
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
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        // Configure ObjectMapper to handle Java 8 date/time types
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("AutoTranslationService initialized with Java 8 date/time support");
    }

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
     * Translates an object to JsonNode WITHOUT round-trip conversion.
     * Returns JsonNode to avoid enum/date deserialization issues.
     *
     * @param obj        Object to translate
     * @param targetLang Target language code
     * @return JsonNode with translated string fields
     */
    public JsonNode translateToJsonNode(Object obj, String targetLang) {
        if (sourceLanguage.equals(targetLang) || obj == null) {
            return objectMapper.valueToTree(obj);
        }

        try {
            JsonNode node = objectMapper.valueToTree(obj);
            translateNode(node, targetLang, obj.getClass());
            return node; // Return JsonNode directly, no round-trip!
        } catch (Exception e) {
            log.error("Object translation failed", e);
            return objectMapper.valueToTree(obj);
        }
    }

    /**
     * Translates a List of objects to JsonNode array.
     *
     * @param list       List to translate
     * @param targetLang Target language code
     * @return JsonNode array with translated elements
     */
    public JsonNode translateList(List<?> list, String targetLang) {
        if (sourceLanguage.equals(targetLang) || list == null || list.isEmpty()) {
            return objectMapper.valueToTree(list);
        }

        try {
            JsonNode arrayNode = objectMapper.valueToTree(list);
            if (arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    translateNode(item, targetLang, Object.class);
                }
            }
            return arrayNode;
        } catch (Exception e) {
            log.error("List translation failed", e);
            return objectMapper.valueToTree(list);
        }
    }

    /**
     * Translates a Page of objects to JsonNode with pagination metadata.
     *
     * @param page       Page to translate
     * @param targetLang Target language code
     * @return JsonNode with translated content and preserved pagination
     */
    public JsonNode translatePage(org.springframework.data.domain.Page<?> page, String targetLang) {
        if (sourceLanguage.equals(targetLang) || page == null) {
            return objectMapper.valueToTree(page);
        }

        try {
            JsonNode pageNode = objectMapper.valueToTree(page);

            // Translate only the 'content' array, preserve pagination metadata
            JsonNode contentNode = pageNode.get("content");
            if (contentNode != null && contentNode.isArray()) {
                for (JsonNode item : contentNode) {
                    translateNode(item, targetLang, Object.class);
                }
            }

            return pageNode;
        } catch (Exception e) {
            log.error("Page translation failed", e);
            return objectMapper.valueToTree(page);
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
                    // Don't translate if it's non-translatable OR looks like an enum
                    if (!isNonTranslatable(text) && !looksLikeEnum(text)) {
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
     * Determines if text looks like an enum value.
     * Enum values are typically UPPERCASE with underscores, no spaces, and short.
     *
     * Examples: CLIENT, ADMIN, SUPER_ADMIN, ACTIF, PENDING_APPROVAL
     */
    private boolean looksLikeEnum(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Enum values are typically short
        if (text.length() > 50) {
            return false;
        }

        // Enum values don't contain spaces
        if (text.contains(" ")) {
            return false;
        }

        // Check if it's mostly uppercase letters and underscores
        // Allow some lowercase (mixed case enums exist) but mostly uppercase
        String uppercaseOnly = text.replaceAll("[^A-Z]", "");
        String lettersOnly = text.replaceAll("[^A-Za-z]", "");

        if (lettersOnly.isEmpty()) {
            return false; // Not an enum if no letters
        }

        // If more than 70% uppercase letters, likely an enum
        double uppercaseRatio = (double) uppercaseOnly.length() / lettersOnly.length();
        boolean isLikelyEnum = uppercaseRatio > 0.7;

        if (isLikelyEnum) {
            log.trace("Detected enum-like value, skipping translation: {}", text);
        }

        return isLikelyEnum;
    }

    /**
     * Builds a cache key for Redis.
     */
    private String buildCacheKey(String targetLang, String text) {
        return String.format("trans:%s:%d", targetLang, text.hashCode());
    }
}
