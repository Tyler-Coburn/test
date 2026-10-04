package dev.emerald.minecraft.server;

import dev.emerald.ai.AiConfig;
import dev.emerald.ai.AiHypothesisSource;
import dev.emerald.ai.OllamaProvider;
import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
import dev.emerald.core.citizen.SkillType;
import dev.emerald.core.construction.BlueprintLibrary;
import dev.emerald.core.construction.StructureNbtParser;
import dev.emerald.core.data.DataException;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.job.CitizenScheduler;
import dev.emerald.core.job.RoutineContext;
import dev.emerald.core.job.TaskType;
import dev.emerald.core.research.HypothesisSource;
import dev.emerald.core.utility.Need;
import dev.emerald.core.village.VillageSimulator;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.minecraft.EmeraldMod;
import dev.emerald.minecraft.config.EmeraldConfig;
import dev.emerald.minecraft.entity.CivBodyPort;
import dev.emerald.minecraft.entity.CivVillager;
import dev.emerald.minecraft.observe.ObservationAdapter;
import dev.emerald.minecraft.registry.EmeraldEntities;
import dev.emerald.minecraft.saved.NbtBridge;
import dev.emerald.minecraft.saved.VillageSavedData;
import dev.emerald.minecraft.world.MinecraftWorldPort;
import dev.emerald.minecraft.world.Positions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side coordinator. Owns the loaded VillageWorld, the map of loaded bodies, the job scheduler
 * and the hypothesis sources, and drives them from the server tick:
 * <ul>
 *   <li>every 10 ticks, each loaded citizen steps its job;</li>
 *   <li>every 20 ticks, each loaded village runs its slow tick (requests, experiments, director,
 *       materialisation of offline results);</li>
 *   <li>every 200 ticks, each unloaded village advances its statistical offline simulation.</li>
 * </ul>
 *
 * <p>Body rule: a record's {@code bodyUuid} names its one canonical body. Unloading keeps the link;
 * a body with another UUID is a stale duplicate and is discarded on join. Citizens with no body at
 * all (newcomers) get one summoned at the village centre while the village is loaded.
 */
public final class EmeraldServer {
    public static final int VILLAGE_RADIUS = 96;
    private static final int JOB_INTERVAL = 10;
    private static final int VILLAGE_INTERVAL = 20;
    private static final int OFFLINE_INTERVAL = 200;

    private static MinecraftServer server;
    private static VillageSavedData data;
    private static BlueprintLibrary blueprints = BlueprintLibrary.withDefaults();
    private static final CitizenScheduler SCHEDULER = new CitizenScheduler();
    private static final Map<UUID, CivVillager> BODIES = new HashMap<>();
    private static final Map<UUID, HypothesisSource> HYPOTHESES = new HashMap<>();
    private static OllamaProvider ollama;

    private EmeraldServer() {
    }

    // --- lifecycle -----------------------------------------------------------------------------

    public static void onServerStarted(ServerStartedEvent event) {
        ensureLoaded(event.getServer());
        blueprints = loadBlueprints(server);
        AiConfig ai = EmeraldConfig.ai();
        ollama = ai.enabled() ? new OllamaProvider(ai) : null;
        EmeraldMod.LOGGER.info("Emerald: {} village(s) loaded; AI hypotheses {}", data.world().villages().size(),
                ai.enabled() ? "enabled via " + ollama.name() : "disabled (deterministic table)");
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        if (ollama != null) {
            ollama.shutdown();
        }
        ollama = null;
        server = null;
        data = null;
        BODIES.clear();
        HYPOTHESES.clear();
        ObservationAdapter.reset();
    }

    /**
     * Loads the SavedData on first use. Spawn-chunk entities can join before ServerStartedEvent, so
     * body binding must not depend on that event having fired.
     */
    private static void ensureLoaded(MinecraftServer srv) {
        if (data == null && srv != null) {
            server = srv;
            data = VillageSavedData.get(srv);
            data.world().villages().forEach(VillageSimulator::wire);
        }
    }

