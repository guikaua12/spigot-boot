package tech.guilhermekaua.spigotboot.core.spigot.utils;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemBuilderTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void setAmount_sets_stack_size() {
        assertEquals(5, new ItemBuilder(Material.STONE).setAmount(5).wrap().getAmount());
    }

    @Test
    void setAmount_clamps_below_one_to_one() {
        assertEquals(1, new ItemBuilder(Material.STONE).setAmount(0).wrap().getAmount());
        assertEquals(1, new ItemBuilder(Material.STONE).setAmount(-4).wrap().getAmount());
    }

    @Test
    void setCustomModelData_sets_value() {
        ItemMeta meta = new ItemBuilder(Material.STONE).setCustomModelData(7).wrap().getItemMeta();
        assertTrue(meta.hasCustomModelData());
        assertEquals(7, meta.getCustomModelData());
    }

    @Test
    void setCustomModelData_null_is_noop() {
        ItemMeta meta = new ItemBuilder(Material.STONE).setCustomModelData(null).wrap().getItemMeta();
        assertFalse(meta.hasCustomModelData());
    }

    @Test
    void setLegacyModelData_sets_item_durability() {
        ItemStack item = new ItemBuilder(Material.STONE).setLegacyModelData(4).wrap();
        assertEquals(4, item.getDurability());
    }

    @Test
    void setItemModel_sets_namespaced_key_when_supported() {
        ItemStack item = mock(ItemStack.class);
        ItemModelMeta meta = mock(ItemModelMeta.class);
        when(item.getItemMeta()).thenReturn(meta);

        new ItemBuilder(item).setItemModel("example:widgets/bronze_sword");

        verify(meta).setItemModel(argThat(key -> "example:widgets/bronze_sword".equals(key.toString())));
        verify(item).setItemMeta(meta);
    }

    @Test
    void setCustomModelDataStrings_applies_component_strings_when_supported() {
        ItemStack item = mock(ItemStack.class);
        ComponentMeta meta = mock(ComponentMeta.class);
        TestCustomModelDataComponent component = new TestCustomModelDataComponent();
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getCustomModelDataComponent()).thenReturn(component);

        new ItemBuilder(item).setCustomModelDataStrings("bronze_sword");

        assertEquals(Collections.singletonList("bronze_sword"), component.strings);
        verify(meta).setCustomModelDataComponent(component);
        verify(item).setItemMeta(meta);
    }

    @Test
    void ofMaterial_resolves_modern_name() {
        assertEquals(Material.DIAMOND_SWORD, ItemBuilder.ofMaterial("DIAMOND_SWORD").wrap().getType());
    }

    @Test
    void ofMaterial_falls_back_to_stone_when_unknown() {
        assertEquals(Material.STONE, ItemBuilder.ofMaterial("NOPE_NOT_REAL", "ALSO_FAKE").wrap().getType());
    }

    @Test
    void ofMaterial_uses_first_resolvable_candidate() {
        assertEquals(Material.DIAMOND_SWORD, ItemBuilder.ofMaterial("FAKE_MODERN", "DIAMOND_SWORD").wrap().getType());
    }

    @Test
    void setName_colorizes_ampersand_codes() {
        String name = new ItemBuilder(Material.STONE).setName("&aPro").wrap().getItemMeta().getDisplayName();
        assertEquals("§aPro", name);
    }

    @Test
    void setRawName_does_not_colorize() {
        String name = new ItemBuilder(Material.STONE).setRawName("&aPro").wrap().getItemMeta().getDisplayName();
        assertEquals("&aPro", name);
    }

    @Test
    void setRawLore_sets_lines_verbatim() {
        List<String> lore = new ItemBuilder(Material.STONE).setRawLore("&aLine", "§bTwo").wrap().getItemMeta().getLore();
        assertEquals(Arrays.asList("&aLine", "§bTwo"), lore);
    }

    @Test
    void addLore_on_lore_less_item_adds_the_line() {
        // regression: previously a no-op when the item had no existing lore
        List<String> lore = new ItemBuilder(Material.STONE).addLore("&aLine").wrap().getItemMeta().getLore();
        assertNotNull(lore);
        assertEquals(1, lore.size());
        assertEquals("§aLine", lore.get(0));
    }

    private interface ItemModelMeta extends ItemMeta {
        void setItemModel(NamespacedKey itemModel);
    }

    private interface ComponentMeta extends ItemMeta {
        TestCustomModelDataComponent getCustomModelDataComponent();

        void setCustomModelDataComponent(TestCustomModelDataComponent customModelData);
    }

    public static final class TestCustomModelDataComponent {
        private List<String> strings = Collections.emptyList();

        public void setStrings(List<String> strings) {
            this.strings = strings;
        }
    }
}
