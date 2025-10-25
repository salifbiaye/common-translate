package com.crm_bancaire.common.translate.interceptor;

import com.crm_bancaire.common.translate.service.AutoTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;
import java.util.Locale;

/**
 * Interceptor that automatically translates REST API responses based on Accept-Language header.
 * This advice runs after the controller returns a response but before it's serialized to JSON.
 *
 * Supports translation of:
 * - Plain String responses
 * - Single objects (UserResponse, AccountResponse, etc.)
 * - Collections (List, Page)
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class TranslationResponseAdvice implements ResponseBodyAdvice<Object> {

    private final AutoTranslationService translationService;

    @Value("${translate.enabled:true}")
    private boolean translationEnabled;

    @Value("${translate.source-language:fr}")
    private String sourceLanguage;

    /**
     * This advice supports all response types.
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return translationEnabled;
    }

    /**
     * Translates the response body before it's written to the HTTP response.
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {

        if (body == null) {
            return null;
        }

        // Extract target language from Accept-Language header
        String targetLang = extractLanguage(request);

        // No translation needed if target language is same as source
        if (sourceLanguage.equals(targetLang)) {
            log.trace("No translation needed, target language matches source: {}", targetLang);
            return body;
        }

        log.debug("Translating response to language: {}", targetLang);

        try {
            // Translate String responses (messages, errors)
            if (body instanceof String) {
                return translationService.translate((String) body, targetLang);
            }

            // Translate complex objects (UserResponse, Module, etc.)
            // WITHOUT round-trip: Object → JsonNode → Translate → Return JsonNode
            // Spring will serialize the JsonNode, avoiding enum/date issues
            if (body instanceof Page) {
                return translationService.translatePage((Page<?>) body, targetLang);
            }
            else if (body instanceof List) {
                return translationService.translateList((List<?>) body, targetLang);
            }
            else {
                // Single object (UserResponse, ModuleResponse, etc.)
                return translationService.translateToJsonNode(body, targetLang);
            }

        } catch (Exception e) {
            log.error("Translation failed, returning original response", e);
            return body;
        }
    }

    /**
     * Extracts the target language from the Accept-Language HTTP header.
     * Defaults to source language if header is missing.
     *
     * Examples:
     * - "en" -> "en"
     * - "en-US" -> "en"
     * - "fr-FR,fr;q=0.9,en;q=0.8" -> "fr"
     * - null -> sourceLanguage
     *
     * @param request HTTP request
     * @return Language code (2 letters: en, fr, es, de, it, pt)
     */
    private String extractLanguage(ServerHttpRequest request) {
        List<String> languages = request.getHeaders().get("Accept-Language");

        if (languages == null || languages.isEmpty()) {
            log.trace("No Accept-Language header found, using source language: {}", sourceLanguage);
            return sourceLanguage;
        }

        String acceptLanguage = languages.get(0);

        // Handle complex Accept-Language headers like "fr-FR,fr;q=0.9,en;q=0.8"
        if (acceptLanguage.contains(",")) {
            acceptLanguage = acceptLanguage.split(",")[0];
        }

        // Handle quality values like "fr;q=0.9"
        if (acceptLanguage.contains(";")) {
            acceptLanguage = acceptLanguage.split(";")[0];
        }

        // Extract first 2 characters (en-US -> en, fr-FR -> fr)
        String lang = acceptLanguage.length() >= 2
                ? acceptLanguage.substring(0, 2).toLowerCase()
                : sourceLanguage;

        log.trace("Extracted language '{}' from Accept-Language header: {}", lang, languages.get(0));
        return lang;
    }
}
