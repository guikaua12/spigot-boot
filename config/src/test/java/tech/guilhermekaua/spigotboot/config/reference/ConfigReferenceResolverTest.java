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

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.reference.key.ReferenceKey;
import tech.guilhermekaua.spigotboot.config.test.TestConfigNode;
import tech.guilhermekaua.spigotboot.config.test.TestConfigReferenceLookup;
import tech.guilhermekaua.spigotboot.config.test.TrackingConfigReferenceErrorHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigReferenceResolver")
class ConfigReferenceResolverTest {

    private TestConfigReferenceLookup lookup;
    private ConfigReferenceParser parser;
    private TrackingConfigReferenceErrorHandler errorHandler;
    private ConfigReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        lookup = new TestConfigReferenceLookup();
        parser = new ConfigReferenceParser();
        errorHandler = new TrackingConfigReferenceErrorHandler();
        resolver = new ConfigReferenceResolver(lookup, parser, errorHandler);
    }

    @Nested
    @DisplayName("resolveIfReference")
    class ResolveIfReference {

        @Test
        @DisplayName("returns original node if not a reference")
        void returnsOriginalNodeIfNotReference() {
            ConfigNode node = testNode("hello world");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertEquals("hello world", result.get(String.class));
        }

        @Test
        @DisplayName("returns original node if null value")
        void returnsOriginalNodeIfNullValue() {
            ConfigNode node = testNode(null);
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertNull(result.raw());
        }

        @Test
        @DisplayName("returns original node if map")
        void returnsOriginalNodeIfMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("key", "value");
            ConfigNode node = testNode(map);
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(result.isMap());
        }

        @Test
        @DisplayName("resolves single config root reference")
        void resolvesSingleConfigRootReference() {
            Map<String, Object> itemsData = new HashMap<>();
            itemsData.put("name", "sword");
            itemsData.put("damage", 10);
            lookup.addConfig("items", itemsData);

            ConfigNode node = testNode("${items}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(result.isMap());
            assertEquals("sword", result.node("name").get(String.class));
        }

        @Test
        @DisplayName("resolves single config path reference")
        void resolvesSingleConfigPathReference() {
            Map<String, Object> database = new HashMap<>();
            database.put("host", "localhost");
            database.put("port", 3306);
            Map<String, Object> config = new HashMap<>();
            config.put("database", database);
            lookup.addConfig("settings", config);

            ConfigNode node = testNode("${settings:database.host}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertEquals("localhost", result.get(String.class));
        }

        @Test
        @DisplayName("resolves collection item root reference")
        void resolvesCollectionItemRootReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Diamond Sword");
            itemData.put("damage", 15);
            lookup.addCollectionItem("items", "diamond_sword", itemData);

            ConfigNode node = testNode("${items.diamond_sword}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(result.isMap());
            assertEquals("Diamond Sword", result.node("name").get(String.class));
        }

        @Test
        @DisplayName("resolves collection item path reference")
        void resolvesCollectionItemPathReference() {
            Map<String, Object> itemData = new HashMap<>();
            itemData.put("name", "Diamond Sword");
            itemData.put("damage", 15);
            lookup.addCollectionItem("items", "diamond_sword", itemData);

            ConfigNode node = testNode("${items.diamond_sword:damage}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertEquals(15, result.get(Integer.class));
        }

        @Test
        @DisplayName("returns null and calls handler for missing reference")
        void returnsNullAndCallsHandlerForMissingReference() {
            ConfigNode node = testNode("${nonexistent}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.resolveIfReference(node, sourceKey, null);

            assertNull(result);
            assertTrue(errorHandler.notFoundCalled);
            assertEquals("${nonexistent}", errorHandler.notFoundContext.getFullReference());
        }

        @Test
        @DisplayName("detects self-cycle")
        void detectsSelfCycle() {
            // Config A references itself
            lookup.addConfig("configA", "${configA}");

            ConfigNode node = testNode("${configA}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("configA");

            resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(errorHandler.circularCalled);
        }

        @Test
        @DisplayName("detects multi-node cycle")
        void detectsMultiNodeCycle() {
            // A -> B -> C -> A
            lookup.addConfig("configA", "${configB}");
            lookup.addConfig("configB", "${configC}");
            lookup.addConfig("configC", "${configA}");

            ConfigNode node = testNode("${configA}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            // This should trigger cycle detection when resolving deep references
            resolver.resolveIfReference(node, sourceKey, null);

            assertTrue(errorHandler.circularCalled);
        }
    }

    @Nested
    @DisplayName("deepResolve")
    class DeepResolve {

        @Test
        @DisplayName("returns same node for scalar non-reference")
        void returnsSameNodeForScalarNonReference() {
            ConfigNode node = testNode("plain value");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.deepResolve(node, sourceKey);

            assertEquals("plain value", result.get(String.class));
        }

        @Test
        @DisplayName("resolves scalar reference")
        void resolvesScalarReference() {
            lookup.addConfig("other", "resolved value");

            ConfigNode node = testNode("${other}");
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.deepResolve(node, sourceKey);

            assertEquals("resolved value", result.get(String.class));
        }

        @Test
        @DisplayName("returns null node unchanged")
        void returnsNullNodeUnchanged() {
            ConfigNode node = testNode(null);
            ReferenceKey sourceKey = ReferenceKey.singleConfig("myconfig");

            ConfigNode result = resolver.deepResolve(node, sourceKey);

            assertTrue(result.isNull());
        }
    }

    @Nested
    @DisplayName("isReference")
    class IsReference {

        @Test
        @DisplayName("returns true for valid reference")
        void returnsTrueForValidReference() {
            assertTrue(resolver.isReference("${config}"));
            assertTrue(resolver.isReference("${config:path}"));
            assertTrue(resolver.isReference("${collection.item}"));
            assertTrue(resolver.isReference("${collection.item:path}"));
        }

        @Test
        @DisplayName("returns false for non-reference")
        void returnsFalseForNonReference() {
            assertFalse(resolver.isReference("plain text"));
            assertFalse(resolver.isReference("${incomplete"));
            assertFalse(resolver.isReference("${}"));
            assertFalse(resolver.isReference(null));
        }
    }

    @Nested
    @DisplayName("alternatives suggestions")
    class AlternativesSuggestions {

        @Test
        @DisplayName("suggests available config names for missing single config")
        void suggestsConfigNamesForMissingSingleConfig() {
            lookup.addConfig("items", "data");
            lookup.addConfig("boosters", "data");

            ConfigNode node = testNode("${nonexistent}");
            resolver.resolveIfReference(node, ReferenceKey.singleConfig("myconfig"), null);

            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("items"));
            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("boosters"));
        }

        @Test
        @DisplayName("suggests available item IDs for missing collection item")
        void suggestsItemIdsForMissingCollectionItem() {
            lookup.addCollectionItem("items", "sword", "data");
            lookup.addCollectionItem("items", "axe", "data");

            ConfigNode node = testNode("${items.nonexistent}");
            resolver.resolveIfReference(node, ReferenceKey.singleConfig("myconfig"), null);

            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("sword"));
            assertTrue(errorHandler.notFoundContext.getAvailableAlternatives().contains("axe"));
        }
    }

    private ConfigNode testNode(@Nullable Object value) {
        return new TestConfigNode(value);
    }
}
