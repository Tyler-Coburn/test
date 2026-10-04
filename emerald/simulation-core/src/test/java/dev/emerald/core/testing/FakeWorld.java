package dev.emerald.core.testing;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.construction.BlueprintLibrary;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.job.BodyPort;
import dev.emerald.core.job.CitizenScheduler;
import dev.emerald.core.job.RoutineContext;
import dev.emerald.core.job.WorldPort;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.village.VillageSimulator;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory stand-in for the Minecraft adapter: blocks, wheat crops, a warehouse and bodies that
 * walk one block per step. Lets the deterministic loop run end to end in plain JUnit.
 */
public final class FakeWorld implements WorldPort {
    public static final String DIM = "minecraft:overworld";

    public long time = 1000;
    public final Map<Pos, String> blocks = new HashMap<>();
    public final Map<Pos, Integer> crops = new LinkedHashMap<>();
    public ItemCounter warehouse;
    public final Map<UUID, FakeBody> bodies = new HashMap<>();
    public final CitizenScheduler scheduler = new CitizenScheduler();
    public final BlueprintLibrary blueprints = BlueprintLibrary.withDefaults();
    public SimulationConfig config = SimulationConfig.DEFAULT;

    public FakeBody spawnBody(CitizenRecord c, Pos at) {
        FakeBody b = new FakeBody(at);
        bodies.put(c.id(), b);
        c.bindBody(UUID.randomUUID());
        return b;
    }

    public void matureWheat(Pos p) {
        crops.put(p, 7);
        blocks.put(p, "minecraft:wheat");
    }

    /** One step for every loaded, living citizen, then the village tick every 20 ticks. */
    public void step(VillageState v, HypothesisSource hypotheses) {
        time += 10;
        for (CitizenRecord c : v.citizens().alive()) {
            FakeBody body = bodies.get(c.id());
            if (body != null) {
                scheduler.tick(new RoutineContext(v, c, body, this, config, blueprints), false, false);
            }
        }
        if (time % 20 == 0) {
            VillageSimulator.tick(v, this, config, hypotheses == null ? HypothesisSource.FALLBACK : hypotheses, blueprints);
        }
    }

    public void run(VillageState v, int steps) {
        for (int i = 0; i < steps; i++) {
            step(v, HypothesisSource.FALLBACK);
        }
    }

    @Override public long gameTime() { return time; }
    @Override public String dimension() { return DIM; }
    @Override public ItemStore warehouse() { return warehouse; }

    public final Map<Pos, ItemCounter> containers = new HashMap<>();

    @Override
    public ItemStore containerAt(Pos pos) {
        return containers.get(pos);
    }

    @Override
    public int countCropPlots(Pos center, int radius) {
        return crops.size();
    }

    @Override
    public int countAnimals(dev.emerald.core.world.Box region, String entityId) {
        return 0;
    }

    @Override
    public boolean placeMaterialized(Pos pos, BlueprintBlock block) {
        if (blocks.containsKey(pos)) return false;
        blocks.put(pos, block.blockId());
        return true;
    }

    /** Sites returned in order; empty list = no free site. */
    public final java.util.List<Pos> sites = new java.util.ArrayList<>();

    @Override
    public Optional<Pos> findBuildSite(Pos center, int sizeX, int sizeZ, int minDist, int maxDist,
                                       java.util.List<dev.emerald.core.world.Box> avoid) {
        return sites.isEmpty() ? Optional.empty() : Optional.of(sites.remove(0));
    }

    @Override
    public Optional<Pos> findMatureCrop(Pos center, int radius) {
        return crops.entrySet().stream()
                .filter(e -> e.getValue() >= 7 && e.getKey().distSq(center) <= (long) radius * radius)
                .map(Map.Entry::getKey)
                .min(Comparator.comparingLong(p -> p.distSq(center)));
    }

    @Override
    public Map<String, Integer> harvestCrop(Pos pos, ItemStore into) {
        Integer age = crops.get(pos);
        if (age == null || age < 7) {
            return Map.of();
        }
        crops.remove(pos);
        blocks.remove(pos);
        into.insert(ItemIds.WHEAT, 1);
        into.insert(ItemIds.WHEAT_SEEDS, 2);
        return Map.of(ItemIds.WHEAT, 1, ItemIds.WHEAT_SEEDS, 2);
    }

    @Override
    public boolean replant(Pos pos, ItemStore from) {
        if (from.extract(ItemIds.WHEAT_SEEDS, 1) == 1) {
            crops.put(pos, 0);
            blocks.put(pos, "minecraft:wheat");
            return true;
        }
        return false;
    }

    @Override
    public boolean matches(Pos pos, BlueprintBlock block) {
        return block.blockId().equals(blocks.get(pos));
    }

    @Override
    public boolean isLoaded(Pos pos) {
        return true;
    }

    @Override
    public boolean place(Pos pos, BlueprintBlock block, ItemStore from) {
        if (from.extract(block.itemId(), 1) != 1) {
            return false;
        }
        blocks.put(pos, block.blockId());
        return true;
    }

    @Override
    public Optional<Pos> bodyPosition(UUID citizenId) {
        return Optional.ofNullable(bodies.get(citizenId)).map(FakeBody::position);
    }

    /** Walks one block per step along the largest axis. */
    public static final class FakeBody implements BodyPort {
        public Pos pos;

        FakeBody(Pos pos) {
            this.pos = pos;
        }

        @Override
        public Pos position() {
            return pos;
        }

        @Override
        public boolean moveTo(Pos target, double reach) {
            if (pos.distSq(target) <= reach * reach) {
                return true;
            }
            int dx = Integer.signum(target.x() - pos.x());
            int dy = Integer.signum(target.y() - pos.y());
            int dz = Integer.signum(target.z() - pos.z());
            pos = pos.offset(dx, dy, dz);
            return pos.distSq(target) <= reach * reach;
        }
    }
}
