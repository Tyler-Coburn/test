package dev.emerald.core.sandbox;

import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.construction.BlueprintLibrary;
import dev.emerald.core.item.ItemCounter;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.job.BodyPort;
import dev.emerald.core.job.CitizenScheduler;
import dev.emerald.core.job.RoutineContext;
import dev.emerald.core.job.TaskType;
import dev.emerald.core.job.WorldPort;
import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.village.VillageSimulator;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.ItemIds;
import dev.emerald.core.world.Pos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * A small deterministic stand-in for the Minecraft server: a flat world with blocks, containers,
 * wheat that grows, chickens that lay eggs, hoppers that pull eggs into chests below them,
 * monsters, and citizen bodies that walk one block per step. It plays the role of the NeoForge
 * adapters (WorldPort, body layer, observation adapter), so the whole simulation can run end to end
 * without Minecraft. Physical facts it reports go through the same ObservationBus.
 */
public final class SandboxWorld implements WorldPort {
    public static final String DIM = "minecraft:overworld";
    public static final int STEP_TICKS = 10;
    /** Average ticks between eggs for one chicken (vanilla: 6000-12000). */
    public static final int EGG_INTERVAL = 9000;
    /** Average ticks for one wheat growth stage. */
    public static final int GROWTH_STAGE_TICKS = 36000 / 7;

    public long time;
    public final int groundY;
    public final Map<Pos, String> blocks = new HashMap<>();
    public final Map<Pos, Map<String, String>> props = new HashMap<>();
    public final Map<Pos, Integer> crops = new LinkedHashMap<>();
    public final Map<Pos, ItemCounter> containers = new HashMap<>();
    public final Map<UUID, SandboxBody> bodies = new LinkedHashMap<>();
    public final List<Pos> chickens = new ArrayList<>();
    public final List<Monster> monsters = new ArrayList<>();
    public final BlueprintLibrary blueprints = BlueprintLibrary.withDefaults();
    public CitizenScheduler scheduler = new CitizenScheduler();
    public SimulationConfig config;
    public VillageState village;
    public HypothesisSource hypotheses = HypothesisSource.FALLBACK;
    /** Chickens may only lay in these pen cells when non-null (lets a scenario keep them off the hopper). */
    public List<Pos> layingCells;
    private boolean loaded = true;
    private final Random rng;
    public int looseEggs;

    public static final class Monster {
        public Pos pos;
        public int health = 6;

        Monster(Pos pos) {
            this.pos = pos;
        }
    }

    public SandboxWorld(long seed, long startTime, int groundY, SimulationConfig config) {
        this.rng = new Random(seed);
        this.time = startTime;
        this.groundY = groundY;
        this.config = config;
    }

    // ---- scenario setup --------------------------------------------------------------------

    public void bind(VillageState v) {
        this.village = v;
        VillageSimulator.wire(v);
    }

    public ItemCounter chestAt(Pos pos) {
        blocks.put(pos, ItemIds.CHEST);
        return containers.computeIfAbsent(pos, p -> new ItemCounter(27 * 64));
    }

