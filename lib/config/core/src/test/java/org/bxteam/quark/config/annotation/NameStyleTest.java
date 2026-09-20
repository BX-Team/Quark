package org.bxteam.quark.config.annotation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameStyleTest {
    @Test
    void convertsCamelCase() {
        assertEquals("maxPlayers", NameStyle.IDENTITY.apply("maxPlayers"));
        assertEquals("max-players", NameStyle.HYPHEN_CASE.apply("maxPlayers"));
        assertEquals("max_players", NameStyle.SNAKE_CASE.apply("maxPlayers"));
        assertEquals("max-http-connections", NameStyle.HYPHEN_CASE.apply("maxHTTPConnections"));
        assertEquals("world2-name", NameStyle.HYPHEN_CASE.apply("world2Name"));
        assertEquals("url", NameStyle.HYPHEN_CASE.apply("URL"));
        assertEquals("already-snake", NameStyle.HYPHEN_CASE.apply("already_snake"));
    }
}
