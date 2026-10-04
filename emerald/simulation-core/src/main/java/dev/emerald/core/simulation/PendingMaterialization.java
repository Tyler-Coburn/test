package dev.emerald.core.simulation;

import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.data.Data;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Abstract results of offline simulation waiting to become real: item deltas per container, and
 * blocks the builder "placed" while unloaded. Applied gradually and safely once chunks load;
 * whatever reality cannot honour is dropped and logged.
 */
public final class PendingMaterialization {
    public record QueuedBlock(UUID projectId, Pos pos, BlueprintBlock block) {
    }

    private final Map<Pos, Map<String, Integer>> deltas = new LinkedHashMap<>();
    private final List<QueuedBlock> blocks = new ArrayList<>();

    public void add(Pos container, String item, int delta) {
        if (delta == 0) return;
        Map<String, Integer> m = deltas.computeIfAbsent(container, p -> new TreeMap<>());
        int v = m.getOrDefault(item, 0) + delta;
        if (v == 0) m.remove(item); else m.put(item, v);
        if (m.isEmpty()) deltas.remove(container);
    }

    public int delta(Pos container, String item) {
        Map<String, Integer> m = deltas.get(container);
        return m == null ? 0 : m.getOrDefault(item, 0);
    }

    public Map<Pos, Map<String, Integer>> deltas() {
        Map<Pos, Map<String, Integer>> copy = new LinkedHashMap<>();
        deltas.forEach((k, v) -> copy.put(k, Map.copyOf(v)));
        return copy;
    }

    public void queue(UUID projectId, Pos pos, BlueprintBlock block) {
        blocks.add(new QueuedBlock(projectId, pos, block));
    }

    public List<QueuedBlock> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public boolean isQueued(Pos pos) {
        return blocks.stream().anyMatch(b -> b.pos().equals(pos));
    }

    public boolean removeBlock(QueuedBlock b) {
        return blocks.remove(b);
    }

    /** Drops up to {@code n} queued blocks that consume {@code item}; returns how many were dropped. */
    public int dropBlocksUsing(String item, int n) {
        int dropped = 0;
        for (Iterator<QueuedBlock> it = blocks.iterator(); it.hasNext() && dropped < n; ) {
            if (it.next().block().itemId().equals(item)) {
                it.remove();
                dropped++;
            }
        }
        return dropped;
    }

    public boolean isEmpty() {
        return deltas.isEmpty() && blocks.isEmpty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = Data.map();
        List<Object> d = new ArrayList<>();
        deltas.forEach((pos, items) -> {
            Map<String, Object> e = Data.map();
            e.put("pos", pos.toMap());
            e.put("items", new LinkedHashMap<String, Object>(items));
            d.add(e);
        });
        m.put("deltas", d);
        m.put("blocks", blocks.stream().<Object>map(b -> {
            Map<String, Object> e = Data.map();
            Data.putUuid(e, "project", b.projectId());
            e.put("pos", b.pos().toMap());
            e.put("block", b.block().blockId());
            e.put("item", b.block().itemId());
            e.put("props", new LinkedHashMap<String, Object>(b.block().properties()));
            return e;
        }).toList());
        return m;
    }

    public void loadFrom(Map<String, Object> m) {
        deltas.clear();
        blocks.clear();
        if (m == null) return;
        for (Map<String, Object> e : Data.maps(m, "deltas")) {
            Pos pos = Pos.fromMap(Data.sub(e, "pos"));
            Map<String, Object> items = Data.subOrNull(e, "items");
            if (items != null) items.forEach((k, v) -> add(pos, k, ((Number) v).intValue()));
        }
        for (Map<String, Object> e : Data.maps(m, "blocks")) {
            Map<String, String> props = new LinkedHashMap<>();
            Map<String, Object> raw = Data.subOrNull(e, "props");
            if (raw != null) raw.forEach((k, v) -> props.put(k, String.valueOf(v)));
            Pos pos = Pos.fromMap(Data.sub(e, "pos"));
            blocks.add(new QueuedBlock(Data.uuidOrNull(e, "project"), pos,
                    new BlueprintBlock(new Pos(0, 0, 0), Data.str(e, "block"), props, Data.str(e, "item"))));
        }
    }
}
