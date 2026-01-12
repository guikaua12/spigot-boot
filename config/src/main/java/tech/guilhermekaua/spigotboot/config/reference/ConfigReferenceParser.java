/*
 * The MIT License
 * Copyright © 2025 Guilherme Kauã da Silva
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package tech.guilhermekaua.spigotboot.config.reference;

import org.jetbrains.annotations.NotNull;
import tech.guilhermekaua.spigotboot.utils.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses config reference tokens from string values.
 * <p>
 * Reference syntax:
 * <ul>
 *   <li>{@code ${configName}} - single config root</li>
 *   <li>{@code ${configName:path.to.value}} - single config with path</li>
 *   <li>{@code ${collectionName.itemId}} - collection item root</li>
 *   <li>{@code ${collectionName.itemId:path.to.value}} - collection item with path</li>
 * </ul>
 * <p>
 * Names (configName, collectionName, itemId) must match {@code [a-zA-Z0-9_-]+}.
 * Names cannot contain '.' or ':' as these are used as delimiters.
 */
public class ConfigReferenceParser {

    private static final String PREFIX = "${";
    private static final String SUFFIX = "}";
    private static final char PATH_SEPARATOR = ':';
    private static final char COLLECTION_SEPARATOR = '.';

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    /**
     * Attempts to parse a string value as a config reference.
     * <p>
     * Only parses if the entire value is exactly a reference token (no interpolation).
     *
     * @param value the string value to parse
     * @return the parsed reference, or empty if not a valid reference
     */
    public @NotNull Optional<ConfigReference> tryParse(@NotNull String value) {
        if (value == null) {
            return Optional.empty();
        }

        String trimmed = value.trim();

        if (!trimmed.startsWith(PREFIX) || !trimmed.endsWith(SUFFIX)) {
            return Optional.empty();
        }

        String content = trimmed.substring(PREFIX.length(), trimmed.length() - SUFFIX.length());

        if (content.isEmpty()) {
            return Optional.empty();
        }

        if (StringUtils.containsWhitespace(content)) {
            return Optional.empty();
        }

        // check for nested braces (not supported)
        if (content.contains("${") || content.contains("}")) {
            return Optional.empty();
        }

        return parseContent(trimmed, content);
    }

    /**
     * Checks if a string value looks like a reference token.
     * <p>
     * This is a quick check that doesn't validate the full syntax.
     *
     * @param value the value to check
     * @return true if it looks like a reference
     */
    public boolean looksLikeReference(@NotNull String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith(PREFIX) && trimmed.endsWith(SUFFIX);
    }

    /**
     * Validates that a name is valid for use in references.
     * <p>
     * Valid names contain only letters, digits, underscores, and hyphens.
     * Names cannot contain '.' or ':' as these are delimiter characters.
     *
     * @param name the name to validate
     * @return true if valid
     */
    public boolean isValidName(@NotNull String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return NAME_PATTERN.matcher(name).matches();
    }

    private @NotNull Optional<ConfigReference> parseContent(@NotNull String rawToken, @NotNull String content) {
        int pathIndex = content.indexOf(PATH_SEPARATOR);

        String namePart;
        String pathPart;

        if (pathIndex >= 0) {
            namePart = content.substring(0, pathIndex);
            pathPart = content.substring(pathIndex + 1);

            if (pathPart.isEmpty()) {
                return Optional.empty();
            }

            if (pathPart.startsWith(".") || pathPart.endsWith(".")) {
                return Optional.empty();
            }

            if (pathPart.contains("..")) {
                return Optional.empty();
            }
        } else {
            namePart = content;
            pathPart = null;
        }

        if (namePart.isEmpty()) {
            return Optional.empty();
        }

        int dotIndex = namePart.indexOf(COLLECTION_SEPARATOR);

        if (dotIndex >= 0) {
            // collection item reference: collectionName.itemId
            String collectionName = namePart.substring(0, dotIndex);
            String itemId = namePart.substring(dotIndex + 1);

            if (!isValidName(collectionName) || !isValidName(itemId)) {
                return Optional.empty();
            }

            return Optional.of(ConfigReference.collectionItem(rawToken, collectionName, itemId, pathPart));
        } else {
            if (!isValidName(namePart)) {
                return Optional.empty();
            }

            return Optional.of(ConfigReference.singleConfig(rawToken, namePart, pathPart));
        }
    }
}
