package dev.emerald.core.simulation;

import dev.emerald.core.item.ItemStore;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.TreeMap;

/**
 * The warehouse as offline simulation believes it to be: last counted contents plus pending deltas.
 * Writes go to the pending deltas only; reality is changed later by the {@link Materializer}.
 */
public final class AbstractStore implements ItemStore {
    private final VillageSnapshot snapshot;
    private final PendingMaterialization pending;
    private final Pos pos;

    public AbstractStore(VillageSnapshot snapshot, PendingMaterialization pending, Pos pos) {
        this.snapshot = snapshot;
        this.pending = pending;
        this.pos = pos;
    }

    @Override
    public int count(String itemId) {
        return Math.max(0, snapshot.warehouseCount(itemId) + pending.delta(pos, itemId));
    }

    @Override
    public int extract(String itemId, int amount) {
        int n = Math.min(amount, count(itemId));
        pending.add(pos, itemId, -n);
        return n;
    }

    @Override
    public int insert(String itemId, int amount) {
        pending.add(pos, itemId, amount);
        return amount;
    }

    @Override
    public Map<String, Integer> contents() {
        Map<String, Integer> out = new TreeMap<>(snapshot.warehouse());
        pending.deltas().getOrDefault(pos, Map.of()).forEach((k, v) -> out.merge(k, v, Integer::sum));
        out.values().removeIf(v -> v <= 0);
        return out;
    }
}
