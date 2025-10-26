package com.crm_bancaire.common.translate.service;

import com.crm_bancaire.common.translate.annotation.NoTranslate;
import com.crm_bancaire.common.translate.annotation.Translatable;
import com.crm_bancaire.common.translate.config.EnumLabelConfig;
import com.crm_bancaire.common.translate.util.FieldLabelGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Service for automatic translation of text and objects using LibreTranslate.
 * Uses two-level caching strategy:
 * - L1 Cache (Caffeine): Fast in-memory cache with automatic synchronization (prevents cache stampede)
 * - L2 Cache (Redis): Distributed cache shared across service instances
 *
 * Concurrent Request Handling:
 * When multiple threads request the same uncached translation simultaneously,
 * Caffeine ensures only ONE thread calls LibreTranslate while others wait.
 * This prevents the "thundering herd" problem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoTranslationService {
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final EnumLabelConfig enumLabelConfig;
    private final ApplicationContext applicationContext;
    private ObjectMapper objectMapper;

    /**
     * L1 cache (Caffeine): Local in-memory cache with automatic synchronization.
     * Prevents cache stampede - only one thread loads a value for concurrent requests.
     * Max 10,000 entries, expires after 30 minutes.
     */
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

    /**
     * Map of entity names to their Class objects for metadata generation
     */
    private final Map<String, Class<?>> translatableEntities = new HashMap<>();

    @PostConstruct
    public void init() {
        // Configure ObjectMapper to handle Java 8 date/time types
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Scan for @Translatable entities
        scanTranslatableEntities();

        log.info("✅ AutoTranslationService initialized:");
        log.info("   📝 Source language: {}", sourceLanguage);
        log.info("   🏷️  Field names language: {}", fieldNamesLanguage);
        log.info("   📦 Translatable entities: {}", translatableEntities.size());
        log.info("   ⏰ Cache TTL: {}s", cacheTtl);
    }

    /**
     * Scans the classpath for classes annotated with @Translatable.
     * These entities will be available for metadata generation.
     */
    private void scanTranslatableEntities() {
        try {
            // Find base package from @SpringBootApplication
            String basePackage = findBasePackage();
            if (basePackage == null) {
                log.warn("Could not find @SpringBootApplication class, skipping entity scan");
                return;
            }

            log.debug("Scanning for @Translatable entities in package: {}", basePackage);

            // Create scanner that looks for @Translatable annotation
            ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Translatable.class));

            // Scan for candidate classes
            Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition bd : candidateComponents) {
                try {
                    // Load the class
                    Class<?> clazz = Class.forName(bd.getBeanClassName());

                    // Get @Translatable annotation
                    Translatable annotation = clazz.getAnnotation(Translatable.class);
                    if (annotation != null) {
                        String entityName = annotation.name();
                        translatableEntities.put(entityName, clazz);
                        log.info("✅ Registered translatable entity: {} -> {}", entityName, clazz.getSimpleName());
                    }
                } catch (ClassNotFoundException e) {
                    log.warn("Could not load class: {}", bd.getBeanClassName());
                }
            }

            log.info("Entity scan complete. Found {} translatable entities", translatableEntities.size());

        } catch (Exception e) {
            log.warn("Failed to scan for @Translatable entities: {}", e.getMessage(), e);
        }
    }

    /**
     * Finds the base package of the application by locating @SpringBootApplication class
     */
    private String findBasePackage() {
        try {
            Map<String, Object> springBootApps = applicationContext.getBeansWithAnnotation(SpringBootApplication.class);
            if (!springBootApps.isEmpty()) {
                Object app = springBootApps.values().iterator().next();
                String packageName = app.getClass().getPackage().getName();

                // Handle proxy classes (Spring may wrap the main class)
                if (packageName.contains("$$EnhancerBySpringCGLIB")) {
                    packageName = app.getClass().getSuperclass().getPackage().getName();
                }

                log.debug("Found base package: {}", packageName);
                return packageName;
            }
        } catch (Exception e) {
            log.warn("Error finding base package: {}", e.getMessage());
        }
        return null;
    }

    @Value("${translate.source-language:fr}")
    private String sourceLanguage;

    @Value("${translate.field-names-language:en}")
    private String fieldNamesLanguage; // Language used for field names (default: en)

    @Value("${translate.libretranslate.url}")
    private String translateUrl;

    @Value("${translate.cache.ttl:86400}")
    private long cacheTtl; // Default 24 hours

    /**
     * Technical fields that should be excluded from metadata generation.
     * These are internal/system fields that don't need labels in forms.
     *
     * NOTE: User data fields like firstName, lastName, email, telephone
     * are NOT in this list because we need their LABELS for forms,
     * even if their VALUES shouldn't be translated (via @NoTranslate).
     */
    private static final Set<String> EXCLUDED_FIELDS = Set.of(
            "id", "uuid", "password", "token", "keycloakId",
            "createdAt", "updatedAt", "dateCreation", "dateModification",
            "url", "uri", "sub", "iss"
    );

    /**
     * Translates a single text from source language to target language.
     * Uses Redis cache for performance.
     *
     * Special handling: If source language is FR but text appears to be EN,
     * translate from EN instead (for field names like "firstName" → "First Name").
     *
     * @param text       Text to translate
     * @param targetLang Target language code (en, es, de, it, pt)
     * @return Translated text or original if translation fails
     */
    public String translate(String text, String targetLang) {
        return translate(text, null, targetLang);
    }

    /**
     * Translates text with explicit source language override.
     *
     * @param text       Text to translate
     * @param sourceLangOverride Override source language (null = use configured source)
     * @param targetLang Target language code
     * @return Translated text
     */
    public String translate(String text, String sourceLangOverride, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // Determine actual source language
        String actualSource = sourceLangOverride != null ? sourceLangOverride : sourceLanguage;

        // No translation needed if target is same as actual source
        if (actualSource.equals(targetLang)) {
            log.info("⏭️  Skipping translation (same language): '{}' ({} → {})", text, actualSource, targetLang);
            return text;
        }

        // Don't translate non-translatable content
        if (isNonTranslatable(text)) {
            log.debug("⏭️  Skipping non-translatable text: {}", text);
            return text;
        }

        // Build cache key (include source in cache key for multi-source support)
        String cacheKey = buildCacheKey(actualSource, targetLang, text);

        // Two-level cache strategy with automatic synchronization for concurrent requests
        // This ensures only ONE thread calls LibreTranslate when 10+ requests arrive simultaneously
        return localCache.get(cacheKey, k -> {
            // This loading function is called ONLY if L1 cache misses
            // Caffeine automatically synchronizes concurrent requests for the same key

            // Check L2 cache (Redis) - distributed cache across service instances
            String redisResult = redisTemplate.opsForValue().get(cacheKey);
            if (redisResult != null) {
                log.trace("📦 L2 Cache HIT (Redis): {} ({} → {}) = {}", text, actualSource, targetLang, redisResult);
                return redisResult;
            }

            // Both caches missed - call LibreTranslate API
            // Only ONE thread reaches this point for concurrent requests
            log.info("🌐 Calling LibreTranslate (L1+L2 cache MISS): '{}' ({} → {})", text, actualSource, targetLang);

            try {
                Map<String, String> request = Map.of(
                        "q", text,
                        "source", actualSource,
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

                    // Store in L2 cache (Redis) for other service instances
                    redisTemplate.opsForValue().set(cacheKey, translated, cacheTtl, TimeUnit.SECONDS);

                    log.info("✅ LibreTranslate result: '{}' ({} → {}) = '{}'", text, actualSource, targetLang, translated);
                    return translated;
                }

                log.warn("⚠️ Translation response missing 'translatedText' field");
                return text;

            } catch (Exception e) {
                log.error("❌ LibreTranslate failed for '{}' ({} → {}): {}", text, actualSource, targetLang, e.getMessage());
                log.error("   Check if LibreTranslate is running at: {}", translateUrl);
                return text; // Fallback to original text
            }
        });
    }

    /**
     * Translates an object to JsonNode WITHOUT round-trip conversion.
     * Returns JsonNode to avoid enum/date deserialization issues.
     *
     * IMPORTANT: Enum labels are ALWAYS generated, even when targetLang == sourceLanguage,
     * because field names are in English but source content is in another language.
     *
     * @param obj        Object to translate
     * @param targetLang Target language code
     * @return JsonNode with translated string fields
     */
    public JsonNode translateToJsonNode(Object obj, String targetLang) {
        if (obj == null) {
            return objectMapper.valueToTree(obj);
        }

        try {
            JsonNode node = objectMapper.valueToTree(obj);
            // ALWAYS process nodes to generate enum labels, even for source language
            translateNode(node, targetLang, obj.getClass());
            return node; // Return JsonNode directly, no round-trip!
        } catch (Exception e) {
            log.error("Object translation failed", e);
            return objectMapper.valueToTree(obj);
        }
    }

    /**
     * Translates a List of objects to JsonNode array.
     * Enum labels are always generated, even for source language.
     *
     * @param list       List to translate
     * @param targetLang Target language code
     * @return JsonNode array with translated elements
     */
    public JsonNode translateList(List<?> list, String targetLang) {
        if (list == null || list.isEmpty()) {
            return objectMapper.valueToTree(list);
        }

        try {
            // Detect actual element class from first non-null element
            Class<?> elementClass = Object.class;
            for (Object item : list) {
                if (item != null) {
                    elementClass = item.getClass();
                    break;
                }
            }

            JsonNode arrayNode = objectMapper.valueToTree(list);
            if (arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    translateNode(item, targetLang, elementClass);
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
     * Enum labels are always generated, even for source language.
     *
     * @param page       Page to translate
     * @param targetLang Target language code
     * @return JsonNode with translated content and preserved pagination
     */
    public JsonNode translatePage(org.springframework.data.domain.Page<?> page, String targetLang) {
        if (page == null) {
            return objectMapper.valueToTree(page);
        }

        try {
            // Detect actual element class from first non-null element
            Class<?> elementClass = Object.class;
            for (Object item : page.getContent()) {
                if (item != null) {
                    elementClass = item.getClass();
                    break;
                }
            }

            JsonNode pageNode = objectMapper.valueToTree(page);

            // Translate only the 'content' array, preserve pagination metadata
            JsonNode contentNode = pageNode.get("content");
            if (contentNode != null && contentNode.isArray()) {
                for (JsonNode item : contentNode) {
                    translateNode(item, targetLang, elementClass);
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
     * Also adds automatic enum label fields for detected enum values.
     */
    private void translateNode(JsonNode node, String targetLang, Class<?> clazz) {
        log.info("🔄 Processing node for translation: class={}, targetLang={}, sourceLanguage={}",
                 clazz.getSimpleName(), targetLang, sourceLanguage);

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;

            // First pass: identify enums and add label fields
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            node.fields().forEachRemaining(entries::add);

            for (Map.Entry<String, JsonNode> entry : entries) {
                String fieldName = entry.getKey();
                JsonNode value = entry.getValue();

                // Skip already processed label fields
                if (fieldName.endsWith("Label")) {
                    continue;
                }

                // Skip excluded fields (technical fields)
                if (EXCLUDED_FIELDS.contains(fieldName)) {
                    log.trace("Skipping excluded field: {}", fieldName);
                    continue;
                }

                // Check if field has @NoTranslate annotation
                boolean hasNoTranslate = hasNoTranslateAnnotation(clazz, fieldName);

                // Handle text fields
                if (value.isTextual()) {
                    String text = value.asText();

                    // Check if it's an enum-like value
                    if (looksLikeEnum(text)) {
                        log.info("🎯 Detected enum field: {}={} (looks like enum)", fieldName, text);
                        // ALWAYS add enum label, even for @NoTranslate fields
                        // (Labels are for UI display, @NoTranslate only affects VALUES)
                        String enumLabel = getEnumLabel(clazz, fieldName, text, targetLang);
                        objectNode.put(fieldName + "Label", enumLabel);
                        log.info("✅ Added enum label: {}={} → {}Label={}", fieldName, text, fieldName, enumLabel);
                    }
                    // Translate regular text fields ONLY if NOT marked with @NoTranslate
                    else if (!hasNoTranslate && !sourceLanguage.equals(targetLang) && !isNonTranslatable(text)) {
                        log.info("📝 Translating text field: {}='{}'", fieldName, text);
                        String translated = translate(text, targetLang);
                        objectNode.put(fieldName, translated);
                    } else {
                        log.debug("⏭️  Skipping translation for field {}: hasNoTranslate={}, sourceLanguage={}, targetLang={}, nonTranslatable={}",
                                  fieldName, hasNoTranslate, sourceLanguage, targetLang, isNonTranslatable(text));
                    }
                }
                // Recursively handle nested objects and arrays
                else if (value.isObject() || value.isArray()) {
                    translateNode(value, targetLang, clazz);
                }
            }
        } else if (node.isArray()) {
            node.forEach(item -> translateNode(item, targetLang, clazz));
        }
    }

    /**
     * Generates a translated label for an enum value.
     * Uses custom mapping if available, otherwise generates from enum value.
     *
     * @param clazz     The entity class
     * @param fieldName The field name (e.g., "typeUser")
     * @param enumValue The enum value (e.g., "ADMIN")
     * @param targetLang Target language
     * @return Translated enum label
     */
    private String getEnumLabel(Class<?> clazz, String fieldName, String enumValue, String targetLang) {
        // Try to get field type to determine enum class name
        String enumClassName = getEnumClassName(clazz, fieldName);

        log.info("🔍 Getting enum label: class={}, field={}, enumValue={}, enumClassName={}, targetLang={}",
                  clazz.getSimpleName(), fieldName, enumValue, enumClassName, targetLang);

        // Check for custom mapping first
        if (enumClassName != null) {
            String customLabel = enumLabelConfig.getCustomLabel(enumClassName, enumValue);
            if (customLabel != null) {
                log.info("✅ Using custom label: {}.{} → '{}' (from config)", enumClassName, enumValue, customLabel);
                // Translate the custom label from SOURCE language to target
                String translated = translate(customLabel, sourceLanguage, targetLang);
                log.info("✅ Custom label translated: '{}' ({} → {}) = '{}'",
                         customLabel, sourceLanguage, targetLang, translated);
                return translated;
            } else {
                log.info("ℹ️ No custom label found for {}.{}, using auto-generation", enumClassName, enumValue);
            }
        } else {
            log.warn("⚠️ Could not determine enum class name for field '{}' in class {}",
                     fieldName, clazz.getSimpleName());
        }

        // Fallback: auto-generate label from enum value (ADMIN -> Admin)
        String generatedLabel = FieldLabelGenerator.generateEnumLabel(enumValue);
        log.info("🔄 Auto-generated label: {} → '{}'", enumValue, generatedLabel);

        // Translate the generated label from field names language to target
        String translated = translate(generatedLabel, fieldNamesLanguage, targetLang);
        log.info("✅ Auto label translated: '{}' ({} → {}) = '{}'",
                  generatedLabel, fieldNamesLanguage, targetLang, translated);
        return translated;
    }

    /**
     * Gets the enum class name for a given field.
     *
     * @param clazz     The entity class
     * @param fieldName The field name
     * @return Enum class simple name, or null if not found
     */
    private String getEnumClassName(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            Class<?> fieldType = field.getType();
            if (fieldType.isEnum()) {
                return fieldType.getSimpleName();
            }
        } catch (NoSuchFieldException e) {
            log.trace("Field '{}' not found in class {}", fieldName, clazz.getSimpleName());
        }
        return null;
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
    private String buildCacheKey(String sourceLang, String targetLang, String text) {
        return String.format("trans:%s:%s:%d", sourceLang, targetLang, text.hashCode());
    }

    /**
     * Gets translated field metadata (labels) for a specific entity.
     * Results are cached in Redis for performance.
     *
     * @param entityName The entity name (as defined in @Translatable annotation)
     * @param targetLang Target language code
     * @return Map of field names to translated labels
     */
    public Map<String, String> getEntityMetadata(String entityName, String targetLang) {
        // Check Redis cache first
        String cacheKey = "metadata:" + entityName + ":" + targetLang;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> cachedMetadata = objectMapper.readValue(cached, Map.class);
                log.debug("Cache HIT for metadata: {}/{}", entityName, targetLang);
                return cachedMetadata;
            } catch (Exception e) {
                log.warn("Failed to deserialize cached metadata: {}", e.getMessage());
            }
        }

        // Get entity class
        Class<?> entityClass = translatableEntities.get(entityName);
        if (entityClass == null) {
            log.warn("Unknown entity for metadata: {}", entityName);
            return new HashMap<>();
        }

        // Generate field labels
        Map<String, String> metadata = new HashMap<>();
        Field[] fields = entityClass.getDeclaredFields();

        for (Field field : fields) {
            String fieldName = field.getName();

            // Skip excluded fields (technical fields)
            if (EXCLUDED_FIELDS.contains(fieldName)) {
                continue;
            }

            // NOTE: @NoTranslate does NOT exclude from metadata!
            // @NoTranslate only prevents VALUE translation, not LABEL translation
            // Users need translated labels for forms even if values aren't translated
            // Example: Label "Prénom" (translated) for value "Salif" (not translated)

            // Generate label (firstName -> First Name)
            String label = FieldLabelGenerator.generateLabel(fieldName);

            // Translate the label from field names language to target
            String translatedLabel = translate(label, fieldNamesLanguage, targetLang);

            log.debug("📋 Field metadata: {} → '{}' ({}) → '{}' ({})",
                      fieldName, label, fieldNamesLanguage, translatedLabel, targetLang);

            metadata.put(fieldName, translatedLabel);
        }

        // Cache the result
        try {
            String json = objectMapper.writeValueAsString(metadata);
            redisTemplate.opsForValue().set(cacheKey, json, cacheTtl, TimeUnit.SECONDS);
            log.debug("Cached metadata for: {}/{}", entityName, targetLang);
        } catch (Exception e) {
            log.warn("Failed to cache metadata: {}", e.getMessage());
        }

        return metadata;
    }

    /**
     * Gets a list of all registered translatable entities.
     *
     * @return Set of entity names
     */
    public Set<String> getRegisteredEntities() {
        return new HashSet<>(translatableEntities.keySet());
    }
}
