package tech.guilhermekaua.spigotboot.config.spigot.test.loader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.guilhermekaua.spigotboot.config.loader.ConfigSource;
import tech.guilhermekaua.spigotboot.config.node.ConfigNode;
import tech.guilhermekaua.spigotboot.config.spigot.loader.YamlConfigLoader;
import tech.guilhermekaua.spigotboot.core.validation.PropertyPath;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
