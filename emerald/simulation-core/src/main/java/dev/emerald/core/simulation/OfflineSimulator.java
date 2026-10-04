package dev.emerald.core.simulation;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.Role;
import dev.emerald.core.construction.Blueprint;
import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.construction.BlueprintLibrary;
import dev.emerald.core.construction.Blueprints;
import dev.emerald.core.construction.ConstructionProject;
import dev.emerald.core.director.VillageDirector;
import dev.emerald.core.economy.EconomyStats;
import dev.emerald.core.event.Provenance;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.research.ExperimentEngine;
import dev.emerald.core.utility.NeedsModel;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

/**
 * Statistical simulation for an unloaded village, in bounded deterministic steps. It never paths
 * entities or edits the world; it produces abstract deltas ({@link PendingMaterialization}) that the
 * {@link Materializer} applies safely once the chunks load again.
 *
 * <p>Rates come only from what the village measured while loaded: counted crop plots, the collector's
 * measured egg output, warehouse stock it saw. Seeds derive from village id + step index, so a run
 * can be replayed exactly.
 */
public final class OfflineSimulator {
    /** Average ticks for a wheat plot to go from replanted to mature. */
    public static final int WHEAT_CYCLE_TICKS = 36000;
    /** Blocks one builder places per offline step. */
    public static final int BLOCKS_PER_STEP = 20;
    /** Citizens eat when hunger reaches this while unloaded. */
    public static final int EAT_THRESHOLD = 40;

    private OfflineSimulator() {
    }

    /** Result of one advance call, for logging and the {@code /emerald offline} command. */
    public record Summary(long fromTick, long toTick, int steps, long skippedTicks, Map<String, Integer> netItems,
                          int blocksQueued, int meals, int hungry) {
    }

    /**
     * Simulates from the village's lastSimulationTime up to {@code now}, in whole steps. Leftover
     * ticks (less than a step) wait for the next call.
     */
    public static Summary advance(VillageState v, long now, SimulationConfig cfg, BlueprintLibrary blueprints) {
        long from = v.lastSimulationTime();
        long skipped = 0;
        if (now - from > cfg.offlineMaxTicks()) {
            skipped = now - from - cfg.offlineMaxTicks();
            from = now - cfg.offlineMaxTicks();
            v.log("OFFLINE_TIME_SKIPPED", now, v.center(), null, v.id(), null, null, Provenance.VILLAGE_DIRECTOR,
                    "ticks", String.valueOf(skipped));
        }
        int step = Math.max(200, cfg.offlineStepTicks());
        Map<String, Integer> net = new TreeMap<>();
        int[] counters = new int[3]; // blocks, meals, hungry
        int steps = 0;
        long t = from;
        while (t + step <= now) {
            stepOnce(v, t, t + step, cfg, blueprints, net, counters);
            t += step;
            steps++;
        }
        v.setLastSimulationTime(t);
        Summary s = new Summary(from, t, steps, skipped, net, counters[0], counters[1], counters[2]);
        if (steps > 0) {
            v.log("OFFLINE_CATCHUP", t, v.center(), null, v.id(), null, null, Provenance.VILLAGE_DIRECTOR,
                    "steps", String.valueOf(steps), "net", net.toString(), "blocks", String.valueOf(counters[0]),
                    "meals", String.valueOf(counters[1]));
        }
        return s;
    }

