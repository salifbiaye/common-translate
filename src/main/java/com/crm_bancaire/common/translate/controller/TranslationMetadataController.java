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
 * Endpoints:
 * - GET /api/translate/metadata/{entity}?lang=en
 * - GET /api/translate/metadata/entities
 */
@RestController
@RequestMapping("/api/translate/metadata")
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
     *
     * Example:
     * GET /api/translate/metadata/User?lang=en
     * Response: {"firstName": "First Name", "email": "Email", "telephone": "Telephone"}
     *
     * GET /api/translate/metadata/User?lang=fr
     * Response: {"firstName": "Prénom", "email": "Email", "telephone": "Téléphone"}
     *
     * @param entity Entity name (as defined in @Translatable annotation)
     * @param lang   Target language code (optional, defaults to source language)
     * @return Map of field names to translated labels
     */
    @GetMapping("/{entity}")
    public ResponseEntity<Map<String, String>> getEntityMetadata(
            @PathVariable String entity,
            @RequestParam(required = false) String lang) {

        if (!translationEnabled) {
            log.warn("Translation is disabled, returning empty metadata");
            return ResponseEntity.ok(new HashMap<>());
        }

        // Use source language if no language specified
        String targetLang = lang != null ? lang : sourceLanguage;

        log.debug("Getting metadata for entity '{}' in language '{}'", entity, targetLang);

        Map<String, String> metadata = translationService.getEntityMetadata(entity, targetLang);

        if (metadata.isEmpty()) {
            log.warn("No metadata found for entity '{}'. Is it annotated with @Translatable?", entity);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(metadata);
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
