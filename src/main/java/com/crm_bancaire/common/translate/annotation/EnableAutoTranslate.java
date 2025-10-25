package com.crm_bancaire.common.translate.annotation;

import com.crm_bancaire.common.translate.config.TranslationAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enable automatic translation for REST API responses.
 *
 * Add this annotation to your Spring Boot application class:
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableAutoTranslate
 * public class YourServiceApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(YourServiceApplication.class, args);
 *     }
 * }
 * }
 * </pre>
 *
 * Configuration in application.yml:
 * <pre>
 * translate:
 *   enabled: true
 *   source-language: fr
 *   libretranslate:
 *     url: http://libretranslate:5000
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(TranslationAutoConfiguration.class)
public @interface EnableAutoTranslate {
}
