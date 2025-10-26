package com.crm_bancaire.common.translate.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for generating human-readable field labels from Java field names.
 * Converts camelCase and snake_case field names to Title Case labels.
 *
 * Examples:
 * - firstName -> First Name
 * - dateCreation -> Date Creation
 * - email -> Email
 * - userRole -> User Role
 * - first_name -> First Name
 */
@Slf4j
public class FieldLabelGenerator {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z])([A-Z])");
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("_");

    /**
     * Converts a field name to a human-readable label in Title Case.
     *
     * @param fieldName The Java field name (e.g., "firstName", "user_role")
     * @return The formatted label (e.g., "First Name", "User Role")
     */
    public static String generateLabel(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }

        try {
            // Handle snake_case (convert to spaces)
            String withSpaces = SNAKE_CASE_PATTERN.matcher(fieldName).replaceAll(" ");

            // Handle camelCase (insert space before uppercase letters)
            Matcher matcher = CAMEL_CASE_PATTERN.matcher(withSpaces);
            String result = matcher.replaceAll("$1 $2");

            // Capitalize first letter of each word
            return capitalize(result);

        } catch (Exception e) {
            log.warn("Failed to generate label for field '{}': {}", fieldName, e.getMessage());
            return capitalizeFirst(fieldName); // Fallback: just capitalize first letter
        }
    }

    /**
     * Capitalizes the first letter of each word in a string.
     *
     * @param input The input string (e.g., "first name")
     * @return Capitalized string (e.g., "First Name")
     */
    private static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            result.append(capitalizeFirst(words[i]));
        }

        return result.toString();
    }

    /**
     * Capitalizes only the first letter of a string.
     *
     * @param input The input string (e.g., "hello")
     * @return Capitalized string (e.g., "Hello")
     */
    private static String capitalizeFirst(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Generates an enum label by capitalizing the first letter and lowercasing the rest.
     * Example: "ADMIN" -> "Admin", "SUPER_USER" -> "Super User"
     *
     * @param enumValue The enum value (e.g., "ADMIN", "SUPER_USER")
     * @return The formatted label (e.g., "Admin", "Super User")
     */
    public static String generateEnumLabel(String enumValue) {
        if (enumValue == null || enumValue.isEmpty()) {
            return enumValue;
        }

        try {
            // Replace underscores with spaces
            String withSpaces = enumValue.replace("_", " ");

            // Lowercase everything
            String lowercase = withSpaces.toLowerCase();

            // Capitalize first letter of each word
            return capitalize(lowercase);

        } catch (Exception e) {
            log.warn("Failed to generate enum label for '{}': {}", enumValue, e.getMessage());
            return capitalizeFirst(enumValue.toLowerCase());
        }
    }
}
