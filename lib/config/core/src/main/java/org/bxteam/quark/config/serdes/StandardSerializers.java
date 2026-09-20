package org.bxteam.quark.config.serdes;

import org.bxteam.quark.config.ConfigNode;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The serializers of {@link SerdesRegistry#create()}.
 */
final class StandardSerializers {
    private StandardSerializers() {
    }

    static void registerAll(SerdesRegistry registry) {
        // registered from the most generic to the most specific: later registrations are matched first
        registry.register(type -> Types.raw(type) == Object.class, new PlainSerializer());
        registry.register(type -> Map.class.isAssignableFrom(Types.raw(type)), new MapSerializer());
        registry.register(type -> Collection.class.isAssignableFrom(Types.raw(type)) || Types.raw(type) == Iterable.class,
                new CollectionSerializer());
        registry.register(type -> type instanceof GenericArrayType || Types.raw(type).isArray(), new ArraySerializer());
        registry.register(type -> Types.raw(type).isEnum() || (Types.raw(type).getSuperclass() != null && Types.raw(type).getSuperclass().isEnum()),
                new EnumSerializer());
        registry.register(type -> Types.raw(type) == Optional.class, new OptionalSerializer());
        registry.register(ConfigNode.class, new NodeSerializer());

        registry.register(String.class, scalar(string -> string, String::valueOf));
        registry.register(Character.class, scalar(string -> {
            if (string.length() != 1) {
                throw new SerializationException("expected a single character, found '" + string + "'");
            }
            return string.charAt(0);
        }, String::valueOf));
        registry.register(Boolean.class, new BooleanSerializer());
        registry.register(Integer.class, new NumberSerializer<>(Integer.class, BigDecimal::intValueExact, Number::intValue));
        registry.register(Long.class, new NumberSerializer<>(Long.class, BigDecimal::longValueExact, Number::longValue));
        registry.register(Short.class, new NumberSerializer<>(Short.class, BigDecimal::shortValueExact, Number::shortValue));
        registry.register(Byte.class, new NumberSerializer<>(Byte.class, BigDecimal::byteValueExact, Number::byteValue));
        registry.register(Double.class, new NumberSerializer<>(Double.class, BigDecimal::doubleValue, Number::doubleValue));
        registry.register(Float.class, new NumberSerializer<>(Float.class, BigDecimal::floatValue, Number::floatValue));
        registry.register(BigInteger.class, new NumberSerializer<>(BigInteger.class, BigDecimal::toBigIntegerExact,
                number -> new BigDecimal(number.toString()).toBigIntegerExact()));
        registry.register(BigDecimal.class, new TypeSerializer<BigDecimal>() {
            @Override
            public BigDecimal deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                return parseDecimal(Scalars.string(node), type);
            }

            @Override
            public void serialize(@NotNull Type type, @NotNull BigDecimal value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                // as a string: YAML floats would lose precision
                node.set(value.toPlainString());
            }
        });

        registry.register(UUID.class, TypeSerializer.ofString(UUID::fromString, UUID::toString));
        registry.register(Duration.class, scalar(Durations::parse, Durations::format));
        registry.register(Instant.class, new TypeSerializer<Instant>() {
            @Override
            public Instant deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                if (node.scalar() instanceof Number number) {
                    return Instant.ofEpochMilli(number.longValue());
                }
                String string = Scalars.string(node);
                try {
                    return Instant.parse(string);
                } catch (DateTimeParseException e) {
                    throw new SerializationException("'" + string + "' is not an ISO-8601 instant such as 2025-01-31T12:00:00Z", e);
                }
            }

            @Override
            public void serialize(@NotNull Type type, @NotNull Instant value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                node.set(value.toString());
            }
        });
    }

    private static <T> TypeSerializer<T> scalar(Function<String, T> parse, Function<T, String> format) {
        return new TypeSerializer<>() {
            @Override
            public T deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                return parse.apply(Scalars.string(node));
            }

            @Override
            public void serialize(@NotNull Type type, @NotNull T value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                node.set(format.apply(value));
            }
        };
    }

    private static BigDecimal parseDecimal(String string, Type type) {
        try {
            return new BigDecimal(string.trim().replace("_", ""));
        } catch (NumberFormatException e) {
            throw new SerializationException("'" + string + "' is not a number (" + Types.name(type) + ")", e);
        }
    }

    private static final class BooleanSerializer implements TypeSerializer<Boolean> {
        @Override
        public Boolean deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            if (node.scalar() instanceof Boolean bool) {
                return bool;
            }
            String string = Scalars.string(node).trim().toLowerCase(Locale.ROOT);
            return switch (string) {
                case "true", "yes", "on" -> true;
                case "false", "no", "off" -> false;
                default -> throw new SerializationException("expected true or false, found '" + string + "'");
            };
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull Boolean value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            node.set(value);
        }
    }

    private static final class NumberSerializer<N extends Number> implements TypeSerializer<N> {
        private final Class<N> type;
        private final Function<BigDecimal, N> exact;
        private final Function<Number, N> convert;

        NumberSerializer(Class<N> type, Function<BigDecimal, N> exact, Function<Number, N> convert) {
            this.type = type;
            this.exact = exact;
            this.convert = convert;
        }

        @Override
        public N deserialize(@NotNull Type requested, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            Object scalar = node.scalar();
            if ((type == Double.class || type == Float.class) && scalar instanceof Number number) {
                return convert.apply(number);
            }
            String string = Scalars.string(node);
            if (type == Double.class || type == Float.class) {
                switch (string.trim().toLowerCase(Locale.ROOT)) {
                    case ".nan", "nan" -> { return convert.apply(Double.NaN); }
                    case ".inf", "+.inf", "infinity" -> { return convert.apply(Double.POSITIVE_INFINITY); }
                    case "-.inf", "-infinity" -> { return convert.apply(Double.NEGATIVE_INFINITY); }
                    default -> { }
                }
            }
            BigDecimal decimal = scalar instanceof BigDecimal value ? value : parseDecimal(string, requested);
            try {
                return exact.apply(decimal);
            } catch (ArithmeticException e) {
                throw new SerializationException("'" + string + "' does not fit " + type.getSimpleName()
                        + (decimal.scale() > 0 && decimal.stripTrailingZeros().scale() > 0 ? ", expected a whole number" : ""), e);
            }
        }

        @Override
        public void serialize(@NotNull Type requested, @NotNull N value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            node.set(value instanceof BigInteger ? value : convert.apply(value));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class EnumSerializer implements TypeSerializer<Enum<?>> {
        @Override
        public Enum<?> deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            Class<?> raw = Types.raw(type);
            Class<? extends Enum> enumType = (Class<? extends Enum>) (raw.isEnum() ? raw : raw.getSuperclass());
            String string = Scalars.string(node).trim();
            Enum<?>[] constants = enumType.getEnumConstants();
            for (Enum<?> constant : constants) {
                if (constant.name().equals(string)) {
                    return constant;
                }
            }
            String normalized = string.replace('-', '_').replace(' ', '_');
            for (Enum<?> constant : constants) {
                if (constant.name().equalsIgnoreCase(normalized)) {
                    return constant;
                }
            }
            String allowed = constants.length <= 30
                    ? ", expected one of " + Arrays.stream(constants).map(Enum::name).collect(Collectors.joining(", "))
                    : "";
            throw new SerializationException("'" + string + "' is not a valid " + enumType.getSimpleName() + allowed);
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull Enum<?> value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            node.set(value.name());
        }
    }

    private static final class OptionalSerializer implements TypeSerializer<Optional<?>> {
        @Override
        public Optional<?> deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            return Optional.ofNullable(context.deserialize(node, Types.argument(type, 0)));
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull Optional<?> value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            context.serialize(value.orElse(null), Types.argument(type, 0), node);
        }

        @Override
        public Optional<?> emptyValue(@NotNull Type type) {
            return Optional.empty();
        }
    }

    private static final class NodeSerializer implements TypeSerializer<ConfigNode> {
        @Override
        public ConfigNode deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            return node.copy();
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull ConfigNode value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            node.set(value);
        }
    }

    /**
     * {@code Object}: plain maps, lists and scalars when reading, the serializer of the runtime class when writing.
     */
    private static final class PlainSerializer implements TypeSerializer<Object> {
        @Override
        public Object deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            return node.raw();
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull Object value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            if (value.getClass() == Object.class) {
                throw new SerializationException("cannot write a plain java.lang.Object");
            }
            context.serialize(value, value.getClass(), node);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class CollectionSerializer implements TypeSerializer<Collection<?>> {
        @Override
        public Collection<?> deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            if (node.isMap()) {
                throw new SerializationException("expected a list, found a section");
            }
            Type elementType = Types.argument(type, 0);
            Collection collection = create(Types.raw(type), elementType);
            if (node.isList()) {
                for (ConfigNode element : node.elements()) {
                    collection.add(context.deserialize(element, elementType));
                }
            } else {
                // a single value where a list is expected
                collection.add(context.deserialize(node, elementType));
            }
            return collection;
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull Collection<?> value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            Type elementType = Types.argument(type, 0);
            node.setList();
            for (Object element : value) {
                context.serialize(element, elementType, node.appendElement());
            }
        }

        private static Collection<?> create(Class<?> raw, Type elementType) {
            if (EnumSet.class.isAssignableFrom(raw)) {
                return EnumSet.noneOf((Class<? extends Enum>) Types.raw(elementType));
            }
            if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
                if (NavigableSet.class.isAssignableFrom(raw) || SortedSet.class.isAssignableFrom(raw)) return new TreeSet<>();
                if (Set.class.isAssignableFrom(raw)) return new LinkedHashSet<>();
                if (Queue.class.isAssignableFrom(raw)) return new ArrayDeque<>();
                return new ArrayList<>();
            }
            return (Collection<?>) instantiate(raw);
        }
    }

    private static final class ArraySerializer implements TypeSerializer<Object> {
        @Override
        public Object deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            if (node.isMap()) {
                throw new SerializationException("expected a list, found a section");
            }
            Type componentType = Types.component(type);
            List<ConfigNode> elements = node.isList() ? node.elements() : List.of(node);
            Object array = Array.newInstance(Types.raw(componentType), elements.size());
            for (int i = 0; i < elements.size(); i++) {
                Object element = context.deserialize(elements.get(i), componentType);
                if (element == null && Types.raw(componentType).isPrimitive()) {
                    throw new SerializationException("null is not allowed in an array of " + Types.raw(componentType).getName());
                }
                Array.set(array, i, element);
            }
            return array;
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull Object value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            Type componentType = Types.component(type);
            node.setList();
            for (int i = 0, length = Array.getLength(value); i < length; i++) {
                context.serialize(Array.get(value, i), componentType, node.appendElement());
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class MapSerializer implements TypeSerializer<Map<?, ?>> {
        @Override
        public Map<?, ?> deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            if (!node.isMap()) {
                throw new SerializationException("expected a section, found " + Scalars.describe(node));
            }
            Type keyType = Types.argument(type, 0);
            Type valueType = Types.argument(type, 1);
            Map map = create(Types.raw(type), keyType);
            node.children().forEach((key, child) -> {
                Object mapKey = keyType == Object.class || keyType == String.class
                        ? key : context.deserialize(ConfigNode.of(key), keyType);
                map.put(mapKey, context.deserialize(child, valueType));
            });
            return map;
        }

        @Override
        public void serialize(@NotNull Type type, @NotNull Map<?, ?> value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
            Type keyType = Types.argument(type, 0);
            Type valueType = Types.argument(type, 1);
            node.setMap();
            value.forEach((key, element) -> {
                if (key == null) {
                    throw new SerializationException("null keys are not supported");
                }
                ConfigNode keyNode = ConfigNode.of(null);
                context.serialize(key, keyType, keyNode);
                if (!keyNode.isScalar()) {
                    throw new SerializationException("map key " + key + " must be written as a single value");
                }
                context.serialize(element, valueType, node.node(String.valueOf(keyNode.scalar())));
            });
        }

        private static Map<?, ?> create(Class<?> raw, Type keyType) {
            if (EnumMap.class.isAssignableFrom(raw)) {
                return new EnumMap(Types.raw(keyType));
            }
            if (raw.isInterface() || Modifier.isAbstract(raw.getModifiers())) {
                if (NavigableMap.class.isAssignableFrom(raw) || SortedMap.class.isAssignableFrom(raw)) return new TreeMap<>();
                if (ConcurrentMap.class.isAssignableFrom(raw)) return new ConcurrentHashMap<>();
                return new LinkedHashMap<>();
            }
            return (Map<?, ?>) instantiate(raw);
        }
    }

    private static Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new SerializationException("cannot create " + type.getName() + ": " + e, e);
        }
    }

    /**
     * Durations as {@code 1h30m}, {@code 500ms}, {@code 2d} or ISO-8601 ({@code PT1H30M}). A bare number is seconds.
     */
    static final class Durations {
        private static final Pattern PART = Pattern.compile("(\\d+)\\s*(ms|s|m|h|d)", Pattern.CASE_INSENSITIVE);

        private Durations() {
        }

        static Duration parse(String text) {
            String string = text.trim();
            if (string.isEmpty()) {
                throw new SerializationException("expected a duration such as 30s, 5m or 1h30m, found nothing");
            }
            try {
                if (string.matches("-?\\d+")) {
                    return Duration.ofSeconds(Long.parseLong(string));
                }
                if (string.toUpperCase(Locale.ROOT).startsWith("P") || string.toUpperCase(Locale.ROOT).startsWith("-P")) {
                    return Duration.parse(string.toUpperCase(Locale.ROOT));
                }
            } catch (NumberFormatException | DateTimeParseException e) {
                throw new SerializationException("'" + text + "' is not a valid duration", e);
            }

            boolean negative = string.startsWith("-");
            String body = negative ? string.substring(1) : string;
            Matcher matcher = PART.matcher(body);
            Duration result = Duration.ZERO;
            int end = 0;
            while (matcher.find()) {
                if (!body.substring(end, matcher.start()).isBlank()) {
                    break;
                }
                long amount = Long.parseLong(matcher.group(1));
                result = result.plus(switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                    case "ms" -> Duration.ofMillis(amount);
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    case "h" -> Duration.ofHours(amount);
                    default -> Duration.ofDays(amount);
                });
                end = matcher.end();
            }
            if (end == 0 || !body.substring(end).isBlank()) {
                throw new SerializationException("'" + text + "' is not a valid duration, expected something like 30s, 5m, 1h30m or 500ms");
            }
            return negative ? result.negated() : result;
        }

        static String format(Duration duration) {
            if (duration.isZero()) {
                return "0s";
            }
            if (duration.getNano() % 1_000_000 != 0) {
                return duration.toString();
            }
            boolean negative = duration.isNegative();
            Duration rest = duration.abs();
            StringBuilder builder = new StringBuilder(negative ? "-" : "");
            long days = rest.toDays();
            if (days > 0) builder.append(days).append('d');
            rest = rest.minusDays(days);
            long hours = rest.toHours();
            if (hours > 0) builder.append(hours).append('h');
            rest = rest.minusHours(hours);
            long minutes = rest.toMinutes();
            if (minutes > 0) builder.append(minutes).append('m');
            rest = rest.minusMinutes(minutes);
            long seconds = rest.getSeconds();
            if (seconds > 0) builder.append(seconds).append('s');
            long millis = rest.getNano() / 1_000_000;
            if (millis > 0) builder.append(millis).append("ms");
            return builder.toString();
        }
    }
}
