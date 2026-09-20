package org.bxteam.quark.config.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.serdes.SerdesContext;
import org.bxteam.quark.config.serdes.SerdesRegistry;
import org.bxteam.quark.config.serdes.SerializationException;
import org.bxteam.quark.config.serdes.TypeSerializer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * Adventure {@link Component}s as MiniMessage strings. Only loaded when MiniMessage is on the class path.
 */
final class ComponentSerializer implements TypeSerializer<Component> {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    static void register(SerdesRegistry registry) {
        registry.registerHierarchy(Component.class, new ComponentSerializer());
    }

    @Override
    public Component deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        String string = node.getString();
        if (string == null) {
            throw new SerializationException("expected a MiniMessage string");
        }
        try {
            return miniMessage.deserialize(string);
        } catch (RuntimeException e) {
            throw new SerializationException("invalid MiniMessage '" + string + "': " + e.getMessage(), e);
        }
    }

    @Override
    public void serialize(@NotNull Type type, @NotNull Component value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        node.set(miniMessage.serialize(value));
    }
}
