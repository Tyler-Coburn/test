package dev.emerald.core.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Typed access to the neutral persistence format used by simulation-core.
 *
 * <p>Domain objects serialise to {@code Map<String, Object>} trees whose leaves are
 * String, Integer, Long, Boolean (or any Number, since NBT stores booleans as bytes),
 * nested maps and homogeneous lists. The NeoForge layer converts these trees to and from
 * CompoundTag, so the core never touches Minecraft classes but still round-trips in tests.
 */
public final class Data {
    private Data() {
    }

    public static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }

    public static String str(Map<String, Object> m, String key) {
        if (m.get(key) instanceof String s) {
            return s;
        }
        throw new DataException("Missing string field '" + key + "'");
    }

    public static String strOr(Map<String, Object> m, String key, String fallback) {
        return m.get(key) instanceof String s ? s : fallback;
    }

    public static int i(Map<String, Object> m, String key) {
        if (m.get(key) instanceof Number n) {
            return n.intValue();
        }
        throw new DataException("Missing int field '" + key + "'");
    }

    public static int iOr(Map<String, Object> m, String key, int fallback) {
        return m.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    public static long l(Map<String, Object> m, String key) {
        if (m.get(key) instanceof Number n) {
            return n.longValue();
        }
        throw new DataException("Missing long field '" + key + "'");
    }

    public static long lOr(Map<String, Object> m, String key, long fallback) {
        return m.get(key) instanceof Number n ? n.longValue() : fallback;
    }

    public static boolean b(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v instanceof Boolean bool) {
            return bool;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        return fallback;
    }

    public static UUID uuid(Map<String, Object> m, String key) {
        try {
            return UUID.fromString(str(m, key));
        } catch (IllegalArgumentException e) {
            throw new DataException("Bad UUID in field '" + key + "'");
        }
    }

    public static UUID uuidOrNull(Map<String, Object> m, String key) {
        return m.get(key) instanceof String s && !s.isEmpty() ? UUID.fromString(s) : null;
    }

    public static void putUuid(Map<String, Object> m, String key, UUID value) {
        if (value != null) {
            m.put(key, value.toString());
        }
    }

    public static <E extends Enum<E>> E enumOr(Map<String, Object> m, String key, Class<E> type, E fallback) {
        if (!(m.get(key) instanceof String s)) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static <E extends Enum<E>> E enumOf(Map<String, Object> m, String key, Class<E> type) {
        String s = str(m, key);
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException e) {
            throw new DataException("Unknown " + type.getSimpleName() + " '" + s + "' in field '" + key + "'");
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> sub(Map<String, Object> m, String key) {
        if (m.get(key) instanceof Map<?, ?> sub) {
            return (Map<String, Object>) sub;
        }
        throw new DataException("Missing compound field '" + key + "'");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> subOrNull(Map<String, Object> m, String key) {
        return m.get(key) instanceof Map<?, ?> sub ? (Map<String, Object>) sub : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> maps(Map<String, Object> m, String key) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (m.get(key) instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> entry) {
                    out.add((Map<String, Object>) entry);
                }
            }
        }
        return out;
    }

    public static List<String> strings(Map<String, Object> m, String key) {
        List<String> out = new ArrayList<>();
        if (m.get(key) instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public static List<Integer> ints(Map<String, Object> m, String key) {
        List<Integer> out = new ArrayList<>();
        if (m.get(key) instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) {
                    out.add(n.intValue());
                }
            }
        }
        return out;
    }
}
