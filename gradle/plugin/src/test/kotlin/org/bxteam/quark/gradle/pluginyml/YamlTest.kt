package org.bxteam.quark.gradle.pluginyml

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YamlTest {
    @Test
    fun `strings SnakeYAML would read as something else are quoted`() {
        assertEquals("\"1.20\"", Yaml.scalar("1.20"))
        assertEquals("\"yes\"", Yaml.scalar("yes"))
        assertEquals("\"Off\"", Yaml.scalar("Off"))
        assertEquals("\"!op\"", Yaml.scalar("!op"))
        assertEquals("\"a: b # c\"", Yaml.scalar("a: b # c"))
        assertEquals("\"say \\\"hi\\\"\\n\"", Yaml.scalar("say \"hi\"\n"))
        assertEquals("\"trailing \"", Yaml.scalar("trailing "))
        assertEquals("op", Yaml.scalar("op"))
        assertEquals("sample.admin", Yaml.scalar("sample.admin"))
        assertEquals("true", Yaml.scalar(true))
    }
}
