package com.crm_bancaire.common.translate.config;

import com.crm_bancaire.common.translate.interceptor.TranslationResponseAdvice;
import com.crm_bancaire.common.translate.service.AutoTranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Auto-configuration for the translation module.
 * This configuration is automatically activated when @EnableAutoTranslate is used.
 *
 * Provides:
 * - RestTemplate for LibreTranslate API calls
 * - AutoTranslationService for translation logic
 * - TranslationResponseAdvice for automatic HTTP response translation
 * - EnumLabelConfig for custom enum label mappings
 * - TranslationMetadataController for field metadata endpoints
 *
 * Configuration properties:
 * - translate.enabled: Enable/disable translation (default: true)
 * - translate.source-language: Source language of your code (default: fr)
 * - translate.libretranslate.url: LibreTranslate server URL (required)
 * - translate.cache.ttl: Cache TTL in seconds (default: 86400 = 24h)
 * - translate.enum-labels: Custom enum label mappings (optional)
 */
@Configuration
@ComponentScan(basePackages = "com.crm_bancaire.common.translate")
@ConditionalOnProperty(name = "translate.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class TranslationAutoConfiguration {

    /**
     * Creates a RestTemplate bean for making HTTP calls to LibreTranslate.
     * Only creates if no RestTemplate bean already exists.
     */
    @Bean
    @ConditionalOnMissingBean(name = "translationRestTemplate")
    public RestTemplate translationRestTemplate(RestTemplateBuilder builder) {
        log.info("Creating RestTemplate for LibreTranslate API calls");
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
