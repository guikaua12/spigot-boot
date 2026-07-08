package tech.guilhermekaua.spigotboot.core.spigot.utils;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelDataCompatTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setLegacyData_sets_item_durability() {
        ItemStack item = new ItemStack(Material.STONE);

        assertTrue(ModelDataCompat.setLegacyData(item, 12));

        assertEquals(12, item.getDurability());
    }

    @Test
    void setLegacyData_null_is_noop() {
        ItemStack item = new ItemStack(Material.STONE);
        item.setDurability((short) 5);

        assertFalse(ModelDataCompat.setLegacyData(item, null));

        assertEquals(5, item.getDurability());
    }

    @Test
    void setCustomModelData_uses_integer_api_when_component_api_is_missing() {
        ItemMeta meta = mock(ItemMeta.class);

        assertTrue(ModelDataCompat.setCustomModelData(meta, 7));

        verify(meta).setCustomModelData(7);
    }

    @Test
    void setCustomModelData_prefers_component_float_api_when_available() {
        ComponentMeta meta = mock(ComponentMeta.class);
        TestCustomModelDataComponent component = new TestCustomModelDataComponent();
        when(meta.getCustomModelDataComponent()).thenReturn(component);

        assertTrue(ModelDataCompat.setCustomModelData(meta, 7));

        assertEquals(Collections.singletonList(7.0F), component.floats);
        verify(meta).setCustomModelDataComponent(component);
        verify(meta, never()).setCustomModelData(7);
    }

    @Test
    void setCustomModelDataComponent_sets_all_supported_component_lists() {
        ComponentMeta meta = mock(ComponentMeta.class);
        TestCustomModelDataComponent component = new TestCustomModelDataComponent();
        when(meta.getCustomModelDataComponent()).thenReturn(component);

        assertTrue(ModelDataCompat.setCustomModelDataComponent(
                meta,
                Arrays.asList(1.0F, 2.5F),
                Arrays.asList(true, false),
                Arrays.asList("bronze", "silver"),
                Collections.singletonList(Color.RED)
        ));

        assertEquals(Arrays.asList(1.0F, 2.5F), component.floats);
        assertEquals(Arrays.asList(true, false), component.flags);
        assertEquals(Arrays.asList("bronze", "silver"), component.strings);
        assertEquals(Collections.singletonList(Color.RED), component.colors);
        verify(meta).setCustomModelDataComponent(component);
    }

    @Test
    void reflection_method_cache_reuses_lookup_results() throws Exception {
        Map<?, ?> methodCache = methodCache();
        methodCache.clear();

        ComponentMeta meta = mock(ComponentMeta.class);
        when(meta.getCustomModelDataComponent()).thenReturn(new TestCustomModelDataComponent());

        assertTrue(ModelDataCompat.setCustomModelDataComponent(
                meta,
                Collections.singletonList(1.0F),
                Collections.singletonList(true),
                Collections.singletonList("bronze"),
                Collections.singletonList(Color.RED)
        ));
        int cacheSizeAfterFirstCall = methodCache.size();

        assertTrue(ModelDataCompat.setCustomModelDataComponent(
                meta,
                Collections.singletonList(2.0F),
                Collections.singletonList(false),
                Collections.singletonList("silver"),
                Collections.singletonList(Color.BLUE)
        ));

        assertTrue(cacheSizeAfterFirstCall > 0);
        assertEquals(cacheSizeAfterFirstCall, methodCache.size());
    }

    @Test
    void setItemModel_sets_namespaced_key_when_api_is_available() {
        ItemModelMeta meta = mock(ItemModelMeta.class);

        assertTrue(ModelDataCompat.setItemModel(meta, "example:widgets/bronze_sword"));

        verify(meta).setItemModel(argThat(key -> "example:widgets/bronze_sword".equals(key.toString())));
    }

    @Test
    void setItemModel_defaults_missing_namespace_to_minecraft() {
        ItemModelMeta meta = mock(ItemModelMeta.class);

        assertTrue(ModelDataCompat.setItemModel(meta, "widgets/bronze_sword"));

        verify(meta).setItemModel(argThat(key -> "minecraft:widgets/bronze_sword".equals(key.toString())));
    }

    @Test
    void setItemModel_rejects_blank_keys() {
        ItemModelMeta meta = mock(ItemModelMeta.class);

        assertFalse(ModelDataCompat.setItemModel(meta, " "));

        verify(meta, never()).setItemModel(org.mockito.ArgumentMatchers.any(NamespacedKey.class));
    }

    private interface ComponentMeta extends ItemMeta {
        TestCustomModelDataComponent getCustomModelDataComponent();

        void setCustomModelDataComponent(TestCustomModelDataComponent customModelData);
    }

    private interface ItemModelMeta extends ItemMeta {
        void setItemModel(NamespacedKey itemModel);
    }

    private static Map<?, ?> methodCache() throws ReflectiveOperationException {
        Field field = ModelDataCompat.class.getDeclaredField("METHOD_CACHE");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }

    public static final class TestCustomModelDataComponent {
        private List<Float> floats = Collections.emptyList();
        private List<Boolean> flags = Collections.emptyList();
        private List<String> strings = Collections.emptyList();
        private List<Color> colors = Collections.emptyList();

        public void setFloats(List<Float> floats) {
            this.floats = floats;
        }

        public void setFlags(List<Boolean> flags) {
            this.flags = flags;
        }

        public void setStrings(List<String> strings) {
            this.strings = strings;
        }

        public void setColors(List<Color> colors) {
            this.colors = colors;
        }
    }
}
