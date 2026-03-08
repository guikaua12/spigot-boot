package tech.guilhermekaua.spigotboot.commands.parse;

import tech.guilhermekaua.spigotboot.commands.internal.CommandSupport;

import java.util.*;

public class CommandPatternParser {
    public CommandPattern parse(String rawPattern) {
        String trimmed = rawPattern == null ? "" : rawPattern.trim();
        if (trimmed.isEmpty()) {
            return new CommandPattern("", Collections.emptyList(), Collections.emptySet());
        }

        String[] tokens = trimmed.split("\\s+");
        List<CommandPattern.CommandSegment> segments = new ArrayList<>();
        Set<String> parameterNames = new LinkedHashSet<>();
        boolean optionalSection = false;
        for (String token : tokens) {
            if (isRequiredArgument(token)) {
                if (optionalSection) {
                    throw new CommandPatternException("Required argument cannot appear after an optional segment: " + rawPattern);
                }

                String name = unwrap(token, '<', '>');
                validateArgumentName(name, rawPattern);
                if (!parameterNames.add(name)) {
                    throw new CommandPatternException("Duplicate parameter name '" + name + "' in pattern: " + rawPattern);
                }
                segments.add(new CommandPattern.RequiredArgumentSegment(name));
                continue;
            }

            if (isOptionalArgument(token)) {
                optionalSection = true;
                String name = unwrap(token, '[', ']');
                validateArgumentName(name, rawPattern);
                if (!parameterNames.add(name)) {
                    throw new CommandPatternException("Duplicate parameter name '" + name + "' in pattern: " + rawPattern);
                }
                segments.add(new CommandPattern.OptionalArgumentSegment(name));
                continue;
            }

            if (token.indexOf('<') >= 0 || token.indexOf('>') >= 0 || token.indexOf('[') >= 0 || token.indexOf(']') >= 0) {
                throw new CommandPatternException("Malformed token '" + token + "' in pattern: " + rawPattern);
            }
            if (optionalSection) {
                throw new CommandPatternException("Optional arguments must be trailing segments: " + rawPattern);
            }
            segments.add(new CommandPattern.LiteralSegment(CommandSupport.normalizeLabel(token)));
        }

        return new CommandPattern(trimmed, segments, parameterNames);
    }

    public List<String> expandAliases(String rawPattern) {
        String trimmed = rawPattern == null ? "" : rawPattern.trim();
        if (trimmed.isEmpty()) {
            return Collections.singletonList("");
        }

        List<String> expansions = new ArrayList<>();
        expansions.add("");
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            List<String> options = expandToken(token);
            List<String> next = new ArrayList<>();
            for (String prefix : expansions) {
                for (String option : options) {
                    String value = prefix.isEmpty() ? option : prefix + " " + option;
                    next.add(value.trim());
                }
            }
            expansions = next;
        }
        return expansions;
    }

    private List<String> expandToken(String token) {
        if (isRequiredArgument(token) || isOptionalArgument(token)) {
            if (token.indexOf('|') >= 0) {
                throw new CommandPatternException("Pipe aliases are only supported for literal tokens: " + token);
            }
            return Collections.singletonList(token);
        }

        String[] parts = token.split("\\|");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String normalized = CommandSupport.normalizeLabel(part);
            if (normalized.isEmpty()) {
                throw new CommandPatternException("Invalid empty alias in token: " + token);
            }
            values.add(normalized);
        }
        return values;
    }

    private boolean isRequiredArgument(String token) {
        return token.startsWith("<") && token.endsWith(">") && token.length() > 2;
    }

    private boolean isOptionalArgument(String token) {
        return token.startsWith("[") && token.endsWith("]") && token.length() > 2;
    }

    private String unwrap(String token, char prefix, char suffix) {
        if (token.charAt(0) != prefix || token.charAt(token.length() - 1) != suffix) {
            throw new CommandPatternException("Malformed argument token: " + token);
        }
        return token.substring(1, token.length() - 1).trim();
    }

    private void validateArgumentName(String name, String rawPattern) {
        if (name.isEmpty()) {
            throw new CommandPatternException("Argument names cannot be blank: " + rawPattern);
        }
        for (int i = 0; i < name.length(); i++) {
            char current = name.charAt(i);
            boolean valid = Character.isLetterOrDigit(current) || current == '_' || current == '-';
            if (!valid) {
                throw new CommandPatternException("Invalid argument name '" + name + "' in pattern: " + rawPattern);
            }
        }
    }
}
