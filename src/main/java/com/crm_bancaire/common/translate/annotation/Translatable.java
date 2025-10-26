package com.crm_bancaire.common.translate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark entity classes whose field metadata should be exposed
 * via the translation metadata endpoint.
 *
 * When an entity is marked with @Translatable, the translation service will
 * generate field labels (e.g., "firstName" -> "First Name") and make them
 * available through the /api/translate/metadata/{entity} endpoint.
 *
 * Example:
 * <pre>
 * @Entity
 * @Translatable(name = "User")
 * public class User {
 *     private String firstName; // Will generate label "First Name"
 *     private String email;     // Will generate label "Email"
 * }
 * </pre>
 *
 * Usage:
 * GET /api/translate/metadata/User?lang=en
 * Response: {"firstName": "First Name", "email": "Email"}
 *
 * GET /api/translate/metadata/User?lang=fr
 * Response: {"firstName": "Prénom", "email": "Email"}
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Translatable {

    /**
     * The name of the entity used in the metadata endpoint URL.
     * Example: @Translatable(name = "User") -> /api/translate/metadata/User
     *
     * @return The entity name for the metadata endpoint
     */
    String name();

    /**
     * Optional description of the entity (for documentation purposes).
     *
     * @return Entity description
     */
    String description() default "";
}
