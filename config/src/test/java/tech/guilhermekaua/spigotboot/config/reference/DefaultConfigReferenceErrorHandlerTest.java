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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.exception.ConfigException;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigCircularReferenceContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigReferenceNotFoundContext;
import tech.guilhermekaua.spigotboot.config.reference.context.ConfigTypeMismatchContext;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;

import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultConfigReferenceErrorHandler")
class DefaultConfigReferenceErrorHandlerTest {

    private DefaultConfigReferenceErrorHandler handler;
    private TestLogHandler logHandler;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("test.config.reference");
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);

        logHandler = new TestLogHandler();
        // Clear any existing handlers
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
        }
        logger.addHandler(logHandler);

        handler = new DefaultConfigReferenceErrorHandler(logger);
    }

    @Nested
    @DisplayName("onReferenceNotFound")
    class OnReferenceNotFound {

        @Test
        @DisplayName("returns null for missing reference")
        void returnsNullForMissingReference() {
            ConfigReferenceNotFoundContext context = new ConfigReferenceNotFoundContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${items:custom_item}",
                    Collections.emptySet()
            );

            Object result = handler.onReferenceNotFound(context);

            assertNull(result);
        }

        @Test
        @DisplayName("logs warning with reference details")
        void logsWarningWithReferenceDetails() {
            ConfigReferenceNotFoundContext context = new ConfigReferenceNotFoundContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${items:custom_item}",
                    Collections.emptySet()
            );

            handler.onReferenceNotFound(context);

            assertTrue(logHandler.hasWarning());
            String message = logHandler.getLastWarning();
            assertTrue(message.contains("${items:custom_item}"), "Should contain reference");
            assertTrue(message.contains("myconfig"), "Should contain source config");
        }

        @Test
        @DisplayName("logs warning with field name if present")
        void logsWarningWithFieldNameIfPresent() throws NoSuchFieldException {
            Field field = SampleConfig.class.getDeclaredField("itemRef");

            ConfigReferenceNotFoundContext context = new ConfigReferenceNotFoundContext(
                    ReferenceKey.singleConfig("myconfig"),
                    field,
                    "${items:custom_item}",
                    Collections.emptySet()
            );

            handler.onReferenceNotFound(context);

            String message = logHandler.getLastWarning();
            assertTrue(message.contains("itemRef"), "Should contain field name");
        }

        @Test
        @DisplayName("logs warning with alternatives if available")
        void logsWarningWithAlternativesIfAvailable() {
            Set<String> alternatives = new LinkedHashSet<>(Arrays.asList("items", "boosters", "rewards"));

            ConfigReferenceNotFoundContext context = new ConfigReferenceNotFoundContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${item:custom_item}",
                    alternatives
            );

            handler.onReferenceNotFound(context);

            String message = logHandler.getLastWarning();
            assertTrue(message.contains("items"), "Should contain alternative");
            assertTrue(message.contains("boosters"), "Should contain alternative");
        }

        @Test
        @DisplayName("handles collection item source key")
        void handlesCollectionItemSourceKey() {
            ConfigReferenceNotFoundContext context = new ConfigReferenceNotFoundContext(
                    ReferenceKey.collectionItem("items", "custom_item"),
                    null,
                    "${boosters.2x:multiplier}",
                    Collections.emptySet()
            );

            Object result = handler.onReferenceNotFound(context);

            assertNull(result);
            assertTrue(logHandler.hasWarning());
            String message = logHandler.getLastWarning();
            assertTrue(message.contains("items") && message.contains("custom_item"),
                    "Should contain collection item info");
        }
    }

    @Nested
    @DisplayName("onCircularReference")
    class OnCircularReference {

        @Test
        @DisplayName("throws ConfigException for circular reference")
        void throwsConfigExceptionForCircularReference() {
            List<ReferenceKey> chain = Arrays.asList(
                    ReferenceKey.singleConfig("configA"),
                    ReferenceKey.singleConfig("configB"),
                    ReferenceKey.singleConfig("configC"),
                    ReferenceKey.singleConfig("configA")
            );

            ConfigCircularReferenceContext context = new ConfigCircularReferenceContext(
                    ReferenceKey.singleConfig("configC"),
                    null,
                    "${configA}",
                    chain
            );

            ConfigException exception = assertThrows(ConfigException.class, () -> {
                handler.onCircularReference(context);
            });

            assertNotNull(exception.getMessage());
        }

        @Test
        @DisplayName("exception message contains reference")
        void exceptionMessageContainsReference() {
            List<ReferenceKey> chain = Arrays.asList(
                    ReferenceKey.singleConfig("configA"),
                    ReferenceKey.singleConfig("configA")
            );

            ConfigCircularReferenceContext context = new ConfigCircularReferenceContext(
                    ReferenceKey.singleConfig("configA"),
                    null,
                    "${configA}",
                    chain
            );

            ConfigException exception = assertThrows(ConfigException.class, () -> {
                handler.onCircularReference(context);
            });

            assertTrue(exception.getMessage().contains("${configA}"),
                    "Should contain reference");
        }

        @Test
        @DisplayName("exception message contains chain")
        void exceptionMessageContainsChain() {
            List<ReferenceKey> chain = Arrays.asList(
                    ReferenceKey.singleConfig("configA"),
                    ReferenceKey.singleConfig("configB"),
                    ReferenceKey.singleConfig("configA")
            );

            ConfigCircularReferenceContext context = new ConfigCircularReferenceContext(
                    ReferenceKey.singleConfig("configB"),
                    null,
                    "${configA}",
                    chain
            );

            ConfigException exception = assertThrows(ConfigException.class, () -> {
                handler.onCircularReference(context);
            });

            String message = exception.getMessage();
            assertTrue(message.contains("configA") && message.contains("configB"),
                    "Should contain chain elements");
        }

        @Test
        @DisplayName("handles collection item in chain")
        void handlesCollectionItemInChain() {
            List<ReferenceKey> chain = Arrays.asList(
                    ReferenceKey.singleConfig("config"),
                    ReferenceKey.collectionItem("items", "sword"),
                    ReferenceKey.singleConfig("config")
            );

            ConfigCircularReferenceContext context = new ConfigCircularReferenceContext(
                    ReferenceKey.collectionItem("items", "sword"),
                    null,
                    "${config}",
                    chain
            );

            ConfigException exception = assertThrows(ConfigException.class, () -> {
                handler.onCircularReference(context);
            });

            String message = exception.getMessage();
            assertTrue(message.contains("items") && message.contains("sword"),
                    "Should contain collection item info");
        }
    }

    @Nested
    @DisplayName("onTypeMismatch")
    class OnTypeMismatch {

        @Test
        @DisplayName("returns null for type mismatch")
        void returnsNullForTypeMismatch() {
            ConfigTypeMismatchContext context = new ConfigTypeMismatchContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${items:count}",
                    Integer.class,
                    String.class,
                    "not a number"
            );

            Object result = handler.onTypeMismatch(context);

            assertNull(result);
        }

        @Test
        @DisplayName("logs warning with type details")
        void logsWarningWithTypeDetails() {
            ConfigTypeMismatchContext context = new ConfigTypeMismatchContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${items:count}",
                    Integer.class,
                    String.class,
                    "not a number"
            );

            handler.onTypeMismatch(context);

            assertTrue(logHandler.hasWarning());
            String message = logHandler.getLastWarning();
            assertTrue(message.contains("Integer") || message.contains("int"),
                    "Should contain expected type");
            assertTrue(message.contains("String"),
                    "Should contain actual type");
        }

        @Test
        @DisplayName("logs warning with field name if present")
        void logsWarningWithFieldNameIfPresent() throws NoSuchFieldException {
            Field field = SampleConfig.class.getDeclaredField("count");

            ConfigTypeMismatchContext context = new ConfigTypeMismatchContext(
                    ReferenceKey.singleConfig("myconfig"),
                    field,
                    "${items:count}",
                    int.class,
                    String.class,
                    "not a number"
            );

            handler.onTypeMismatch(context);

            String message = logHandler.getLastWarning();
            assertTrue(message.contains("count"), "Should contain field name");
        }

        @Test
        @DisplayName("handles null actual value")
        void handlesNullActualValue() {
            ConfigTypeMismatchContext context = new ConfigTypeMismatchContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${items:data}",
                    Map.class,
                    Object.class,
                    null
            );

            Object result = handler.onTypeMismatch(context);

            assertNull(result);
            assertTrue(logHandler.hasWarning());
        }
    }

    @Nested
    @DisplayName("Context Classes")
    class ContextClasses {

        @Test
        @DisplayName("ConfigReferenceNotFoundContext toString includes all fields")
        void notFoundContextToString() {
            ConfigReferenceNotFoundContext context = new ConfigReferenceNotFoundContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${ref}",
                    new HashSet<>(Arrays.asList("alt1", "alt2"))
            );

            String str = context.toString();
            assertTrue(str.contains("myconfig"));
            assertTrue(str.contains("${ref}"));
            assertTrue(str.contains("alt1"));
        }

        @Test
        @DisplayName("ConfigCircularReferenceContext formatChain")
        void circularContextFormatChain() {
            List<ReferenceKey> chain = Arrays.asList(
                    ReferenceKey.singleConfig("A"),
                    ReferenceKey.singleConfig("B"),
                    ReferenceKey.singleConfig("A")
            );

            ConfigCircularReferenceContext context = new ConfigCircularReferenceContext(
                    ReferenceKey.singleConfig("B"),
                    null,
                    "${A}",
                    chain
            );

            String formatted = context.formatChain();
            assertTrue(formatted.contains("->"), "Should contain arrow separator");
            assertTrue(formatted.contains("config:A"));
            assertTrue(formatted.contains("config:B"));
        }

        @Test
        @DisplayName("ConfigTypeMismatchContext toString includes all fields")
        void typeMismatchContextToString() {
            ConfigTypeMismatchContext context = new ConfigTypeMismatchContext(
                    ReferenceKey.singleConfig("myconfig"),
                    null,
                    "${ref}",
                    String.class,
                    Integer.class,
                    42
            );

            String str = context.toString();
            assertTrue(str.contains("myconfig"));
            assertTrue(str.contains("${ref}"));
            assertTrue(str.contains("String"));
            assertTrue(str.contains("Integer"));
        }
    }

    // Helper class for capturing log records
    private static class TestLogHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() throws SecurityException {
        }

        boolean hasWarning() {
            return records.stream()
                    .anyMatch(r -> r.getLevel() == Level.WARNING);
        }

        String getLastWarning() {
            for (int i = records.size() - 1; i >= 0; i--) {
                if (records.get(i).getLevel() == Level.WARNING) {
                    return records.get(i).getMessage();
                }
            }
            return null;
        }
    }

    // Sample config class for field tests
    @SuppressWarnings("unused")
    private static class SampleConfig {
        private String itemRef;
        private int count;
    }
}
