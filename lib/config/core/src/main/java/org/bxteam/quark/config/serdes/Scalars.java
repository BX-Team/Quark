package org.bxteam.quark.config.serdes;

import org.bxteam.quark.config.ConfigNode;

/**
 * Helpers for serializers of scalar types.
 */
final class Scalars {
    private Scalars() {
    }

    static String string(ConfigNode node) {
        if (!node.isScalar()) {
            throw new SerializationException("expected a single value, found " + describe(node));
        }
        return String.valueOf(node.scalar());
    }

    static String describe(ConfigNode node) {
        if (node.isMap()) return "a section";
        if (node.isList()) return "a list";
        if (node.isNull()) return "nothing";
        return "'" + node.scalar() + "'";
    }
}
