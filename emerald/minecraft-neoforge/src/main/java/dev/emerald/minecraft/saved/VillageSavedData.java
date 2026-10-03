package dev.emerald.minecraft.saved;

import dev.emerald.core.data.DataException;
import dev.emerald.core.village.VillageWorld;
import dev.emerald.minecraft.EmeraldMod;
import dev.emerald.minecraft.config.EmeraldConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistence bridge: one file, {@code data/emerald_villages.dat} in the overworld, holding every
 * village and citizen record. Entity NBT is never the source of truth.
 *
 * <p>If the file cannot be read (newer schema, corruption), the original tag is preserved and
 * written back untouched, and the simulation stays paused, rather than overwriting the save.
 */
public final class VillageSavedData extends SavedData {
    public static final String FILE = "emerald_villages";

    private VillageWorld world;
    private CompoundTag unreadable;
    private String unreadableReason;

    public VillageSavedData() {
        this.world = new VillageWorld(EmeraldConfig.simulation());
    }

    public static VillageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(VillageSavedData::new, VillageSavedData::load, null), FILE);
    }

    public static VillageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        VillageSavedData data = new VillageSavedData();
        try {
            data.world = VillageWorld.fromMap(NbtBridge.toMap(tag), EmeraldConfig.simulation());
        } catch (DataException | IllegalArgumentException e) {
            data.unreadable = tag.copy();
            data.unreadableReason = e.getMessage();
            EmeraldMod.LOGGER.error("Emerald could not read {}.dat ({}). The file is preserved unchanged and the "
                    + "simulation is paused for this world.", FILE, e.getMessage());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadable != null) {
            tag.merge(unreadable);
            return tag;
        }
        tag.merge(NbtBridge.toTag(world.toMap()));
        return tag;
    }

    public VillageWorld world() {
        return world;
    }

    /** Null when healthy; otherwise why the simulation is paused. */
    public String pausedReason() {
        return unreadable == null ? null : unreadableReason;
    }
}
