package com.crm_bancaire.common.translate.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for custom enum label mappings.
 * Loads optional override mappings from application.yml.
 *
 * Configuration example in application.yml:
 * <pre>
 * translate:
 *   enum-labels:
 *     UserRole:
 *       ADMIN: Administrator
 *       CLIENT: Customer
 *       CONSEILLER: Advisor
 *     UserLevel:
 *       LEVEL_1: Level One
 *       LEVEL_2: Level Two
 *       LEVEL_3: Level Three
 * </pre>
 *
 * If no mapping is provided, the system uses automatic capitalization:
 * - ADMIN -> Admin
 * - SUPER_USER -> Super User
 */
@Component
@ConfigurationProperties(prefix = "translate")
@Data
@Slf4j
public class EnumLabelConfig {

    /**
     * Map of enum class names to their custom label mappings.
     * Key: Enum class name (e.g., "UserRole")
     * Value: Map of enum values to custom labels (e.g., "ADMIN" -> "Administrator")
     */
    private Map<String, Map<String, String>> enumLabels = new HashMap<>();

    /**
     * Gets the custom label for a specific enum value.
     *
     * @param enumClassName The enum class name (e.g., "UserRole")
     * @param enumValue     The enum value (e.g., "ADMIN")
     * @return Custom label if configured, null otherwise
     */
    public String getCustomLabel(String enumClassName, String enumValue) {
        if (enumLabels == null || !enumLabels.containsKey(enumClassName)) {
            return null;
        }

        Map<String, String> classLabels = enumLabels.get(enumClassName);
        String customLabel = classLabels.get(enumValue);

        if (customLabel != null) {
            log.trace("Using custom enum label: {}.{} -> {}", enumClassName, enumValue, customLabel);
        }

        return customLabel;
    }

    /**
     * Checks if there are any custom mappings configured for a specific enum class.
     *
     * @param enumClassName The enum class name (e.g., "UserRole")
     * @return true if custom mappings exist for this enum class
     */
    public boolean hasCustomLabels(String enumClassName) {
        return enumLabels != null && enumLabels.containsKey(enumClassName);
    }

    /**
     * Gets all custom labels for a specific enum class.
     *
     * @param enumClassName The enum class name (e.g., "UserRole")
     * @return Map of enum values to custom labels, or empty map if none configured
     */
    public Map<String, String> getEnumClassLabels(String enumClassName) {
        if (enumLabels == null || !enumLabels.containsKey(enumClassName)) {
            return new HashMap<>();
        }
        return new HashMap<>(enumLabels.get(enumClassName));
    }
}
