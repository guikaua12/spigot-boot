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

    @Test
    void set_onSubKeysOfVirtualNode_attachesWholeChainToRoot() {
        Map<String, Object> data = new LinkedHashMap<>();
        YamlConfigNode root = new YamlConfigNode(data);

        // a virtual child populated ONLY through sub-keys; set() is never called on the child itself
        MutableConfigNode child = root.node("nested");
        assertTrue(child.isVirtual(), "child should start as virtual");
        child.node("a").set("x");
        child.node("b").set("y");

        MutableConfigNode refetched = root.node("nested");
        assertFalse(refetched.isVirtual(), "node populated via sub-keys must be live in the root");
        assertEquals("x", refetched.node("a").raw());
        assertEquals("y", refetched.node("b").raw());
        assertTrue(root.childrenMap().containsKey("nested"), "root map should contain the nested key");
    }

    @Test
    void appendListItem_populatedViaSubKeys_replacesPlaceholderInList() {
        Map<String, Object> data = new LinkedHashMap<>();
        YamlConfigNode root = new YamlConfigNode(data);

        MutableConfigNode listNode = root.node("items");
        MutableConfigNode item = listNode.appendListItem();
        // populate the appended item ONLY through sub-keys, never item.set(...) directly
        item.node("k").set("v");

        MutableConfigNode refetched = root.node("items");
        assertTrue(refetched.isList(), "re-navigated node should be a list");
        assertEquals(1, refetched.childrenList().size(), "list should contain one element");
        assertNotNull(refetched.node(0).raw(), "list element populated via sub-keys must not stay null");
        assertEquals("v", refetched.node(0).node("k").raw());
    }

    @Test
    void set_withStringKeyUnderEstablishedList_doesNotFlipListToMapAtRoot() {
        List<Object> items = new ArrayList<>();
        items.add("a");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        YamlConfigNode root = new YamlConfigNode(data);

        // contradictory access: a string key under a node that is already a list. the re-link must
        // not coerce the established list into a map, and must not propagate that flip to the root.
        root.node("items").node("stringKey").set("value");

        assertTrue(root.node("items").isList(), "established list must stay a list");
        assertInstanceOf(List.class, ((Map<?, ?>) root.raw()).get("items"), "root container must keep the list");
        assertEquals(1, root.node("items").childrenList().size(), "the original element must be preserved");
    }

    @Test
    void set_withIntegerKeyUnderEstablishedMap_doesNotFlipMapToListAtRoot() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("k", "v");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("obj", inner);
        YamlConfigNode root = new YamlConfigNode(data);

        // contradictory access: an integer index under a node that is already a map.
        root.node("obj").node(0).set("value");

        assertTrue(root.node("obj").isMap(), "established map must stay a map");
        assertInstanceOf(Map.class, ((Map<?, ?>) root.raw()).get("obj"), "root container must keep the map");
        assertEquals("v", root.node("obj").node("k").raw(), "the original entry must be preserved");
    }
}
