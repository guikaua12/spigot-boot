package tech.guilhermekaua.spigotboot.commands.metadata;

import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;

import java.util.*;

public final class CommandAliasSet {
    private final String primary;
    private final List<String> aliases;

    public CommandAliasSet(String primary, List<String> aliases) {
        this.primary = primary == null ? "" : primary.trim();

        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        if (!this.primary.isEmpty()) {
            normalized.put(CommandSupport.normalizeLabel(this.primary), this.primary);
        }

        String normalizedPrimary = this.primary.isEmpty() ? null : CommandSupport.normalizeLabel(this.primary);

        if (aliases != null) {
            for (String alias : aliases) {
                if (alias == null) {
                    continue;
                }

                String trimmed = alias.trim();
                if (!trimmed.isEmpty()) {
                    String normalizedKey = CommandSupport.normalizeLabel(trimmed);
                    if (!normalizedKey.equals(normalizedPrimary)) {
                        normalized.put(normalizedKey, trimmed);
                    }
                }
            }
        }

        List<String> values = new ArrayList<>(normalized.values());
        if (!this.primary.isEmpty()) {
            values.remove(this.primary);
        }
        this.aliases = Collections.unmodifiableList(values);
    }

    public static CommandAliasSet of(String primary, String[] aliases) {
        return new CommandAliasSet(primary, aliases == null ? Collections.<String>emptyList() : Arrays.asList(aliases));
    }

    public String getPrimary() {
        return primary;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public List<String> allValues() {
        List<String> values = new ArrayList<>();
        if (!primary.isEmpty()) {
            values.add(primary);
        }
        values.addAll(aliases);
        return Collections.unmodifiableList(values);
    }
}
