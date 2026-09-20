package org.bxteam.quark.config.yaml;

import org.bxteam.quark.config.ConfigException;
import org.bxteam.quark.config.ConfigNode;
import org.snakeyaml.engine.v2.api.Dump;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.representer.StandardRepresenter;
import org.snakeyaml.engine.v2.schema.CoreSchema;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The part of {@link YamlFormat} that touches snakeyaml-engine. Only loaded after {@link YamlFormat#BACKEND} is
 * available.
 */
final class SnakeYamlBackend {
    private final LoadSettings loadSettings = LoadSettings.builder()
            .setAllowDuplicateKeys(false)
            .setSchema(new CoreSchema())
            .build();
    private final DumpSettings dumpSettings = DumpSettings.builder()
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .setIndicatorIndent(2)
            .setIndentWithIndicator(true)
            .setSplitLines(false)
            .setDumpComments(true)
            .setSchema(new CoreSchema())
            .build();

    ConfigNode read(String content) {
        Object loaded;
        try {
            loaded = new Load(loadSettings).loadFromString(content);
        } catch (YamlEngineException e) {
            throw new ConfigException(e.getMessage(), e);
        }
        ConfigNode root = ConfigNode.root();
        if (loaded == null) {
            return root;
        }
        if (!(loaded instanceof Map<?, ?>)) {
            throw new ConfigException("the file must contain keys at the top level, found " + loaded.getClass().getSimpleName());
        }
        return root.set(plain(loaded));
    }

    String write(ConfigNode root) {
        Node node = new StandardRepresenter(dumpSettings).represent(root.raw() != null ? root.raw() : Map.of());
        attachComments(root, node);

        StringBuilder output = new StringBuilder();
        for (String line : root.comment()) {
            output.append(line.isEmpty() ? "" : "# " + line).append('\n');
        }
        if (!root.comment().isEmpty()) {
            output.append('\n');
        }
        new Dump(dumpSettings).dumpNode(node, new StreamDataWriter() {
            @Override
            public void write(String string) {
                output.append(string);
            }

            @Override
            public void write(String string, int offset, int length) {
                output.append(string, offset, offset + length);
            }
        });
        return output.toString();
    }

    private static void attachComments(ConfigNode configNode, Node yamlNode) {
        if (yamlNode instanceof MappingNode mapping) {
            Map<String, ConfigNode> children = configNode.children();
            for (NodeTuple tuple : mapping.getValue()) {
                if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
                    continue;
                }
                ConfigNode child = children.get(key.getValue());
                if (child == null) {
                    continue;
                }
                if (!child.comment().isEmpty()) {
                    tuple.getKeyNode().setBlockComments(comments(child.comment()));
                }
                attachComments(child, tuple.getValueNode());
            }
        } else if (yamlNode instanceof SequenceNode sequence) {
            List<ConfigNode> elements = configNode.elements();
            List<Node> values = sequence.getValue();
            for (int i = 0; i < Math.min(elements.size(), values.size()); i++) {
                attachComments(elements.get(i), values.get(i));
            }
        }
    }

    private static List<CommentLine> comments(List<String> lines) {
        List<CommentLine> comments = new ArrayList<>(lines.size());
        for (String line : lines) {
            comments.add(line.isEmpty()
                    ? new CommentLine(Optional.empty(), Optional.empty(), "", CommentType.BLANK_LINE)
                    : new CommentLine(Optional.empty(), Optional.empty(), " " + line, CommentType.BLOCK));
        }
        return comments;
    }

    /**
     * Converts what snakeyaml-engine loaded to values a {@link ConfigNode} accepts.
     */
    private static Object plain(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, element) -> result.put(String.valueOf(key), plain(element)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(element -> result.add(plain(element)));
            return result;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        return String.valueOf(value);
    }
}
