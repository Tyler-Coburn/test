package dev.emerald.minecraft.observe;

import dev.emerald.core.observe.ObservationType;
import dev.emerald.core.observe.WorldObservation;
import dev.emerald.core.village.VillageState;
import dev.emerald.core.world.ItemIds;
import dev.emerald.minecraft.entity.CivVillager;
import dev.emerald.minecraft.server.EmeraldServer;
import dev.emerald.minecraft.world.ContainerItemStore;
import dev.emerald.minecraft.world.MinecraftWorldPort;
import dev.emerald.minecraft.world.Positions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns real server events into ObservationBus entries. This is the only Minecraft-side writer of
 * facts. Causation is tracked: egg spawn -> hopper pull (causedBy egg) -> stored in the container the
 * hopper feeds (causedBy pull).
 *
 * <p>Not yet observed: item movement on water.
 */
public final class ObservationAdapter {
    /** Live egg item entities we saw spawn, mapped to their ITEM_SPAWNED observation. */
    private static final Map<UUID, UUID> TRACKED_EGGS = new HashMap<>();
    /** Hopper pulls waiting to see the egg arrive in the container below/beside the hopper. */
    private static final List<PendingStore> PENDING_STORES = new ArrayList<>();
    /** Last REDSTONE_SIGNAL observation per block position, so clocks cannot flood the bounded bus. */
    private static final Map<Long, Long> LAST_SIGNAL = new HashMap<>();
    private static final int SIGNAL_COOLDOWN_TICKS = 200;

    private record PendingStore(UUID villageId, BlockPos target, int baseline, UUID pullObservation, long expiresAt) {
    }

    private ObservationAdapter() {
    }

    public static void reset() {
        TRACKED_EGGS.clear();
        PENDING_STORES.clear();
        LAST_SIGNAL.clear();
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()
                || !(event.getEntity() instanceof ItemEntity item) || !item.getItem().is(Items.EGG)) {
            return;
        }
        if (level.getEntitiesOfClass(Chicken.class, item.getBoundingBox().inflate(2.0)).isEmpty()) {
            return;
        }
        Optional<VillageState> village = EmeraldServer.villageAt(level, item.blockPosition());
        if (village.isEmpty()) {
            return;
        }
        VillageState v = village.get();
        WorldObservation obs = v.observations().record(ObservationType.ITEM_SPAWNED, dim(level),
                Positions.toPos(item.blockPosition()), ItemIds.EGG, witness(level, v, item.blockPosition()),
                level.getGameTime(), null);
        TRACKED_EGGS.put(item.getUUID(), obs.id());
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        UUID spawnObs = TRACKED_EGGS.remove(item.getUUID());
        if (spawnObs == null || !item.isRemoved() || item.getRemovalReason() == null
                || !item.getRemovalReason().shouldDestroy()) {
            return;
        }
        BlockPos at = item.blockPosition();
        BlockPos hopper = level.getBlockState(at).getBlock() instanceof HopperBlock ? at
                : level.getBlockState(at.below()).getBlock() instanceof HopperBlock ? at.below() : null;
        if (hopper == null || level.getNearestPlayer(item, 1.5) instanceof Player) {
            return;
        }
        ContainerItemStore hopperStore = MinecraftWorldPort.containerAt(level, hopper);
        if (hopperStore == null || hopperStore.count(ItemIds.EGG) == 0) {
            return;
        }
        Optional<VillageState> village = EmeraldServer.villageAt(level, hopper);
        if (village.isEmpty()) {
            return;
        }
        VillageState v = village.get();
        WorldObservation pull = v.observations().record(ObservationType.HOPPER_PULLED, dim(level),
                Positions.toPos(hopper), ItemIds.EGG, witness(level, v, hopper), level.getGameTime(), spawnObs);
        // Follow the hopper chain (each hopper pushes toward its facing) to the final container.
        BlockPos target = hopper.relative(level.getBlockState(hopper).getValue(HopperBlock.FACING));
        for (int hops = 0; hops < 9 && level.getBlockState(target).getBlock() instanceof HopperBlock; hops++) {
            Direction next = level.getBlockState(target).getValue(HopperBlock.FACING);
            target = target.relative(next);
        }
        ContainerItemStore targetStore = MinecraftWorldPort.containerAt(level, target);
        if (targetStore != null) {
            PENDING_STORES.add(new PendingStore(v.id(), target, targetStore.count(ItemIds.EGG), pull.id(),
                    level.getGameTime() + 600));
        }
    }

    /** Called every 20 ticks: confirms eggs reaching the container a hopper feeds. */
    public static void poll(ServerLevel level, VillageState v) {
        Iterator<PendingStore> it = PENDING_STORES.iterator();
        while (it.hasNext()) {
            PendingStore p = it.next();
            if (!p.villageId().equals(v.id())) {
                continue;
            }
            ContainerItemStore store = MinecraftWorldPort.containerAt(level, p.target());
            if (store != null && store.count(ItemIds.EGG) > p.baseline()) {
                v.observations().record(ObservationType.ITEM_STORED, dim(level), Positions.toPos(p.target()),
                        ItemIds.EGG, witness(level, v, p.target()), level.getGameTime(), p.pullObservation());
                it.remove();
            } else if (level.getGameTime() > p.expiresAt()) {
                it.remove();
            }
        }
    }

    public static void onCropGrow(CropGrowEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState s = event.getState();
        if (!s.is(Blocks.WHEAT) || !(s.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(s)) {
            return;
        }
        EmeraldServer.villageAt(level, event.getPos()).ifPresent(v -> v.observations().record(
                ObservationType.CROP_GREW, dim(level), Positions.toPos(event.getPos()), "minecraft:wheat",
                witness(level, v, event.getPos()), level.getGameTime(), null));
    }

    public static void onPistonMoved(PistonEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        EmeraldServer.villageAt(level, event.getPos()).ifPresent(v -> v.observations().record(
                ObservationType.PISTON_MOVED, dim(level), Positions.toPos(event.getPos()), null,
                witness(level, v, event.getPos()), level.getGameTime(), null));
    }

    /**
     * A signal source (lever, button, observer, redstone wire, torch...) updated its neighbours inside
     * a village: one REDSTONE_SIGNAL per position per cooldown.
     */
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().isSignalSource()) {
            return;
        }
        BlockPos pos = event.getPos();
        long now = level.getGameTime();
        Long last = LAST_SIGNAL.get(pos.asLong());
        if (last != null && now - last < SIGNAL_COOLDOWN_TICKS) {
            return;
        }
        EmeraldServer.villageAt(level, pos).ifPresent(v -> {
            LAST_SIGNAL.put(pos.asLong(), now);
            if (LAST_SIGNAL.size() > 4096) {
                LAST_SIGNAL.clear();
            }
            v.observations().record(ObservationType.REDSTONE_SIGNAL, dim(level), Positions.toPos(pos),
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString(),
                    witness(level, v, pos), now, null);
        });
    }

    /** Nearest living citizen body within 16 blocks, if any. */
    private static UUID witness(ServerLevel level, VillageState v, BlockPos at) {
        CivVillager best = null;
        double bestDist = 16 * 16;
        for (CivVillager body : EmeraldServer.bodies()) {
            if (body.level() != level || body.citizenId() == null) {
                continue;
            }
            double d = body.blockPosition().distSqr(at);
            if (d < bestDist && v.citizens().get(body.citizenId()).isPresent()) {
                best = body;
                bestDist = d;
            }
        }
        return best == null ? null : best.citizenId();
    }

    private static String dim(ServerLevel level) {
        return level.dimension().location().toString();
    }
}
