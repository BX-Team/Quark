package org.bxteam.quark.gradle.pluginyml

/**
 * Block-style YAML for maps, lists and scalars; enough for plugin descriptors without a YAML library on the build class path.
 */
internal object Yaml {
    private val PLAIN = Regex("^[A-Za-z_][A-Za-z0-9_ ./-]*$")
    private val RESERVED = setOf("y", "n", "yes", "no", "on", "off", "true", "false", "null")

    fun write(root: Map<String, Any>): String = buildString { map(root, 0) }

    private fun StringBuilder.map(map: Map<*, *>, indent: Int) {
        for ((key, value) in map) {
            append(" ".repeat(indent)).append(scalar(key)).append(':')
            value(value, indent)
        }
    }

    private fun StringBuilder.value(value: Any?, indent: Int) {
        when (value) {
            is Map<*, *> -> if (value.isEmpty()) {
                append(" {}\n")
            } else {
                append('\n')
                map(value, indent + 2)
            }
            is Collection<*> -> if (value.isEmpty()) {
                append(" []\n")
            } else {
                append('\n')
                value.forEach { append(" ".repeat(indent + 2)).append("- ").append(scalar(it)).append('\n') }
            }
            else -> append(' ').append(scalar(value)).append('\n')
        }
    }

    fun scalar(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Int, is Long -> value.toString()
        else -> value.toString().let { if (PLAIN.matches(it) && it == it.trimEnd() && it.lowercase() !in RESERVED) it else quote(it) }
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (char in text) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}

/**
 * Indented JSON for maps, lists and scalars, for `velocity-plugin.json`.
 */
internal object Json {
    fun write(root: Map<String, Any>): String = buildString {
        value(root, 0)
        append('\n')
    }

    private fun StringBuilder.value(value: Any?, indent: Int) {
        when (value) {
            null -> append("null")
            is Boolean, is Int, is Long -> append(value)
            is Map<*, *> -> block('{', '}', value.entries, indent) { (key, item) ->
                string(key.toString())
                append(": ")
                value(item, indent + 2)
            }
            is Collection<*> -> block('[', ']', value, indent) { value(it, indent + 2) }
            else -> string(value.toString())
        }
    }

    private fun <T> StringBuilder.block(open: Char, close: Char, items: Collection<T>, indent: Int, item: StringBuilder.(T) -> Unit) {
        if (items.isEmpty()) {
            append(open).append(close)
            return
        }
        append(open).append('\n')
        items.forEachIndexed { index, it ->
            append(" ".repeat(indent + 2))
            item(it)
            if (index < items.size - 1) append(',')
            append('\n')
        }
        append(" ".repeat(indent)).append(close)
    }

    private fun StringBuilder.string(text: String) {
        append('"')
        for (char in text) {
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
}
