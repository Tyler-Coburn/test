package dev.emerald.minecraft.server;

import dev.emerald.ai.AiConfig;
import dev.emerald.ai.AiHypothesisSource;
import dev.emerald.ai.OllamaProvider;
import dev.emerald.core.SimulationConfig;
import dev.emerald.core.citizen.CitizenRecord;
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
import dev.emerald.minecraft.saved.NbtBridge;
import dev.emerald.minecraft.saved.VillageSavedData;
import dev.emerald.minecraft.world.MinecraftWorldPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side coordinator. Owns the loaded VillageWorld, the map of loaded bodies, the job scheduler
 * and the hypothesis sources, and drives them from the server tick:
 * every 10 ticks each loaded citizen steps its job; every 20 ticks each village runs its slow tick.
 */
public final class EmeraldServer {
    public static final int VILLAGE_RADIUS = 96;
    private static final int JOB_INTERVAL = 10;
    private static final int VILLAGE_INTERVAL = 20;

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

    /** Loads data/emerald/structure/*.nbt we ship; falls back to the built-in hut if hut.nbt is absent. */
    private static BlueprintLibrary loadBlueprints(MinecraftServer server) {
        BlueprintLibrary lib = BlueprintLibrary.withDefaults();
        for (String name : new String[]{"hut", "hopper_pen"}) {
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath("emerald", "structure/" + name + ".nbt");
            Optional<Resource> res = server.getResourceManager().getResource(file);
            if (res.isEmpty()) {
                EmeraldMod.LOGGER.info("Emerald: {} not found; {}", file,
                        name.equals("hut") ? "using the built-in fallback hut" : "design blueprint not available yet");
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

    // --- accessors used by commands and adapters -------------------------------------------------

    public static Optional<VillageWorld> world() {
        return data == null || data.pausedReason() != null ? Optional.empty() : Optional.of(data.world());
    }

    public static String pausedReason() {
        if (data == null) {
            return "server not started";
        }
        return data.pausedReason();
    }

    public static void markDirty() {
        if (data != null) {
            data.setDirty();
        }
    }

    public static BlueprintLibrary blueprints() {
        return blueprints;
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
        if (citizenId == null) {
            EmeraldMod.LOGGER.warn("Emerald: discarding a citizen body with no record link at {}", body.blockPosition());
            event.setCanceled(true);
            return;
        }
        Optional<CitizenRecord> record = world().flatMap(w -> w.citizen(citizenId));
        if (record.isEmpty() || !record.get().alive()) {
            EmeraldMod.LOGGER.warn("Emerald: discarding body for {} citizen {}", record.isEmpty() ? "unknown" : "dead", citizenId);
            event.setCanceled(true);
            return;
        }
        CivVillager existing = BODIES.get(citizenId);
        if (existing != null && existing != body && existing.isAlive() && !existing.isRemoved()) {
            EmeraldMod.LOGGER.warn("Emerald: duplicate body for {}; keeping the loaded one", record.get().name());
            event.setCanceled(true);
            return;
        }
        BODIES.put(citizenId, body);
        record.get().bindBody(body.getUUID());
        markDirty();
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof CivVillager body) || body.citizenId() == null) {
            return;
        }
        if (BODIES.get(body.citizenId()) == body) {
            BODIES.remove(body.citizenId());
            SCHEDULER.forget(body.citizenId());
            world().flatMap(w -> w.citizen(body.citizenId()))
                    .filter(c -> body.getUUID().equals(c.bodyUuid()))
                    .ifPresent(CitizenRecord::unbindBody);
            markDirty();
        }
    }

    /** A body's death is a validated world event: the citizen dies, the record stays. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof CivVillager body)
                || body.citizenId() == null) {
            return;
        }
        Level level = body.level();
        world().ifPresent(w -> w.villageOf(body.citizenId()).ifPresent(v ->
                w.citizen(body.citizenId()).ifPresent(c -> {
                    v.recordDeath(c, event.getSource().getMsgId(), level.getGameTime());
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
        if (!jobTick && !villageTick) {
            return;
        }
        SimulationConfig cfg = w.get().config();
        for (VillageState v : w.get().villages()) {
            Optional<ServerLevel> level = levelOf(v);
            if (level.isEmpty()) {
                continue;
            }
            MinecraftWorldPort port = new MinecraftWorldPort(level.get(), v);
            if (jobTick) {
                stepCitizens(level.get(), v, port, cfg);
            }
            if (villageTick) {
                ObservationAdapter.poll(level.get(), v);
                ItemStore warehouse = port.warehouse();
                VillageSimulator.tick(v, level.get().getGameTime(), warehouse, cfg, hypothesisSource(v));
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
            SCHEDULER.tick(new RoutineContext(v, c, new CivBodyPort(e), port, cfg, blueprints), threatened, level.isNight());
            e.setFleeing(c.currentNeed() == Need.FLEE);
            e.setTask(c.currentTask());
            if (c.currentTask() == TaskType.IDLE || c.currentTask() == TaskType.PATROL) {
                e.clearWalkGoal();
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
