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
package tech.guilhermekaua.spigotboot.config.spigot.test.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceErrorHandler;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceParser;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceResolver;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.spigot.node.YamlConfigNode;
import tech.guilhermekaua.spigotboot.config.spigot.reference.ReferenceResolvingPreprocessor;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ReferenceResolvingPreprocessor")
class ReferenceResolvingPreprocessorTest {

    @Test
    @DisplayName("passes the expected type to the resolver so onTypeMismatch fires")
    void firesTypeMismatchWhenResolvedValueIncompatibleWithExpectedType() {
        RecordingErrorHandler errorHandler = new RecordingErrorHandler();
        ConfigReferenceResolver resolver = new ConfigReferenceResolver(
                new SingleValueLookup("label", "not a number"),
                new ConfigReferenceParser(),
                errorHandler);

        ReferenceResolvingPreprocessor preprocessor = new ReferenceResolvingPreprocessor(resolver);
        preprocessor.setCurrentSourceKey(ReferenceKey.singleConfig("source"));

        try {
            ConfigNode input = new YamlConfigNode("${label}");
            preprocessor.preprocess(input, null, Integer.class);
        } finally {
            preprocessor.clearCurrentSourceKey();
        }

        assertTrue(errorHandler.typeMismatchCalled);
        assertEquals("${label}", errorHandler.typeMismatchContext.getFullReference());
        assertEquals(Integer.class, errorHandler.typeMismatchContext.getExpectedType());
    }

    /**
     * minimal lookup that resolves a single root config name to a scalar value.
     */
    private static final class SingleValueLookup implements ConfigReferenceLookup {

        private final String configName;
        private final Object value;

        private SingleValueLookup(String configName, Object value) {
            this.configName = configName;
            this.value = value;
        }

        @Override
        public @Nullable ConfigNode findSingleConfigRoot(@NotNull String configName) {
            return this.configName.equals(configName) ? new YamlConfigNode(value) : null;
        }

        @Override
        public @Nullable ConfigNode findSingleConfigPath(@NotNull String configName, @NotNull String path) {
            return null;
        }

        @Override
        public @Nullable ConfigNode findFolderConfigItemRoot(@NotNull String folderConfigName, @NotNull String itemId) {
            return null;
        }

        @Override
        public @Nullable ConfigNode findFolderConfigItemPath(@NotNull String folderConfigName, @NotNull String itemId, @NotNull String path) {
            return null;
        }

        @Override
        public @NotNull Set<String> getAvailableConfigNames() {
            return Collections.singleton(configName);
        }

        @Override
        public @NotNull Set<String> getAvailableFolderConfigNames() {
            return Collections.emptySet();
        }

        @Override
        public @NotNull Set<String> getAvailableItemIds(@NotNull String folderConfigName) {
            return Collections.emptySet();
        }

        @Override
        public @NotNull Set<String> getAvailableKeysAt(@NotNull String configName, @Nullable String path) {
            return Collections.emptySet();
        }

        @Override
        public boolean hasConfig(@NotNull String configName) {
            return this.configName.equals(configName);
        }

        @Override
        public boolean hasFolderConfig(@NotNull String folderConfigName) {
            return false;
        }

        @Override
        public boolean hasFolderConfigItem(@NotNull String folderConfigName, @NotNull String itemId) {
            return false;
        }
    }

    private static final class RecordingErrorHandler implements ConfigReferenceErrorHandler {

        private boolean typeMismatchCalled;
        private ConfigTypeMismatchContext typeMismatchContext;

        @Override
        public @Nullable Object onReferenceNotFound(@NotNull ConfigReferenceNotFoundContext context) {
            return null;
        }

        @Override
        public void onCircularReference(@NotNull ConfigCircularReferenceContext context) {
        }

        @Override
        public @Nullable Object onTypeMismatch(@NotNull ConfigTypeMismatchContext context) {
            this.typeMismatchCalled = true;
            this.typeMismatchContext = context;
            return null;
        }
    }
}