    static void stepOnce(VillageState v, long a, long b, SimulationConfig cfg, BlueprintLibrary blueprints,
                         Map<String, Integer> net, int[] counters) {
        long stepIndex = a / Math.max(1, b - a);
        Random rng = new Random(v.id().getMostSignificantBits() * 31 + v.id().getLeastSignificantBits() ^ (stepIndex * 0x9E3779B97F4A7C15L));
        Pos whPos = v.warehouse();
        ItemStore wh = whPos == null ? null : new AbstractStore(v.snapshot(), v.pending(), whPos);
        boolean night = isNight(a);

        // Needs and meals.
        for (CitizenRecord c : v.citizens().alive()) {
            NeedsModel.advance(c, a, b, night);
            if (c.hunger() >= EAT_THRESHOLD) {
                String food = wh == null ? null : bestFood(wh);
                if (food != null && wh.extract(food, 1) == 1) {
                    c.setHunger(c.hunger() - NeedsModel.FOOD_VALUES.get(food));
                    v.economy().consumed(food, 1, b);
                    net.merge(food, -1, Integer::sum);
                    counters[1]++;
                } else {
                    counters[2]++;
                }
            }
        }

        // Farming: counted plots, worked only if someone farms.
        long farmers = v.citizens().alive().stream().filter(c -> c.effectiveRole() == Role.FARMER).count();
        if (farmers > 0 && wh != null && v.snapshot().cropPlots() > 0) {
            double expected = v.snapshot().cropPlots() * (double) (b - a) / WHEAT_CYCLE_TICKS;
            int wheat = (int) Math.min(farmers * 48, sample(expected, rng));
            if (wheat > 0) {
                wh.insert(ItemIds.WHEAT, wheat);
                v.economy().produced(ItemIds.WHEAT, wheat, b);
                net.merge(ItemIds.WHEAT, wheat, Integer::sum);
            }
        }

        // Adopted, built collector: measured egg output only.
        double eggsPerDay = v.economy().producedPerDay(ItemIds.EGG);
        Optional<Pos> collectorChest = v.construction().buildings().stream()
                .filter(bd -> Blueprints.isCollector(bd.type()))
                .reduce((first, second) -> second)
                .map(Blueprints::collectorChest);
        if (collectorChest.isPresent() && eggsPerDay > 0) {
            int eggs = sample(eggsPerDay * (b - a) / EconomyStats.DAY, rng);
            if (eggs > 0) {
                v.pending().add(collectorChest.get(), ItemIds.EGG, eggs);
                v.economy().produced(ItemIds.EGG, eggs, b);
                net.merge(ItemIds.EGG, eggs, Integer::sum);
            }
        }

        // Construction from abstract stock, queued for materialisation.
        for (ConstructionProject p : v.construction().active()) {
            Optional<CitizenRecord> builder = v.citizens().get(p.builder()).filter(CitizenRecord::alive);
            Optional<Blueprint> bp = blueprints.get(p.blueprintId());
            if (builder.isEmpty() || bp.isEmpty() || wh == null) continue;
            int placed = 0;
            for (Pos target : v.snapshot().remaining(p.id())) {
                if (placed >= BLOCKS_PER_STEP) break;
                if (v.pending().isQueued(target)) continue;
                BlueprintBlock block = blockAt(bp.get(), p.origin(), target);
                if (block == null || wh.extract(block.itemId(), 1) != 1) continue;
                v.pending().queue(p.id(), target, block);
                v.economy().consumed(block.itemId(), 1, b);
                net.merge(block.itemId(), -1, Integer::sum);
                placed++;
            }
            counters[0] += placed;
        }

        ExperimentEngine.tick(v, b);
        if (wh != null) {
            VillageDirector.evaluateFood(v, b, wh);
        }
        VillageDirector.evaluateHousing(v, b);
        VillageDirector.considerImmigration(v, b, wh, cfg);
    }

    static BlueprintBlock blockAt(Blueprint bp, Pos origin, Pos absolute) {
        for (BlueprintBlock blk : bp.blocks()) {
            if (origin.offset(blk.rel()).equals(absolute)) return blk;
        }
        return null;
    }

    static String bestFood(ItemStore store) {
        String best = null;
        int value = 0;
        for (Map.Entry<String, Integer> e : NeedsModel.FOOD_VALUES.entrySet()) {
            if (store.count(e.getKey()) > 0 && e.getValue() > value) {
                best = e.getKey();
                value = e.getValue();
            }
        }
        return best;
    }

    /** Integer part plus a Bernoulli draw for the fraction: unbiased and deterministic per seed. */
    static int sample(double expected, Random rng) {
        int whole = (int) Math.floor(expected);
        return whole + (rng.nextDouble() < expected - whole ? 1 : 0);
    }

    static boolean isNight(long tick) {
        long t = tick % 24000;
        return t >= 13000 && t < 23000;
    }
}
