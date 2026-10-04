package dev.emerald.core.simulation;

import dev.emerald.core.event.Provenance;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.job.WorldPort;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reconciles offline results with the real world once it is loaded, a little per tick:
 * <ol>
 *   <li>Consumption first: extract what offline citizens ate or built with. If reality has less
 *       (a player emptied the chest), the shortfall is dropped and queued blocks needing that
 *       material are cancelled.</li>
 *   <li>Production: insert harvested wheat / collected eggs. Anything that does not fit stays pending.</li>
 *   <li>Blocks: place queued blocks a few per tick, skipping spots that are no longer free.</li>
 * </ol>
 * Reality always wins; every adjustment is logged.
 */
public final class Materializer {
    private Materializer() {
    }

    public static int step(VillageState v, WorldPort world, int maxBlocks) {
        PendingMaterialization p = v.pending();
        if (p.isEmpty()) {
            return 0;
        }
        long now = world.gameTime();
        Map<String, Integer> shortfalls = new TreeMap<>();
        for (Map.Entry<Pos, Map<String, Integer>> e : p.deltas().entrySet()) {
            ItemStore store = world.containerAt(e.getKey());
            if (store == null) {
                continue;
            }
            for (Map.Entry<String, Integer> d : e.getValue().entrySet()) {
                String item = d.getKey();
                int delta = d.getValue();
                if (delta < 0) {
                    int taken = store.extract(item, -delta);
                    p.add(e.getKey(), item, -delta);   // clear the whole negative delta
                    int missing = -delta - taken;
                    if (missing > 0) {
                        shortfalls.merge(item, missing, Integer::sum);
                    }
                } else {
                    int put = store.insert(item, delta);
                    p.add(e.getKey(), item, -put);
                }
            }
        }
        shortfalls.forEach((item, missing) -> {
            int dropped = p.dropBlocksUsing(item, missing);
            v.log("RECONCILED_SHORTFALL", now, null, null, v.id(), null, null, Provenance.SYSTEM_OBSERVED,
                    "item", item, "missing", String.valueOf(missing), "blocksCancelled", String.valueOf(dropped));
        });

        int placed = 0;
        for (PendingMaterialization.QueuedBlock q : new ArrayList<>(p.blocks())) {
            if (placed >= maxBlocks) break;
            if (!world.isLoaded(q.pos())) continue;
            p.removeBlock(q);
            if (world.matches(q.pos(), q.block())) continue;
            if (world.placeMaterialized(q.pos(), q.block())) {
                placed++;
                v.construction().get(q.projectId()).ifPresent(proj -> proj.notePlaced());
                v.observations().record(ObservationType.BLOCK_PLACED, world.dimension(), q.pos(), q.block().itemId(),
                        null, now, null);
            } else {
                ItemStore refund = world.warehouse();
                if (refund != null) {
                    refund.insert(q.block().itemId(), 1);
                }
                v.log("MATERIALIZE_SKIPPED", now, q.pos(), null, q.projectId(), null, q.projectId(),
                        Provenance.SYSTEM_OBSERVED, "block", q.block().blockId(), "reason", "spot not free");
            }
        }
        return placed;
    }
}
