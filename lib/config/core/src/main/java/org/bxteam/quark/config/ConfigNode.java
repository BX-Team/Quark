package org.bxteam.quark.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * A node of the tree between configuration objects and the file format.
 *
 * <p>A node holds either nothing ({@code null}), a scalar ({@link String}, {@link Boolean} or {@link Number}),
 * a map of named children or a list of children, plus comment lines written above it. Serializers and
 * {@link Migration}s work on this tree, formats read and write it.</p>
 *
 * <p>{@link #node(String...)} never returns null: a missing child is <em>virtual</em> ({@link #isVirtual()})
 * and becomes part of the tree once a value is {@link #set(Object) set} on it.</p>
 *
 * <pre>{@code
 * root.node("database", "host").set("localhost");
 * int port = root.node("database", "port").getInt(3306);
 * root.node("old-key").moveTo(root.node("new-key"));
 * }</pre>
 */
public final class ConfigNode {
    private final ConfigNode parent;
    private final String key;
    private Object value;
    private List<String> comment = List.of();
    private boolean attached;

    private ConfigNode(@Nullable ConfigNode parent, @Nullable String key, boolean attached) {
        this.parent = parent;
        this.key = key;
        this.attached = attached;
    }

    /**
     * @return a new empty root node holding a map
     */
    @NotNull
    public static ConfigNode root() {
        ConfigNode root = new ConfigNode(null, null, true);
        root.value = new LinkedHashMap<String, ConfigNode>();
        return root;
    }

    /**
     * Creates a detached node holding the given value, see {@link #set(Object)}.
     *
     * @param value the value
     * @return the node
     */
    @NotNull
    public static ConfigNode of(@Nullable Object value) {
        return new ConfigNode(null, null, true).set(value);
    }

    /**
     * Gets a descendant by its keys. Missing children are returned as virtual nodes.
     *
     * @param path the keys, empty for this node
     * @return the node, never null
     */
    @NotNull
    public ConfigNode node(@NotNull String... path) {
        ConfigNode current = this;
        for (String segment : requireNonNull(path, "Path cannot be null")) {
            current = current.child(requireNonNull(segment, "Path segment cannot be null"));
        }
        return current;
    }

    private ConfigNode child(String childKey) {
        if (value instanceof Map<?, ?> map) {
            ConfigNode existing = (ConfigNode) map.get(childKey);
            if (existing != null) {
                return existing;
            }
        } else if (value instanceof List<?> list && attached) {
            try {
                int index = Integer.parseInt(childKey);
                if (index >= 0 && index < list.size()) {
                    return (ConfigNode) list.get(index);
                }
            } catch (NumberFormatException ignored) {
                // not an index, falls through to a virtual node
            }
        }
        return new ConfigNode(this, childKey, false);
    }

    /**
     * @return the parent node, or null for a root or detached node
     */
    @Nullable
    public ConfigNode parent() {
        return parent;
    }

    /**
     * @return the key of this node in its parent map, its index for list elements, or null for a root node
     */
    @Nullable
    public String key() {
        return key;
    }

    /**
     * @return the keys from the root to this node, list indices included
     */
    @NotNull
    public List<String> path() {
        List<String> path = new ArrayList<>();
        for (ConfigNode node = this; node != null && node.key != null; node = node.parent) {
            path.add(0, node.key);
        }
        return path;
    }

    /**
     * @return the path joined with dots, {@code <root>} for the root node
     */
    @NotNull
    public String pathString() {
        List<String> path = path();
        return path.isEmpty() ? "<root>" : String.join(".", path);
    }

    /**
     * @return true if this node is not part of the tree: it was returned for a missing key
     */
    public boolean isVirtual() {
        return !attached;
    }

    /**
     * @return true if this node is virtual or holds {@code null}
     */
    public boolean isNull() {
        return !attached || value == null;
    }

    /**
     * @return true if this node holds a map of children
     */
    public boolean isMap() {
        return attached && value instanceof Map;
    }

    /**
     * @return true if this node holds a list of children
     */
    public boolean isList() {
        return attached && value instanceof List;
    }

    /**
     * @return true if this node holds a string, boolean or number
     */
    public boolean isScalar() {
        return attached && value != null && !(value instanceof Map) && !(value instanceof List);
    }

    /**
     * @return the scalar value, or null if this node holds no scalar
     */
    @Nullable
    public Object scalar() {
        return isScalar() ? value : null;
    }

    /**
     * @return the children of a map node in order, empty for other nodes
     */
    @NotNull
    @Unmodifiable
    @SuppressWarnings("unchecked")
    public Map<String, ConfigNode> children() {
        return isMap() ? Collections.unmodifiableMap((Map<String, ConfigNode>) value) : Map.of();
    }

    /**
     * @return the elements of a list node, empty for other nodes
     */
    @NotNull
    @Unmodifiable
    @SuppressWarnings("unchecked")
    public List<ConfigNode> elements() {
        return isList() ? Collections.unmodifiableList((List<ConfigNode>) value) : List.of();
    }

    /**
     * Converts this node to plain Java values: {@link LinkedHashMap}s of strings, {@link ArrayList}s and scalars.
     *
     * @return the value, null for a virtual or null node
     */
    @Nullable
    public Object raw() {
        if (!attached) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put((String) k, ((ConfigNode) v).raw()));
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(element -> result.add(((ConfigNode) element).raw()));
            return result;
        }
        return value;
    }

    /**
     * Sets the value of this node and attaches it to the tree.
     *
     * <p>Accepted values: {@code null}, {@link String}, {@link Character} (stored as a string), {@link Boolean},
     * {@link Number}, another {@link ConfigNode} (copied with its comment), maps (keys are converted with
     * {@link String#valueOf(Object)}), iterables and arrays of accepted values. Anything else needs a serializer.</p>
     *
     * @param newValue the value
     * @return this node
     * @throws IllegalArgumentException if the value is not accepted
     */
    @NotNull
    public ConfigNode set(@Nullable Object newValue) {
        if (newValue instanceof ConfigNode node) {
            Object copied = node.attached ? copyValue(node.value) : null;
            attach();
            this.value = adopt(copied);
            this.comment = node.comment;
            return this;
        }
        Object converted = convert(newValue);
        attach();
        this.value = adopt(converted);
        return this;
    }

    /**
     * Makes this node an empty map unless it already is one.
     *
     * @return this node
     */
    @NotNull
    public ConfigNode setMap() {
        if (!isMap()) {
            set(Map.of());
        }
        return this;
    }

    /**
     * Makes this node an empty list unless it already is one.
     *
     * @return this node
     */
    @NotNull
    public ConfigNode setList() {
        if (!isList()) {
            set(List.of());
        }
        return this;
    }

    /**
     * Appends a new null element to this node, turning it into a list first if needed.
     *
     * @return the new element
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public ConfigNode appendElement() {
        setList();
        List<ConfigNode> list = (List<ConfigNode>) value;
        ConfigNode element = new ConfigNode(this, String.valueOf(list.size()), true);
        list.add(element);
        return element;
    }

    /**
     * Removes this node from its parent map or list.
     *
     * @return true if the node was part of the tree
     */
    public boolean remove() {
        if (!attached || parent == null) {
            return false;
        }
        boolean removed = false;
        if (parent.value instanceof Map<?, ?> map) {
            removed = map.remove(key, this);
        } else if (parent.value instanceof List<?> list) {
            removed = list.remove(this);
            if (removed) {
                parent.reindex();
            }
        }
        attached = false;
        return removed;
    }

    /**
     * Moves the value and comment of this node to {@code target} and removes this node. Does nothing
     * if this node is virtual. Used by {@link Migration}s to rename keys.
     *
     * @param target the new location
     * @return true if a value was moved
     */
    public boolean moveTo(@NotNull ConfigNode target) {
        requireNonNull(target, "Target cannot be null");
        if (!attached || target == this) {
            return false;
        }
        ConfigNode copy = copy();
        remove();
        target.set(copy);
        return true;
    }

    /**
     * @return a detached deep copy of this node
     */
    @NotNull
    public ConfigNode copy() {
        ConfigNode copy = new ConfigNode(null, null, true);
        copy.value = copy.adopt(attached ? copyValue(value) : null);
        copy.comment = comment;
        return copy;
    }

    /**
     * @return the comment lines written above this node, an empty line becomes a blank line
     */
    @NotNull
    @Unmodifiable
    public List<String> comment() {
        return comment;
    }

    /**
     * Sets the comment lines written above this node. Lines containing line breaks are split.
     *
     * @param lines the lines, an empty line becomes a blank line
     * @return this node
     */
    @NotNull
    public ConfigNode comment(@NotNull List<String> lines) {
        List<String> split = new ArrayList<>();
        for (String line : requireNonNull(lines, "Comment cannot be null")) {
            Collections.addAll(split, requireNonNull(line, "Comment line cannot be null").split("\\R", -1));
        }
        this.comment = List.copyOf(split);
        return this;
    }

    /**
     * @param lines the comment lines, see {@link #comment(List)}
     * @return this node
     */
    @NotNull
    public ConfigNode comment(@NotNull String... lines) {
        return comment(List.of(lines));
    }

    /**
     * @return the scalar as a string, or null if this node holds no scalar
     */
    @Nullable
    public String getString() {
        return isScalar() ? String.valueOf(value) : null;
    }

    /**
     * @param def the default
     * @return the scalar as a string, or {@code def} if this node holds no scalar
     */
    @NotNull
    public String getString(@NotNull String def) {
        String string = getString();
        return string != null ? string : def;
    }

    /**
     * @param def the default
     * @return the scalar as an int, or {@code def} if it is missing or not a number
     */
    public int getInt(int def) {
        Number number = number();
        return number != null ? number.intValue() : def;
    }

    /**
     * @param def the default
     * @return the scalar as a long, or {@code def} if it is missing or not a number
     */
    public long getLong(long def) {
        Number number = number();
        return number != null ? number.longValue() : def;
    }

    /**
     * @param def the default
     * @return the scalar as a double, or {@code def} if it is missing or not a number
     */
    public double getDouble(double def) {
        Number number = number();
        return number != null ? number.doubleValue() : def;
    }

    /**
     * @param def the default
     * @return the scalar as a boolean, or {@code def} if it is missing or not {@code true}/{@code false}
     */
    public boolean getBoolean(boolean def) {
        if (value instanceof Boolean bool && attached) {
            return bool;
        }
        String string = getString();
        if ("true".equalsIgnoreCase(string)) return true;
        if ("false".equalsIgnoreCase(string)) return false;
        return def;
    }

    private Number number() {
        if (!attached) {
            return null;
        }
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String string) {
            try {
                return new BigDecimal(string.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private void attach() {
        if (attached) {
            return;
        }
        if (parent == null) {
            attached = true;
            return;
        }
        parent.attach();
        if (!(parent.value instanceof Map)) {
            parent.value = new LinkedHashMap<String, ConfigNode>();
        }
        @SuppressWarnings("unchecked")
        Map<String, ConfigNode> siblings = (Map<String, ConfigNode>) parent.value;
        ConfigNode existing = siblings.get(key);
        if (existing != null && existing != this) {
            existing.attached = false;
        }
        siblings.put(key, this);
        attached = true;
    }

    private void reindex() {
        @SuppressWarnings("unchecked")
        List<ConfigNode> list = (List<ConfigNode>) value;
        List<ConfigNode> reindexed = new ArrayList<>(list.size());
        for (ConfigNode element : list) {
            ConfigNode moved = new ConfigNode(this, String.valueOf(reindexed.size()), true);
            moved.value = moved.adopt(element.value);
            moved.comment = element.comment;
            element.attached = false;
            reindexed.add(moved);
        }
        value = reindexed;
    }

    /**
     * Wraps plain children (from {@link #convert(Object)} or {@link #copyValue(Object)}) into child nodes of this node.
     */
    private Object adopt(Object plain) {
        if (plain instanceof Map<?, ?> map) {
            Map<String, ConfigNode> children = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                ConfigNode child = new ConfigNode(this, (String) k, true);
                if (v instanceof Commented commented) {
                    child.value = child.adopt(commented.value);
                    child.comment = commented.comment;
                } else {
                    child.value = child.adopt(v);
                }
                children.put((String) k, child);
            });
            return children;
        }
        if (plain instanceof List<?> list) {
            List<ConfigNode> elements = new ArrayList<>(list.size());
            for (Object v : list) {
                ConfigNode child = new ConfigNode(this, String.valueOf(elements.size()), true);
                if (v instanceof Commented commented) {
                    child.value = child.adopt(commented.value);
                    child.comment = commented.comment;
                } else {
                    child.value = child.adopt(v);
                }
                elements.add(child);
            }
            return elements;
        }
        return plain;
    }

    /**
     * Deep-copies a node value into plain maps and lists, keeping the comments of children.
     */
    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                ConfigNode child = (ConfigNode) v;
                copy.put((String) k, new Commented(copyValue(child.value), child.comment));
            });
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object v : list) {
                ConfigNode child = (ConfigNode) v;
                copy.add(new Commented(copyValue(child.value), child.comment));
            }
            return copy;
        }
        return value;
    }

    private static Object convert(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character character) {
            return character.toString();
        }
        if (value instanceof Number number) {
            return number instanceof BigDecimal || number instanceof BigInteger
                    || number instanceof Integer || number instanceof Long || number instanceof Double
                    || number instanceof Float || number instanceof Short || number instanceof Byte
                    ? number : new BigDecimal(number.toString());
        }
        if (value instanceof ConfigNode node) {
            return new Commented(node.attached ? copyValue(node.value) : null, node.comment);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((k, v) -> converted.put(String.valueOf(k), convert(v)));
            return converted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> converted = new ArrayList<>();
            iterable.forEach(element -> converted.add(convert(element)));
            return converted;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> converted = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                converted.add(convert(java.lang.reflect.Array.get(value, i)));
            }
            return converted;
        }
        throw new IllegalArgumentException("Cannot store " + value.getClass().getName()
                + " in a ConfigNode directly, serialize it with a TypeSerializer");
    }

    /**
     * A copied child value with its comment, unwrapped by {@link #adopt(Object)}.
     */
    private record Commented(Object value, List<String> comment) {
    }

    @Override
    public String toString() {
        return "ConfigNode{" + pathString() + "=" + raw() + "}";
    }
}
