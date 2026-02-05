package tech.guilhermekaua.spigotboot.config.spigot.test.node;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.spigot.node.YamlConfigNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class YamlConfigNodeTest {

    @Test
    void remove_fromMapParent_removesEntryFromParent() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("a", "val_a");
        data.put("b", "val_b");
        YamlConfigNode root = new YamlConfigNode(data);

        MutableConfigNode child = root.node("a");
        assertFalse(child.isVirtual());
        assertEquals("val_a", child.raw());

        child.remove();

        assertTrue(child.isVirtual(), "removed node should be virtual");
        assertNull(child.raw(), "removed node value should be null");
        assertFalse(root.childrenMap().containsKey("a"), "parent map should no longer contain removed key");
        assertTrue(root.node("a").isVirtual(), "re-accessing removed key should return a virtual node");
        assertEquals(1, root.childrenMap().size(), "parent map should have one remaining entry");
    }

    @Test
    void remove_fromListParent_removesElementFromParent() {
        ArrayList<Object> list = new ArrayList<>();
        list.add("first");
        list.add("second");
        list.add("third");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", list);
        YamlConfigNode root = new YamlConfigNode(data);

        MutableConfigNode listNode = root.node("items");
        assertEquals(3, listNode.childrenList().size());

        MutableConfigNode child = listNode.node(1);
        assertFalse(child.isVirtual());
        assertEquals("second", child.raw());

        child.remove();

        assertTrue(child.isVirtual(), "removed node should be virtual");
        assertNull(child.raw(), "removed node value should be null");
        assertEquals(2, listNode.childrenList().size(), "parent list should have one fewer element after removal");
    }
}
