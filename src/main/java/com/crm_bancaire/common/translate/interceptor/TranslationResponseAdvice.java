package com.crm_bancaire.common.translate.interceptor;

import com.crm_bancaire.common.translate.service.AutoTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.InputStream;
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
     * This advice supports all response types EXCEPT binary file responses.
     * Binary responses (Excel files, PDFs, images) should not be translated.
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!translationEnabled) {
            return false;
        }

        // Don't translate binary responses (file downloads)
        if (ByteArrayHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }

        if (ResourceHttpMessageConverter.class.isAssignableFrom(converterType)) {
            return false;
        }

        return true;
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

        // Defense-in-depth: Skip binary data types (file downloads)
        if (body instanceof byte[] || body instanceof Resource || body instanceof InputStream) {
            log.trace("⏭️  Skipping translation for binary response: {}", body.getClass().getSimpleName());
            return body;
        }

        // Skip binary media types (Excel, PDF, images, etc.)
        if (selectedContentType != null && isBinaryMediaType(selectedContentType)) {
            log.trace("⏭️  Skipping translation for binary media type: {}", selectedContentType);
            return body;
        }

        // Extract target language from Accept-Language header
        String targetLang = extractLanguage(request);

        // IMPORTANT: Always process responses, even for source language!
        // Reason: Enum labels must be generated even when targetLang == sourceLanguage
        // because field names are in English but content is in source language
        log.debug("🌐 Processing response for language: {} (source: {})", targetLang, sourceLanguage);

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

    /**
     * Checks if the media type represents binary content that should not be translated.
     *
     * @param mediaType The media type to check
     * @return true if the media type is binary (Excel, PDF, images, etc.)
     */
    private boolean isBinaryMediaType(MediaType mediaType) {
        // Skip images
        if (mediaType.getType().equals("image")) {
            return true;
        }

        // Skip video and audio
        if (mediaType.getType().equals("video") || mediaType.getType().equals("audio")) {
            return true;
        }

        // Skip common binary application types
        String subtype = mediaType.getSubtype().toLowerCase();
        return subtype.contains("octet-stream")
                || subtype.contains("pdf")
                || subtype.contains("zip")
                || subtype.contains("excel")
                || subtype.contains("spreadsheet")
                || subtype.contains("word")
                || subtype.contains("msword")
                || subtype.contains("powerpoint")
                || subtype.contains("presentation");
    }
}
