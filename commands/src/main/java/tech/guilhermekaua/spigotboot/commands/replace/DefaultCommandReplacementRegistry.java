package tech.guilhermekaua.spigotboot.commands.replace;

import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistry;
import tech.guilhermekaua.spigotboot.commands.CommandReplacementRegistryCustomizer;
import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultCommandReplacementRegistry implements CommandReplacementRegistry {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("%([A-Za-z0-9_.-]+)%");

    private final Map<String, Supplier<String>> replacements = new LinkedHashMap<>();

    public DefaultCommandReplacementRegistry(List<CommandReplacementRegistryCustomizer> customizers) {
        for (CommandReplacementRegistryCustomizer customizer : CommandSupport.sortBeans(customizers)) {
            customizer.customize(this);
        }
    }

    @Override
    public void register(String key, String value) {
        final String resolvedValue = value == null ? "" : value;
        register(key, () -> resolvedValue);
    }

    @Override
    public void register(String key, Supplier<String> valueSupplier) {
        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("Replacement key cannot be blank.");
        }
        replacements.put(normalizedKey, valueSupplier == null ? () -> "" : valueSupplier);
    }

    @Override
    public String replace(String value) {
        if (value == null) return "";
        if (value.trim().isEmpty()) return "";
        return replace(value, new LinkedHashSet<>());
    }

    private String replace(String value, Set<String> stack) {
        Matcher matcher = TOKEN_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Supplier<String> valueSupplier = replacements.get(key);
            if (valueSupplier == null) {
                continue;
            }
            if (!stack.add(key)) {
                throw new IllegalStateException("Cyclic command replacement detected for key '" + key + "'.");
            }

            String resolvedValue = valueSupplier.get();
            String replacement = replace(resolvedValue == null ? "" : resolvedValue, stack);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            stack.remove(key);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