    /** Ships data/emerald/structure/{hut,chicken_collector}.nbt when present; built-ins otherwise. */
    private static BlueprintLibrary loadBlueprints(MinecraftServer server) {
        BlueprintLibrary lib = BlueprintLibrary.withDefaults();
        for (String name : new String[]{"hut", "chicken_collector"}) {
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath("emerald", "structure/" + name + ".nbt");
            Optional<Resource> res = server.getResourceManager().getResource(file);
            if (res.isEmpty()) {
                EmeraldMod.LOGGER.info("Emerald: {} not found; using the built-in emerald:{} blueprint", file, name);
                continue;
            }
            try (InputStream in = res.get().open()) {
                CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                lib.register(StructureNbtParser.parse("emerald:" + name, NbtBridge.toMap(tag)), file.toString());
                EmeraldMod.LOGGER.info("Emerald: loaded blueprint emerald:{} from {}", name, file);
            } catch (IOException | DataException e) {
                EmeraldMod.LOGGER.error("Emerald: could not read {}: {}", file, e.getMessage());
            }
        }
        return lib;
    }

    // --- accessors used by commands, adapters and GameTests ----------------------------------------

    public static Optional<VillageWorld> world() {
        return data == null || data.pausedReason() != null ? Optional.empty() : Optional.of(data.world());
    }

    public static String pausedReason() {
        return data == null ? "server not started" : data.pausedReason();
    }

    public static void markDirty() {
        if (data != null) {
            data.setDirty();
        }
    }

    public static BlueprintLibrary blueprints() {
        return blueprints;
    }

    public static CitizenScheduler scheduler() {
        return SCHEDULER;
    }

    public static Optional<CivVillager> body(UUID citizenId) {
        CivVillager e = BODIES.get(citizenId);
        return e != null && e.isAlive() && !e.isRemoved() ? Optional.of(e) : Optional.empty();
    }

    public static Collection<CivVillager> bodies() {
        return Collections.unmodifiableCollection(BODIES.values());
    }

