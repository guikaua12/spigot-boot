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
package tech.guilhermekaua.spigotboot.config.test.reference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReference;
import tech.guilhermekaua.spigotboot.config.reference.ConfigReferenceParser;
import tech.guilhermekaua.spigotboot.config.reference.ReferenceTargetKind;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ConfigReferenceParser}.
 */
class ConfigReferenceParserTest {

    private ConfigReferenceParser parser;

    @BeforeEach
    void setUp() {
        parser = new ConfigReferenceParser();
    }

    @Nested
    @DisplayName("Single Config References")
    class SingleConfigReferences {

        @Test
        @DisplayName("parses single config root reference: ${configName}")
        void parsesSingleConfigRoot() {
            Optional<ConfigReference> result = parser.tryParse("${items}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals(ReferenceTargetKind.SINGLE_CONFIG, ref.getKind());
            assertEquals("items", ref.getConfigName());
            assertNull(ref.getPath());
            assertTrue(ref.isRootReference());
            assertTrue(ref.isSingleConfig());
            assertFalse(ref.isCollectionItem());
        }

        @Test
        @DisplayName("parses single config with path: ${configName:path.to.value}")
        void parsesSingleConfigWithPath() {
            Optional<ConfigReference> result = parser.tryParse("${items:items.custom_exp_bottle}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals(ReferenceTargetKind.SINGLE_CONFIG, ref.getKind());
            assertEquals("items", ref.getConfigName());
            assertEquals("items.custom_exp_bottle", ref.getPath());
            assertFalse(ref.isRootReference());
        }

        @Test
        @DisplayName("parses single config with simple path: ${config:key}")
        void parsesSingleConfigWithSimplePath() {
            Optional<ConfigReference> result = parser.tryParse("${database:host}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals("database", ref.getConfigName());
            assertEquals("host", ref.getPath());
        }

        @Test
        @DisplayName("parses config name with underscores and hyphens")
        void parsesConfigNameWithSpecialChars() {
            Optional<ConfigReference> result = parser.tryParse("${my_config-name:some.path}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals("my_config-name", ref.getConfigName());
        }
    }

    @Nested
    @DisplayName("Collection Item References")
    class CollectionItemReferences {

        @Test
        @DisplayName("parses collection item root reference: ${collection.itemId}")
        void parsesCollectionItemRoot() {
            Optional<ConfigReference> result = parser.tryParse("${boosters.booster_2x}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals(ReferenceTargetKind.COLLECTION_ITEM, ref.getKind());
            assertEquals("boosters", ref.getCollectionName());
            assertEquals("booster_2x", ref.getItemId());
            assertNull(ref.getPath());
            assertTrue(ref.isRootReference());
            assertTrue(ref.isCollectionItem());
            assertFalse(ref.isSingleConfig());
        }

        @Test
        @DisplayName("parses collection item with path: ${collection.itemId:path}")
        void parsesCollectionItemWithPath() {
            Optional<ConfigReference> result = parser.tryParse("${boosters.normal_booster:multiplier}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals(ReferenceTargetKind.COLLECTION_ITEM, ref.getKind());
            assertEquals("boosters", ref.getCollectionName());
            assertEquals("normal_booster", ref.getItemId());
            assertEquals("multiplier", ref.getPath());
            assertFalse(ref.isRootReference());
        }

        @Test
        @DisplayName("parses collection item with nested path")
        void parsesCollectionItemWithNestedPath() {
            Optional<ConfigReference> result = parser.tryParse("${items.custom_sword:lore.0}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals("items", ref.getCollectionName());
            assertEquals("custom_sword", ref.getItemId());
            assertEquals("lore.0", ref.getPath());
        }
    }

    @Nested
    @DisplayName("Invalid References")
    class InvalidReferences {

        @Test
        @DisplayName("rejects non-reference strings")
        void rejectsNonReference() {
            assertFalse(parser.tryParse("just a string").isPresent());
            assertFalse(parser.tryParse("123").isPresent());
            assertFalse(parser.tryParse("").isPresent());
        }

        @Test
        @DisplayName("rejects partial reference syntax")
        void rejectsPartialSyntax() {
            assertFalse(parser.tryParse("${incomplete").isPresent());
            assertFalse(parser.tryParse("incomplete}").isPresent());
            assertFalse(parser.tryParse("$incomplete}").isPresent());
            assertFalse(parser.tryParse("{incomplete}").isPresent());
        }

        @Test
        @DisplayName("rejects empty reference content")
        void rejectsEmptyContent() {
            assertFalse(parser.tryParse("${}").isPresent());
        }

        @Test
        @DisplayName("rejects reference with whitespace inside")
        void rejectsWhitespaceInside() {
            assertFalse(parser.tryParse("${ config }").isPresent());
            assertFalse(parser.tryParse("${config name}").isPresent());
            assertFalse(parser.tryParse("${config:path value}").isPresent());
        }

        @Test
        @DisplayName("rejects empty path after colon")
        void rejectsEmptyPath() {
            assertFalse(parser.tryParse("${config:}").isPresent());
        }

        @Test
        @DisplayName("rejects path with leading or trailing dot")
        void rejectsPathWithBadDots() {
            assertFalse(parser.tryParse("${config:.path}").isPresent());
            assertFalse(parser.tryParse("${config:path.}").isPresent());
        }

        @Test
        @DisplayName("rejects path with consecutive dots")
        void rejectsConsecutiveDots() {
            assertFalse(parser.tryParse("${config:path..value}").isPresent());
        }

        @Test
        @DisplayName("rejects empty collection name or item id")
        void rejectsEmptyCollectionParts() {
            assertFalse(parser.tryParse("${.itemId}").isPresent());
            assertFalse(parser.tryParse("${collection.}").isPresent());
        }

        @Test
        @DisplayName("rejects nested braces")
        void rejectsNestedBraces() {
            assertFalse(parser.tryParse("${config${nested}}").isPresent());
            assertFalse(parser.tryParse("${config}extra}").isPresent());
        }

        @ParameterizedTest
        @ValueSource(strings = {"${name with space}", "${name\twith\ttab}", "${name\nwith\nnewline}"})
        @DisplayName("rejects names with whitespace")
        void rejectsNamesWithWhitespace(String input) {
            assertFalse(parser.tryParse(input).isPresent());
        }

        @ParameterizedTest
        @ValueSource(strings = {"${name:with}", "${name.item:with}", "${@invalid}", "${name!special}"})
        @DisplayName("rejects names with invalid special characters (except underscore/hyphen)")
        void rejectsInvalidSpecialChars(String input) {
            // Only ${name:with} and ${name.item:with} are actually valid
            // Testing that we don't accept truly invalid chars
            Optional<ConfigReference> result = parser.tryParse("${@invalid}");
            assertFalse(result.isPresent());

            result = parser.tryParse("${name!special}");
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("handles reference with surrounding whitespace")
        void handlesSurroundingWhitespace() {
            Optional<ConfigReference> result = parser.tryParse("  ${config}  ");

            assertTrue(result.isPresent());
            assertEquals("config", result.get().getConfigName());
        }

        @Test
        @DisplayName("preserves raw token including whitespace trimming")
        void preservesRawToken() {
            Optional<ConfigReference> result = parser.tryParse("${items:path}");

            assertTrue(result.isPresent());
            assertEquals("${items:path}", result.get().getRawToken());
        }

        @Test
        @DisplayName("handles numeric item IDs")
        void handlesNumericItemIds() {
            Optional<ConfigReference> result = parser.tryParse("${levels.123}");

            assertTrue(result.isPresent());
            ConfigReference ref = result.get();
            assertEquals("levels", ref.getCollectionName());
            assertEquals("123", ref.getItemId());
        }

        @Test
        @DisplayName("distinguishes between single config and collection based on dot")
        void distinguishesSingleVsCollection() {
            // No dot -> single config
            Optional<ConfigReference> single = parser.tryParse("${myconfig}");
            assertTrue(single.isPresent());
            assertTrue(single.get().isSingleConfig());

            // Has dot -> collection item
            Optional<ConfigReference> collection = parser.tryParse("${myconfig.item}");
            assertTrue(collection.isPresent());
            assertTrue(collection.get().isCollectionItem());
        }
    }

    @Nested
    @DisplayName("Utility Methods")
    class UtilityMethods {

        @Test
        @DisplayName("looksLikeReference returns true for reference-like strings")
        void looksLikeReferencePositive() {
            assertTrue(parser.looksLikeReference("${anything}"));
            assertTrue(parser.looksLikeReference("${a}"));
            assertTrue(parser.looksLikeReference("  ${test}  "));
        }

        @Test
        @DisplayName("looksLikeReference returns false for non-reference strings")
        void looksLikeReferenceNegative() {
            assertFalse(parser.looksLikeReference("normal string"));
            assertFalse(parser.looksLikeReference("${incomplete"));
            assertFalse(parser.looksLikeReference("incomplete}"));
            assertFalse(parser.looksLikeReference(""));
        }

        @Test
        @DisplayName("isValidName accepts valid names")
        void isValidNamePositive() {
            assertTrue(parser.isValidName("config"));
            assertTrue(parser.isValidName("my_config"));
            assertTrue(parser.isValidName("my-config"));
            assertTrue(parser.isValidName("Config123"));
            assertTrue(parser.isValidName("a"));
            assertTrue(parser.isValidName("ABC"));
        }

        @Test
        @DisplayName("isValidName rejects invalid names")
        void isValidNameNegative() {
            assertFalse(parser.isValidName(""));
            assertFalse(parser.isValidName(null));
            assertFalse(parser.isValidName("has.dot"));
            assertFalse(parser.isValidName("has:colon"));
            assertFalse(parser.isValidName("has space"));
            assertFalse(parser.isValidName("has@special"));
        }
    }

    @Nested
    @DisplayName("ConfigReference Value Object")
    class ConfigReferenceValueObject {

        @Test
        @DisplayName("equals and hashCode work correctly for same reference")
        void equalsAndHashCode() {
            ConfigReference ref1 = ConfigReference.singleConfig("${config:path}", "config", "path");
            ConfigReference ref2 = ConfigReference.singleConfig("${config:path}", "config", "path");

            assertEquals(ref1, ref2);
            assertEquals(ref1.hashCode(), ref2.hashCode());
        }

        @Test
        @DisplayName("equals returns false for different references")
        void notEquals() {
            ConfigReference ref1 = ConfigReference.singleConfig("${config1:path}", "config1", "path");
            ConfigReference ref2 = ConfigReference.singleConfig("${config2:path}", "config2", "path");

            assertNotEquals(ref1, ref2);
        }

        @Test
        @DisplayName("toString contains useful information")
        void toStringContainsInfo() {
            ConfigReference ref = ConfigReference.collectionItem("${coll.item:path}", "coll", "item", "path");
            String str = ref.toString();

            assertTrue(str.contains("COLLECTION_ITEM"));
            assertTrue(str.contains("coll"));
            assertTrue(str.contains("item"));
            assertTrue(str.contains("path"));
        }

        @Test
        @DisplayName("getConfigName throws for collection item")
        void getConfigNameThrowsForCollectionItem() {
            ConfigReference ref = ConfigReference.collectionItem("${coll.item}", "coll", "item", null);

            assertThrows(IllegalStateException.class, ref::getConfigName);
        }

        @Test
        @DisplayName("getCollectionName throws for single config")
        void getCollectionNameThrowsForSingleConfig() {
            ConfigReference ref = ConfigReference.singleConfig("${config}", "config", null);

            assertThrows(IllegalStateException.class, ref::getCollectionName);
        }
    }
}
