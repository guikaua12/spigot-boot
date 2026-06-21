package tech.guilhermekaua.spigotboot.commands.message;

import java.util.Map;

/**
 * Interpolates {@code {placeholder}} tokens in command message templates.
 */
public final class CommandMessageTemplates {
    private CommandMessageTemplates() {
    }

    /**
     * Replaces every {@code {name}} token in {@code template} with the string form of the
     * matching value. Tokens with no matching value are left verbatim.
     *
     * @param template     the template, may be {@code null}
     * @param placeholders the placeholder values, may be {@code null}
     * @return the interpolated text, or an empty string when {@code template} is {@code null}
     */
    public static String interpolate(String template, Map<String, Object> placeholders) {
        if (template == null) {
            return "";
        }
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
