package org.bxteam.quark.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigNodeTest {
    @Test
    void missingNodesAreVirtualUntilSet() {
        ConfigNode root = ConfigNode.root();
        ConfigNode port = root.node("database", "port");

        assertTrue(port.isVirtual());
        assertEquals(Map.of(), root.raw());
        assertEquals(3306, port.getInt(3306));

        port.set(5432);

        assertFalse(port.isVirtual());
        assertEquals(Map.of("database", Map.of("port", 5432)), root.raw());
        assertEquals(List.of("database", "port"), port.path());
        assertEquals("database.port", port.pathString());
    }

    @Test
    void setConvertsMapsListsAndArrays() {
        ConfigNode node = ConfigNode.of(Map.of("list", List.of(1, 'c'), "array", new int[]{1, 2}));

        assertEquals(List.of(1, "c"), node.node("list").raw());
        assertEquals(List.of(1, 2), node.node("array").raw());
        assertEquals("c", node.node("list", "1").getString());
        assertThrows(IllegalArgumentException.class, () -> ConfigNode.of(new Object()));
    }

    @Test
    void moveToRenamesKeysWithTheirComment() {
        ConfigNode root = ConfigNode.root();
        root.node("old").set(Map.of("a", 1)).comment("kept");

        assertTrue(root.node("old").moveTo(root.node("section", "new")));

        assertTrue(root.node("old").isVirtual());
        assertEquals(1, root.node("section", "new", "a").getInt(0));
        assertEquals(List.of("kept"), root.node("section", "new").comment());
        assertFalse(root.node("missing").moveTo(root.node("other")));
        assertTrue(root.node("other").isVirtual());
    }

    @Test
    void removingListElementsReindexes() {
        ConfigNode list = ConfigNode.root().node("list");
        list.appendElement().set("a");
        list.appendElement().set("b");
        list.appendElement().set("c");

        assertTrue(list.elements().get(1).remove());

        assertEquals(List.of("a", "c"), list.raw());
        assertEquals("1", list.elements().get(1).key());
        assertEquals("c", list.node("1").getString());
    }

    @Test
    void copiesAreDeepAndDetached() {
        ConfigNode root = ConfigNode.root();
        root.node("a", "b").set("x");
        ConfigNode copy = root.node("a").copy();

        copy.node("b").set("y");

        assertEquals("x", root.node("a", "b").getString());
        assertNull(copy.parent());
    }

    @Test
    void scalarAccessorsConvertStrings() {
        assertEquals(12, ConfigNode.of("12").getInt(0));
        assertEquals(1.5, ConfigNode.of("1.5").getDouble(0));
        assertTrue(ConfigNode.of("TRUE").getBoolean(false));
        assertEquals(7, ConfigNode.of("seven").getInt(7));
        assertEquals("def", ConfigNode.of(Map.of()).getString("def"));
    }

    @Test
    void commentsAreSplitIntoLines() {
        ConfigNode node = ConfigNode.of(1).comment("one\ntwo", "");

        assertEquals(List.of("one", "two", ""), node.comment());
    }
}
