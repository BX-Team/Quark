package org.bxteam.quark.config.bukkit;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.serdes.SerdesContext;
import org.bxteam.quark.config.serdes.SerializationException;
import org.bxteam.quark.config.serdes.TypeSerializer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Locale;

/**
 * {@link Sound}s as their key ({@code entity.player.levelup}, the {@code minecraft:} namespace is left out),
 * accepting enum names ({@code ENTITY_PLAYER_LEVELUP}) as well.
 *
 * <p>{@code Sound} is an enum up to 1.21.2 and an interface since 1.21.3, so it is only used through
 * {@link Keyed}, {@link Registry} and reflection.</p>
 */
final class SoundSerializer implements TypeSerializer<Sound> {
    @Override
    public Sound deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        String string = node.getString();
        if (string == null || string.isBlank()) {
            throw new SerializationException("expected a sound such as entity.player.levelup");
        }
        string = string.trim();
        Sound sound = string.contains(":") || string.contains(".") ? byKey(string) : byName(string);
        if (sound == null) {
            sound = string.contains(":") || string.contains(".") ? byName(string) : byKey(string);
        }
        if (sound == null) {
            throw new SerializationException("unknown sound '" + string + "'");
        }
        return sound;
    }

    @Override
    public void serialize(@NotNull Type type, @NotNull Sound value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        NamespacedKey key = ((Keyed) value).getKey();
        node.set(NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey() : key.toString());
    }

    @SuppressWarnings("unchecked")
    private static Sound byKey(String string) {
        NamespacedKey key = NamespacedKey.fromString(string.toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        for (String field : new String[]{"SOUNDS", "SOUND_EVENT"}) {
            try {
                Registry<Sound> registry = (Registry<Sound>) Registry.class.getField(field).get(null);
                Sound sound = registry.get(key);
                if (sound != null) {
                    return sound;
                }
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // registry not present in this version
            }
        }
        return null;
    }

    private static Sound byName(String string) {
        try {
            Method valueOf = Sound.class.getMethod("valueOf", String.class);
            return (Sound) valueOf.invoke(null, string.toUpperCase(Locale.ROOT).replace('.', '_').replace('-', '_'));
        } catch (ReflectiveOperationException e) {
            // InvocationTargetException: no constant with this name
            return null;
        }
    }
}
