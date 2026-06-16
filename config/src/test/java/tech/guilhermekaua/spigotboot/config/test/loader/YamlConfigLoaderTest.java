package tech.guilhermekaua.spigotboot.config.test.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.loader.YamlConfigLoader;
import tech.guilhermekaua.spigotboot.config.node.YamlConfigNode;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class YamlConfigLoaderTest {

    @TempDir
    Path tempDir;

    private YamlConfigLoader loader;

    @BeforeEach
    void setUp() {
        loader = new YamlConfigLoader();
    }

    @Test
    void save_scalarWithColon_roundTripsCorrectly() throws Exception {
        String value = "host: localhost";
        ConfigSource source = ConfigSource.file(tempDir.resolve("colon.yml"));

        loader.save(new ScalarConfigNode(value), source);
        ConfigNode loaded = loader.load(source);

        assertEquals(value, loaded.raw());
    }

    @Test
    void save_scalarWithHash_roundTripsCorrectly() throws Exception {
        String value = "# this is not a comment";
        ConfigSource source = ConfigSource.file(tempDir.resolve("hash.yml"));

        loader.save(new ScalarConfigNode(value), source);
        ConfigNode loaded = loader.load(source);

        assertEquals(value, loaded.raw());
    }

    @Test
    void save_scalarBooleanLikeString_roundTripsCorrectly() throws Exception {
        String value = "yes";
        ConfigSource source = ConfigSource.file(tempDir.resolve("bool.yml"));

        loader.save(new ScalarConfigNode(value), source);
        ConfigNode loaded = loader.load(source);

        assertEquals(value, String.valueOf(loaded.raw()));
    }

    @Test
    void save_scalarWithMidStringHash_roundTripsCorrectly() throws Exception {
        String value = "foo #bar";
        ConfigSource source = ConfigSource.file(tempDir.resolve("midhash.yml"));

        loader.save(new ScalarConfigNode(value), source);
        ConfigNode loaded = loader.load(source);

        assertEquals(value, loaded.raw());
    }

    @Test
    void save_plainScalar_roundTripsCorrectly() throws Exception {
        String value = "hello world";
        ConfigSource source = ConfigSource.file(tempDir.resolve("plain.yml"));

        loader.save(new ScalarConfigNode(value), source);
        ConfigNode loaded = loader.load(source);

        assertEquals(value, loaded.raw());
    }

    @ParameterizedTest(name = "map value round-trip keeps scalar string: {0}")
    @MethodSource("yamlSpecialScalarCases")
    void save_yamlConfigNodeMapSpecialScalar_roundTripsAsString(String fileName, String value) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", value);

        ConfigSource source = ConfigSource.file(tempDir.resolve(fileName + "-map.yml"));

        loader.save(new YamlConfigNode(data), source);
        ConfigNode loaded = loader.load(source);

        Object loadedValue = loaded.node("value").raw();
        assertEquals(value, loadedValue);
        assertInstanceOf(String.class, loadedValue, "expected loaded map scalar to remain a string");
    }

    @ParameterizedTest(name = "list item round-trip keeps scalar string: {0}")
    @MethodSource("yamlSpecialScalarCases")
    void save_yamlConfigNodeListSpecialScalar_roundTripsAsString(String fileName, String value) throws Exception {
        List<Object> items = new ArrayList<>();
        items.add(value);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);

        ConfigSource source = ConfigSource.file(tempDir.resolve(fileName + "-list.yml"));

        loader.save(new YamlConfigNode(data), source);
        ConfigNode loaded = loader.load(source);

        Object loadedValue = loaded.node("items").node(0).raw();
        assertEquals(value, loadedValue);
        assertInstanceOf(String.class, loadedValue, "expected loaded list item to remain a string");
    }

    private static Stream<Arguments> yamlSpecialScalarCases() {
        return Stream.of(
                Arguments.of("contains-inline-comment", "foo #bar"),
                Arguments.of("ends-with-colon", "foo:"),
                Arguments.of("ends-with-colon-space", "foo:\u0020"),
                Arguments.of("leading-space", "\u0020\u0020leading"),
                Arguments.of("trailing-space", "trailing\u0020\u0020"),
                Arguments.of("flow-empty-list", "[]"),
                Arguments.of("flow-empty-map", "{}"),
                Arguments.of("comma-only", ",")
        );
    }

    private static class ScalarConfigNode implements ConfigNode {
        private final Object value;

        ScalarConfigNode(@Nullable Object value) {
            this.value = value;
        }

        @Override
        public @Nullable Object raw() {
            return value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @Nullable T get(@NotNull Class<T> type) {
            return type.isInstance(value) ? (T) value : null;
        }

        @Override
        public <T> @NotNull T get(@NotNull Class<T> type, @NotNull T defaultValue) {
            T result = get(type);
            return result != null ? result : defaultValue;
        }

        @Override
        public @NotNull ConfigNode node(@NotNull Object... path) {
            return this;
        }

        @Override
        public @NotNull ConfigNode node(@NotNull PropertyPath path) {
            return this;
        }

        @Override
        public boolean hasChild(@NotNull Object... path) {
            return false;
        }

        @Override
        public @NotNull Map<String, ? extends ConfigNode> childrenMap() {
            return Collections.emptyMap();
        }

        @Override
        public @NotNull List<? extends ConfigNode> childrenList() {
            return Collections.emptyList();
        }

        @Override
        public boolean isMap() {
            return false;
        }

        @Override
        public boolean isList() {
            return false;
        }

        @Override
        public boolean isScalar() {
            return value != null;
        }

        @Override
        public boolean isNull() {
            return value == null;
        }

        @Override
        public boolean isVirtual() {
            return false;
        }

        @Override
        public @NotNull PropertyPath path() {
            return PropertyPath.root();
        }
    }
}
