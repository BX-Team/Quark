package org.bxteam.quark.config.yaml;

import org.bxteam.quark.config.ConfigException;
import org.bxteam.quark.config.ConfigNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFormatTest {
    private final YamlFormat format = YamlFormat.create();

    @Test
    void readsScalarsListsAndSections() {
        ConfigNode root = format.read("""
                name: test
                count: 3
                ratio: 0.5
                enabled: true
                empty: ~
                1: numeric key
                tags: [a, b]
                nested:
                  deep:
                    value: x
                """);

        assertEquals("test", root.node("name").getString());
        assertEquals(3, root.node("count").scalar());
        assertEquals(0.5, root.node("ratio").scalar());
        assertEquals(true, root.node("enabled").scalar());
        assertTrue(root.node("empty").isNull());
        assertTrue(!root.node("empty").isVirtual());
        assertEquals("numeric key", root.node("1").getString());
        assertEquals(List.of("a", "b"), root.node("tags").raw());
        assertEquals("x", root.node("nested", "deep", "value").getString());
    }

    @Test
    void emptyFilesAreEmptySections() {
        assertEquals(Map.of(), format.read("").raw());
        assertEquals(Map.of(), format.read("# only a comment\n").raw());
    }

    @Test
    void rejectsDuplicateKeysAndNonSectionRoots() {
        assertThrows(ConfigException.class, () -> format.read("a: 1\na: 2\n"));
        assertThrows(ConfigException.class, () -> format.read("- a\n- b\n"));
    }

    @Test
    void writesQuotedStringsAndMultilineText() {
        ConfigNode root = ConfigNode.root();
        root.node("number-like").set("123");
        root.node("bool-like").set("yes");
        root.node("text").set("line one\nline two");
        root.node("empty-list").setList();

        String text = format.write(root);
        ConfigNode back = format.read(text);

        assertEquals("123", back.node("number-like").scalar());
        assertEquals("yes", back.node("bool-like").scalar());
        assertEquals("line one\nline two", back.node("text").scalar());
        assertEquals(List.of(), back.node("empty-list").raw());
    }

    @Test
    void writesCommentsOnListElementsSections() {
        ConfigNode root = ConfigNode.root();
        root.node("items").appendElement().node("name").set("a").comment("first item");

        String text = format.write(root);

        assertEquals("items:\n  - # first item\n    name: a\n", text);
    }
}