    public static Optional<ServerLevel> levelOf(VillageState v) {
        if (server == null) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(v.dimension());
        return id == null ? Optional.empty()
                : Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, id)));
    }

    public static Optional<VillageState> villageAt(ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        return world().flatMap(w -> w.villages().stream()
                .filter(v -> v.dimension().equals(dim))
                .filter(v -> {
                    long dx = v.center().x() - pos.getX();
                    long dz = v.center().z() - pos.getZ();
                    return dx * dx + dz * dz <= (long) VILLAGE_RADIUS * VILLAGE_RADIUS;
                })
                .findFirst());
    }

    /** True while the village centre is loaded and entity-ticking (players nearby). */
    public static boolean isActive(ServerLevel level, VillageState v) {
        BlockPos c = Positions.toBlockPos(v.center());
        return level.isLoaded(c) && level.isPositionEntityTicking(c);
    }

    /** Spawns a body for a citizen and lets the join handler bind it. */
    public static Optional<CivVillager> summonBody(ServerLevel level, CitizenRecord c, BlockPos at) {
        CivVillager body = EmeraldEntities.CIV_VILLAGER.get().create(level);
        if (body == null) {
            return Optional.empty();
        }
        body.setCitizenId(c.id());
        body.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        body.setCustomName(Component.literal(c.name() + " (" + c.role().name().toLowerCase(Locale.ROOT) + ")"));
        return level.addFreshEntity(body) ? Optional.of(body) : Optional.empty();
    }

    /** Clears the body link of citizens whose body is not loaded, so they can be re-summoned. */
    public static void forgetMissingBodies(VillageState v) {
        for (CitizenRecord c : v.citizens().alive()) {
            if (body(c.id()).isEmpty()) {
                c.unbindBody();
            }
        }
        markDirty();
    }

    /** GameTest support: start from an empty civilisation. */
    public static void resetForTests() {
        world().ifPresent(VillageWorld::clear);
        BODIES.values().forEach(b -> b.discard());
        BODIES.clear();
        HYPOTHESES.clear();
        ObservationAdapter.reset();
        markDirty();
    }

    // --- body binding -------------------------------------------------------------------------

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof CivVillager body)) {
            return;
        }
        ensureLoaded(level.getServer());
        if (pausedReason() != null) {
            return; // save unreadable: never delete bodies we cannot check
        }
        UUID citizenId = body.citizenId();
        Optional<CitizenRecord> record = citizenId == null ? Optional.empty() : world().flatMap(w -> w.citizen(citizenId));
        if (record.isEmpty() || !record.get().alive()) {
            EmeraldMod.LOGGER.warn("Emerald: discarding body of {} citizen {}",
                    record.isEmpty() ? "unknown" : "dead", citizenId);
            event.setCanceled(true);
            return;
        }
        CitizenRecord c = record.get();
        if (c.bodyUuid() != null && !c.bodyUuid().equals(body.getUUID())) {
            EmeraldMod.LOGGER.warn("Emerald: discarding stale duplicate body of {}", c.name());
            event.setCanceled(true);
            return;
        }
        c.bindBody(body.getUUID());
        BODIES.put(citizenId, body);
        markDirty();
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof CivVillager body) || body.citizenId() == null) {
            return;
        }
        if (BODIES.get(body.citizenId()) == body) {
            BODIES.remove(body.citizenId());
            SCHEDULER.forget(body.citizenId());
        }
    }

    /** A body's death is a validated world event: the citizen dies, the record stays. Kills train guards. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Monster && event.getSource().getEntity() instanceof CivVillager killer
                && killer.citizenId() != null) {
            world().flatMap(w -> w.citizen(killer.citizenId())).ifPresent(c -> c.addXp(SkillType.COMBAT, 4));
            return;
        }
        if (!(event.getEntity() instanceof CivVillager body) || body.citizenId() == null) {
            return;
        }
        long time = body.level().getGameTime();
        world().ifPresent(w -> w.villageOf(body.citizenId()).ifPresent(v ->
                w.citizen(body.citizenId()).ifPresent(c -> {
                    v.recordDeath(c, event.getSource().getMsgId(), time);
                    markDirty();
                })));
    }

    // --- ticking ------------------------------------------------------------------------------

    public static void onServerTick(ServerTickEvent.Post event) {
        Optional<VillageWorld> w = world();
        if (w.isEmpty()) {
            return;
        }
        long tick = event.getServer().getTickCount();
        boolean jobTick = tick % JOB_INTERVAL == 0;
        boolean villageTick = tick % VILLAGE_INTERVAL == 0;
        boolean offlineTick = tick % OFFLINE_INTERVAL == 0;
        if (!jobTick && !villageTick) {
            return;
        }
        SimulationConfig cfg = w.get().config();
        for (VillageState v : w.get().villages()) {
            Optional<ServerLevel> level = levelOf(v);
            if (level.isEmpty()) {
                continue;
            }
            if (!isActive(level.get(), v)) {
                if (offlineTick) {
                    VillageSimulator.tickUnloaded(v, level.get().getGameTime(), cfg, blueprints);
                }
                continue;
            }
            MinecraftWorldPort port = new MinecraftWorldPort(level.get(), v);
            if (jobTick) {
                stepCitizens(level.get(), v, port, cfg);
            }
            if (villageTick) {
                ObservationAdapter.poll(level.get(), v);
                VillageSimulator.tick(v, port, cfg, hypothesisSource(v), blueprints);
                summonNewcomers(level.get(), v);
            }
        }
        markDirty();
    }

    private static void stepCitizens(ServerLevel level, VillageState v, MinecraftWorldPort port, SimulationConfig cfg) {
        for (CitizenRecord c : v.citizens().alive()) {
            Optional<CivVillager> body = body(c.id());
            if (body.isEmpty() || body.get().level() != level) {
                continue;
            }
            CivVillager e = body.get();
            boolean threatened = e.senseThreat();
            SCHEDULER.tick(new RoutineContext(v, c, new CivBodyPort(e), port, cfg, blueprints), threatened, port.isNight());
            e.setFleeing(c.currentNeed() == Need.FLEE);
            e.setTask(c.currentTask());
            if (c.currentTask() == TaskType.IDLE || c.currentTask() == TaskType.DEFEND) {
                e.clearWalkGoal();
            }
        }
    }

    /** Newcomers (no body link at all) get a body at the centre while the village is loaded. */
    private static void summonNewcomers(ServerLevel level, VillageState v) {
        for (CitizenRecord c : v.citizens().alive()) {
            if (c.bodyUuid() == null && body(c.id()).isEmpty()) {
                BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        Positions.toBlockPos(v.center()));
                summonBody(level, c, at);
            }
        }
    }

    private static HypothesisSource hypothesisSource(VillageState v) {
        if (ollama == null) {
            return HypothesisSource.FALLBACK;
        }
        return HYPOTHESES.computeIfAbsent(v.id(), id -> new AiHypothesisSource(ollama, EmeraldConfig.ai(), village ->
                levelOf(village).map(l -> new MinecraftWorldPort(l, village).warehouse())
                        .map(ItemStore::contents).orElse(Map.of())));
    }
}
