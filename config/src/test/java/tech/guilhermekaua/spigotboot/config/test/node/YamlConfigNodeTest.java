package tech.guilhermekaua.spigotboot.config.test.node;

import org.junit.jupiter.api.Test;
import tech.guilhermekaua.spigotboot.config.node.MutableConfigNode;
import tech.guilhermekaua.spigotboot.config.node.YamlConfigNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    @Test
    void appendListItem_onVirtualNode_listVisibleFromRoot() {
        Map<String, Object> data = new LinkedHashMap<>();
        YamlConfigNode root = new YamlConfigNode(data);

        MutableConfigNode listNode = root.node("items");
        assertTrue(listNode.isVirtual(), "node should start as virtual");

        MutableConfigNode item = listNode.appendListItem();
        item.set("hello");

        MutableConfigNode refetched = root.node("items");
        assertTrue(refetched.isList(), "re-navigated node should be a list");
        List<?> children = refetched.childrenList();
        assertEquals(1, children.size(), "list should contain one element");
        assertEquals("hello", refetched.node(0).raw(), "element should be the value we set");
    }

    @Test
    void appendListItem_onScalarNode_listVisibleFromRoot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", "scalar");
        YamlConfigNode root = new YamlConfigNode(data);

        MutableConfigNode scalarNode = root.node("key");
        assertTrue(scalarNode.isScalar(), "node should start as scalar");

        MutableConfigNode item = scalarNode.appendListItem();
        item.set("replaced");

        MutableConfigNode refetched = root.node("key");
        assertTrue(refetched.isList(), "re-navigated node should now be a list, not a scalar");
        assertEquals(1, refetched.childrenList().size(), "list should contain one element");
        assertEquals("replaced", refetched.node(0).raw());
    }

    @Test
    void appendListItem_onExistingList_itemVisibleFromRoot() {
        ArrayList<Object> list = new ArrayList<>();
        list.add("existing");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", list);
        YamlConfigNode root = new YamlConfigNode(data);

        MutableConfigNode listNode = root.node("items");
        assertTrue(listNode.isList(), "node should already be a list");

        MutableConfigNode item = listNode.appendListItem();
        item.set("appended");

        MutableConfigNode refetched = root.node("items");
        assertTrue(refetched.isList(), "re-navigated node should still be a list");
        assertEquals(2, refetched.childrenList().size(), "list should now have two elements");
        assertEquals("existing", refetched.node(0).raw());
        assertEquals("appended", refetched.node(1).raw());
    }
}
