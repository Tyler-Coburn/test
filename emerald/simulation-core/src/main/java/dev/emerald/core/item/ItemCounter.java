package dev.emerald.core.item;

import dev.emerald.core.data.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Simple counted inventory with an optional total capacity. Used for carried items and test fakes. */
public final class ItemCounter implements ItemStore {
    private final Map<String, Integer> counts = new TreeMap<>();
    private final int capacity;

    public ItemCounter() {
        this(Integer.MAX_VALUE);
    }

    public ItemCounter(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public int count(String itemId) {
        return counts.getOrDefault(itemId, 0);
    }

    @Override
    public int extract(String itemId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int have = count(itemId);
        int taken = Math.min(have, amount);
        if (have - taken == 0) {
            counts.remove(itemId);
        } else {
            counts.put(itemId, have - taken);
        }
        return taken;
    }

    @Override
    public int insert(String itemId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int room = capacity == Integer.MAX_VALUE ? amount : Math.max(0, capacity - total());
        int added = Math.min(room, amount);
        if (added > 0) {
            counts.merge(itemId, added, Integer::sum);
        }
        return added;
    }

    @Override
    public Map<String, Integer> contents() {
        return Collections.unmodifiableMap(new TreeMap<>(counts));
    }

    public int total() {
        int sum = 0;
        for (int c : counts.values()) {
            sum += c;
        }
        return sum;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    public void clear() {
        counts.clear();
    }

    public List<Object> toList() {
        return counts.entrySet().stream().<Object>map(e -> {
            Map<String, Object> m = Data.map();
            m.put("item", e.getKey());
            m.put("count", e.getValue());
            return m;
        }).toList();
    }

    public void loadFrom(List<Map<String, Object>> entries) {
        counts.clear();
        for (Map<String, Object> m : entries) {
            int c = Data.i(m, "count");
            if (c > 0) {
                counts.put(Data.str(m, "item"), c);
            }
        }
    }
}
