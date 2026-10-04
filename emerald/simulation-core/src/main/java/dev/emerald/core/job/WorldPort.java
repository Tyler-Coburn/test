package dev.emerald.core.job;

import dev.emerald.core.construction.BlueprintBlock;
import dev.emerald.core.item.ItemStore;
import dev.emerald.core.world.Box;
import dev.emerald.core.world.Pos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative world access for the simulation. Every method performs or checks a real
 * world change; callers record observations only after these confirm success.
 */
public interface WorldPort {
    long gameTime();

    String dimension();

    /** True during the night part of the day cycle. */
    default boolean isNight() {
        long t = gameTime() % 24000;
        return t >= 13000 && t < 23000;
    }

    /** The registered warehouse inventory, or null if unset or not loaded. */
    ItemStore warehouse();

    /** Any container (chest, barrel, hopper) at a position, or null if absent or not loaded. */
    ItemStore containerAt(Pos pos);

    Optional<Pos> findMatureCrop(Pos center, int radius);

    /** Breaks a mature crop and moves its real drops into {@code into}. Empty if nothing was harvested. */
    Map<String, Integer> harvestCrop(Pos pos, ItemStore into);

    /** Replants the crop at {@code pos}, consuming one seed from {@code from}. */
    boolean replant(Pos pos, ItemStore from);

    /** Planted crop plots (any growth stage) within the radius: the farm's real size. */
    int countCropPlots(Pos center, int radius);

    /** Living animals of {@code entityId} inside the region (e.g. chickens in the pen). */
    int countAnimals(Box region, String entityId);

    /** True if the world already holds {@code block} at {@code pos}. */
    boolean matches(Pos pos, BlueprintBlock block);

    /** True if the position is loaded and can be read or edited this tick. */
    boolean isLoaded(Pos pos);

    /** Places {@code block}, consuming one {@code block.itemId()} from {@code from}. */
    boolean place(Pos pos, BlueprintBlock block, ItemStore from);

    /**
     * Places a block whose material was already consumed by offline simulation (and reconciled
     * against the real warehouse by the Materializer). Refuses if the spot is no longer free.
     */
    boolean placeMaterialized(Pos pos, BlueprintBlock block);

    /**
     * A flat, free, loaded spot for a {@code sizeX x sizeZ} footprint between minDist and maxDist
     * blocks from {@code center}, not overlapping {@code avoid}. Returns the origin (lowest wall layer).
     */
    Optional<Pos> findBuildSite(Pos center, int sizeX, int sizeZ, int minDist, int maxDist, List<Box> avoid);

    /** Current position of a citizen's loaded body, if any. */
    Optional<Pos> bodyPosition(UUID citizenId);
}
