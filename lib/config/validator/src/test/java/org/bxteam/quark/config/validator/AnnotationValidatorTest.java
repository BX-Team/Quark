package org.bxteam.quark.config.validator;

import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.serdes.SerdesContext;
import org.bxteam.quark.config.serdes.SerdesRegistry;
import org.bxteam.quark.config.validation.ConfigValidator;
import org.bxteam.quark.config.validation.Violation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationValidatorTest {
    public static final class Even implements Predicate<Integer> {
        @Override
        public boolean test(Integer value) {
            return value == null || value % 2 == 0;
        }
    }

    static class Settings {
        @NotNull
        public String name = "default";

        @Min(1) @Max(65535)
        public int port = 25565;

        @Min(0.5)
        public double ratio = 1;

        @Pattern("[a-z_]+")
        public List<String> worlds = new ArrayList<>(List.of("world"));

        @Check(value = Even.class, message = "must be even")
        public int slots = 4;

        public Nested nested = new Nested();
    }

    static class Nested {
        @Max(10)
        public long limit = 5;
    }

    private List<Violation> validate(Map<String, Object> values) {
        SerdesContext context = new SerdesContext(SerdesRegistry.create(), List.of(new AnnotationValidator()), new IdentityHashMap<>());
        context.deserializeInto(ConfigNode.of(values), new Settings());
        return context.violations();
    }

    @Test
    void validValuesPass() {
        assertEquals(List.of(), validate(Map.of("port", 80, "worlds", List.of("lobby", "pvp_arena"))));
    }

    @Test
    void everyViolationIsReportedWithItsPath() {
        Map<String, Object> values = new java.util.HashMap<>();
        values.put("name", null);
        values.put("port", 70000);
        values.put("ratio", 0.1);
        values.put("worlds", List.of("ok", "Bad World"));
        values.put("slots", 3);
        values.put("nested", Map.of("limit", 11));

        List<String> violations = validate(values).stream().map(Violation::toString).toList();

        assertEquals(List.of(
                "name: must be set",
                "port: must be at most 65535 (was 70000)",
                "ratio: must be at least 0.5 (was 0.1)",
                "worlds: must match [a-z_]+ (was 'Bad World')",
                "slots: must be even (was 3)",
                "nested.limit: must be at most 10 (was 11)"
        ), violations);
    }

    @Test
    void registeredWithServiceLoader() {
        List<ConfigValidator> validators = new ArrayList<>();
        ServiceLoader.load(ConfigValidator.class).forEach(validators::add);

        assertEquals(1, validators.size());
        assertInstanceOf(AnnotationValidator.class, validators.get(0));
        assertTrue(validators.get(0).validate(fieldOf("port"), 0).get(0).startsWith("must be at least 1"));
    }

    private static java.lang.reflect.Field fieldOf(String name) {
        try {
            return Settings.class.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }
}