    public void plantField(Pos corner, int sizeX, int sizeZ, int startAge) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                Pos p = corner.offset(x, 0, z);
                blocks.put(p.below(), "minecraft:farmland");
                blocks.put(p, "minecraft:wheat");
                crops.put(p, startAge);
            }
        }
    }

    public void addChicken(Pos cell) {
        chickens.add(cell);
    }

    public Monster spawnMonster(Pos pos) {
        Monster m = new Monster(pos);
        monsters.add(m);
        return m;
    }

    /** Spawns bodies for living citizens that have none (founders, newcomers). */
    public void summonMissingBodies(Pos at) {
        for (CitizenRecord c : village.citizens().alive()) {
            if (!bodies.containsKey(c.id())) {
                bodies.put(c.id(), new SandboxBody(at));
                c.bindBody(UUID.nameUUIDFromBytes(("body-" + c.id()).getBytes()));
            }
        }
    }

    public void kill(CitizenRecord c, String cause) {
        bodies.remove(c.id());
        scheduler.forget(c.id());
        village.recordDeath(c, cause, time);
    }

    public boolean isLoadedVillage() {
        return loaded;
    }

    /** Unloading freezes the physical world; the village continues statistically. */
    public void setLoaded(boolean value) {
        if (value && !loaded) {
            scheduler = new CitizenScheduler();   // bodies reload: no stale per-entity state
        }
        loaded = value;
    }

    // ---- simulation step -------------------------------------------------------------------

    public void step() {
        time += STEP_TICKS;
        if (!loaded) {
            if (time % 200 == 0) {
                VillageSimulator.tickUnloaded(village, time, config, blueprints);
            }
            return;
        }
        growCrops();
        layEggs();
        summonMissingBodies(village.center());
        boolean night = isNight();
        for (CitizenRecord c : village.citizens().alive()) {
            SandboxBody body = bodies.get(c.id());
            if (body == null) continue;
            boolean threatened = nearestMonster(body.pos, 8) != null;
            scheduler.tick(new RoutineContext(village, c, body, this, config, blueprints), threatened, night);
            bodyBehaviour(c, body);
        }
        if (time % 20 == 0) {
            VillageSimulator.tick(village, this, config, hypotheses, blueprints);
        }
    }

    public void runTicks(long ticks) {
        long end = time + ticks;
        while (time < end) {
            step();
        }
    }

    /** The body layer: flee and defend are executed here, as SmartBrainLib behaviours do in game. */
    private void bodyBehaviour(CitizenRecord c, SandboxBody body) {
        Monster m = nearestMonster(body.pos, 16);
        if (c.currentTask() == TaskType.FLEE && m != null) {
            body.pos = body.pos.offset(Integer.signum(body.pos.x() - m.pos.x()), 0, Integer.signum(body.pos.z() - m.pos.z()));
        } else if (c.currentTask() == TaskType.DEFEND && m != null) {
            if (body.pos.distSq(m.pos) <= 2) {
                m.health -= 1 + c.skillLevel(dev.emerald.core.citizen.SkillType.COMBAT) / 3;
                if (m.health <= 0) {
                    monsters.remove(m);
                    c.addXp(dev.emerald.core.citizen.SkillType.COMBAT, 4);
                }
            } else {
                body.moveTo(m.pos, 1.2);
            }
        }
    }

    private Monster nearestMonster(Pos p, int range) {
        return monsters.stream().filter(m -> m.pos.distSq(p) <= (long) range * range)
                .min(Comparator.comparingLong(m -> m.pos.distSq(p))).orElse(null);
    }

    private void growCrops() {
        double chance = (double) STEP_TICKS / GROWTH_STAGE_TICKS;
        for (Map.Entry<Pos, Integer> e : crops.entrySet()) {
            if (e.getValue() < 7 && rng.nextDouble() < chance) {
                e.setValue(e.getValue() + 1);
                if (e.getValue() == 7) {
                    village.observations().record(ObservationType.CROP_GREW, DIM, e.getKey(), ItemIds.WHEAT,
                            witness(e.getKey()), time, null);
                }
            }
        }
    }

    /** Chickens lay; eggs over a hopper are pulled; a hopper facing down into a chest stores them. */
    private void layEggs() {
        if (village.pen() == null) return;
        double chance = (double) STEP_TICKS / EGG_INTERVAL;
        for (int i = 0; i < chickens.size(); i++) {
            if (rng.nextDouble() >= chance) continue;
            Pos cell = chickens.get(i);
            if (layingCells != null && !layingCells.isEmpty()) {
                cell = layingCells.get(rng.nextInt(layingCells.size()));
            } else {
                Box pen = village.pen();
                cell = new Pos(pen.min().x() + rng.nextInt(pen.max().x() - pen.min().x() + 1), pen.min().y(),
                        pen.min().z() + rng.nextInt(pen.max().z() - pen.min().z() + 1));
            }
            UUID w = witness(cell);
            WorldObservation spawn = village.observations().record(ObservationType.ITEM_SPAWNED, DIM, cell, ItemIds.EGG,
                    w, time, null);
            Pos floor = cell.below();
            if (ItemIds.HOPPER.equals(blocks.get(floor)) && containers.containsKey(floor)) {
                containers.get(floor).insert(ItemIds.EGG, 1);
                WorldObservation pull = village.observations().record(ObservationType.HOPPER_PULLED, DIM, floor,
                        ItemIds.EGG, w, time, spawn.id());
                // Follow the hopper chain (each hopper pushes toward its facing) to the final container.
                Pos at = floor;
                for (int hop = 0; hop < 9; hop++) {
                    Pos next = at.offset(facingOffset(props.getOrDefault(at, Map.of()).getOrDefault("facing", "down")));
                    if (!containers.containsKey(next)) break;
                    containers.get(at).extract(ItemIds.EGG, 1);
                    containers.get(next).insert(ItemIds.EGG, 1);
                    if (!ItemIds.HOPPER.equals(blocks.get(next))) {
                        village.observations().record(ObservationType.ITEM_STORED, DIM, next, ItemIds.EGG, w, time, pull.id());
                        break;
                    }
                    at = next;
                }
            } else {
                looseEggs++;
            }
        }
    }

    static Pos facingOffset(String facing) {
        return switch (facing) {
            case "east" -> new Pos(1, 0, 0);
            case "west" -> new Pos(-1, 0, 0);
            case "south" -> new Pos(0, 0, 1);
            case "north" -> new Pos(0, 0, -1);
            default -> new Pos(0, -1, 0);
        };
    }

    private UUID witness(Pos at) {
        UUID best = null;
        long bestD = 16L * 16;
        for (Map.Entry<UUID, SandboxBody> e : bodies.entrySet()) {
            long d = e.getValue().pos.distSq(at);
            if (d <= bestD) {
                best = e.getKey();
                bestD = d;
            }
        }
        return best;
    }

    // ---- WorldPort -------------------------------------------------------------------------

    @Override public long gameTime() { return time; }
    @Override public String dimension() { return DIM; }

    @Override
    public ItemStore warehouse() {
        return village.warehouse() == null ? null : containers.get(village.warehouse());
    }

    @Override
    public ItemStore containerAt(Pos pos) {
        return loaded ? containers.get(pos) : null;
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
        int seeds = 1 + rng.nextInt(3);
        into.insert(ItemIds.WHEAT, 1);
        into.insert(ItemIds.WHEAT_SEEDS, seeds);
        return Map.of(ItemIds.WHEAT, 1, ItemIds.WHEAT_SEEDS, seeds);
    }

    @Override
    public boolean replant(Pos pos, ItemStore from) {
        if (blocks.containsKey(pos) || !"minecraft:farmland".equals(blocks.get(pos.below()))) {
            return false;
        }
        if (from.extract(ItemIds.WHEAT_SEEDS, 1) == 1) {
            crops.put(pos, 0);
            blocks.put(pos, "minecraft:wheat");
            return true;
        }
        return false;
    }

    @Override
    public int countCropPlots(Pos center, int radius) {
        return (int) blocks.entrySet().stream()
                .filter(e -> e.getValue().equals("minecraft:farmland") && e.getKey().distSq(center) <= (long) radius * radius)
                .count();
    }

    @Override
    public int countAnimals(Box region, String entityId) {
        if (!"minecraft:chicken".equals(entityId)) return 0;
        return (int) chickens.stream().filter(c -> region.containsWithin(c, 1)).count();
    }

    @Override
    public boolean matches(Pos pos, BlueprintBlock block) {
        return block.blockId().equals(blocks.get(pos));
    }

    @Override
    public boolean isLoaded(Pos pos) {
        return loaded;
    }

    @Override
    public boolean place(Pos pos, BlueprintBlock block, ItemStore from) {
        if (blocks.containsKey(pos) || from.extract(block.itemId(), 1) != 1) {
            return false;
        }
        put(pos, block);
        return true;
    }

    @Override
    public boolean placeMaterialized(Pos pos, BlueprintBlock block) {
        if (blocks.containsKey(pos)) {
            return false;
        }
        put(pos, block);
        return true;
    }

    private void put(Pos pos, BlueprintBlock block) {
        blocks.put(pos, block.blockId());
        props.put(pos, block.properties());
        if (block.blockId().equals(ItemIds.CHEST)) {
            containers.put(pos, new ItemCounter(27 * 64));
        } else if (block.blockId().equals(ItemIds.HOPPER)) {
            containers.put(pos, new ItemCounter(5 * 64));
        }
    }

    @Override
    public Optional<Pos> findBuildSite(Pos center, int sizeX, int sizeZ, int minDist, int maxDist, List<Box> avoid) {
        for (int r = minDist; r <= maxDist; r += 3) {
            for (int dx = -r; dx <= r; dx += 7) {
                for (int dz : new int[]{-r, r}) {
                    Pos origin = new Pos(center.x() + dx, groundY, center.z() + dz);
                    if (siteFree(origin, sizeX, sizeZ, avoid)) return Optional.of(origin);
                    Pos swapped = new Pos(center.x() + dz, groundY, center.z() + dx);
                    if (siteFree(swapped, sizeX, sizeZ, avoid)) return Optional.of(swapped);
                }
            }
        }
        return Optional.empty();
    }

    private boolean siteFree(Pos origin, int sx, int sz, List<Box> avoid) {
        Box footprint = Box.of(origin, origin.offset(sx - 1, 3, sz - 1));
        for (Box b : avoid) {
            if (overlaps(footprint, b)) return false;
        }
        for (Pos p : blocks.keySet()) {
            if (footprint.containsWithin(p, 1) || footprint.containsWithin(p.above(), 1)) return false;
        }
        return true;
    }

    private static boolean overlaps(Box a, Box b) {
        return a.min().x() <= b.max().x() && a.max().x() >= b.min().x()
                && a.min().y() <= b.max().y() && a.max().y() >= b.min().y()
                && a.min().z() <= b.max().z() && a.max().z() >= b.min().z();
    }

    @Override
    public Optional<Pos> bodyPosition(UUID citizenId) {
        return loaded ? Optional.ofNullable(bodies.get(citizenId)).map(SandboxBody::position) : Optional.empty();
    }

    /** Walks one block per step along the largest axes. */
    public static final class SandboxBody implements BodyPort {
        public Pos pos;

        public SandboxBody(Pos pos) {
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
            pos = pos.offset(Integer.signum(target.x() - pos.x()), Integer.signum(target.y() - pos.y()),
                    Integer.signum(target.z() - pos.z()));
            return pos.distSq(target) <= reach * reach;
        }
    }

    /** Removes loose eggs counter; used by scenario reports. */
    public int drainLooseEggs() {
        int n = looseEggs;
        looseEggs = 0;
        return n;
    }

    /** Removes all monsters (e.g. dawn). */
    public void clearMonsters() {
        for (Iterator<Monster> it = monsters.iterator(); it.hasNext(); ) {
            it.next();
            it.remove();
        }
    }
}
