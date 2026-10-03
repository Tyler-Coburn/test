package dev.emerald.core.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Simulates an NBT save/load: deep copy, booleans become bytes (as ByteTag does). */
public final class TestData {
    private TestData() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T reserialize(T value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put((String) k, reserialize(v)));
            return (T) out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(v -> out.add(reserialize(v)));
            return (T) out;
        }
        if (value instanceof Boolean b) {
            return (T) Byte.valueOf((byte) (b ? 1 : 0));
        }
        return value;
    }
}
