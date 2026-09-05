package org.bxteam.quark.update.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader, so the update checker needs no JSON library in the plugin JAR.
 *
 * <p>Objects become {@link Map}s, arrays {@link List}s, numbers {@link Long} or {@link Double},
 * plus {@link String}, {@link Boolean} and {@code null}. Internal API, not covered by compatibility guarantees.</p>
 */
public final class Json {
    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /**
     * @param text JSON text
     * @return the parsed value
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    @Nullable
    public static Object parse(@NotNull String text) {
        Json json = new Json(text);
        json.skipWhitespace();
        Object value = json.readValue();
        json.skipWhitespace();
        if (json.position != text.length()) {
            throw json.error("Unexpected trailing content");
        }
        return value;
    }

    /**
     * Follows a path such as {@code "data.versions[0].name"} or {@code "[0].tag"}.
     *
     * @param root the parsed JSON
     * @param path the path
     * @return the value, or null if any segment is missing
     */
    @Nullable
    public static Object path(@Nullable Object root, @NotNull String path) {
        Object current = root;
        int i = 0;
        while (i < path.length() && current != null) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
            } else if (c == '[') {
                int end = path.indexOf(']', i);
                if (end < 0) throw new IllegalArgumentException("Unclosed '[' in path: " + path);
                int index = Integer.parseInt(path.substring(i + 1, end).trim());
                current = current instanceof List<?> list && index >= 0 && index < list.size() ? list.get(index) : null;
                i = end + 1;
            } else {
                int end = i;
                while (end < path.length() && path.charAt(end) != '.' && path.charAt(end) != '[') end++;
                String key = path.substring(i, end);
                current = current instanceof Map<?, ?> map ? map.get(key) : null;
                i = end;
            }
        }
        return current;
    }

    /**
     * @param value a parsed JSON value
     * @return the value as a string, or null if it is not a string or a number
     */
    @Nullable
    public static String string(@Nullable Object value) {
        if (value instanceof String string) return string;
        if (value instanceof Number number) return number.toString();
        return null;
    }

    private Object readValue() {
        if (position >= text.length()) throw error("Unexpected end of input");
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) yield readNumber();
                throw error("Unexpected character '" + c + "'");
            }
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        position++;
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("Expected a key");
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') return map;
            if (c != ',') throw error("Expected ',' or '}'");
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        position++;
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') return list;
            if (c != ',') throw error("Expected ',' or ']'");
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escape = next();
            switch (escape) {
                case '"', '\\', '/' -> out.append(escape);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) throw error("Invalid unicode escape");
                    out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    position += 4;
                }
                default -> throw error("Invalid escape '\\" + escape + "'");
            }
        }
    }

    private Number readNumber() {
        int start = position;
        if (peek() == '-') position++;
        boolean decimal = false;
        while (position < text.length()) {
            char c = text.charAt(position);
            if (c >= '0' && c <= '9') {
                position++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                decimal = true;
                position++;
            } else {
                break;
            }
        }
        String number = text.substring(start, position);
        try {
            if (decimal) {
                return Double.parseDouble(number);
            }
            return Long.parseLong(number);
        } catch (NumberFormatException e) {
            return Double.parseDouble(number);
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) throw error("Expected '" + literal + "'");
        position += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
    }

    private char peek() {
        if (position >= text.length()) throw error("Unexpected end of input");
        return text.charAt(position);
    }

    private char next() {
        char c = peek();
        position++;
        return c;
    }

    private void expect(char expected) {
        if (next() != expected) throw error("Expected '" + expected + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + position);
    }
}
