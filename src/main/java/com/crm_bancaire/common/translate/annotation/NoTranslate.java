package com.crm_bancaire.common.translate.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields or classes that should not be translated.
 * Use this on fields like firstName, lastName, email, etc.
 *
 * Example:
 * <pre>
 * {@code
 * @Entity
 * public class User {
 *     @NoTranslate
 *     private String firstName;
 *
 *     @NoTranslate
 *     private String lastName;
 *
 *     private String bio; // Will be translated
 * }
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoTranslate {
}
