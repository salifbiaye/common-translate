package com.crm_bancaire.common.translate.controller;

import com.crm_bancaire.common.translate.service.AutoTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * REST controller that exposes translation metadata endpoints.
 * Provides field labels for entities annotated with @Translatable.
 *
 * The base path is configurable per service:
 * - user-service: /api/users/translate/metadata
 * - customer-service: /api/customers/translate/metadata
 *
 * Configure in application.yml:
 * translate:
 *   metadata:
 *     base-path: /api/users  # Service-specific prefix
 *
 * Endpoints:
 * - GET {base-path}/translate/metadata/{entity}
 * - GET {base-path}/translate/metadata/entities
 * - GET {base-path}/translate/metadata/health
 */
@RestController
@RequestMapping("${translate.metadata.base-path:/api}/translate/metadata")
@RequiredArgsConstructor
@Slf4j
public class TranslationMetadataController {

    private final AutoTranslationService translationService;

    @Value("${translate.enabled:true}")
    private boolean translationEnabled;

    @Value("${translate.source-language:fr}")
    private String sourceLanguage;

    /**
     * Gets translated field labels for a specific entity.
     * Uses Accept-Language header to determine target language.
     *
     * Example:
     * GET /api/translate/metadata/User
     * Accept-Language: en
     * Response: {"firstName": "First Name", "email": "Email", "telephone": "Telephone"}
     *
     * GET /api/translate/metadata/User
     * Accept-Language: fr
     * Response: {"firstName": "Prénom", "email": "Email", "telephone": "Téléphone"}
     *
     * @param entity Entity name (as defined in @Translatable annotation)
     * @param acceptLanguage Accept-Language header (optional, defaults to source language)
     * @return Map of field names to translated labels
     */
    @GetMapping("/{entity}")
    public ResponseEntity<Map<String, String>> getEntityMetadata(
            @PathVariable String entity,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {

        if (!translationEnabled) {
            log.warn("Translation is disabled, returning empty metadata");
            return ResponseEntity.ok(new HashMap<>());
        }

        // Extract language code from Accept-Language header
        String targetLang = extractLanguageCode(acceptLanguage);

        log.debug("Getting metadata for entity '{}' in language '{}' (from Accept-Language: {})",
                  entity, targetLang, acceptLanguage);

        Map<String, String> metadata = translationService.getEntityMetadata(entity, targetLang);

        if (metadata.isEmpty()) {
            log.warn("No metadata found for entity '{}'. Is it annotated with @Translatable?", entity);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(metadata);
    }

    /**
     * Extracts language code from Accept-Language header.
     * Supports: "en", "en-US", "en-US,fr;q=0.9"
     */
    private String extractLanguageCode(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.trim().isEmpty()) {
            return sourceLanguage;
        }

        // Take first language (before comma)
        String firstLang = acceptLanguage.split(",")[0].trim();

        // Extract language code (before dash or semicolon)
        String langCode = firstLang.split("[-;]")[0].trim();

        return langCode.isEmpty() ? sourceLanguage : langCode;
    }

    /**
     * Gets a list of all registered translatable entities.
     *
     * Example:
     * GET /api/translate/metadata/entities
     * Response: ["User", "Account", "Module"]
     *
     * @return Set of entity names that have metadata available
     */
    @GetMapping("/entities")
    public ResponseEntity<Set<String>> getRegisteredEntities() {
        if (!translationEnabled) {
            log.warn("Translation is disabled, returning empty entity list");
            return ResponseEntity.ok(Set.of());
        }

        Set<String> entities = translationService.getRegisteredEntities();
        log.debug("Registered translatable entities: {}", entities);

        return ResponseEntity.ok(entities);
    }

    /**
     * Health check endpoint for the metadata service.
     *
     * @return Status information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", translationEnabled);
        status.put("sourceLanguage", sourceLanguage);
        status.put("entitiesCount", translationService.getRegisteredEntities().size());
        status.put("entities", translationService.getRegisteredEntities());

        return ResponseEntity.ok(status);
    }
}
