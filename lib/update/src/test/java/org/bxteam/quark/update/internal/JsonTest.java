package org.bxteam.quark.update.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {
    @Test
    void parsesAllValueTypes() {
        Object value = Json.parse("""
                {"s": "a\\"b\\u00e9\\n", "i": 42, "d": -1.5e2, "t": true, "f": false, "n": null,
                 "a": [1, {"x": []}], "o": {}}
                """);

        Map<?, ?> map = assertInstanceOf(Map.class, value);
        assertEquals("a\"bé\n", map.get("s"));
        assertEquals(42L, map.get("i"));
        assertEquals(-150.0, map.get("d"));
        assertEquals(true, map.get("t"));
        assertEquals(false, map.get("f"));
        assertTrue(map.containsKey("n"));
        assertNull(map.get("n"));
        assertEquals(List.of(1L, Map.of("x", List.of())), map.get("a"));
        assertEquals(Map.of(), map.get("o"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1, 2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{} trailing"));
    }

    @Test
    void followsPaths() {
        Object root = Json.parse("{\"data\": {\"versions\": [{\"name\": \"1.0\"}, {\"name\": \"2.0\"}]}}");

        assertEquals("2.0", Json.path(root, "data.versions[1].name"));
        assertNull(Json.path(root, "data.versions[5].name"));
        assertNull(Json.path(root, "data.missing.name"));
        assertEquals("x", Json.path(Json.parse("[{\"tag\": \"x\"}]"), "[0].tag"));
    }
}
