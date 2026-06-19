/*
 * The MIT License
 * Copyright © 2026 Guilherme Kauã da Silva
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.loader.YamlConfigLoader;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.node.YamlConfigNode;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializer;
import tech.guilhermekaua.spigotboot.config.serialization.TypeSerializerRegistry;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * regression coverage for issue #90: {@code Binder.unbind} dropping values produced by a
 * {@link TypeSerializer} (or a plain nested pojo) that populates a node through sub-keys.
 */
@DisplayName("Binder.unbind nested values (#90)")
class DefaultBinderUnbindNestedTest {

    @TempDir
    Path tempDir;

    private Binder newBinder() {
        TypeSerializerRegistry serializers = TypeSerializerRegistry.defaults();
        serializers.register(Decoration.class, new DecorationSerializer());
        return Binder.builder()
                .serializers(serializers)
                .implicitDefaults(true)
                .useConstructorBinding(true)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unbindToMap(Object config) {
        MutableConfigNode node = new YamlConfigNode();
        newBinder().unbind(config, node, NamingStrategy.SNAKE_CASE);
        Object raw = node.raw();
        assertInstanceOf(Map.class, raw, "root node should serialize to a map");
        return (Map<String, Object>) raw;
    }

    @Test
    @DisplayName("a single nested field written via sub-keys is present, not omitted")
    void singleNestedFieldViaSerializerIsPresent() {
        Map<String, Object> map = unbindToMap(new DemoConfig());

        Object single = map.get("single");
        assertInstanceOf(Map.class, single, "single nested field must be present as a map");
        Map<?, ?> singleMap = (Map<?, ?>) single;
        assertEquals("4,13", singleMap.get("slots"));
        assertEquals("GRAY_STAINED_GLASS_PANE", singleMap.get("material"));
    }

    @Test
    @DisplayName("List<T> elements written via sub-keys are populated maps, not null")
    void listElementsViaSerializerAreNotNull() {
        Map<String, Object> map = unbindToMap(new DemoConfig());

        Object many = map.get("many");
        assertInstanceOf(List.class, many, "list field must be present as a list");
        List<?> manyList = (List<?>) many;
        assertEquals(2, manyList.size());

        assertNotNull(manyList.get(0), "first list element must not be null");
        assertNotNull(manyList.get(1), "second list element must not be null");

        assertEquals("4,13", ((Map<?, ?>) manyList.get(0)).get("slots"));
        assertEquals("GRAY", ((Map<?, ?>) manyList.get(0)).get("material"));
        assertEquals("36,38", ((Map<?, ?>) manyList.get(1)).get("slots"));
        assertEquals("BLACK", ((Map<?, ?>) manyList.get(1)).get("material"));
    }

    @Test
    @DisplayName("a plain nested pojo (no serializer) is present, not omitted")
    void plainNestedPojoIsPresent() {
        Map<String, Object> map = unbindToMap(new DemoConfig());

        Object plain = map.get("plain");
        assertInstanceOf(Map.class, plain, "plain nested pojo must be present as a map");
        Map<?, ?> plainMap = (Map<?, ?>) plain;
        assertEquals("hello", plainMap.get("first"));
        assertEquals("world", plainMap.get("second"));
    }

    @Test
    @DisplayName("unbind -> YAML save -> load -> bind preserves nested values end-to-end")
    void roundTripThroughYamlPreservesNestedValues() throws Exception {
        // drive the full default-generation path: serialize to a node, write real YAML through the
        // bespoke loader emitter (list-of-maps), read it back, and bind it to a fresh instance.
        MutableConfigNode node = new YamlConfigNode();
        newBinder().unbind(new DemoConfig(), node, NamingStrategy.SNAKE_CASE);

        YamlConfigLoader loader = new YamlConfigLoader();
        ConfigSource source = ConfigSource.file(tempDir.resolve("roundtrip.yml"));
        loader.save(node, source);
        ConfigNode loaded = loader.load(source);

        // the generated YAML must round-trip the nested structures, not drop them
        assertEquals("4,13", loaded.node("single").node("slots").get(String.class));
        assertEquals("GRAY_STAINED_GLASS_PANE", loaded.node("single").node("material").get(String.class));
        assertEquals(2, loaded.node("many").childrenList().size());
        assertEquals("4,13", loaded.node("many").node(0).node("slots").get(String.class));
        assertEquals("36,38", loaded.node("many").node(1).node("slots").get(String.class));
        assertEquals("hello", loaded.node("plain").node("first").get(String.class));

        // and binding the reloaded config back must NOT yield an empty list (the user-facing #90 symptom)
        BindingResult<DemoConfig> result = newBinder().bind(loaded, DemoConfig.class, NamingStrategy.SNAKE_CASE);
        assertTrue(result.isSuccess(), "reloaded config should bind without errors");

        DemoConfig bound = result.get();
        assertNotNull(bound.single);
        assertEquals("4,13", bound.single.slots);
        assertEquals(2, bound.many.size(), "List<T> must come back with both elements, not empty");
        assertEquals("4,13", bound.many.get(0).slots);
        assertEquals("36,38", bound.many.get(1).slots);
        assertEquals("hello", bound.plain.first);
        assertEquals("world", bound.plain.second);
    }

    // a value type whose serializer populates the node via sub-keys (produces a nested map).
    static final class Decoration {
        final String slots;
        final String material;

        Decoration(String slots, String material) {
            this.slots = slots;
            this.material = material;
        }
    }

    static final class DecorationSerializer implements TypeSerializer<Decoration> {
        @Override
        public Decoration deserialize(ConfigNode node, Class<Decoration> type) {
            return new Decoration(node.node("slots").get(String.class), node.node("material").get(String.class));
        }

        @Override
        public void serialize(Decoration value, MutableConfigNode node) {
            node.node("slots").set(value.slots);
            node.node("material").set(value.material);
        }
    }

    // a plain nested pojo with no custom serializer; unbind routes it through unbind(value, childNode).
    static final class PlainNested {
        String first = "hello";
        String second = "world";
    }

    static final class DemoConfig {
        Decoration single = new Decoration("4,13", "GRAY_STAINED_GLASS_PANE");
        List<Decoration> many = Arrays.asList(new Decoration("4,13", "GRAY"), new Decoration("36,38", "BLACK"));
        PlainNested plain = new PlainNested();
    }
}
