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
package tech.guilhermekaua.spigotboot.config.binding;

import org.jetbrains.annotations.NotNull;

/**
 * Strategy for converting between Java field names and config keys.
 * <p>
 * Different strategies support different naming conventions commonly used in
 * YAML configuration files.
 */
public enum NamingStrategy {

    /**
     * No conversion: fieldName → fieldName
     */
    IDENTITY {
        @Override
        public @NotNull String toConfig(@NotNull String javaName) {
            return javaName;
        }

        @Override
        public @NotNull String toJava(@NotNull String configName) {
            return configName;
        }
    },

    /**
     * Snake case: fieldName → field_name (DEFAULT)
     */
    SNAKE_CASE {
        @Override
        public @NotNull String toConfig(@NotNull String javaName) {
            if (javaName == null || javaName.isEmpty()) {
                return javaName == null ? "" : javaName;
            }
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < javaName.length(); i++) {
                char c = javaName.charAt(i);
                if (Character.isUpperCase(c) && i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            }
            return result.toString();
        }

        @Override
        public @NotNull String toJava(@NotNull String configName) {
            if (configName == null || configName.isEmpty()) {
                return configName == null ? "" : configName;
            }
            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = false;
            for (int i = 0; i < configName.length(); i++) {
                char c = configName.charAt(i);
                if (c == '_') {
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        }
    },

    /**
     * Kebab case: fieldName → field-name
     */
    KEBAB_CASE {
        @Override
        public @NotNull String toConfig(@NotNull String javaName) {
            return SNAKE_CASE.toConfig(javaName).replace('_', '-');
        }

        @Override
        public @NotNull String toJava(@NotNull String configName) {
            return SNAKE_CASE.toJava(configName.replace('-', '_'));
        }
    },

    /**
     * Lower camel: FieldName → fieldName
     */
    LOWER_CAMEL {
        @Override
        public @NotNull String toConfig(@NotNull String javaName) {
            if (javaName == null || javaName.isEmpty()) {
                return javaName == null ? "" : javaName;
            }
            return Character.toLowerCase(javaName.charAt(0)) + javaName.substring(1);
        }

        @Override
        public @NotNull String toJava(@NotNull String configName) {
            return configName == null ? "" : configName;
        }
    };

    /**
     * Converts a Java field name to a config key.
     *
     * @param javaName the Java field name
     * @return the config key
     */
    public abstract @NotNull String toConfig(@NotNull String javaName);

    /**
     * Converts a config key to a Java field name.
     *
     * @param configName the config key
     * @return the Java field name
     */
    public abstract @NotNull String toJava(@NotNull String configName);
}
