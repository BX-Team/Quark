package org.bxteam.quark.update.provider;

import org.bxteam.quark.update.UpdateCheckException;
import org.bxteam.quark.update.internal.Json;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Helpers shared by the providers.
 */
final class Releases {
    static final String JSON = "application/json";

    private Releases() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    static Object parse(String body, URI source) {
        try {
            return Json.parse(body);
        } catch (IllegalArgumentException e) {
            throw new UpdateCheckException("Invalid JSON from " + source, e);
        }
    }

    @Nullable
    static Instant instant(@Nullable Object value) {
        if (!(value instanceof String text)) return null;
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Nullable
    static URI uri(@Nullable String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static String encode(String segment) {
        return java.net.URLEncoder.encode(segment, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
